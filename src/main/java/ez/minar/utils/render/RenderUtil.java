package ez.minar.utils.render;

import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.msdf.MsdfManager;
import ez.minar.utils.render.pipeline.*;
import ez.minar.utils.render.utils.ColorHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RenderUtil {
    private static final List<Runnable> OVERRIDE_TASKS = new ArrayList<>();
    private static final float Z_OVERRIDE = 0.0f;
    private static final int FIXED_GUI_SCALE = 2;
    private static float uiScale = 1f;
    private static float uiScaleAnchorX;
    private static float uiScaleAnchorY;

    public static void addOverrideTask(Runnable runnable) {
        OVERRIDE_TASKS.add(runnable);
    }

    public static int getFixedScaledWidth() {
        var window = MinecraftClient.getInstance().getWindow();
        return (int) Math.ceil((double) window.getWidth() / FIXED_GUI_SCALE);
    }

    public static int getFixedScaledHeight() {
        var window = MinecraftClient.getInstance().getWindow();
        return (int) Math.ceil((double) window.getHeight() / FIXED_GUI_SCALE);
    }

    public static Matrix4f createProjection() {
        Matrix4f projection = new Matrix4f().ortho(0, (float) getFixedScaledWidth(),
                (float) getFixedScaledHeight(), 0, -1000, 1000);
        if (Math.abs(uiScale - 1f) > 0.001f) {
            projection.translate(uiScaleAnchorX, uiScaleAnchorY, 0f)
                    .scale(uiScale, uiScale, 1f)
                    .translate(-uiScaleAnchorX, -uiScaleAnchorY, 0f);
        }
        return projection;
    }

    public static void setUiScale(float anchorX, float anchorY, float scale) {
        uiScaleAnchorX = anchorX;
        uiScaleAnchorY = anchorY;
        uiScale = Math.max(0.1f, scale);
    }

    public static void resetUiScale() {
        uiScale = 1f;
        uiScaleAnchorX = 0f;
        uiScaleAnchorY = 0f;
    }

    public static double transformUiX(double x) {
        return uiScaleAnchorX + (x - uiScaleAnchorX) * uiScale;
    }

    public static double transformUiY(double y) {
        return uiScaleAnchorY + (y - uiScaleAnchorY) * uiScale;
    }

    public static double transformUiSize(double size) {
        return size * uiScale;
    }

    public static float getScaleFactor() {
        var window = MinecraftClient.getInstance().getWindow();
        int currentScale = window.getScaleFactor();
        return (float) currentScale / FIXED_GUI_SCALE;
    }

    public static float convertX(float x) {
        return x * getScaleFactor();
    }

    public static float convertY(float y) {
        return y * getScaleFactor();
    }

    public static void rect(Matrix4f matrix, float x, float y, float width, float height, float tl, float tr, float br,
                            float bl, boolean overrideContext, Color... colors) {
        if (overrideContext) {
            OVERRIDE_TASKS.add(() -> RectPipeline.draw(matrix, x, y, width, height, tl, tr, br, bl, Z_OVERRIDE, ColorHelper.convertColor(colors)));
            return;
        }
        RectPipeline.draw(matrix, x, y, width, height, tl, tr, br, bl, Z_OVERRIDE, ColorHelper.convertColor(colors));
    }

    public static void rect(float x, float y, float width, float height, float tl, float tr, float br,
                            float bl, Color... colors) {
        rect(createProjection(), x, y, width, height, tl, tr, br, bl, false, colors);
    }

    public static void rect(float x, float y, float width, float height, float radius, Color... colors) {
        rect(createProjection(), x, y, width, height, radius, radius, radius, radius, false, colors);
    }

    public static void blur(float x, float y, float width, float height, float radius, float strength) {
        BlurPipeline.draw(createProjection(), x, y, width, height, radius, strength, Z_OVERRIDE);
    }

    public static float getDrawContextScale() {
        return 1.0f / Math.max(0.001f, getScaleFactor());
    }

    public static void hudBlur(float x, float y, float width, float height, float tl, float tr, float br, float bl, float blurRadius, float alpha, Color tint) {
        HudBlurPipeline.draw(createProjection(), x, y, width, height, tl, tr, br, bl, blurRadius, alpha, tint, Z_OVERRIDE);
    }

    public static void hudBlur(float x, float y, float width, float height, float radius, float blurRadius, float alpha, Color tint) {
        hudBlur(x, y, width, height, radius, radius, radius, radius, blurRadius, alpha, tint);
    }

    public static void hudBlurSharp(float x, float y, float width, float height, float radius, float blurRadius, float alpha, Color tint) {
        HudBlurPipeline.draw(createProjection(), x, y, width, height, radius, radius, radius, radius,
                blurRadius, 0.15f, alpha, tint, Z_OVERRIDE);
    }

    public static void motionBlur(float x, float y, float width, float height, float blurAmount, float dirX, float dirY, float alpha) {
        MotionBlurPipeline.draw(createProjection(), x, y, width, height, 0f, 0f, 0f, 0f,
                blurAmount, dirX, dirY, alpha, Z_OVERRIDE);
    }

    public static void motionBlur(float x, float y, float width, float height, float radius,
                                  float blurAmount, float dirX, float dirY, float alpha) {
        MotionBlurPipeline.draw(createProjection(), x, y, width, height, radius, radius, radius, radius,
                blurAmount, dirX, dirY, alpha, Z_OVERRIDE);
    }

    public static void liquidGlass(float x, float y, float width, float height, float radius,
                                   float strength, float alpha, Color tint) {
        if (tint != null && alpha > 0.01f) {
            Color normalColor = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(),
                    (int) Math.clamp(tint.getAlpha() * alpha, 0, 255));
            rect(x, y, width, height, radius, normalColor);
        }
    }

    public static void fluidGlass(float x, float y, float width, float height, float radius,
                                  float strength, float alpha, Color tint) {
        if (tint != null && alpha > 0.01f) {
            Color normalColor = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(),
                    (int) Math.clamp(tint.getAlpha() * alpha, 0, 255));
            rect(x, y, width, height, radius, normalColor);
        }
    }

    public static void outline(float x, float y, float width, float height, float tl, float tr, float br, float bl, float thickness, Color... colors) {
        OutlinePipeline.draw(createProjection(), x, y, width, height, tl, tr, br, bl, thickness, Z_OVERRIDE, ColorHelper.convertColor(colors));
    }

    public static void outline(float x, float y, float width, float height, float radius, float thickness, Color... colors) {
        OutlinePipeline.draw(createProjection(), x, y, width, height, radius, radius, radius, radius, thickness, Z_OVERRIDE, ColorHelper.convertColor(colors));
    }

    public static void textGlow(DrawContext context, float x, float y, String text, float size, Color textColor, Color glowColor, float intensity) {
        Msdf.glowText(context, text, x, y, size, ColorHelper.convertColor(textColor), ColorHelper.convertColor(glowColor), intensity, false);
    }

    public static void textGlow(DrawContext context, MsdfFont font, float x, float y, String text, float size, Color textColor, Color glowColor, float intensity) {
        Msdf.glowText(context, font, text, x, y, size, ColorHelper.convertColor(textColor), ColorHelper.convertColor(glowColor), intensity, false);
    }

    public static void text(DrawContext context, float x, float y, String text, float size, Color color) {
        Msdf.text(context, text, x, y, size, ColorHelper.convertColor(color), false);
    }

    public static void text(DrawContext context, float x, float y, String text, float size, Color color, String align) {
        if (align.equals("center")) {
            Msdf.textCentered(context, text, x, y, size, ColorHelper.convertColor(color));
        } else if (align.equals("right")) {
            Msdf.textRight(context, text, x, y, size, ColorHelper.convertColor(color));
        } else {
            Msdf.text(context, text, x, y, size, ColorHelper.convertColor(color));
        }
    }

    public static void text(DrawContext context, float x, float y, String text, float size, Color startColor, Color endColor, String align) {
        if (align.equals("center")) {
            Msdf.textCentered(context, text, x, y, size, ColorHelper.convertColor(startColor), ColorHelper.convertColor(endColor));
        } else if (align.equals("right")) {
            Msdf.textRight(context, text, x, y, size, ColorHelper.convertColor(startColor), ColorHelper.convertColor(endColor));
        } else {
            Msdf.textGradient(context, text, x, y, size, ColorHelper.convertColor(startColor), ColorHelper.convertColor(endColor));
        }
    }

    public static void text(DrawContext context, MsdfFont font, float x, float y, String text, float size, Color color) {
        Msdf.text(context, font, text, x, y, size, ColorHelper.convertColor(color), false);
    }

    public static void text(DrawContext context, float x, float y, String text, float size, Color... color) {
        Msdf.textColored(context, MsdfManager.getDefault(), text, x, y, size, false, ColorHelper.convertColor(color));
    }

    public static void texture(float x, float y, float size, Identifier texture, float radius, Color color) {
        var client = MinecraftClient.getInstance();
        var tex = client.getTextureManager().getTexture(texture);
        TexturePipeline.draw(createProjection(), x, y, size, tex.getGlTextureView(), ColorHelper.convertColor(color), radius, Z_OVERRIDE);
    }

    public static void texture(float x, float y, float width, float height, Identifier texture, float radius, Color color) {
        var client = MinecraftClient.getInstance();
        var tex = client.getTextureManager().getTexture(texture);
        TexturePipeline.draw(createProjection(), x, y, width, height, tex.getGlTextureView(), ColorHelper.convertColor(color), radius, Z_OVERRIDE);
    }

    public static void glowCircle(float x, float y, float size, Color color) {
        GlowPipeline.draw(createProjection(), x, y, size, ColorHelper.convertColor(color), Z_OVERRIDE);
    }

    public static void circleOutline(float cx, float cy, float radius, float thickness, Color color) {
        CirclePipeline.draw(createProjection(), cx, cy, radius, thickness, Z_OVERRIDE, color);
    }

    public static void circleOutline(float cx, float cy, float radius, float thickness, float startAngle, float progress, Color color) {
        CirclePipeline.draw(createProjection(), cx, cy, radius, thickness, startAngle, progress, Z_OVERRIDE, color);
    }



    public static void glow(float x, float y, float width, float height, float thickness, float radius, Color color) {
        glow(x, y, width, height, thickness, radius, 0f, 1f, 0f, color);
    }

    public static void glow(float x, float y, float width, float height, float thickness, float radius, float progress, Color color) {
        glow(x, y, width, height, thickness, radius, 0f, 1f, Math.clamp(progress, 0f, 1f), color);
    }

    public static void glow(float x, float y, float width, float height, float thickness, float radius,
                            float glowSize, float glowIntensity, float glowSoftness, Color color) {
        GlowOutlinePipeline.draw(createProjection(), x, y, width, height, thickness,
                ColorHelper.convertColor(color), radius,
                glowSize, Math.clamp(glowIntensity, 0, 1), glowSoftness, 1, Z_OVERRIDE);
    }

    public static void shadow(float x, float y, float width, float height,
                              float radius, float size, float intensity,
                              float softness, Color color) {
        if (size <= 0f || intensity <= 0f) return;
        GlowOutlinePipeline.draw(createProjection(), x, y, width, height,
                0f, ColorHelper.convertColor(color), radius,
                size, Math.clamp(intensity, 0f, 1f), Math.max(0.1f, softness),
                1, Z_OVERRIDE);
    }

    public static void shadow(float x, float y, float width, float height, float radius, Color color) {
        shadow(x, y, width, height, radius, 12f, 0.55f, 2.2f, color);
    }

    public static boolean isOverrideActive() {
        var client = MinecraftClient.getInstance();
        return client.currentScreen == null || client.currentScreen instanceof ChatScreen;
    }
    public static void text(DrawContext context, MsdfFont font, float x, float y, String text, float size, Color color, String align) {
        float drawX = x;

        if (align.equals("center")) {
            drawX = x - Msdf.width(font, text, size) / 2f;
        } else if (align.equals("right")) {
            drawX = x - Msdf.width(font, text, size);
        }

        Msdf.text(
                context,
                font,
                text,
                drawX,
                y,
                size,
                ColorHelper.convertColor(color),
                false
        );
    }
}
