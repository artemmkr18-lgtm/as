package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.ChamsPipeline;

import java.awt.Color;

@NewFunction(name = "HandChams", desc = "Текстурированные чамсы для рук от первого лица", category = Category.RENDER)
public class HandChams extends Function {
    public static HandChams Instance;

    private static final String EFFECT_CHAMS = "Chams";
    private static final String EFFECT_OUTLINE = "Обводка";
    private static final String OUTLINE_NORMAL = "Обычная";
    private static final String OUTLINE_GLOW = "Glow";

    private final MultiListSetting effects = new MultiListSetting("Эффекты", EFFECT_CHAMS, EFFECT_OUTLINE);
    private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", true);
    private final ColorSetting color = new ColorSetting("Цвет", new Color(255, 255, 255));
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность заливки", 0.65, 0.05, 1.0, 0.05);
    private final ModeSetting fillMode = new ModeSetting("Тип заливки", "Обычная", "Full", "WebShader", "Plasma", "ChamsFill", "Waves", "Water");
    private final ModeSetting outlineMode = new ModeSetting("Тип обводки", OUTLINE_NORMAL, OUTLINE_GLOW);
    private final NumberSetting outlineWidth = new NumberSetting("Ширина обводки", 2.0, 1.0, 8.0, 0.5);
    private final NumberSetting glowStrength = new NumberSetting("Сила свечения", 1.15, 0.2, 3.0, 0.05);
    private final BooleanSetting hideTexture = new BooleanSetting("Скрыть текстуру", false);

    public HandChams() {
        Instance = this;
        addSettings(effects, themeColor, color, fillAlpha, fillMode, outlineMode, outlineWidth, glowStrength, hideTexture);
        effects.runnable(this::updateVisibility);
        themeColor.runnable(this::updateVisibility);
        outlineMode.runnable(this::updateVisibility);
        updateVisibility();
    }

    public void renderMask(Runnable renderer) {
        if (shouldRenderHands()) {
            ChamsPipeline.renderMask(renderer, false);
        }
    }

    public void drawPostEffect() {
        if (isEnabled() && ChamsPipeline.hasNormalEntityMask()) {
            Color selected = selectedColor();
            int outlineType = getOutlineType();
            if (effects.isEnabled(EFFECT_CHAMS)) {
                ChamsPipeline.draw(selected, (float) fillAlpha.getValue(), getShaderMode(),
                        outlineType, (float) outlineWidth.getValue(), (float) glowStrength.getValue());
            } else if (outlineType != 0) {
                ChamsPipeline.draw(selected, 0.0f, -1,
                        outlineType, (float) outlineWidth.getValue(), (float) glowStrength.getValue());
            }
        }
    }

    public boolean shouldRenderHands() {
        return isEnabled() && (effects.isEnabled(EFFECT_CHAMS) || effects.isEnabled(EFFECT_OUTLINE));
    }

    public boolean shouldHideHandsTexture() {
        return shouldRenderHands() && hideTexture.isEnabled();
    }

    private void updateVisibility() {
        color.setVisible(!themeColor.isEnabled());
        fillAlpha.setVisible(effects.isEnabled(EFFECT_CHAMS));
        fillMode.setVisible(effects.isEnabled(EFFECT_CHAMS));
        outlineMode.setVisible(effects.isEnabled(EFFECT_OUTLINE));
        outlineWidth.setVisible(effects.isEnabled(EFFECT_OUTLINE));
        glowStrength.setVisible(effects.isEnabled(EFFECT_OUTLINE) && outlineMode.isEnabled(OUTLINE_GLOW));
        hideTexture.setVisible(effects.isEnabled(EFFECT_CHAMS) || effects.isEnabled(EFFECT_OUTLINE));
    }

    private Color selectedColor() {
        Color selected = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();
        if (!themeColor.isEnabled()) return selected;

        return new Color(brighten(selected.getRed()), brighten(selected.getGreen()), brighten(selected.getBlue()));
    }

    private static int brighten(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.45f), 0, 255);
    }

    private int getShaderMode() {
        if (fillMode.isEnabled("Обычная")) return -1;
        if (fillMode.isEnabled("Full")) return 0;
        if (fillMode.isEnabled("WebShader")) return 2;
        if (fillMode.isEnabled("Plasma")) return 4;
        if (fillMode.isEnabled("ChamsFill")) return 6;
        if (fillMode.isEnabled("Waves")) return 10;
        if (fillMode.isEnabled("Water")) return 12;
        return -1;
    }

    private int getOutlineType() {
        if (!effects.isEnabled(EFFECT_OUTLINE)) return 0;
        if (outlineMode.isEnabled(OUTLINE_GLOW)) return 2;
        return 1;
    }
}
