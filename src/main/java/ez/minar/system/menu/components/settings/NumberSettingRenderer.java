package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.GuiTheme;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NumberSettingRenderer implements SettingRenderer<NumberSetting> {
    private static final float HEIGHT = 40f;
    private static final float TRACK_HEIGHT = 4.5f;
    private static final float KNOB_SIZE = 13f;

    private final Map<NumberSetting, Float> animProgress = new HashMap<>();
    private final Map<NumberSetting, Long> lastFrameTimes = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof NumberSetting;
    }

    @Override
    public void render(DrawContext context, NumberSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        float delta = updateDelta(setting);
        float targetProgress = getSettingProgress(setting);
        float currentProgress = animProgress.getOrDefault(setting, targetProgress);
        currentProgress = currentProgress + (targetProgress - currentProgress) * (1.0f - (float) Math.exp(-delta * 22.0f));
        animProgress.put(setting, currentProgress);

        float textSize = 10.5f * scale;
        float valueTextSize = 10.5f * scale;
        boolean hovered = rendererContext.isHovered(x, y, width, HEIGHT * scale);
        Color labelColor = rendererContext.withOpacity(hovered ? new Color(255, 255, 255) : GuiTheme.TEXT_SETTING_LABEL, opacity);

        // Value text on right
        String valText = formatValue(setting);
        float valTextWidth = Msdf.width(valText, valueTextSize);

        // Label on left
        float maxLabelWidth = width - valTextWidth - 8f * scale;
        String name = setting.getDisplayName();
        if (Msdf.width(name, textSize) > maxLabelWidth) {
            name = rendererContext.trimToWidth(name, maxLabelWidth, textSize);
        }
        RenderUtil.text(context, x, y + 1.5f * scale, name, textSize, labelColor);
        RenderUtil.text(context, x + width - valTextWidth, y + 1.5f * scale,
                valText, valueTextSize, rendererContext.withOpacity(GuiTheme.TEXT_VALUE, opacity));

        // Slider track
        float trackY = y + 24f * scale;
        float trackHeight = TRACK_HEIGHT * scale;
        float trackRadius = trackHeight / 2f;
        Color trackBg = rendererContext.withOpacity(GuiTheme.TRACK_BG, opacity);
        RenderUtil.rect(x, trackY, width, trackHeight, trackRadius, trackBg);
        RenderUtil.outline(x, trackY, width, trackHeight, trackRadius, 0.65f * scale,
                rendererContext.withOpacity(new Color(255, 255, 255, 25), opacity));

        // Slider fill
        float knobSize = KNOB_SIZE * scale;
        float fillWidth = Math.max(trackHeight, width * currentProgress);
        Color fillBg = rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity);
        RenderUtil.rect(x, trackY, fillWidth, trackHeight, trackRadius, fillBg);

        // Slider knob (glass styling with subtle shadow & outline)
        float knobX = x + (width - knobSize) * currentProgress;
        float knobY = trackY + (trackHeight - knobSize) / 2f;
        float knobRadius = knobSize / 2f;
        RenderUtil.shadow(knobX, knobY + 0.4f * scale, knobSize, knobSize, knobRadius, 2.5f * scale, 0.22f * opacity, 0.8f * scale,
                new Color(0, 0, 0, 150));
        RenderUtil.rect(knobX, knobY, knobSize, knobSize, knobRadius, rendererContext.withOpacity(Color.WHITE, opacity * 0.92f));
        RenderUtil.fluidGlass(knobX, knobY, knobSize, knobSize, knobRadius, 0.35f, 0.28f * opacity,
                rendererContext.withOpacity(ThemeManager.getThemeColor(), 0.20f * opacity));
        RenderUtil.outline(knobX, knobY, knobSize, knobSize, knobRadius, 0.75f * scale,
                rendererContext.withOpacity(new Color(255, 255, 255, 200), opacity * 0.85f),
                rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity * 0.5f),
                rendererContext.withOpacity(ThemeManager.getThemeColor(), opacity * 0.3f),
                rendererContext.withOpacity(new Color(255, 255, 255, 80), opacity * 0.65f));
    }

    private float updateDelta(NumberSetting setting) {
        long now = System.currentTimeMillis();
        long last = lastFrameTimes.getOrDefault(setting, now);
        float delta = Math.min(50f, now - last) / 1000f;
        lastFrameTimes.put(setting, now);
        return delta;
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
        animProgress.put(setting, progress);
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
