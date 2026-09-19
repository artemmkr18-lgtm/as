package ez.minar.system.features.render;

import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ColoredTextRenderer {

    public static class ColoredString {
        public final String plainText;
        public final int[] colors;

        public ColoredString(String text, int[] colors) {
            this.plainText = text;
            this.colors = colors;
        }

        // Parses legacy section signs (§)
        public ColoredString(String text, int defaultColor) {
            StringBuilder builder = new StringBuilder();
            int[] tempColors = new int[text.length()];
            int currentColor = defaultColor;
            int idx = 0;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    char code = text.charAt(++i);
                    Formatting format = Formatting.byCode(code);
                    if (format != null) {
                        if (format.getColorValue() != null) {
                            currentColor = format.getColorValue() | 0xFF000000;
                        } else if (format == Formatting.RESET) {
                            currentColor = defaultColor;
                        }
                    }
                } else {
                    builder.append(c);
                    tempColors[idx++] = currentColor;
                }
            }

            this.plainText = builder.toString();
            this.colors = new int[idx];
            System.arraycopy(tempColors, 0, this.colors, 0, idx);
        }

        // Parses Minecraft Text object with Styles
        public static ColoredString fromText(Text text, int defaultColor) {
            StringBuilder builder = new StringBuilder();
            List<Integer> colorList = new ArrayList<>();

            text.visit(new StringVisitable.StyledVisitor<Void>() {
                @Override
                public Optional<Void> accept(Style style, String string) {
                    int color = defaultColor;
                    if (style != null && style.getColor() != null) {
                        color = style.getColor().getRgb() | 0xFF000000;
                    }
                    // Some servers still put § inside Text objects
                    int currentColor = color;
                    for (int i = 0; i < string.length(); i++) {
                        char c = string.charAt(i);
                        if (c == '§' && i + 1 < string.length()) {
                            char code = string.charAt(++i);
                            Formatting format = Formatting.byCode(code);
                            if (format != null) {
                                if (format.getColorValue() != null) {
                                    currentColor = format.getColorValue() | 0xFF000000;
                                } else if (format == Formatting.RESET) {
                                    currentColor = color; // reset to the Style's color
                                }
                            }
                        } else {
                            builder.append(c);
                            colorList.add(currentColor);
                        }
                    }
                    return Optional.empty();
                }
            }, Style.EMPTY);

            int[] colors = new int[colorList.size()];
            for (int i = 0; i < colorList.size(); i++) {
                colors[i] = colorList.get(i);
            }

            return new ColoredString(builder.toString(), colors);
        }
    }

    public static void drawText(DrawContext context, String text, float x, float y, float size, Color defaultColor) {
        ColoredString cs = new ColoredString(text, defaultColor.getRGB());
        Msdf.textColored(context, cs.plainText, x, y, size, cs.colors);
    }

    public static void drawTextCenter(DrawContext context, String text, float centerX, float y, float size, Color defaultColor) {
        ColoredString cs = new ColoredString(text, defaultColor.getRGB());
        float width = Msdf.width(cs.plainText, size);
        Msdf.textColored(context, cs.plainText, centerX - width / 2f, y, size, cs.colors);
    }

    public static void drawTextRight(DrawContext context, String text, float rightX, float y, float size, Color defaultColor) {
        ColoredString cs = new ColoredString(text, defaultColor.getRGB());
        float width = Msdf.width(cs.plainText, size);
        Msdf.textColored(context, cs.plainText, rightX - width, y, size, cs.colors);
    }
}
