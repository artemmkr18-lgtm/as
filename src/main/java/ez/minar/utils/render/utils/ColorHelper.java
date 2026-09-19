package ez.minar.utils.render.utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class ColorHelper {
    public static int[] convertColor(Color... colors) {
        int[] RGB = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            RGB[i] = colors[i].getRGB();
        }
        return RGB;
    }

    public static int convertColor(Color color) {
        return color.getRGB();
    }

    public static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) |
                ((int) (r1 + (r2 - r1) * t) << 16) |
                ((int) (g1 + (g2 - g1) * t) << 8) |
                ((int) (b1 + (b2 - b1) * t));
    }
}
