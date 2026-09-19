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

public class ChamsPipeline {
    private static final int UNIFORM_SIZE = 128;
    public static org.joml.Matrix4f capturedInvViewProj;

    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "chams_post"))
                    .withVertexShader(Identifier.of("minar", "chams_post_vertex"))
                    .withFragmentShader(Identifier.of("minar", "chams_post_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static SimpleFramebuffer maskFramebuffer;
    private static SimpleFramebuffer friendMaskFramebuffer;
    private static GpuBuffer[] uniformBuffers = new GpuBuffer[4];
    private static int uniformBufferIndex = 0;
    private static GpuBuffer dummyVertexBuffer;
    private static boolean renderingMask;
    private static boolean entityFrameStarted;
    private static boolean entityMaskPending;
    private static boolean friendEntityMaskPending;

    private ChamsPipeline() {
    }

    public static Framebuffer getMaskFramebuffer() {
        return getMaskFramebuffer(false);
    }

    private static Framebuffer getMaskFramebuffer(boolean friendMask) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) return null;

        int width = main.textureWidth;
        int height = main.textureHeight;
        SimpleFramebuffer framebuffer = friendMask ? friendMaskFramebuffer : maskFramebuffer;
        if (maskFramebuffer == null) {
            maskFramebuffer = new SimpleFramebuffer("minar_chams_mask", width, height, true);
        }
        if (friendMaskFramebuffer == null) {
            friendMaskFramebuffer = new SimpleFramebuffer("minar_chams_friend_mask", width, height, true);
        }

        framebuffer = friendMask ? friendMaskFramebuffer : maskFramebuffer;
        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }

        return framebuffer;
    }

    private static void clearMask() {
        clearMask(false);
    }

    private static void clearMask(boolean friendMask) {
        Framebuffer framebuffer = getMaskFramebuffer(friendMask);
        if (framebuffer == null || framebuffer.getColorAttachment() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(framebuffer.getColorAttachment(), 0);
        if (framebuffer.getDepthAttachment() != null) {
            encoder.clearDepthTexture(framebuffer.getDepthAttachment(), 1.0);
        }
    }

    public static void renderMask(Runnable renderer, boolean respectDepth) {
        clearMask();
        if (respectDepth) {
            copyMainDepthToMask();
        }
        renderIntoMask(renderer);
        entityMaskPending = true;
    }

    public static void renderEntityMask(Runnable renderer, boolean respectDepth) {
        renderEntityMask(renderer, respectDepth, false);
    }

    public static void renderFriendEntityMask(Runnable renderer, boolean respectDepth) {
        renderEntityMask(renderer, respectDepth, true);
    }

    private static void renderEntityMask(Runnable renderer, boolean respectDepth, boolean friendMask) {
        if (!entityFrameStarted) {
            clearMask(false);
            clearMask(true);
            if (respectDepth) {
                copyMainDepthToMask(false);
                copyMainDepthToMask(true);
            }
            entityFrameStarted = true;
        }
        renderIntoMask(renderer, friendMask);
        if (friendMask) {
            friendEntityMaskPending = true;
        } else {
            entityMaskPending = true;
        }
    }

    private static void renderIntoMask(Runnable renderer) {
        renderIntoMask(renderer, false);
    }

    private static void renderIntoMask(Runnable renderer, boolean friendMask) {
        Framebuffer framebuffer = getMaskFramebuffer(friendMask);
        if (framebuffer == null || framebuffer.getColorAttachmentView() == null) return;

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        try {
            renderingMask = true;
            RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();
            renderer.run();
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            renderingMask = false;
        }
    }

    public static boolean isRenderingMask() {
        return renderingMask;
    }

    public static boolean hasEntityMask() {
        return entityMaskPending || friendEntityMaskPending;
    }

    public static boolean hasNormalEntityMask() {
        return entityMaskPending;
    }

    public static boolean hasFriendEntityMask() {
        return friendEntityMaskPending;
    }

    public static void finishEntityFrame() {
        entityFrameStarted = false;
        entityMaskPending = false;
        friendEntityMaskPending = false;
    }

    private static void copyMainDepthToMask() {
        copyMainDepthToMask(false);
    }

    private static void copyMainDepthToMask(boolean friendMask) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Framebuffer mask = getMaskFramebuffer(friendMask);
        if (main != null && mask != null && main.getDepthAttachment() != null && mask.getDepthAttachment() != null) {
            mask.copyDepthFrom(main);
        }
    }

    public static void draw(Color color, float fillAlpha, int shaderMode) {
        draw(color, fillAlpha, shaderMode, 0, 0.0f, 0.0f, false, false);
    }

    public static void draw(Color color, float fillAlpha, int shaderMode, boolean useOffsets) {
        draw(color, fillAlpha, shaderMode, 0, 0.0f, 0.0f, false, useOffsets);
    }

    public static void draw(Color color, float fillAlpha, int shaderMode,
                            int outlineType, float outlineWidth, float glowStrength) {
        draw(color, fillAlpha, shaderMode, outlineType, outlineWidth, glowStrength, false, false);
    }

    public static void drawFriend(Color color, float fillAlpha, int shaderMode) {
        draw(color, fillAlpha, shaderMode, 0, 0.0f, 0.0f, true, false);
    }

    public static void drawFriend(Color color, float fillAlpha, int shaderMode, boolean useOffsets) {
        draw(color, fillAlpha, shaderMode, 0, 0.0f, 0.0f, true, useOffsets);
    }

    public static void drawFriend(Color color, float fillAlpha, int shaderMode,
                                  int outlineType, float outlineWidth, float glowStrength) {
        draw(color, fillAlpha, shaderMode, outlineType, outlineWidth, glowStrength, true, false);
    }

    private static void draw(Color color, float fillAlpha, int shaderMode,
                             int outlineType, float outlineWidth, float glowStrength, boolean friendMask, boolean useOffsets) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Framebuffer mask = getMaskFramebuffer(friendMask);
        if (main == null || mask == null || main.getColorAttachmentView() == null || mask.getColorAttachmentView() == null) {
            return;
        }

        boolean useShader = shaderMode >= 0;

        ensureBuffers();
        GpuBuffer uniformBuffer = uniformBuffers[uniformBufferIndex];
        if (uniformBuffer == null || dummyVertexBuffer == null) return;
        uniformBufferIndex = (uniformBufferIndex + 1) % uniformBuffers.length;

        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        buffer.putFloat(main.textureWidth).putFloat(main.textureHeight).putFloat(0f).putFloat(0f);
        buffer.putFloat(color.getRed() / 255f).putFloat(color.getGreen() / 255f).putFloat(color.getBlue() / 255f).putFloat(1f);
        buffer.putFloat(Math.clamp(fillAlpha, 0f, 1f)).putFloat(useOffsets ? 1f : 0f).putFloat(useShader ? 1f : 0f).putFloat((System.currentTimeMillis() % 100000L) / 1000f);
        buffer.putFloat((float) shaderMode)
                .putFloat((float) outlineType)
                .putFloat(Math.clamp(outlineWidth, 0.0f, 16.0f));
        buffer.putFloat(Math.clamp(glowStrength, 0.0f, 4.0f));
        if (capturedInvViewProj != null) {
            float[] mat = new float[16];
            capturedInvViewProj.get(mat);
            for (float f : mat) buffer.putFloat(f);
        } else {
            for (int i=0; i<16; i++) buffer.putFloat(0f);
        }
        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:chams_post",
                main.getColorAttachmentView(),
                OptionalInt.empty(),
                main.getDepthAttachmentView(),
                OptionalDouble.empty())) {
            pass.setPipeline(COMPOSITE_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.bindTexture("Sampler0", mask.getColorAttachmentView(), sampler);
            pass.bindTexture("Sampler1", main.getDepthAttachmentView(), sampler);
            pass.draw(0, 3);
        }
    }

    private static void ensureBuffers() {
        for (int i = 0; i < uniformBuffers.length; i++) {
            if (uniformBuffers[i] == null) {
                int index = i;
                uniformBuffers[i] = RenderSystem.getDevice().createBuffer(
                        () -> "minar:chams_uniforms_" + index,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_SIZE);
            }
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:chams_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
        }
    }

    public static void shutdown() {
        if (maskFramebuffer != null) {
            maskFramebuffer.delete();
            maskFramebuffer = null;
        }
        if (friendMaskFramebuffer != null) {
            friendMaskFramebuffer.delete();
            friendMaskFramebuffer = null;
        }
        for (int i = 0; i < uniformBuffers.length; i++) {
            if (uniformBuffers[i] != null) {
                uniformBuffers[i].close();
                uniformBuffers[i] = null;
            }
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
    }
}
