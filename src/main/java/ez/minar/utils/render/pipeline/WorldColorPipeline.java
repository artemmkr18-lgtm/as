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
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class WorldColorPipeline {
    private static final int UNIFORM_SIZE = 48;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "world_color"))
                    .withVertexShader(Identifier.of("minar", "world_color_vertex"))
                    .withFragmentShader(Identifier.of("minar", "world_color_fragment"))
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
    private static ByteBuffer uniformData;

    private static GpuTexture copyTexture;
    private static GpuTextureView copyTextureView;
    private static int lastWidth = 0;
    private static int lastHeight = 0;

    private WorldColorPipeline() {
    }

    public static void render(int color, float saturation, float brightness,
                              float strength, float contrast, float snowMask,
                              float hazeStrength, float hazeDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null || fb.getDepthAttachment() == null) return;
        if (fb.textureWidth <= 0 || fb.textureHeight <= 0) return;

        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;

        ensureResources(fbWidth, fbHeight);
        if (copyTexture == null || copyTextureView == null) return;
        if (uniformBuffer == null || dummyVertexBuffer == null || uniformData == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), copyTexture, 0, 0, 0, 0, 0, fbWidth, fbHeight);

        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;

        uniformData.clear();
        // vec4 Tint (r, g, b, 1.0)
        uniformData.putFloat(r).putFloat(g).putFloat(b).putFloat(1.0f);
        // vec4 Params (saturation, brightness, strength, contrast)
        uniformData.putFloat(clamp(saturation, 0f, 2f))
                .putFloat(clamp(brightness, 0.1f, 2f))
                .putFloat(clamp(strength, 0f, 1f))
                .putFloat(clamp(contrast, 0.5f, 1.5f));
        // vec4 Snow (snowMask, hazeStrength, hazeDistance, 0.0)
        uniformData.putFloat(clamp(snowMask, 0f, 1f))
                .putFloat(clamp(hazeStrength, 0f, 1f))
                .putFloat(clamp(hazeDistance, 1f, 256f))
                .putFloat(0.0f);

        uniformData.flip();
        encoder.writeToBuffer(uniformBuffer.slice(), uniformData);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:world_color",
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

    private static void ensureResources(int width, int height) {
        if (uniformData == null) {
            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        }
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:world_color_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:world_color_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
        }
        if (copyTexture == null || lastWidth != width || lastHeight != height) {
            if (copyTextureView != null) copyTextureView = null;
            if (copyTexture != null) {
                copyTexture.close();
                copyTexture = null;
            }
            copyTexture = RenderSystem.getDevice().createTexture(
                    () -> "minar:world_color_scene_copy",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8,
                    width, height, 1, 1);
            copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);
            lastWidth = width;
            lastHeight = height;
        }
    }

    public static void shutdown() {
        if (uniformData != null) {
            MemoryUtil.memFree(uniformData);
            uniformData = null;
        }
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
