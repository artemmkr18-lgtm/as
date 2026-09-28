package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NumberSettingRenderer implements SettingRenderer<NumberSetting> {
    private static final float HEIGHT = 38f;
    private static final float TRACK_HEIGHT = 4f;
    private static final float KNOB_WIDTH = 10f;
    private static final float KNOB_HEIGHT = 14f;

    private final Map<NumberSetting, AnimatedFloat> animProgress = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof NumberSetting;
    }

    @Override
    public void render(DrawContext context, NumberSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        float targetProgress = getSettingProgress(setting);
        AnimatedFloat anim = animProgress.computeIfAbsent(setting, s -> new AnimatedFloat(targetProgress, Motion.ROW));
        anim.setTarget(targetProgress);
        float currentProgress = Math.clamp(anim.getValue(), 0f, 1f);

        float textSize = 10f * scale;
        float valueTextSize = 10f * scale;
        boolean hovered = rendererContext.isHovered(x, y, width, HEIGHT * scale);
        Color labelColor = rendererContext.withOpacity(hovered ? Color.WHITE : RockstarColors.TEXT_SECONDARY, opacity);

        // Value text on right
        String valText = formatValue(setting);
        float valTextWidth = Msdf.width(valText, valueTextSize);

        // Label on left
        float maxLabelWidth = width - valTextWidth - 8f * scale;
        String name = setting.getDisplayName();
        if (Msdf.width(name, textSize) > maxLabelWidth) {
            name = rendererContext.trimToWidth(name, maxLabelWidth, textSize);
        }
        RenderUtil.text(context, x, y + 1f * scale, name, textSize, labelColor);
        RenderUtil.text(context, x + width - valTextWidth, y + 1f * scale,
                valText, valueTextSize, rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity));

        // Slider track
        float trackY = y + 23f * scale;
        float trackHeight = TRACK_HEIGHT * scale;
        float trackRadius = trackHeight / 2f;
        Color trackBg = rendererContext.withOpacity(new Color(30, 27, 36, 180), opacity);
        RenderUtil.rect(x, trackY, width, trackHeight, trackRadius, trackBg);
        RenderUtil.outline(x, trackY, width, trackHeight, trackRadius, 0.65f * scale,
                rendererContext.withOpacity(new Color(255, 255, 255, 25), opacity));

        // Slider fill
        float fillWidth = Math.max(trackHeight, width * currentProgress);
        Color fillBg = rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity);
        RenderUtil.rect(x, trackY, fillWidth, trackHeight, trackRadius, fillBg);

        // Slider thumb: Rockstar squircle / capsule pill
        float knobW = KNOB_WIDTH * scale;
        float knobH = KNOB_HEIGHT * scale;
        float knobRadius = 3.5f * scale;
        float knobX = x + (width - knobW) * currentProgress;
        float knobY = trackY + (trackHeight - knobH) / 2f;

        RenderUtil.shadow(knobX, knobY + 0.5f * scale, knobW, knobH, knobRadius, 3f * scale, 0.22f * opacity, 0.8f * scale,
                new Color(0, 0, 0, 150));
        RenderUtil.rect(knobX, knobY, knobW, knobH, knobRadius, rendererContext.withOpacity(Color.WHITE, opacity * 0.95f));
        RenderUtil.fluidGlass(knobX, knobY, knobW, knobH, knobRadius, 0.35f, 0.28f * opacity,
                rendererContext.withOpacity(ThemeManager.getThemeColor(), 0.22f * opacity));
        RenderUtil.outline(knobX, knobY, knobW, knobH, knobRadius, 0.75f * scale,
                rendererContext.withOpacity(new Color(255, 255, 255, 200), opacity * 0.85f),
                rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity * 0.5f),
                rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity * 0.3f),
                rendererContext.withOpacity(new Color(255, 255, 255, 80), opacity * 0.65f));
    }

    private float getSettingProgress(NumberSetting setting) {
        double min = setting.getMin();
        double max = setting.getMax();
        if (max <= min) return 0f;
        return (float) Math.clamp((setting.getValue() - min) / (max - min), 0.0, 1.0);
    }

    private String formatValue(NumberSetting setting) {
        double val = setting.getValue();
        double step = setting.getStep();
        if (step >= 1.0 && Math.abs(val - Math.round(val)) < 0.0001) {
            return String.valueOf((long) Math.round(val));
        } else if (step >= 0.1 && Math.abs(val * 10 - Math.round(val * 10)) < 0.001) {
            return String.format(Locale.US, "%.1f", val);
        }
        return String.format(Locale.US, "%.2f", val);
    }

    @Override
    public boolean click(NumberSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        if (button == 1) {
            setting.setValue(Math.clamp(setting.getValue() - setting.getStep(), setting.getMin(), setting.getMax()));
            return false;
        }
        if (button == 0) {
            updateFromMouse(setting, localX, width);
            return true;
        }
        return false;
    }

    @Override
    public boolean drag(NumberSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        if (button == 0) {
            updateFromMouse(setting, localX, width);
            return true;
        }
        return false;
    }

    private void updateFromMouse(NumberSetting setting, float localX, float width) {
        float progress = Math.clamp(localX / width, 0f, 1f);
        double min = setting.getMin();
        double max = setting.getMax();
        double step = setting.getStep();
        double rawValue = min + progress * (max - min);
        double steppedValue = Math.round(rawValue / step) * step;
        steppedValue = Math.clamp(steppedValue, min, max);
        setting.setValue(steppedValue);
        AnimatedFloat anim = animProgress.computeIfAbsent(setting, s -> new AnimatedFloat(progress, Motion.ROW));
        anim.setTarget(progress);
    }

    @Override
    public float getHeight(NumberSetting setting, SettingRendererContext rendererContext) {
        return HEIGHT;
    }

    @Override
    public String getValue(NumberSetting setting) {
        return formatValue(setting);
    }
}
