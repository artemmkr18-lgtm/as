package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class TextSettingRenderer implements SettingRenderer<TextSetting> {
    @Override
    public boolean supports(Setting setting) {
        return setting instanceof TextSetting;
    }

    @Override
    public void render(DrawContext context, TextSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        rendererContext.renderRow(context, setting, getValue(setting), x, y, width, scale, opacity);

        if (setting.isEditing()) {
            Color color = rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity);
            RenderUtil.rect(x + width - 2f * scale, y + 4f * scale, 1f * scale, 10f * scale, 0.5f * scale, color);
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
        return setting.isEditing() ? setting.getValue() + "_" : setting.getValue();
    }
}
