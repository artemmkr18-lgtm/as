package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ClickGui;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class ColorSettingRenderer implements SettingRenderer<ColorSetting> {
    private final Map<String, AnimatedFloat> hoverAnims = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof ColorSetting;
    }

    @Override
    public void render(DrawContext context, ColorSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        Color color = setting.getColor();
        float textSize = 10f * scale;
        Color label = rendererContext.withOpacity(RockstarColors.TEXT_SECONDARY, opacity);
        float swatchSize = 12f * scale;
        float swatchX = x + width - swatchSize;
        float swatchY = y + 4.5f * scale;

        boolean hovered = rendererContext.isHovered(x, y, width, SettingRendererContext.SETTING_HEIGHT * scale);
        AnimatedFloat hoverAnim = hoverAnims.computeIfAbsent(setting.getName(), k -> new AnimatedFloat(hovered ? 1f : 0f, Motion.ROW));
        hoverAnim.setTarget(hovered ? 1f : 0f);
        float hProgress = Math.clamp(hoverAnim.getValue(), 0f, 1f);

        // Setting name
        rendererContext.renderBoundedText(context, setting, "name", setting.getDisplayName(), x, y + 3.5f * scale,
                width * 0.48f, SettingRendererContext.SETTING_HEIGHT * scale, textSize, label, false, hovered);

        // Hex text
        String hex = toHex(color);
        float hexSize = 8.5f * scale;
        float hexW = Msdf.width(hex, hexSize);
        RenderUtil.text(context, swatchX - hexW - 5f * scale, y + 4.5f * scale, hex, hexSize,
                rendererContext.withOpacity(RockstarColors.TEXT_MUTED, opacity));

        // Swatch
        RenderUtil.shadow(swatchX, swatchY + 0.5f * scale, swatchSize, swatchSize, 3.5f * scale, 2.5f * scale, 0.20f * opacity, 0.6f * scale, new Color(0, 0, 0, 130));
        RenderUtil.rect(swatchX, swatchY, swatchSize, swatchSize, 3.5f * scale, rendererContext.withOpacity(color, opacity));
        RenderUtil.fluidGlass(swatchX, swatchY, swatchSize, swatchSize, 3.5f * scale, 0.35f, 0.25f * opacity, rendererContext.withOpacity(color, 0.25f * opacity));

        Color outlineCol = RockstarColors.lerp(new Color(255, 255, 255, 40), Color.WHITE, hProgress);
        RenderUtil.outline(swatchX, swatchY, swatchSize, swatchSize, 3.5f * scale, 0.65f * scale,
                rendererContext.withOpacity(outlineCol, (0.4f + 0.4f * hProgress) * opacity));
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

