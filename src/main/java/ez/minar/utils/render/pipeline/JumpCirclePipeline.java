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
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class JumpCirclePipeline {
    public static final int MAX_CIRCLES = 12;

    // Layout std140:
    // mat4 InvViewProj: 64 bytes (offset 0)
    // vec4 CircleCount: 16 bytes (offset 64)
    // vec4 CircleCenter[12]: 192 bytes (offset 80)
    // vec4 CircleParams[12]: 192 bytes (offset 272)
    // vec4 CircleColor[12]:  192 bytes (offset 464)
    // Total: 656 bytes (41 * 16 bytes)
    private static final int UNIFORM_SIZE = 656;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "jump_shockwave"))
                    .withVertexShader(Identifier.of("minar", "jump_shockwave_vertex"))
                    .withFragmentShader(Identifier.of("minar", "jump_shockwave_fragment"))
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

    private JumpCirclePipeline() {}

    public static void render(Matrix4f invViewProj, List<CircleInstance> circles) {
        if (circles == null || circles.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null || fb.getDepthAttachment() == null) return;
        if (fb.textureWidth <= 0 || fb.textureHeight <= 0) return;

        int count = Math.min(circles.size(), MAX_CIRCLES);
        if (count <= 0) return;

        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;

        ensureResources(fbWidth, fbHeight);
        if (copyTexture == null || copyTextureView == null) return;
        if (uniformBuffer == null || dummyVertexBuffer == null || uniformData == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // 1. Copy current frame to copyTexture ONCE
        encoder.copyTextureToTexture(fb.getColorAttachment(), copyTexture, 0, 0, 0, 0, 0, fbWidth, fbHeight);

        // 2. Pack uniforms for all active circles into a single UBO upload
        uniformData.clear();
        writeMatrix(uniformData, invViewProj);
        uniformData.putFloat((float) count).putFloat(0f).putFloat(0f).putFloat(0f);

        // CircleCenter array (12 * vec4 = 192 bytes)
        for (int i = 0; i < MAX_CIRCLES; i++) {
            if (i < count) {
                CircleInstance c = circles.get(i);
                uniformData.putFloat(c.cx()).putFloat(c.cy()).putFloat(c.cz()).putFloat(0f);
            } else {
                uniformData.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
            }
        }

        // CircleParams array (12 * vec4 = 192 bytes)
        for (int i = 0; i < MAX_CIRCLES; i++) {
            if (i < count) {
                CircleInstance c = circles.get(i);
                uniformData.putFloat(c.radius()).putFloat(c.thickness()).putFloat(c.strength()).putFloat(0f);
            } else {
                uniformData.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
            }
        }

        // CircleColor array (12 * vec4 = 192 bytes)
        for (int i = 0; i < MAX_CIRCLES; i++) {
            if (i < count) {
                CircleInstance c = circles.get(i);
                uniformData.putFloat(c.r()).putFloat(c.g()).putFloat(c.b()).putFloat(c.intensity());
            } else {
                uniformData.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
            }
        }
        uniformData.flip();

        encoder.writeToBuffer(uniformBuffer.slice(), uniformData);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        // 3. Exactly ONE RenderPass directly to framebuffer, NO ping-pong passes!
        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:jump_shockwave",
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
        ensureBuffers();
        ensureTextures(width, height);
    }

    private static void ensureBuffers() {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:jump_shockwave_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (uniformData == null) {
            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:jump_shockwave_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
        }
    }

    private static void ensureTextures(int width, int height) {
        if (copyTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        closeTextures();

        copyTexture = RenderSystem.getDevice().createTexture(
                () -> "minar:jump_shockwave_copy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width, height, 1, 1);
        copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private static void closeTextures() {
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

    public static void shutdown() {
        closeTextures();
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (uniformData != null) {
            MemoryUtil.memFree(uniformData);
            uniformData = null;
        }
    }

    public record CircleInstance(
            float cx, float cy, float cz,
            float radius, float thickness, float strength,
            float r, float g, float b, float intensity
    ) {}
}
