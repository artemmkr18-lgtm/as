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
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class TexturePipeline {

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static final int UNIFORM_SIZE = 256;

    public static void init() {
        if (pipeline != null)
            return;

        pipeline = RenderPipeline.builder()
                .withLocation(Identifier.of("minar", "texture"))
                .withVertexShader(Identifier.of("minar", "texture_vertex"))
                .withFragmentShader(Identifier.of("minar", "texture_fragment"))
                .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build();

        uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "TexturePipeline Uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, GpuTextureView textureView, int color,
                            float radius, float z) {
        draw(matrix, x, y, width, height, textureView, color, radius, z, 0f, 0f, 1f, 1f);
    }

    public static void draw(Matrix4f matrix, float x, float y, float size, GpuTextureView textureView, int color,
                            float radius, float z) {
        draw(matrix, x, y, size, size, textureView, color, radius, z, 0f, 0f, 1f, 1f);
    }

    public static void draw(Matrix4f matrix, float x, float y, float size, GpuTextureView textureView, int color,
                            float radius, float z, float u, float v, float uvWidth, float uvHeight) {
        draw(matrix, x, y, size, size, textureView, color, radius, z, u, v, uvWidth, uvHeight, true);
    }

    public static void draw(Matrix4f matrix, float x, float y, float size, GpuTextureView textureView, int color,
                            float radius, float z, float u, float v, float uvWidth, float uvHeight, boolean linearFilter) {
        draw(matrix, x, y, size, size, textureView, color, radius, z, u, v, uvWidth, uvHeight, linearFilter);
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, GpuTextureView textureView, int color,
                            float radius, float z, float u, float v, float uvWidth, float uvHeight) {
        draw(matrix, x, y, width, height, textureView, color, radius, z, u, v, uvWidth, uvHeight, true);
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, GpuTextureView textureView, int color,
                            float radius, float z, float u, float v, float uvWidth, float uvHeight, boolean linearFilter) {
        if (pipeline == null)
            init();
        if (pipeline == null || uniformBuffer == null || textureView == null)
            return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());

        buffer.position(64);
        buffer.putFloat(x).putFloat(y).putFloat(width).putFloat(height); // uRect (vec4)
        buffer.putFloat(r).putFloat(g).putFloat(b).putFloat(a); // uColor (vec4)
        buffer.putFloat(radius).putFloat(0).putFloat(0).putFloat(0); // uRadius_Padding (vec4)
        buffer.putFloat(z).putFloat(0).putFloat(0).putFloat(0); // uZ_Padding (vec4)
        buffer.putFloat(u).putFloat(v).putFloat(uvWidth).putFloat(uvHeight); // uUvRect (vec4)
        buffer.flip();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(linearFilter ? FilterMode.LINEAR : FilterMode.NEAREST);
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "TexturePipeline",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.of(1.0))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.bindTexture("Sampler0", textureView, sampler);
            pass.draw(0, 6);
        }
    }

    public static void shutdown() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
    }
}
