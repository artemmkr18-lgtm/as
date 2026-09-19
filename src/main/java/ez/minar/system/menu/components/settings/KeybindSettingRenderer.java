package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.GuiTheme;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.utils.helpers.KeyHelper;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class KeybindSettingRenderer implements SettingRenderer<KeybindSetting> {
    private static final float ROW_HEIGHT = 28f;
    private static KeybindSetting bindingSetting = null;

    public static KeybindSetting getBindingSetting() {
        return bindingSetting;
    }

    public static void setBindingSetting(KeybindSetting setting) {
        bindingSetting = setting;
    }

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof KeybindSetting;
    }

    @Override
    public void render(DrawContext context, KeybindSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        float rowHeight = ROW_HEIGHT * scale;
        float textSize = 10.5f * scale;
        float badgeTextSize = 9.5f * scale;
        boolean isBinding = (bindingSetting == setting);

        String keyName = isBinding ? "..." : (setting.getKeybind() <= 0 ? "NONE" : KeyHelper.getKeyName(setting.getKeybind()));
        String badgeStr = "[" + keyName + "]";
        float badgeWidth = Math.max(40f * scale, Msdf.width(badgeStr, badgeTextSize) + 12f * scale);
        float badgeHeight = 18f * scale;
        float badgeRadius = 5f * scale;
        float badgeX = x + width - badgeWidth;
        float badgeY = y + (rowHeight - badgeHeight) / 2f;

        // Name on left
        float maxNameWidth = width - badgeWidth - 8f * scale;
        String name = setting.getDisplayName();
        if (Msdf.width(name, textSize) > maxNameWidth) {
            name = rendererContext.trimToWidth(name, maxNameWidth, textSize);
        }
        RenderUtil.text(context, x, y + (rowHeight - Msdf.height(textSize)) / 2f, name, textSize,
                rendererContext.withOpacity(new Color(240, 236, 245), opacity));

        // Badge on right (navy when binding)
        Color badgeBg = isBinding
                ? rendererContext.withOpacity(GuiTheme.ACCENT, opacity)
                : rendererContext.withOpacity(GuiTheme.BADGE_BG, opacity);

        RenderUtil.shadow(badgeX, badgeY + 0.5f * scale, badgeWidth, badgeHeight, badgeRadius, 2.5f * scale, 0.14f * opacity, 0.6f * scale, new Color(0, 0, 0, 110));
        RenderUtil.rect(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius, badgeBg);

        float badgeGlassAlpha = (isBinding ? 0.40f : 0.20f) * opacity;
        Color badgeGlassTint = rendererContext.withOpacity(ThemeManager.getThemeColor(), (isBinding ? 0.35f : 0.15f) * opacity);
        RenderUtil.fluidGlass(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius, 0.40f, badgeGlassAlpha, badgeGlassTint);

        RenderUtil.outline(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius, 0.75f * scale,
                rendererContext.withOpacity(isBinding ? GuiTheme.ACCENT_BRIGHT : new Color(255, 255, 255, 30), opacity));

        RenderUtil.text(context, badgeX + badgeWidth / 2f, badgeY + (badgeHeight - Msdf.height(badgeTextSize)) / 2f + 0.5f * scale,
                badgeStr, badgeTextSize, rendererContext.withOpacity(Color.WHITE, opacity), "center");
    }

    @Override
    public boolean click(KeybindSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        if (bindingSetting == setting) {
            if (KeybindSetting.isBindableMouseButton(button)) {
                setting.setKeybind(KeybindSetting.toMouseKey(button));
                bindingSetting = null;
                return true;
            }
        }
        if (button == 0) {
            if (bindingSetting == setting) {
                bindingSetting = null;
            } else {
                bindingSetting = setting;
            }
            return true;
        }
        return false;
    }

    @Override
    public float getHeight(KeybindSetting setting, SettingRendererContext rendererContext) {
        return ROW_HEIGHT;
    }

    @Override
    public String getValue(KeybindSetting setting) {
        return setting.getKeybind() <= 0 ? "NONE" : KeyHelper.getKeyName(setting.getKeybind());
    }
}