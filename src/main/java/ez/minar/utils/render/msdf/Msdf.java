package ez.minar.utils.render.msdf;

import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.pipeline.MsdfGlowPipeline;
import ez.minar.utils.render.pipeline.MsdfPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

public class Msdf {
    public static MsdfFont SF_REGULAR;
    public static MsdfFont SF_BOLD;
    public static MsdfFont MONTSERRAT;
    public static MsdfFont BOLD;
    public static MsdfFont WTMICO;
    public static MsdfFont ICONS;
    public static MsdfFont WILD;
    public static MsdfFont SUISSE;
    public static MsdfFont WILDICONS;
    public static MsdfFont SF_MEDIUM;
    public static MsdfFont SF_SEMIBOLD;
    public static MsdfFont SF_LIGHT;
    public static MsdfFont SF_ROUND;

    private static boolean useFallback = false;

    public static void setFallback(boolean enabled) {
        useFallback = enabled;
    }

    public static boolean hasFonts() {
        MsdfManager.init();
        MsdfPipeline.init();
        return MsdfManager.getDefault() != null && MsdfManager.getDefault().isLoaded();
    }

    public static void text(DrawContext context, String text, float x, float y, float size, int color) {
        text(context, text, x, y, size, color, false);
    }

    public static void text(DrawContext context, String text, float x, float y, float size, int color,
            boolean overrideContext) {
        if (useFallback || !hasFonts()) {
            fallbackText(context, text, x, y, size, color);
            return;
        }
        text(context, MsdfManager.getDefault(), text, x, y, size, color, overrideContext);
    }

    public static void text(DrawContext context, String fontName, String text, float x, float y, float size,
            int color, boolean overrideContext) {
        MsdfFont font = MsdfManager.get(fontName);
        if (useFallback || font == null || !font.isLoaded()) {
            fallbackText(context, text, x, y, size, color);
            return;
        }
        text(context, font, text, x, y, size, color, overrideContext);
    }

    public static void text(DrawContext context, MsdfFont font, String text, float x, float y, float size, int color,
            boolean overrideContext) {
        text(context, font, text, x, y, size, color, 0.0f, overrideContext);
    }

    public static void text(DrawContext context, MsdfFont font, String text, float x, float y, float size, int color,
            float rotation, boolean overrideContext) {
        if (!hasFonts()) {
            fallbackText(context, text, x, y, size, color);
            return;
        }

        if (font == null) {
            font = MsdfManager.getDefault();
        }

        if (overrideContext) {
            MsdfFont finalFont = font;
            RenderUtil.addOverrideTask(
                    () -> MsdfPipeline.drawString(RenderUtil.createProjection(), finalFont, text, x, y, size, color, rotation,
                            500.0f));
            return;
        }
        if (font == null || !font.isLoaded()) {
            fallbackText(context, text, x, y, size, color);
            return;
        }
        MsdfPipeline.drawString(RenderUtil.createProjection(), font, text, x, y, size, color, rotation, 0.0f);
    }

    public static void text(Matrix4f matrix, String text, float x, float y, float size, int color) {
        MsdfPipeline.drawString(matrix, MsdfManager.getDefault(), text, x, y, size, color, 0.0f, 0.0f);
    }

    public static void text(Matrix4f matrix, String fontName, String text, float x, float y, float size, int color) {
        MsdfPipeline.drawString(matrix, MsdfManager.get(fontName), text, x, y, size, color, 0.0f, 0.0f);
    }

    public static void text(Matrix4f matrix, MsdfFont font, String text, float x, float y, float size, int color) {
        MsdfPipeline.drawString(matrix, font, text, x, y, size, color, 0.0f, 0.0f);
    }

