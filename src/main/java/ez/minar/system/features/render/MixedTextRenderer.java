package ez.minar.system.features.render;

import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.msdf.MsdfManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class MixedTextRenderer {

    public static float getWidth(MsdfFont font, ColoredTextRenderer.ColoredString cs, float size) {
        if (font == null) font = MsdfManager.getDefault();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float width = 0;
        float scale = size / font.getEmSize();
        
        int guiScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float vanillaScale = 2.0f / guiScale;

        for (int i = 0; i < cs.plainText.length(); i++) {
            char c = cs.plainText.charAt(i);
            boolean isSupported = (font.getGlyph(c) != null) || c == ' ';
            if (isSupported) {
                if (c == ' ') {
                    var glyph = font.getGlyph(' ');
                    width += glyph != null ? glyph.advance * scale : 0.25f * size;
                } else {
                    width += font.getGlyph(c).advance * scale;
                }
            } else {
                width += tr.getWidth(String.valueOf(c)) * vanillaScale;
            }
        }
        return width;
    }

    public static void drawText(DrawContext context, MsdfFont font, ColoredTextRenderer.ColoredString cs, float x, float y, float size) {
        if (font == null) font = MsdfManager.getDefault();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        
        int guiScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float vanillaScale = 2.0f / guiScale;

        float currentX = x;
        StringBuilder currentSegment = new StringBuilder();
        List<Integer> segmentColors = new ArrayList<>();
        boolean wasSupported = true;

        for (int i = 0; i <= cs.plainText.length(); i++) {
            boolean isSupported = true;
            char c = 0;
            if (i < cs.plainText.length()) {
                c = cs.plainText.charAt(i);
                isSupported = (font.getGlyph(c) != null) || c == ' ';
            }

            if (i == 0) {
                wasSupported = isSupported;
            }

            if (i == cs.plainText.length() || isSupported != wasSupported) {
                if (currentSegment.length() > 0) {
                    if (wasSupported) {
                        int[] cArr = new int[segmentColors.size()];
                        for (int j = 0; j < segmentColors.size(); j++) cArr[j] = segmentColors.get(j);
                        Msdf.textColored(context, font, currentSegment.toString(), currentX, y-2, size, false, cArr);
                        
                        float scale = size / font.getEmSize();
                        for (int j = 0; j < currentSegment.length(); j++) {
                            char sc = currentSegment.charAt(j);
                            if (sc == ' ') {
                                var glyph = font.getGlyph(' ');
                                currentX += glyph != null ? glyph.advance * scale : 0.25f * size;
                            } else {
                                currentX += font.getGlyph(sc).advance * scale;
                            }
                        }
                    } else {
                        for (int j = 0; j < currentSegment.length(); j++) {
                            char sc = currentSegment.charAt(j);
                            int color = segmentColors.get(j);
                            
                            context.getMatrices().pushMatrix();
                            context.getMatrices().translate(currentX, y + size / 2f - (4.5f * vanillaScale));
                            context.getMatrices().scale(vanillaScale, vanillaScale);
                            context.drawText(tr, net.minecraft.text.Text.literal(String.valueOf(sc)), 0, 0, color, false);
                            context.getMatrices().popMatrix();
                            
                            currentX += tr.getWidth(String.valueOf(sc)) * vanillaScale;
                        }
                    }
                }
                if (i < cs.plainText.length()) {
                    currentSegment = new StringBuilder();
                    segmentColors = new ArrayList<>();
                    currentSegment.append(c);
                    segmentColors.add(cs.colors[i]);
                    wasSupported = isSupported;
                }
            } else {
                currentSegment.append(c);
                segmentColors.add(cs.colors[i]);
            }
        }
    }
}
