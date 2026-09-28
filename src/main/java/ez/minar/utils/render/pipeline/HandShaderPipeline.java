package ez.minar.utils.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class HandShaderPipeline {
    private static final int UNIFORM_SIZE = 112; // 7 * vec4
    private static final int TRAIL_UNIFORM_SIZE = 32; // 2 * vec4

    private static final RenderPipeline FIRE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "hands_fire_pipeline"))
                    .withVertexShader(Identifier.of("minar", "chams_post_vertex"))
                    .withFragmentShader(Identifier.of("minar", "hands_shader_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final RenderPipeline BLEND_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "hands_blend_pipeline"))
                    .withVertexShader(Identifier.of("minar", "chams_post_vertex"))
                    .withFragmentShader(Identifier.of("minar", "hands_shader_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    // Feedback-пасс: тянет предыдущий кадр шлейфа, затушевляет и подмешивает свежую маску.
    private static final RenderPipeline TRAIL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "hands_trail_pipeline"))
                    .withVertexShader(Identifier.of("minar", "chams_post_vertex"))
                    .withFragmentShader(Identifier.of("minar", "hands_trail_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static SimpleFramebuffer handMaskFramebuffer;

    // Пингпонг для шлейфа: читаем trailRead, пишем trailWrite, потом меняем местами.
    private static SimpleFramebuffer trailRead;
    private static SimpleFramebuffer trailWrite;

    // Копия цвета основного кадра: её отдаём в Sampler1, чтобы не читать текстуру,
    // в которую в этот же момент идёт запись.
    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneCopyView;
    private static int copyWidth = -1;
    private static int copyHeight = -1;

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer trailUniformBuffer;
    private static GpuBuffer dummyVertexBuffer;

    private static ByteBuffer uniformScratch;
    private static ByteBuffer trailUniformScratch;

    private static boolean renderingMask = false;
    private static boolean maskCaptured = false;

    private static float prevYaw = 0f;
    private static float prevPitch = 0f;
    private static boolean hasPrevCam = false;
    private static float slashAnim = 0f;
    private static boolean wasSwinging = false;
    private static long lastFrameNanos = 0L;
    private static final long startNanoTime = System.nanoTime();

    private HandShaderPipeline() {
    }

    public static Framebuffer getMaskFramebuffer() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) return null;

        int width = main.textureWidth;
        int height = main.textureHeight;
        if (handMaskFramebuffer == null) {
            handMaskFramebuffer = new SimpleFramebuffer("minar_hand_mask", width, height, true);
        }
        if (handMaskFramebuffer.textureWidth != width || handMaskFramebuffer.textureHeight != height) {
            handMaskFramebuffer.resize(width, height);
        }
        return handMaskFramebuffer;
    }

    public static void renderMask(Runnable renderer) {
        Framebuffer framebuffer = getMaskFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(framebuffer.getColorAttachment(), 0);
        if (framebuffer.getDepthAttachment() != null) {
            encoder.clearDepthTexture(framebuffer.getDepthAttachment(), 1.0);
        }

        GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
        try {
            renderingMask = true;
            RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();
            renderer.run();
            maskCaptured = true;
        } finally {
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
            renderingMask = false;
        }
    }

    public static boolean isRenderingMask() {
        return renderingMask;
    }

    public static void draw(int mode, float intensity, float speed, float height, float wind, float wave,
                            float glow, float smoke, float trailDecay, float rise, Color mainColor, Color tipColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) return;

        int width = main.textureWidth;
        int heightPixels = main.textureHeight;

        // Без захвата маски в этом кадре (третье лицо, скрытые руки) шлейф тянуть
        // не от чего — гасим накопленное, чтобы старый след не всплыл позже.
        if (!maskCaptured) {
            clearTrail();
            return;
        }
        maskCaptured = false;

        Framebuffer mask = getMaskFramebuffer();
        if (mask == null || mask.getColorAttachmentView() == null) {
            clearTrail();
            return;
        }

        ensureBuffers();
        ensureSceneCopy(width, heightPixels);
        ensureTrailTargets(width, heightPixels);
        if (sceneCopyView == null || trailRead == null || trailWrite == null) return;

        float dt = frameDelta();
        float time = (float) ((System.nanoTime() - startNanoTime) / 1_000_000_000.0);
        float[] camShift = computeCameraShift(client, width, heightPixels);

        boolean swinging = client.player != null && client.player.handSwinging;
        if (swinging && !wasSwinging) {
            slashAnim = 1.0f;
        }
        wasSwinging = swinging;
        slashAnim = Math.max(0.0f, slashAnim - dt * 3.0f);

        float activity = 0.0f;
        if (client.player != null) {
            activity = (float) Math.min(1.0, client.player.getVelocity().horizontalLength() * 3.0);
            if (client.player.isUsingItem()) activity = Math.max(activity, 0.7f);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // Сцену копируем до записи в неё: пост-шейдер читает копию как Sampler1.
        encoder.copyTextureToTexture(main.getColorAttachment(), sceneCopy, 0, 0, 0, 0, 0, width, heightPixels);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        // --- 1. Feedback-пасс: старый кадр шлейфа + свежая маска ---
        float decay = (float) Math.pow(Math.clamp(trailDecay, 0.0, 0.999), dt * 60.0);
        float shiftX = (float) (camShift[0] * 0.5);
        float shiftY = (float) (camShift[1] * 0.5);

        trailUniformScratch.clear();
        trailUniformScratch.putFloat((float) width).putFloat((float) heightPixels).putFloat(time).putFloat(decay);
        trailUniformScratch.putFloat(shiftX).putFloat(shiftY).putFloat(rise).putFloat(0f);
        trailUniformScratch.flip();
        encoder.writeToBuffer(trailUniformBuffer.slice(), trailUniformScratch);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:hands_trail",
                trailWrite.getColorAttachmentView(),
                OptionalInt.empty(),
                trailWrite.getDepthAttachmentView(),
                OptionalDouble.empty())) {
            pass.setPipeline(TRAIL_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.setUniform("Uniforms", trailUniformBuffer);
            pass.bindTexture("Sampler0", trailRead.getColorAttachmentView(), sampler);
            pass.bindTexture("Sampler1", mask.getColorAttachmentView(), sampler);
            pass.draw(0, 3);
        }

        // trailWrite теперь актуальный шлейф.
        SimpleFramebuffer swap = trailRead;
        trailRead = trailWrite;
        trailWrite = swap;

        // --- 2. Пост-пасс поверх кадра ---
        uniformScratch.clear();

        // vec4 uScreen: width, height, time, mode
        uniformScratch.putFloat((float) width).putFloat((float) heightPixels).putFloat(time).putFloat((float) mode);

        // vec4 uColor: r, g, b, alpha
        uniformScratch.putFloat(mainColor.getRed() / 255f)
                .putFloat(mainColor.getGreen() / 255f)
                .putFloat(mainColor.getBlue() / 255f)
                .putFloat(mainColor.getAlpha() / 255f);

        // vec4 uColor2: r, g, b, 1.0
        uniformScratch.putFloat(tipColor.getRed() / 255f)
                .putFloat(tipColor.getGreen() / 255f)
                .putFloat(tipColor.getBlue() / 255f)
                .putFloat(1.0f);

        // vec4 uParams: intensity, speed, height, glow
        uniformScratch.putFloat(intensity).putFloat(speed).putFloat(height).putFloat(glow);

        // vec4 uExtra: wind, wave, smoke, activity
        uniformScratch.putFloat(wind).putFloat(wave).putFloat(smoke).putFloat(activity);

        // vec4 uMotion: camShiftX, camShiftY, slash, 0.0
        uniformScratch.putFloat(camShift[0]).putFloat(camShift[1]).putFloat(slashAnim).putFloat(0f);

        uniformScratch.flip();
        encoder.writeToBuffer(uniformBuffer.slice(), uniformScratch);

        RenderPipeline pipeline = (mode == 0 || mode == 3) ? FIRE_PIPELINE : BLEND_PIPELINE;

        // Sampler0 — всегда свежая маска рук (жёсткая, привязана к рукам).
        // Sampler1 — для стекла это копия сцены, для остальных режимов накопленный шлейф.
        // Так широкие сглаживания не растягивают старый след по всему экрану.
        GpuTextureView secondary = (mode == 2) ? sceneCopyView : trailRead.getColorAttachmentView();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:hands_shader_post",
                main.getColorAttachmentView(),
                OptionalInt.empty(),
                main.getDepthAttachmentView(),
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.bindTexture("Sampler0", mask.getColorAttachmentView(), sampler);
            pass.bindTexture("Sampler1", secondary, sampler);
            pass.draw(0, 3);
        }
    }

    private static float frameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 1.0f / 60.0f;
        }
        float dt = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        return Math.clamp(dt, 1.0f / 240.0f, 1.0f / 15.0f);
    }

    private static float[] computeCameraShift(MinecraftClient client, int width, int heightPixels) {
        float camShiftX = 0f;
        float camShiftY = 0f;
        if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            float yaw = client.gameRenderer.getCamera().getYaw();
            float pitch = client.gameRenderer.getCamera().getPitch();
            if (hasPrevCam) {
                float dYaw = (yaw - prevYaw) % 360f;
                if (dYaw >= 180f) dYaw -= 360f;
                if (dYaw < -180f) dYaw += 360f;
                float dPitch = pitch - prevPitch;

                double fovDeg = client.options.getFov().getValue();
                float fovRad = (float) Math.toRadians(Math.max(1.0, fovDeg));
                float tanHalf = (float) Math.tan(fovRad * 0.5f);
                float aspect = heightPixels > 0 ? (float) width / (float) heightPixels : 1f;

                camShiftX = (float) Math.toRadians(dYaw) / (2f * tanHalf * aspect);
                camShiftY = -(float) Math.toRadians(dPitch) / (2f * tanHalf);
                camShiftX = Math.clamp(camShiftX, -0.25f, 0.25f);
                camShiftY = Math.clamp(camShiftY, -0.25f, 0.25f);
            }
            prevYaw = yaw;
            prevPitch = pitch;
            hasPrevCam = true;
        }
        return new float[]{camShiftX, camShiftY};
    }

    private static void ensureSceneCopy(int width, int height) {
        if (sceneCopy != null && width == copyWidth && height == copyHeight) return;

        if (sceneCopyView != null) {
            sceneCopyView.close();
            sceneCopyView = null;
        }
        if (sceneCopy != null) {
            sceneCopy.close();
            sceneCopy = null;
        }

        sceneCopy = RenderSystem.getDevice().createTexture(
                () -> "minar:hands_scene_copy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width, height, 1, 1);
        sceneCopyView = RenderSystem.getDevice().createTextureView(sceneCopy);
        copyWidth = width;
        copyHeight = height;
    }

    private static void ensureTrailTargets(int width, int height) {
        if (trailRead != null && trailRead.textureWidth == width && trailRead.textureHeight == height) return;

        deleteTrailTargets();
        trailRead = new SimpleFramebuffer("minar_hand_trail_read", width, height, false);
        trailWrite = new SimpleFramebuffer("minar_hand_trail_write", width, height, false);
        clearTrail();
    }

    private static void clearTrail() {
        if (trailRead == null || trailWrite == null) return;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (trailRead.getColorAttachment() != null) {
            encoder.clearColorTexture(trailRead.getColorAttachment(), 0);
        }
        if (trailWrite.getColorAttachment() != null) {
            encoder.clearColorTexture(trailWrite.getColorAttachment(), 0);
        }
    }

    private static void deleteTrailTargets() {
        if (trailRead != null) {
            trailRead.delete();
            trailRead = null;
        }
        if (trailWrite != null) {
            trailWrite.delete();
            trailWrite = null;
        }
    }

    private static void ensureBuffers() {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:hands_shader_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (trailUniformBuffer == null) {
            trailUniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:hands_trail_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    TRAIL_UNIFORM_SIZE);
        }
        if (uniformScratch == null) {
            uniformScratch = MemoryUtil.memAlloc(UNIFORM_SIZE);
        }
        if (trailUniformScratch == null) {
            trailUniformScratch = MemoryUtil.memAlloc(TRAIL_UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:hands_shader_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
        }
    }

    public static void shutdown() {
        if (handMaskFramebuffer != null) {
            handMaskFramebuffer.delete();
            handMaskFramebuffer = null;
        }
        deleteTrailTargets();
        if (sceneCopyView != null) {
            sceneCopyView.close();
            sceneCopyView = null;
        }
        if (sceneCopy != null) {
            sceneCopy.close();
            sceneCopy = null;
        }
        copyWidth = -1;
        copyHeight = -1;
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (trailUniformBuffer != null) {
            trailUniformBuffer.close();
            trailUniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (uniformScratch != null) {
            MemoryUtil.memFree(uniformScratch);
            uniformScratch = null;
        }
        if (trailUniformScratch != null) {
            MemoryUtil.memFree(trailUniformScratch);
            trailUniformScratch = null;
        }
    }
}
