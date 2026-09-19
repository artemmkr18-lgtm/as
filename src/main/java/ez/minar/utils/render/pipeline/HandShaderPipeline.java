package ez.minar.utils.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
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

    private static SimpleFramebuffer handMaskFramebuffer;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static boolean renderingMask = false;

    private static float prevYaw = 0f;
    private static float prevPitch = 0f;
    private static boolean hasPrevCam = false;
    private static float slashAnim = 0f;
    private static boolean wasSwinging = false;
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
                            float glow, float smoke, Color mainColor, Color tipColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Framebuffer mask = getMaskFramebuffer();
        if (main == null || main.getColorAttachmentView() == null || mask == null || mask.getColorAttachmentView() == null) {
            return;
        }

        ensureBuffers();

        int width = main.textureWidth;
        int heightPixels = main.textureHeight;
        float time = (float) ((System.nanoTime() - startNanoTime) / 1_000_000_000.0);

        // Расчет движения камеры для репроекции шлейфа/огня
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

        // Взмах рукой и активность движения
        boolean swinging = client.player != null && client.player.handSwinging;
        if (swinging && !wasSwinging) {
            slashAnim = 1.0f;
        }
        wasSwinging = swinging;
        slashAnim = Math.max(0.0f, slashAnim - 0.05f);

        float activity = 0.0f;
        if (client.player != null) {
            activity = (float) Math.min(1.0, client.player.getVelocity().horizontalLength() * 3.0);
            if (client.player.isUsingItem()) activity = Math.max(activity, 0.7f);
        }

        // Запись uniform буфера
        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);

        // vec4 uScreen: width, height, time, mode
        buffer.putFloat((float) width).putFloat((float) heightPixels).putFloat(time).putFloat((float) mode);

        // vec4 uColor: r, g, b, alpha
        buffer.putFloat(mainColor.getRed() / 255f)
                .putFloat(mainColor.getGreen() / 255f)
                .putFloat(mainColor.getBlue() / 255f)
                .putFloat(mainColor.getAlpha() / 255f);

        // vec4 uColor2: r, g, b, 1.0
        buffer.putFloat(tipColor.getRed() / 255f)
                .putFloat(tipColor.getGreen() / 255f)
                .putFloat(tipColor.getBlue() / 255f)
                .putFloat(1.0f);

        // vec4 uParams: intensity, speed, height, glow
        buffer.putFloat(intensity).putFloat(speed).putFloat(height).putFloat(glow);

        // vec4 uExtra: wind, wave, smoke, activity
        buffer.putFloat(wind).putFloat(wave).putFloat(smoke).putFloat(activity);

        // vec4 uMotion: camShiftX, camShiftY, slash, 0.0
        buffer.putFloat(camShiftX).putFloat(camShiftY).putFloat(slashAnim).putFloat(0.0f);

        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        RenderPipeline pipeline = (mode == 0 || mode == 3) ? FIRE_PIPELINE : BLEND_PIPELINE;

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
            pass.bindTexture("Sampler1", main.getDepthAttachmentView(), sampler);
            pass.draw(0, 3);
        }
    }

    private static void ensureBuffers() {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:hands_shader_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
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
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
    }
}
