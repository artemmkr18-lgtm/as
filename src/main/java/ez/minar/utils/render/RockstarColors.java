package ez.minar.utils.render;

import java.awt.Color;

/**
 * Palette and dynamic theme colors ported from Rockstar's ThemeColors.
 */
public final class RockstarColors {

    // Default primary accent (purple)
    public static final Color ACCENT = new Color(144, 107, 255);
    public static final Color PANEL_BG = new Color(24, 21, 29, 230);
    public static final Color PANEL_SURFACE = new Color(26, 23, 31);
    public static final Color SURFACE = new Color(24, 21, 29);
    public static final Color SURFACE_HOVER = new Color(34, 29, 41);
    public static final Color ROW_BG = new Color(24, 24, 27);
    public static final Color BORDER = new Color(61, 54, 71, 64);
    public static final Color DIVIDER = new Color(255, 255, 255, 18);
    public static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    public static final Color TEXT_SECONDARY = new Color(255, 255, 255, 180);
    public static final Color TEXT_MUTED = new Color(255, 255, 255, 100);

    private static Color currentAccent = ACCENT;

    private RockstarColors() {}

    public static Color getAccent() {
        return currentAccent;
    }

    public static void setAccent(Color accent) {
        if (accent != null) {
            currentAccent = accent;
        }
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color withAlpha(Color color, float alphaFactor) {
        int a = Math.round(color.getAlpha() * Math.clamp(alphaFactor, 0f, 1f));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public static Color lerp(Color c1, Color c2, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
    }
}
