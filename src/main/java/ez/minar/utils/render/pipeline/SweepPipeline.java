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

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class SweepPipeline {
    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static final int UNIFORM_SIZE = 128; 

    public static void init() {
        if (pipeline != null) return;
        try {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "sweep"))
                    .withVertexShader(Identifier.of("minar", "sweep_vertex"))
                    .withFragmentShader(Identifier.of("minar", "sweep_fragment"))
                    .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build();

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "SweepPipeline Uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        } catch (Exception e) {}
    }

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, float tl, float tr, float br, float bl, float z, float progress, float angle, float opacity) {
        if (pipeline == null) init();
        if (pipeline == null || uniformBuffer == null) return;

        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());

        buffer.position(64);
        buffer.putFloat(x).putFloat(y).putFloat(width).putFloat(height); // uRect
        buffer.putFloat(tr).putFloat(br).putFloat(tl).putFloat(bl); // uRadii
        buffer.putFloat(z); // uZ
        buffer.putFloat(progress); // uProgress
        buffer.putFloat(angle); // uAngle
        buffer.putFloat(opacity); // uOpacity
        buffer.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        try (RenderPass pass = encoder.createRenderPass(() -> "SweepPipeline", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.of(1.0))) {
            pass.setPipeline(pipeline);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.draw(0, 6);
        }
    }
}
