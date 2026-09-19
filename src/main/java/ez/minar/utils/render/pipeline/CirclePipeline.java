package ez.minar.utils.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class CirclePipeline {

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static final int UNIFORM_SIZE = 128;

    public static void init() {
        if (pipeline != null)
            return;

        try {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "circle"))
                    .withVertexShader(Identifier.of("minar", "circle_vertex"))
                    .withFragmentShader(Identifier.of("minar", "circle_fragment"))
                    .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build();

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "CirclePipeline Uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        } catch (Exception ignored) {
        }
    }

    public static void draw(Matrix4f matrix, float cx, float cy, float radius, float thickness, float z, Color color) {
        draw(matrix, cx, cy, radius, thickness, 0f, 1f, z, color);
    }

    public static void draw(Matrix4f matrix, float cx, float cy, float radius, float thickness,
                            float startAngle, float progress, float z, Color color) {
        if (pipeline == null)
            init();
        if (pipeline == null || uniformBuffer == null)
            return;

        float r = color.getRed() / 255.0f;
        float g = color.getGreen() / 255.0f;
        float b = color.getBlue() / 255.0f;
        float a = color.getAlpha() / 255.0f;

        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());

        buffer.position(64);
        buffer.putFloat(cx).putFloat(cy).putFloat(radius).putFloat(z);

        buffer.putFloat(thickness);
        buffer.putFloat(startAngle);
        buffer.putFloat(Math.max(0f, Math.min(1f, progress)));
        buffer.putFloat(0f);

        buffer.position(96);
        buffer.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "CirclePipeline",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.of(1.0))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Uniforms", uniformBuffer);
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
