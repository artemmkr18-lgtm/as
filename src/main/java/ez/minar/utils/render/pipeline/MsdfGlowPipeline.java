package ez.minar.utils.render.pipeline;

import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.msdf.MsdfGlyph;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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

public class MsdfGlowPipeline {

    private static final int MAX_GLYPHS = 128;
    private static final int MSDF_DATA_SIZE = 128;
    private static final int GLYPH_DATA_SIZE = MAX_GLYPHS * 2 * 16;

    private static RenderPipeline pipeline;
    private static GpuBuffer msdfBuffer;
    private static GpuBuffer glyphBuffer;
    private static ByteBuffer msdfData;
    private static ByteBuffer glyphData;
    private static boolean initialized = false;

    public static void init() {
        if (initialized)
            return;

        try {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "msdf_glow"))
                    .withVertexShader(Identifier.of("minar", "msdf_glow_vertex"))
                    .withFragmentShader(Identifier.of("minar", "msdf_glow_fragment"))
                    .withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("MsdfData", UniformType.UNIFORM_BUFFER)
                    .withUniform("GlyphData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build();

            msdfBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "MsdfGlowPipeline MsdfData",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    MSDF_DATA_SIZE);

            glyphBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "MsdfGlowPipeline GlyphData",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    GLYPH_DATA_SIZE);

            msdfData = MemoryUtil.memAlloc(MSDF_DATA_SIZE);
            glyphData = MemoryUtil.memAlloc(GLYPH_DATA_SIZE);
            initialized = true;
        } catch (Exception e) {
            System.err.println("[MsdfGlowPipeline] Failed to init: " + e.getMessage());
        }
    }

    public static void drawString(Matrix4f matrix, MsdfFont font, String text, float x, float y, float size,
                                  int textColor, int glowColor, float glowIntensity, float z) {
        if (!initialized || font == null || !font.isLoaded() || text.isEmpty())
            return;

        float scale = size / font.getEmSize();
        float cursorX = x;
        float baseline = y + font.getAscender() * scale;

        float tr = ((textColor >> 16) & 0xFF) / 255.0f;
        float tg = ((textColor >> 8) & 0xFF) / 255.0f;
        float tb = (textColor & 0xFF) / 255.0f;
        float ta = ((textColor >> 24) & 0xFF) / 255.0f;

        float gr = ((glowColor >> 16) & 0xFF) / 255.0f;
        float gg = ((glowColor >> 8) & 0xFF) / 255.0f;
        float gb = (glowColor & 0xFF) / 255.0f;
        float ga = ((glowColor >> 24) & 0xFF) / 255.0f;

        msdfData.clear();
        msdfData.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        msdfData.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        msdfData.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        msdfData.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());
        msdfData.putFloat(tr).putFloat(tg).putFloat(tb).putFloat(ta);
        msdfData.putFloat(gr).putFloat(gg).putFloat(gb).putFloat(ga);
        msdfData.putFloat(font.getPxRange()).putFloat(glowIntensity).putFloat(0).putFloat(0);
        msdfData.putFloat(z).putFloat(0).putFloat(0).putFloat(0);
        msdfData.flip();

        glyphData.clear();
        int glyphCount = 0;

        for (int i = 0; i < text.length() && glyphCount < MAX_GLYPHS; i++) {
            int ch = text.charAt(i);
            MsdfGlyph glyph = font.getGlyph(ch);
            if (glyph == null) {
                glyph = font.getGlyph('?');
                if (glyph == null)
                    continue;
            }

            if (glyph.width > 0 && glyph.height > 0) {
                float gx = cursorX + glyph.bearingX * scale;
                float gy = baseline - glyph.bearingY * scale;
                float gw = glyph.width * scale;
                float gh = glyph.height * scale;

                glyphData.putFloat(gx).putFloat(gy).putFloat(gw).putFloat(gh);
                glyphData.putFloat(glyph.u0).putFloat(glyph.v0);
                glyphData.putFloat(glyph.u1 - glyph.u0).putFloat(glyph.v1 - glyph.v0);
                glyphCount++;
            }

            cursorX += glyph.advance * scale;
        }

        if (glyphCount == 0)
            return;

        glyphData.flip();
        render(font, glyphCount);
    }

    private static void render(MsdfFont font, int glyphCount) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        encoder.writeToBuffer(msdfBuffer.slice(), msdfData);
        encoder.writeToBuffer(glyphBuffer.slice(), glyphData);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "MsdfGlowPipeline",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.of(1.0))) {
            pass.setPipeline(pipeline);
            pass.setUniform("MsdfData", msdfBuffer);
            pass.setUniform("GlyphData", glyphBuffer);
            pass.bindTexture("Sampler0", font.getTextureView(), sampler);
            pass.draw(0, glyphCount * 6);
        }
    }

    public static void shutdown() {
        if (msdfBuffer != null) {
            msdfBuffer.close();
            msdfBuffer = null;
        }
        if (glyphBuffer != null) {
            glyphBuffer.close();
            glyphBuffer = null;
        }
        if (msdfData != null) {
            MemoryUtil.memFree(msdfData);
            msdfData = null;
        }
        if (glyphData != null) {
            MemoryUtil.memFree(glyphData);
            glyphData = null;
        }
        initialized = false;
    }
}
