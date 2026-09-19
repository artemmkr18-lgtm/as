package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.GuiTheme;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.ButtonSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class ButtonSettingRenderer implements SettingRenderer<ButtonSetting> {
    @Override
    public boolean supports(Setting setting) {
        return setting instanceof ButtonSetting;
    }

    @Override
    public void render(DrawContext context, ButtonSetting setting, SettingRendererContext rendererContext,
                       float x, float y, float width, float scale, float opacity) {
        float height = SettingRendererContext.SETTING_HEIGHT * scale;
        float textSize = 10f * scale;
        String btnText = ez.minar.system.managers.LocalizationManager.get(setting.getButtonText());
        float buttonWidth = Math.max(44f * scale, Msdf.width(btnText, textSize) + 16f * scale);
        float buttonHeight = 17f * scale;
        float buttonX = x + width - buttonWidth;
        float buttonY = y + (height - buttonHeight) / 2f;
        boolean hovered = rendererContext.isHovered(buttonX, buttonY, buttonWidth, buttonHeight);

        rendererContext.renderBoundedText(context, setting, "name", setting.getDisplayName(), x, y + 4.5f * scale,
                width - buttonWidth - 7f * scale, height, textSize,
                rendererContext.withOpacity(new Color(240, 236, 245), opacity), false,
                rendererContext.isHovered(x, y, width, height));

        Color accent = ThemeManager.getThemeColor();
        Color background = hovered
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 115)
                : new Color(255, 255, 255, 14);

        // Compact glass depth for setting button
        RenderUtil.shadow(buttonX, buttonY + 0.6f * scale, buttonWidth, buttonHeight, 5f * scale, 3f * scale, 0.16f * opacity, 0.8f * scale, new Color(0, 0, 0, 120));
        RenderUtil.rect(buttonX, buttonY, buttonWidth, buttonHeight, 5f * scale,
                rendererContext.withOpacity(background, opacity));

        float btnGlassAlpha = (hovered ? 0.38f : 0.20f) * opacity;
        Color btnGlassTint = rendererContext.withOpacity(accent, (hovered ? 0.32f : 0.15f) * opacity);
        RenderUtil.fluidGlass(buttonX, buttonY, buttonWidth, buttonHeight, 5f * scale, 0.45f, btnGlassAlpha, btnGlassTint);

        RenderUtil.outline(buttonX, buttonY, buttonWidth, buttonHeight, 5f * scale, 0.8f * scale,
                rendererContext.withOpacity(new Color(255, 255, 255, 70), opacity * 0.7f),
                rendererContext.withOpacity(hovered ? GuiTheme.ACCENT_BRIGHT : new Color(255, 255, 255, 35), opacity),
                rendererContext.withOpacity(accent, (hovered ? 0.40f : 0.18f) * opacity),
                rendererContext.withOpacity(new Color(255, 255, 255, 18), opacity * 0.5f));

        RenderUtil.text(context, buttonX + buttonWidth / 2f, buttonY + 3.6f * scale,
                btnText, textSize,
                rendererContext.withOpacity(hovered ? Color.WHITE : new Color(163, 155, 175), opacity), "center");
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
