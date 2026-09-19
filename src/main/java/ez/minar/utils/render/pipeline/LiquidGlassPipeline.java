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
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import ez.minar.utils.render.RenderUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class LiquidGlassPipeline {
    private static final int UNIFORM_SIZE = 256;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuTexture copyTexture;
    private static GpuTextureView copyTextureView;
    private static int lastWidth;
    private static int lastHeight;

    public static void init() {
        if (pipeline != null) return;

        pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("minar", "liquid_glass"))
                .withVertexShader(Identifier.of("minar", "liquid_glass_vertex"))
                .withFragmentShader(Identifier.of("minar", "liquid_glass_fragment"))
                .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();

        uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "LiquidGlassPipeline Uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, float radius,
                            float strength, float alpha, int tint, float z) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) return;

        if (pipeline == null) init();
        if (pipeline == null || uniformBuffer == null) return;

        int fbWidth = framebuffer.textureWidth;
        int fbHeight = framebuffer.textureHeight;
        int screenWidth = RenderUtil.getFixedScaledWidth();
        int screenHeight = RenderUtil.getFixedScaledHeight();
        ensureCopyTexture(fbWidth, fbHeight);
        if (copyTexture == null || copyTextureView == null) return;

        float red = ((tint >> 16) & 0xFF) / 255.0f;
        float green = ((tint >> 8) & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;
        float tintAlpha = ((tint >> 24) & 0xFF) / 255.0f;
        float time = (System.currentTimeMillis() % 120_000L) / 1000.0f;

        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());

        buffer.position(64);
        buffer.putFloat(x).putFloat(y).putFloat(width).putFloat(height);
        buffer.putFloat(screenWidth).putFloat(screenHeight).putFloat(0f).putFloat(0f);
        buffer.putFloat(radius).putFloat(radius).putFloat(radius).putFloat(radius);
        buffer.putFloat(time).putFloat(Math.clamp(strength, 0f, 1f)).putFloat(Math.clamp(alpha, 0f, 1f)).putFloat(tintAlpha);
        buffer.putFloat(red).putFloat(green).putFloat(blue).putFloat(0f);
        buffer.putFloat(z).putFloat(0f).putFloat(0f).putFloat(0f);
        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(framebuffer.getColorAttachment(), copyTexture, 0, 0, 0, 0, 0, fbWidth, fbHeight);
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "LiquidGlassPipeline",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.of(1.0))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.bindTexture("Sampler0", copyTextureView, sampler);
            pass.draw(0, 6);
        }
    }

    private static void ensureCopyTexture(int width, int height) {
        if (copyTexture != null && width == lastWidth && height == lastHeight) return;

        if (copyTextureView != null) {
            copyTextureView.close();
            copyTextureView = null;
        }
        if (copyTexture != null) {
            copyTexture.close();
            copyTexture = null;
        }

        copyTexture = RenderSystem.getDevice().createTexture(
                () -> "minar:liquid_glass_copy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width, height, 1, 1);
        copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);
        lastWidth = width;
        lastHeight = height;
    }

    public static void shutdown() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (copyTextureView != null) {
            copyTextureView.close();
            copyTextureView = null;
        }
        if (copyTexture != null) {
            copyTexture.close();
            copyTexture = null;
        }
        lastWidth = 0;
        lastHeight = 0;
    }
}