    private static void fallbackText(DrawContext context, String text, float x, float y, float size, int color) {
        var client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            context.drawText(client.textRenderer, text, (int) x, (int) y, color, false);
        }
    }

    public static float width(String text, float size) {
        return width(MsdfManager.getDefault(), text, size);
    }

    public static float width(String fontName, String text, float size) {
        return width(MsdfManager.get(fontName), text, size);
    }

    public static float width(MsdfFont font, String text, float size) {
        if (font == null || !font.isLoaded()) {
            if (MsdfManager.getDefault() != null && MsdfManager.getDefault().isLoaded()) {
                return MsdfManager.getDefault().getStringWidth(text, size);
            }
            var client = MinecraftClient.getInstance();
            if (client != null && client.textRenderer != null) {
                return client.textRenderer.getWidth(text);
            }
            return text != null ? text.length() * size * 0.6f : 0f;
        }
        return font.getStringWidth(text, size);
    }

    public static float height(float size) {
        return height(MsdfManager.getDefault(), size);
    }

    public static float height(String fontName, float size) {
        return height(MsdfManager.get(fontName), size);
    }

    public static float height(MsdfFont font, float size) {
        if (font == null || !font.isLoaded()) {
            if (MsdfManager.getDefault() != null && MsdfManager.getDefault().isLoaded()) {
                return MsdfManager.getDefault().getLineHeight() * size;
            }
            return size;
        }
        return font.getLineHeight() * size;
    }

    public static void textCentered(DrawContext context, String text, float x, float y, float size, int color) {
        text(context, text, x - width(text, size) / 2, y, size, color, false);
    }

    public static void textCentered(DrawContext context, String fontName, String text, float x, float y, float size,
            int color) {
        text(context, fontName, text, x - width(fontName, text, size) / 2, y, size, color, false);
    }

    public static void textRight(DrawContext context, String text, float x, float y, float size, int color) {
        text(context, text, x - width(text, size), y, size, color, false);
    }

    public static void textCentered(DrawContext context, String text, float x, float y, float size, int startColor, int endColor) {
        float xOffset = x - width(text, size) / 2;
        textGradient(context, MsdfManager.getDefault(), text, xOffset, y, size, startColor, endColor, false);
    }

    public static void textCentered(DrawContext context, String fontName, String text, float x, float y, float size, int startColor, int endColor) {
        float xOffset = x - width(fontName, text, size) / 2;
        textGradient(context, MsdfManager.get(fontName), text, xOffset, y, size, startColor, endColor, false);
    }

    public static void textRight(DrawContext context, String text, float x, float y, float size, int startColor, int endColor) {
        float xOffset = x - width(text, size);
        textGradient(context, MsdfManager.getDefault(), text, xOffset, y, size, startColor, endColor, false);
    }

    public static void textRight(DrawContext context, String fontName, String text, float x, float y, float size, int startColor, int endColor) {
        float xOffset = x - width(fontName, text, size);
        textGradient(context, MsdfManager.get(fontName), text, xOffset, y, size, startColor, endColor, false);
    }

    public static void textRight(DrawContext context, String fontName, String text, float x, float y, float size,
            int color) {
        text(context, fontName, text, x - width(fontName, text, size), y, size, color, false);
    }

    public static void textWithShadow(DrawContext context, String text, float x, float y, float size, int color) {
        text(context, text, x + 1, y + 1, size, 0x44000000, false);
        text(context, text, x, y, size, color, false);
    }

    public static void textWithShadow(DrawContext context, String fontName, String text, float x, float y, float size,
            int color) {
        text(context, fontName, text, x + 1, y + 1, size, 0x44000000, false);
        text(context, fontName, text, x, y, size, color, false);
    }

    public static void textWithShadow(DrawContext context, MsdfFont font, String text, float x, float y, float size,
            int color) {
        text(context, font, text, x + 1, y + 1, size, 0x44000000, false);
        text(context, font, text, x, y, size, color, false);
    }

    public static void glowText(DrawContext context, String text, float x, float y, float size,
            int textColor, int glowColor, float glowIntensity, boolean overrideContext) {
        glowText(context, MsdfManager.getDefault(), text, x, y, size, textColor, glowColor, glowIntensity,
                overrideContext);
    }

    public static void glowText(DrawContext context, MsdfFont font, String text, float x, float y, float size,
            int textColor, int glowColor, float glowIntensity, boolean overrideContext) {
        if (!hasFonts())
            return;

        if (font == null) {
            font = MsdfManager.getDefault();
        }

        if (overrideContext) {
            MsdfFont finalFont = font;
            RenderUtil.addOverrideTask(
                    () -> MsdfGlowPipeline.drawString(RenderUtil.createProjection(), finalFont, text, x, y, size,
                            textColor, glowColor, glowIntensity, 500.0f));
            return;
        }
        if (font == null || !font.isLoaded())
            return;
        MsdfGlowPipeline.drawString(RenderUtil.createProjection(), font, text, x, y, size, textColor, glowColor, glowIntensity,
                0.0f);
    }

    public static void glowText(DrawContext context, String fontName, String text, float x, float y, float size,
            int textColor, int glowColor, float glowIntensity, boolean overrideContext) {
        MsdfFont font = MsdfManager.get(fontName);
        glowText(context, font, text, x, y, size, textColor, glowColor, glowIntensity, overrideContext);
    }

    public static void textRainbow(DrawContext context, String text, float x, float y, float size, float speed) {
        textRainbow(context, MsdfManager.getDefault(), text, x, y, size, speed, false);
    }

    public static void textRainbow(DrawContext context, MsdfFont font, String text, float x, float y, float size,
            float speed, boolean overrideContext) {
        if (overrideContext) {
            RenderUtil.addOverrideTask(() -> textRainbow(context, font, text, x, y, size, speed, false));
            return;
        }
        if (font == null || text.isEmpty())
            return;
        int[] colors = new int[text.length()];
        long time = System.currentTimeMillis();
        for (int i = 0; i < text.length(); i++) {
            float hue = ((time * speed / 1000f) + i * 0.1f) % 1.0f;
            colors[i] = hsbToRgb(hue, 1.0f, 1.0f);
        }
        MsdfPipeline.drawStringColored(RenderUtil.createProjection(), font, text, x, y, size, colors, 500.0f);
    }

    public static void textGradient(DrawContext context, String text, float x, float y, float size, int startColor,
            int endColor) {
        textGradient(context, MsdfManager.getDefault(), text, x, y, size, startColor, endColor, false);
    }

    public static void textGradient(DrawContext context, MsdfFont font, String text, float x, float y, float size,
            int startColor, int endColor, boolean overrideContext) {
        if (overrideContext) {
            RenderUtil.addOverrideTask(() -> textGradient(context, font, text, x, y, size, startColor, endColor, false));
            return;
        }
        if (font == null || text.isEmpty())
            return;
        int[] colors = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            float t = text.length() > 1 ? (float) i / (text.length() - 1) : 0;
            colors[i] = lerpColor(startColor, endColor, t);
        }
        MsdfPipeline.drawStringColored(RenderUtil.createProjection(), font, text, x, y, size, colors, 500.0f);
    }

    public static void textColored(DrawContext context, String text, float x, float y, float size, int... colors) {
        textColored(context, MsdfManager.getDefault(), text, x, y, size, false, colors);
    }

    public static void textColored(DrawContext context, MsdfFont font, String text, float x, float y, float size,
            boolean overrideContext, int... colors) {
        if (overrideContext) {
            RenderUtil.addOverrideTask(() -> textColored(context, font, text, x, y, size, false, colors));
            return;
        }
        if (font == null || text.isEmpty() || colors.length == 0)
            return;
        float z = RenderUtil.isOverrideActive() ? 500.0f : 0.0f;
        MsdfPipeline.drawStringColored(RenderUtil.createProjection(), font, text, x, y, size, colors, z);
    }

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0 -> {
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                }
                case 1 -> {
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                }
                case 2 -> {
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                }
                case 3 -> {
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                }
                case 4 -> {
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                }
                case 5 -> {
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                }
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
