package ez.minar.utils.font;

import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * A font at one fixed pixel size - the ported equivalent of Rockstar's {@code SizedFont}.
 *
 * <p>Caching one instance per size matters the same way it does upstream: metrics lookups go
 * through the MSDF atlas, and Rockstar's panels resolve a font once in a static field rather
 * than on every frame.
 */
public final class SizedFont {

    private final MsdfFont font;
    private final float size;

    SizedFont(MsdfFont font, float size) {
        this.font = font;
        this.size = size;
    }

    public MsdfFont msdf() {
        return font;
    }

    public float size() {
        return size;
    }

    /** Line advance of this font, matching the text block height Rockstar's rows are sized by. */
    public float height() {
        return Msdf.height(font, size);
    }

    public float ascent() {
        return font.getAscender() * size;
    }

    public float descent() {
        return font.getDescender() * size;
    }

    public float width(String text) {
        return Msdf.width(font, text, size);
    }

    /** Top-left anchored draw. */
    public void draw(DrawContext ctx, String text, float x, float y, Color color) {
        RenderUtil.text(ctx, font, x, y, text, size, color);
    }

    /** Draws so that {@code y} is the vertical centre of the text. */
    public void drawCenteredY(DrawContext ctx, String text, float x, float centerY, Color color) {
        draw(ctx, text, x, centerY - height() / 2f, color);
    }

    /** Draws so that {@code centerX} is the horizontal centre of the text. */
    public void drawCentered(DrawContext ctx, String text, float centerX, float y, Color color) {
        draw(ctx, text, centerX - width(text) / 2f, y, color);
    }

    /** Draws so that {@code right} is the right edge of the text; used for slider values. */
    public void drawRight(DrawContext ctx, String text, float right, float y, Color color) {
        draw(ctx, text, right - width(text), y, color);
    }

    /** Vertically centred and right aligned - the common pairing on a setting row. */
    public void drawRightCenteredY(DrawContext ctx, String text, float right, float centerY, Color color) {
        drawRight(ctx, text, right, centerY - height() / 2f, color);
    }

    /**
     * Truncates with an ellipsis so the result still fits {@code maxWidth}, which is how the
     * source client keeps long module names from escaping their row.
     */
    public String trim(String text, float maxWidth) {
        if (width(text) <= maxWidth) return text;
        String suffix = "…";
        float suffixWidth = width(suffix);
        int end = text.length();
        while (end > 0 && width(text.substring(0, end)) + suffixWidth > maxWidth) end--;
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }
}
