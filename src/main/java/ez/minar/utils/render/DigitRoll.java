package ez.minar.utils.render;

import ez.minar.utils.math.Easings;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * The main menu clock's digit roll (TitleScreenMenuRenderer#renderAnimatedTime): a changed digit
 * slides up and fades out while its replacement slides in from below with a back-easing overshoot,
 * each pair clipped to the digit's own cell. Fixed-width cells keep the number from jittering.
 */
public final class DigitRoll {
    private static final long ROLL_MS = 720L;

    private String displayed = "";
    private String previous = "";
    private long[] startedAt = new long[0];

    /** Advances the roll state to the new text. Call once per frame before rendering. */
    public void update(String text) {
        long now = System.currentTimeMillis();
        if (displayed.length() != text.length()) {
            displayed = text;
            previous = text;
            startedAt = new long[text.length()];
            return;
        }
        char[] cur = displayed.toCharArray();
        char[] prev = previous.toCharArray();
        for (int i = 0; i < text.length(); i++) {
            char next = text.charAt(i);
            if (cur[i] != next) {
                prev[i] = cur[i];
                cur[i] = next;
                startedAt[i] = now;
            }
        }
        displayed = new String(cur);
        previous = new String(prev);
    }

    /** Total drawn width for this number at the given font size and inter-digit gap. */
    public float width(MsdfFont font, float size, float gap) {
        if (displayed.isEmpty()) return 0f;
        return displayed.length() * cell(font, size) + (displayed.length() - 1) * gap;
    }

    /** Draws the number left-aligned from (x, y), rolling only the digits that changed. */
    public void render(DrawContext context, MsdfFont font, float x, float y, float size, float gap, Color color) {
        if (displayed.isEmpty()) return;
        float cell = cell(font, size);
        float height = Msdf.height(font, size);
        long now = System.currentTimeMillis();
        float curX = x;
        for (int i = 0; i < displayed.length(); i++) {
            char current = displayed.charAt(i);
            char old = previous.charAt(i);
            float charWidth = Msdf.width(font, String.valueOf(current), size);
            float charX = curX + (cell - charWidth) / 2f;
            if (startedAt[i] == 0L || old == current) {
                Msdf.text(context, font, String.valueOf(current), charX, y, size, color.getRGB(), false);
            } else {
                float progress = Math.clamp((now - startedAt[i]) / (float) ROLL_MS, 0f, 1f);
                float incoming = Easings.OutBack(progress);
                float outgoing = Easings.OutCubic(progress);
                float pad = 4f;
                Scissor.push(curX - pad, y - pad, cell + pad * 2f, height + pad * 2f);
                float oldWidth = Msdf.width(font, String.valueOf(old), size);
                Color faded = withAlpha(color, 1f - progress * 0.35f);
                Msdf.text(context, font, String.valueOf(old), curX + (cell - oldWidth) / 2f,
                        y - height * outgoing, size, faded.getRGB(), false);
                Msdf.text(context, font, String.valueOf(current), charX,
                        y + height * (1f - incoming), size, color.getRGB(), false);
                Scissor.pop();
                if (progress >= 1f) {
                    startedAt[i] = 0L;
                    char[] prev = previous.toCharArray();
                    prev[i] = current;
                    previous = new String(prev);
                }
            }
            curX += cell + gap;
        }
    }

    /** Every digit occupies the widest digit's cell, so "1" never narrows the number mid-roll. */
    private static float cell(MsdfFont font, float size) {
        return Math.max(Msdf.width(font, "0", size), Msdf.width(font, "8", size));
    }

    private static Color withAlpha(Color color, float alpha) {
        int a = Math.clamp(Math.round(color.getAlpha() * Math.clamp(alpha, 0f, 1f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }
}
