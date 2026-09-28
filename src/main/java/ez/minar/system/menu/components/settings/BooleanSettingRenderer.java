package ez.minar.system.menu.components.settings;

import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.misc.Sounds;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class BooleanSettingRenderer implements SettingRenderer<BooleanSetting> {
    private static final float ROW_HEIGHT = 28f;
    private static final float SWITCH_WIDTH = 28f;
    private static final float SWITCH_HEIGHT = 16f;

    private final Map<BooleanSetting, AnimatedFloat> animMap = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof BooleanSetting;
    }

    @Override
    public void render(DrawContext context, BooleanSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        float rowHeight = ROW_HEIGHT * scale;
        float swW = SWITCH_WIDTH * scale;
        float swH = SWITCH_HEIGHT * scale;
        float swRadius = swH / 2f;
        float swX = x + width - swW;
        float swY = y + (rowHeight - swH) / 2f;
        float textSize = 10f * scale;

        AnimatedFloat anim = animMap.computeIfAbsent(setting, s -> new AnimatedFloat(s.isEnabled() ? 1f : 0f, Motion.ROW));
        anim.setTarget(setting.isEnabled() ? 1f : 0f);
        float currentAnim = Math.clamp(anim.getValue(), 0f, 1.2f);

        boolean hovered = rendererContext.isHovered(x, y, width, rowHeight);
        Color textColor = rendererContext.withOpacity(hovered ? Color.WHITE : RockstarColors.TEXT_SECONDARY, opacity);

        // Setting label
        String name = setting.getDisplayName();
        float maxTextWidth = width - swW - 8f * scale;
        if (Msdf.width(name, textSize) > maxTextWidth) {
            name = rendererContext.trimToWidth(name, maxTextWidth, textSize);
        }
        RenderUtil.text(context, x, y + (rowHeight - Msdf.height(textSize)) / 2f, name, textSize, textColor);

        // Switch track background (lerp between dark surface and accent)
        Color accent = ThemeManager.getThemeColor();
        Color trackOff = new Color(30, 27, 36, 180);
        Color trackOn = RockstarColors.lerp(trackOff, accent, Math.clamp(currentAnim, 0f, 1f));

        // Track shadow & pill background
        RenderUtil.shadow(swX, swY + 0.5f * scale, swW, swH, swRadius, 3f * scale, 0.15f * opacity, 0.6f * scale, new Color(0, 0, 0, 100));
        RenderUtil.rect(swX, swY, swW, swH, swRadius, rendererContext.withOpacity(trackOn, opacity));

        // Track subtle outline
        Color borderCol = RockstarColors.lerp(new Color(255, 255, 255, 20), accent, Math.clamp(currentAnim * 0.5f, 0f, 1f));
        RenderUtil.outline(swX, swY, swW, swH, swRadius, 0.65f * scale, rendererContext.withOpacity(borderCol, opacity));

        // Switch knob (thumb)
        float knobPadding = 2f * scale;
        float knobSize = swH - knobPadding * 2f;
        float knobTravel = swW - knobPadding * 2f - knobSize;
        float knobX = swX + knobPadding + knobTravel * Math.clamp(currentAnim, 0f, 1f);
        float knobY = swY + knobPadding;
        float knobRadius = knobSize / 2f;

        // Knob shadow & body
        RenderUtil.shadow(knobX, knobY + 0.5f * scale, knobSize, knobSize, knobRadius, 2.5f * scale, 0.25f * opacity, 0.8f * scale, new Color(0, 0, 0, 140));
        RenderUtil.rect(knobX, knobY, knobSize, knobSize, knobRadius, rendererContext.withOpacity(Color.WHITE, opacity * 0.95f));

        // Liquid glass highlight on the knob
        RenderUtil.fluidGlass(knobX, knobY, knobSize, knobSize, knobRadius, 0.40f, 0.30f * opacity, rendererContext.withOpacity(accent, 0.25f * opacity));
    }

    @Override
    public boolean click(BooleanSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        if (button != 0 && button != 1) return false;
        setting.setEnabled(!setting.isEnabled());
        try {
            Function sounds = FunctionManager.getFunction(Sounds.class);
            if (sounds != null && sounds.isEnabled()) {
                Sounds.playToggleSound(setting.isEnabled());
            }
        } catch (Exception ignored) {}
        return true;
    }

    @Override
    public float getHeight(BooleanSetting setting, SettingRendererContext rendererContext) {
        return ROW_HEIGHT;
    }

    @Override
    public String getValue(BooleanSetting setting) {
        return setting.isEnabled() ? "true" : "false";
    }
}
