package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class TextSettingRenderer implements SettingRenderer<TextSetting> {
    private final Map<String, AnimatedFloat> cursorAnims = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof TextSetting;
    }

    @Override
    public void render(DrawContext context, TextSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        float height = SettingRendererContext.SETTING_HEIGHT * scale;
        float textSize = 10f * scale;
        float valTextSize = 9f * scale;
        boolean hovered = rendererContext.isHovered(x, y, width, height);

        // Name on left
        rendererContext.renderBoundedText(context, setting, "name", setting.getDisplayName(), x, y + 3.5f * scale,
                width * 0.48f, height, textSize,
                rendererContext.withOpacity(RockstarColors.TEXT_SECONDARY, opacity), false, hovered);

        // Box on right
        String val = setting.getValue();
        if (val == null || val.isEmpty()) {
            val = setting.isEditing() ? "" : "...";
        }
        float valWidth = Msdf.width(val, valTextSize);
        float boxWidth = Math.max(45f * scale, valWidth + 14f * scale);
        float boxHeight = 16f * scale;
        float boxX = x + width - boxWidth;
        float boxY = y + (height - boxHeight) / 2f;

        AnimatedFloat cursorAnim = cursorAnims.computeIfAbsent(setting.getName(), k -> new AnimatedFloat(setting.isEditing() ? 1f : 0f, Motion.ROW));
        cursorAnim.setTarget(setting.isEditing() ? 1f : 0f);
        float cProgress = Math.clamp(cursorAnim.getValue(), 0f, 1f);

        Color accent = ThemeManager.getThemeColor();
        Color idleBg = new Color(28, 25, 34, 180);
        Color boxBg = RockstarColors.lerp(idleBg, accent, cProgress * 0.35f);

        RenderUtil.shadow(boxX, boxY + 0.5f * scale, boxWidth, boxHeight, 4.5f * scale, 2.5f * scale, (0.12f + 0.08f * cProgress) * opacity, 0.6f * scale, new Color(0, 0, 0, 110));
        RenderUtil.rect(boxX, boxY, boxWidth, boxHeight, 4.5f * scale, rendererContext.withOpacity(boxBg, opacity));

        float boxGlassAlpha = (0.16f + 0.18f * cProgress) * opacity;
        Color boxGlassTint = RockstarColors.lerp(Color.WHITE, accent, cProgress);
        RenderUtil.fluidGlass(boxX, boxY, boxWidth, boxHeight, 4.5f * scale, 0.35f, boxGlassAlpha, rendererContext.withOpacity(boxGlassTint, 0.20f * opacity));

        Color outlineCol = RockstarColors.lerp(new Color(255, 255, 255, 25), accent, cProgress);
        RenderUtil.outline(boxX, boxY, boxWidth, boxHeight, 4.5f * scale, 0.65f * scale,
                rendererContext.withOpacity(outlineCol, opacity));

        Color textCol = RockstarColors.lerp(RockstarColors.TEXT_MUTED, Color.WHITE, cProgress);
        float textX = boxX + 6f * scale;
        float textY = boxY + (boxHeight - Msdf.height(valTextSize)) / 2f;
        RenderUtil.text(context, textX, textY, val, valTextSize, rendererContext.withOpacity(textCol, opacity));

        // Pulsing cursor when editing
        if (setting.isEditing()) {
            long now = System.currentTimeMillis();
            if ((now / 450) % 2 == 0) {
                float cursorX = textX + valWidth + 1.5f * scale;
                RenderUtil.rect(cursorX, boxY + 3.5f * scale, 1f * scale, boxHeight - 7f * scale, 0.5f * scale,
                        rendererContext.withOpacity(accent, opacity));
            }
        }
    }

    @Override
    public boolean click(TextSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        if (button != 0) {
            return false;
        }

        setting.setEditing(true);
        return true;
    }

    @Override
    public float getHeight(TextSetting setting, SettingRendererContext rendererContext) {
        return SettingRendererContext.SETTING_HEIGHT;
    }

    @Override
    public String getValue(TextSetting setting) {
        return setting.getValue();
    }
}

