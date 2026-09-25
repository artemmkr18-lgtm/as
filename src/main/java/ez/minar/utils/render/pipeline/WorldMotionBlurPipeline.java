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

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class WorldMotionBlurPipeline {
    private static final int UNIFORM_SIZE = 384;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "world_motion_blur"))
                    .withVertexShader(Identifier.of("minar", "world_motion_blur_vertex"))
                    .withFragmentShader(Identifier.of("minar", "world_motion_blur_fragment"))
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
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private WorldMotionBlurPipeline() {
    }

    public static void render(
            Matrix4f proj,
            Matrix4f invProj,
            Matrix4f viewRot,
            Matrix4f prevProj,
            Matrix4f prevViewRot,
            Vec3d camPos,
            Vec3d prevCamPos,
            float fov,
            float prevFov,
            float blendFactor,
            int samples,
            float algorithm,
            float maxVelocity
    ) {
        if (blendFactor <= 0.001f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null || fb.getDepthAttachment() == null) {
            return;
        }

        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;
        if (fbWidth <= 0 || fbHeight <= 0) return;

        ensureResources(fbWidth, fbHeight);
        if (uniformBuffer == null || dummyVertexBuffer == null || uniformData == null || copyTexture == null || copyTextureView == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorAttachment(),
                copyTexture,
                0, 0, 0, 0, 0,
                fbWidth, fbHeight
        );

        uniformData.clear();

        // mat4 uInvProjection (64 bytes, 0..63)
        writeMatrix(uniformData, invProj);
        // mat4 uProjection (64 bytes, 64..127)
        writeMatrix(uniformData, proj);
        // mat4 uViewRot (64 bytes, 128..191)
        writeMatrix(uniformData, viewRot);
        // mat4 uPrevProjection (64 bytes, 192..255)
        writeMatrix(uniformData, prevProj);
        // mat4 uPrevViewRot (64 bytes, 256..319)
        writeMatrix(uniformData, prevViewRot);

        // vec4 uCameraPos (16 bytes, 320..335)
        uniformData.putFloat((float) camPos.x).putFloat((float) camPos.y).putFloat((float) camPos.z).putFloat(fov);
        // vec4 uPrevCameraPos (16 bytes, 336..351)
        uniformData.putFloat((float) prevCamPos.x).putFloat((float) prevCamPos.y).putFloat((float) prevCamPos.z).putFloat(prevFov);
        // vec4 uScreen (16 bytes, 352..367)
        uniformData.putFloat((float) fbWidth).putFloat((float) fbHeight).putFloat(1.0f / fbWidth).putFloat(1.0f / fbHeight);
        // vec4 uParams (16 bytes, 368..383)
        uniformData.putFloat(blendFactor).putFloat((float) samples).putFloat(algorithm).putFloat(maxVelocity);

        uniformData.flip();
        encoder.writeToBuffer(uniformBuffer.slice(), uniformData);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:world_motion_blur",
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
        if (uniformData == null) {
            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        }
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:world_motion_blur_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:world_motion_blur_dummy_vertex",
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
                    () -> "minar:world_motion_blur_color_copy",
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
}
