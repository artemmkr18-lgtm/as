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
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class TitleBackgroundPipeline {
    private static final int UNIFORM_SIZE = 32;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "title_background"))
                    .withVertexShader(Identifier.of("minar", "title_background_vertex"))
                    .withFragmentShader(Identifier.of("minar", "title_background_fragment"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;

    private TitleBackgroundPipeline() {
    }

    public static void draw() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) {
            drawStaticBackground();
            return;
        }

        try {
            ensureBuffers();
            if (uniformBuffer == null || dummyVertexBuffer == null) {
                drawStaticBackground();
                return;
            }

            ByteBuffer buffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
            buffer.putFloat(main.textureWidth).putFloat(main.textureHeight).putFloat(0f).putFloat(0f);
            buffer.putFloat((float) ((System.currentTimeMillis() % 1000000L) / 1000.0)).putFloat(0f).putFloat(0f).putFloat(0f);
            buffer.flip();

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(), buffer);
            MemoryUtil.memFree(buffer);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "minar:title_background",
                    main.getColorAttachmentView(),
                    OptionalInt.empty(),
                    main.getDepthAttachmentView(),
                    OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.setUniform("Uniforms", uniformBuffer);
                pass.draw(0, 3);
            }
        } catch (Throwable e) {
            drawStaticBackground();
        }
    }

    private static void drawStaticBackground() {
        MinecraftClient client = MinecraftClient.getInstance();
        float sw = ez.minar.utils.render.RenderUtil.getFixedScaledWidth();
        float sh = ez.minar.utils.render.RenderUtil.getFixedScaledHeight();
        float screenAspect = sw / sh;
        float imageAspect = 1920f / 1080f;

        float drawW, drawH, drawX, drawY;
        if (screenAspect > imageAspect) {
            drawW = sw;
            drawH = sw / imageAspect;
            drawX = 0;
            drawY = (sh - drawH) / 2f;
        } else {
            drawW = sh * imageAspect;
            drawH = sh;
            drawX = (sw - drawW) / 2f;
            drawY = 0;
        }

        var tex = client.getTextureManager().getTexture(Identifier.of("minar", "images/background.png"));
        if (tex != null) {
            ez.minar.utils.render.pipeline.TexturePipeline.draw(
                    ez.minar.utils.render.RenderUtil.createProjection(),
                    drawX, drawY, drawW, drawH,
                    tex.getGlTextureView(),
                    java.awt.Color.WHITE.getRGB(),
                    0f, 0f
            );
        }
    }

    private static void ensureBuffers() {
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:title_background_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null) {
            ByteBuffer dummy = MemoryUtil.memAlloc(4);
            dummy.putInt(0);
            dummy.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:title_background_dummy_vertex",
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

