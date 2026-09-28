package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.ButtonSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class ButtonSettingRenderer implements SettingRenderer<ButtonSetting> {
    private final Map<String, AnimatedFloat> hoverAnims = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof ButtonSetting;
    }

    @Override
    public void render(DrawContext context, ButtonSetting setting, SettingRendererContext rendererContext,
                       float x, float y, float width, float scale, float opacity) {
        float height = SettingRendererContext.SETTING_HEIGHT * scale;
        float textSize = 10f * scale;
        float btnTextSize = 9f * scale;
        String btnText = ez.minar.system.managers.LocalizationManager.get(setting.getButtonText());
        float buttonWidth = Math.max(38f * scale, Msdf.width(btnText, btnTextSize) + 14f * scale);
        float buttonHeight = 16f * scale;
        float buttonX = x + width - buttonWidth;
        float buttonY = y + (height - buttonHeight) / 2f;
        boolean hovered = rendererContext.isHovered(buttonX, buttonY, buttonWidth, buttonHeight);

        rendererContext.renderBoundedText(context, setting, "name", setting.getDisplayName(), x, y + 3.5f * scale,
                width - buttonWidth - 7f * scale, height, textSize,
                rendererContext.withOpacity(RockstarColors.TEXT_SECONDARY, opacity), false,
                rendererContext.isHovered(x, y, width, height));

        AnimatedFloat hoverAnim = hoverAnims.computeIfAbsent(setting.getName(), k -> new AnimatedFloat(hovered ? 1f : 0f, Motion.ROW));
        hoverAnim.setTarget(hovered ? 1f : 0f);
        float hProgress = Math.clamp(hoverAnim.getValue(), 0f, 1f);

        Color accent = ThemeManager.getThemeColor();
        Color idleBg = new Color(28, 25, 34, 180);
        Color btnBg = RockstarColors.lerp(idleBg, accent, hProgress * 0.5f);

        RenderUtil.shadow(buttonX, buttonY + 0.5f * scale, buttonWidth, buttonHeight, 4.5f * scale, 2.5f * scale, (0.12f + 0.08f * hProgress) * opacity, 0.6f * scale, new Color(0, 0, 0, 110));
        RenderUtil.rect(buttonX, buttonY, buttonWidth, buttonHeight, 4.5f * scale,
                rendererContext.withOpacity(btnBg, opacity));

        float btnGlassAlpha = (0.16f + 0.20f * hProgress) * opacity;
        Color btnGlassTint = RockstarColors.lerp(Color.WHITE, accent, hProgress);
        RenderUtil.fluidGlass(buttonX, buttonY, buttonWidth, buttonHeight, 4.5f * scale, 0.35f, btnGlassAlpha, rendererContext.withOpacity(btnGlassTint, 0.20f * opacity));

        Color outlineCol = RockstarColors.lerp(new Color(255, 255, 255, 25), accent, hProgress);
        RenderUtil.outline(buttonX, buttonY, buttonWidth, buttonHeight, 4.5f * scale, 0.65f * scale,
                rendererContext.withOpacity(outlineCol, opacity));

        Color textCol = RockstarColors.lerp(RockstarColors.TEXT_MUTED, Color.WHITE, hProgress);
        RenderUtil.text(context, buttonX + buttonWidth / 2f, buttonY + (buttonHeight - Msdf.height(btnTextSize)) / 2f,
                btnText, btnTextSize,
                rendererContext.withOpacity(textCol, opacity), "center");
    }

    @Override
    public boolean click(ButtonSetting setting, SettingRendererContext rendererContext, int button,
                         float localX, float localY, float width) {
        if (button != 0) {
            return false;
        }
        setting.press();
        return true;
    }

    @Override
    public float getHeight(ButtonSetting setting, SettingRendererContext rendererContext) {
        return SettingRendererContext.SETTING_HEIGHT;
    }

    @Override
    public String getValue(ButtonSetting setting) {
        return setting.getButtonText();
    }
}

