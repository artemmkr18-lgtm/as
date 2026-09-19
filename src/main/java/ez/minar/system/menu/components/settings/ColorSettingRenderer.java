package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ClickGui;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class ColorSettingRenderer implements SettingRenderer<ColorSetting> {

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof ColorSetting;
    }

    @Override
    public void render(DrawContext context, ColorSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        Color color = setting.getColor();
        float textSize = 10f * scale;
        Color label = rendererContext.withOpacity(new Color(240, 236, 245), opacity);
        float swatchSize = 13f * scale;
        float swatchX = x + width - swatchSize;
        float swatchY = y + 4f * scale;

        boolean hovered = rendererContext.isHovered(x, y, width, SettingRendererContext.SETTING_HEIGHT * scale);

        // Setting name
        rendererContext.renderBoundedText(context, setting, "name", setting.getDisplayName(), x, y + 4.5f * scale,
                width * 0.48f, SettingRendererContext.SETTING_HEIGHT * scale, textSize, label, false, hovered);

        // Hex text
        String hex = toHex(color);
        float hexSize = 8.5f * scale;
        float hexW = Msdf.width(hex, hexSize);
        RenderUtil.text(context, swatchX - hexW - 5f * scale, y + 5.5f * scale, hex, hexSize,
                rendererContext.withOpacity(new Color(160, 150, 175), opacity));

        // Swatch
        RenderUtil.shadow(swatchX, swatchY + 0.5f * scale, swatchSize, swatchSize, 3f * scale, 2.5f * scale, 0.25f * opacity, 0.8f * scale, new Color(0, 0, 0, 150));
        RenderUtil.rect(swatchX, swatchY, swatchSize, swatchSize, 3f * scale, rendererContext.withOpacity(color, opacity));
        RenderUtil.fluidGlass(swatchX, swatchY, swatchSize, swatchSize, 3f * scale, 0.35f, 0.30f * opacity, rendererContext.withOpacity(color, 0.3f * opacity));
        RenderUtil.outline(swatchX, swatchY, swatchSize, swatchSize, 3f * scale, 0.8f * scale,
                rendererContext.withOpacity(hovered ? Color.WHITE : new Color(255, 255, 255), hovered ? opacity * 0.75f : opacity * 0.42f));
    }

    @Override
    public boolean click(ColorSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        ClickGui.openColorPicker(setting, rendererContext.getMouseX(), rendererContext.getMouseY());
        return true;
    }

    @Override
    public boolean drag(ColorSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        return false;
    }

    @Override
    public float getHeight(ColorSetting setting, SettingRendererContext rendererContext) {
        return SettingRendererContext.SETTING_HEIGHT;
    }

    @Override
    public String getValue(ColorSetting setting) {
        return toHex(setting.getColor());
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
