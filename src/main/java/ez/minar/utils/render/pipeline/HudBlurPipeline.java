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
import java.awt.Color;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class HudBlurPipeline {
    private static final int UNIFORM_SIZE = 256;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuTexture copyTexture;
    private static GpuTextureView copyTextureView;
    private static int lastWidth;
    private static int lastHeight;
    private static ByteBuffer uniformData;
    private static int backdropDepth;
    private static boolean backdropValid;
    private static Framebuffer backdropFramebuffer;
    private static GpuTexture backdropColorAttachment;
    private static int backdropWidth;
    private static int backdropHeight;

    /** Captures the background before panels render; nested scopes reuse that capture. */
    public static void beginBackdrop() {
        if (backdropDepth++ > 0) return;
        backdropValid = false;
        backdropFramebuffer = null;
        backdropColorAttachment = null;
        try {
            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
            if (framebuffer == null) return;
            GpuTexture colorAttachment = framebuffer.getColorAttachment();
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            if (colorAttachment == null || width <= 0 || height <= 0) return;

            ensureCopyTexture(width, height);
            if (copyTexture == null || copyTextureView == null) return;
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    colorAttachment, copyTexture, 0, 0, 0, 0, 0, width, height);
            backdropFramebuffer = framebuffer;
            backdropColorAttachment = colorAttachment;
            backdropWidth = width;
            backdropHeight = height;
            backdropValid = true;
        } catch (RuntimeException ignored) {
            // Never recapture panel contents or use an older capture after failure.
            backdropValid = false;
        }
    }

    public static void endBackdrop() {
        if (backdropDepth == 0 || --backdropDepth > 0) return;
        backdropValid = false;
        backdropFramebuffer = null;
        backdropColorAttachment = null;
        backdropWidth = 0;
        backdropHeight = 0;
    }

    public static void init() {
        if (pipeline != null) return;

        pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("minar", "hud_blur"))
                .withVertexShader(Identifier.of("minar", "hud_blur_vertex"))
                .withFragmentShader(Identifier.of("minar", "hud_blur_fragment"))
                .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();

        uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "HudBlurPipeline Uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, float tl, float tr, float br, float bl,
                            float blurRadius, float alpha, Color tint, float z) {
        draw(matrix, x, y, width, height, tl, tr, br, bl, blurRadius, 1.0f, alpha, tint, z);
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, float tl, float tr, float br, float bl,
                            float blurRadius, float smoothness, float alpha, Color tint, float z) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            if (backdropDepth > 0) backdropValid = false;
            return;
        }

        if (pipeline == null) init();
        if (pipeline == null || uniformBuffer == null) return;

        int fbWidth = framebuffer.textureWidth;
        int fbHeight = framebuffer.textureHeight;
        int screenWidth = RenderUtil.getFixedScaledWidth();
        int screenHeight = RenderUtil.getFixedScaledHeight();
        GpuTextureView backdropView;
        if (backdropDepth > 0) {
            if (!backdropValid || !isBackdropCurrent(framebuffer, fbWidth, fbHeight)) {
                // Once invalidated, skip all remaining draws in this scope.
                backdropValid = false;
                return;
            }
            backdropView = copyTextureView;
        } else {
            if (fbWidth <= 0 || fbHeight <= 0) return;
            ensureCopyTexture(fbWidth, fbHeight);
            if (copyTexture == null || copyTextureView == null) return;
            CommandEncoder captureEncoder = RenderSystem.getDevice().createCommandEncoder();
            captureEncoder.copyTextureToTexture(framebuffer.getColorAttachment(), copyTexture, 0, 0, 0, 0, 0, fbWidth, fbHeight);
            backdropView = copyTextureView;
        }
        if (backdropView == null) return;

        float red = tint.getRed() / 255.0f;
        float green = tint.getGreen() / 255.0f;
        float blue = tint.getBlue() / 255.0f;
        float tintAlpha = tint.getAlpha() / 255.0f;
        ByteBuffer buffer = uniformData;
        if (buffer == null) {
            buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
            uniformData = buffer;
        }
        buffer.clear();
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());

        buffer.position(64);
        buffer.putFloat(x).putFloat(y).putFloat(width).putFloat(height);
        buffer.putFloat(screenWidth).putFloat(screenHeight).putFloat(0f).putFloat(0f);
        buffer.putFloat(tr).putFloat(br).putFloat(tl).putFloat(bl);
        buffer.putFloat(blurRadius).putFloat(smoothness).putFloat(Math.clamp(alpha, 0f, 1f)).putFloat(tintAlpha);
        buffer.putFloat(red).putFloat(green).putFloat(blue).putFloat(0f);
        buffer.putFloat(z).putFloat(0f).putFloat(0f).putFloat(0f);
        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "HudBlurPipeline",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.bindTexture("Sampler0", backdropView, sampler);
            pass.draw(0, 6);
        }
    }

    /** A scoped capture is only reusable while the target framebuffer, its size and its color attachment are unchanged. */
    private static boolean isBackdropCurrent(Framebuffer framebuffer, int width, int height) {
        return framebuffer == backdropFramebuffer
                && width == backdropWidth
                && height == backdropHeight
                && framebuffer.getColorAttachment() == backdropColorAttachment
                && copyTextureView != null;
    }

    private static void ensureCopyTexture(int width, int height) {
        if (copyTexture != null && copyTextureView != null && width == lastWidth && height == lastHeight) return;

        if (copyTextureView != null) {
            copyTextureView.close();
            copyTextureView = null;
        }
        if (copyTexture != null) {
            copyTexture.close();
            copyTexture = null;
        }

        copyTexture = RenderSystem.getDevice().createTexture(
                () -> "minar:hud_blur_copy",
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
        backdropValid = false;
        backdropFramebuffer = null;
        backdropColorAttachment = null;
        backdropWidth = 0;
        backdropHeight = 0;
        backdropDepth = 0;
        if (uniformData != null) {
            MemoryUtil.memFree(uniformData);
            uniformData = null;
        }
    }
}
