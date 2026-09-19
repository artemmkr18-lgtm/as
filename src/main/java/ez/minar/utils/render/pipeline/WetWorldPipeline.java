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
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class WetWorldPipeline {
    private static final int UNIFORM_SIZE = 288;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "wet_world"))
                    .withVertexShader(Identifier.of("minar", "wet_world_vertex"))
                    .withFragmentShader(Identifier.of("minar", "wet_world_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static GpuTexture copyTexture;
    private static GpuTextureView copyTextureView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private WetWorldPipeline() {
    }

    public static void render(
            Matrix4f proj,
            Matrix4f invProj,
            Matrix4f viewRot,
            Vec3d camPos,
            float fov,
            float reflectionStrength,
            float darkening,
            float time,
            int qualityIdx,
            boolean ripples,
            float rippleSpeed,
            float rainGradient,
            float fogStart,
            float fogEnd,
            float fogActive,
            Color fogColor,
            float fogBrightness
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null || fb.getDepthAttachment() == null) {
            return;
        }

        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;
        if (fbWidth <= 0 || fbHeight <= 0) return;

        ensureResources(fbWidth, fbHeight);
        if (uniformBuffer == null || dummyVertexBuffer == null || copyTexture == null || copyTextureView == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorAttachment(),
                copyTexture,
                0, 0, 0, 0, 0,
                fbWidth, fbHeight
        );

        ByteBuffer buf = MemoryUtil.memAlloc(UNIFORM_SIZE);

        // mat4 uInvProjection (64 bytes)
        writeMatrix(buf, invProj);
        // mat4 uProjection (64 bytes)
        writeMatrix(buf, proj);
        // mat4 uViewRot (64 bytes)
        writeMatrix(buf, viewRot);

        // vec4 uCameraPos (16 bytes): xyz = camPos, w = fov
        buf.putFloat((float) camPos.x).putFloat((float) camPos.y).putFloat((float) camPos.z).putFloat(fov);
        // vec4 uScreen (16 bytes): xy = screen size, zw = unused
        buf.putFloat((float) fbWidth).putFloat((float) fbHeight).putFloat(0f).putFloat(0f);
        // vec4 uParams (16 bytes): x = reflectionStrength, y = darkening, z = time, w = fogBrightness
        buf.putFloat(reflectionStrength).putFloat(darkening).putFloat(time).putFloat(fogBrightness);
        // vec4 uQualityParams (16 bytes): x = qualityIdx, y = ripples, z = rippleSpeed, w = rainGradient
        buf.putFloat((float) qualityIdx).putFloat(ripples ? 1.0f : 0.0f).putFloat(rippleSpeed).putFloat(rainGradient);
        // vec4 uFogParams (16 bytes): x = fogStart, y = fogEnd, z = fogActive, w = unused
        buf.putFloat(fogStart).putFloat(fogEnd).putFloat(fogActive).putFloat(0f);
        // vec4 uFogColor (16 bytes): rgba
        Color c = fogColor != null ? fogColor : new Color(0, 0, 0, 0);
        buf.putFloat(c.getRed() / 255f)
           .putFloat(c.getGreen() / 255f)
           .putFloat(c.getBlue() / 255f)
           .putFloat(c.getAlpha() / 255f);

        buf.flip();
        encoder.writeToBuffer(uniformBuffer.slice(), buf);
        MemoryUtil.memFree(buf);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:wet_world",
                fb.getColorAttachmentView(),
                OptionalInt.empty(),
                null,
                OptionalDouble.empty())) {

            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("Sampler0", copyTextureView, sampler);
            pass.bindTexture("Sampler1", fb.getDepthAttachmentView(), sampler);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.draw(0, 3);
        }
    }

    private static void writeMatrix(ByteBuffer buf, Matrix4f mat) {
        buf.putFloat(mat.m00()).putFloat(mat.m01()).putFloat(mat.m02()).putFloat(mat.m03());
        buf.putFloat(mat.m10()).putFloat(mat.m11()).putFloat(mat.m12()).putFloat(mat.m13());
        buf.putFloat(mat.m20()).putFloat(mat.m21()).putFloat(mat.m22()).putFloat(mat.m23());
        buf.putFloat(mat.m30()).putFloat(mat.m31()).putFloat(mat.m32()).putFloat(mat.m33());
    }

    private static void ensureResources(int width, int height) {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:wet_world_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:wet_world_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
        }
        if (copyTexture == null || lastWidth != width || lastHeight != height) {
            if (copyTextureView != null) {
                copyTextureView = null;
            }
            if (copyTexture != null) {
                copyTexture.close();
                copyTexture = null;
            }
            copyTexture = RenderSystem.getDevice().createTexture(
                    () -> "minar:wet_world_color_copy",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8,
                    width, height, 1, 1);
            copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);
            lastWidth = width;
            lastHeight = height;
        }
    }

    public static void shutdown() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (copyTexture != null) {
            copyTexture.close();
            copyTexture = null;
        }
        copyTextureView = null;
    }
}
