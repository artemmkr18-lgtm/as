package ez.minar.system.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.pipeline.TexturePipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Inter SemiBold from the user's Excellent font. Not a Suisse Intl substitute.
 * Bitmap rasterization differs from Figma; original font version equality is unverified.
 * Coordinates/sizes are RenderUtil pixels. Y is the baseline, not the text-box top.
 */
public final class ClickGuiFont {
    private static final Identifier TEXTURE = Identifier.of("minar", "textures/clickgui/inter_semibold.png");
    private static final JsonObject METRICS = loadMetrics();
    private static final JsonObject GLYPHS = METRICS.getAsJsonObject("glyphs");
    private static final float RASTER_SIZE = METRICS.get("rasterSize").getAsFloat();
    private static final float ATLAS_WIDTH = METRICS.get("width").getAsFloat();
    private static final float ATLAS_HEIGHT = METRICS.get("height").getAsFloat();

    private ClickGuiFont() {}

    private static JsonObject loadMetrics() {
        try (var stream = ClickGuiFont.class.getResourceAsStream("/assets/minar/textures/clickgui/inter_semibold.json")) {
            if (stream == null) throw new IllegalStateException("Missing Inter SemiBold metrics");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load ClickGUI Inter SemiBold", e);
        }
    }

    private static JsonObject glyph(int codepoint) {
        JsonObject g = GLYPHS.getAsJsonObject(Integer.toString(codepoint));
        return g == null ? GLYPHS.getAsJsonObject("63") : g;
    }

    /** Single-line advance; includes ASCII/Latin and the full Russian alphabet. */
    public static float width(String text, float size) {
        float width = 0f;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            width += glyph(cp).get("advance").getAsFloat();
        }
        return width * size / RASTER_SIZE;
    }

    /** Draw single-line text at a baseline. No silent family substitution or MSDF path. */
    public static void draw(String text, float x, float baselineY, float size, Color color) {
        if (text.isEmpty() || size <= 0f || color.getAlpha() == 0) return;
        var texture = MinecraftClient.getInstance().getTextureManager().getTexture(TEXTURE);
        var projection = RenderUtil.createProjection();
        float scale = size / RASTER_SIZE;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            JsonObject g = glyph(cp);
            float w = g.get("w").getAsFloat(), h = g.get("h").getAsFloat();
            TexturePipeline.draw(projection,
                    x + g.get("left").getAsFloat() * scale,
                    baselineY + g.get("top").getAsFloat() * scale,
                    w * scale, h * scale, texture.getGlTextureView(), color.getRGB(), 0f, 0f,
                    g.get("x").getAsFloat() / ATLAS_WIDTH,
                    g.get("y").getAsFloat() / ATLAS_HEIGHT,
                    w / ATLAS_WIDTH, h / ATLAS_HEIGHT, true);
            x += g.get("advance").getAsFloat() * scale;
        }
    }
}
