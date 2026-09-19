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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class ShaderSkyPipeline {
    private static final int UNIFORM_SIZE = 112;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "shader_sky"))
                    .withVertexShader(Identifier.of("minar", "shader_sky_vertex"))
                    .withFragmentShader(Identifier.of("minar", "shader_sky_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;

    private ShaderSkyPipeline() {
    }

    public static void draw(Color color, float alpha, float speed, int shaderMode,
                            Vector3f right, Vector3f up, Vector3f forward) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) {
            return;
        }

        ensureBuffers();
        if (uniformBuffer == null || dummyVertexBuffer == null) {
            return;
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        buffer.putFloat(main.textureWidth).putFloat(main.textureHeight).putFloat(0f).putFloat(0f);
        buffer.putFloat(color.getRed() / 255f).putFloat(color.getGreen() / 255f).putFloat(color.getBlue() / 255f).putFloat(1f);
        buffer.putFloat(Math.clamp(alpha, 0f, 1f)).putFloat(Math.max(0.01f, speed)).putFloat(0f).putFloat((System.currentTimeMillis() % 100000L) / 1000f);
        buffer.putFloat((float) shaderMode).putFloat(0f).putFloat(0f).putFloat(0f);
        buffer.putFloat(right.x).putFloat(right.y).putFloat(right.z).putFloat(0f);
        buffer.putFloat(up.x).putFloat(up.y).putFloat(up.z).putFloat(0f);
        buffer.putFloat(forward.x).putFloat(forward.y).putFloat(forward.z).putFloat(0f);
        buffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), buffer);
        MemoryUtil.memFree(buffer);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:shader_sky",
                main.getColorAttachmentView(),
                OptionalInt.empty(),
                main.getDepthAttachmentView(),
                OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.draw(0, 3);
        }
    }

    private static void ensureBuffers() {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:shader_sky_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:shader_sky_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX,
                    dummy);
            MemoryUtil.memFree(dummy);
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
    }
}
