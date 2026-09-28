package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.helpers.KeyHelper;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class KeybindSettingRenderer implements SettingRenderer<KeybindSetting> {
    private static final float ROW_HEIGHT = 22f;
    private static KeybindSetting bindingSetting = null;
    private final Map<String, AnimatedFloat> bindAnims = new HashMap<>();

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
        float textSize = 10f * scale;
        float badgeTextSize = 8.5f * scale;
        boolean isBinding = (bindingSetting == setting);

        String keyName = isBinding ? "..." : (setting.getKeybind() <= 0 ? "NONE" : KeyHelper.getKeyName(setting.getKeybind()));
        String badgeStr = "[" + keyName + "]";
        float badgeWidth = Math.max(34f * scale, Msdf.width(badgeStr, badgeTextSize) + 10f * scale);
        float badgeHeight = 15f * scale;
        float badgeRadius = 4f * scale;
        float badgeX = x + width - badgeWidth;
        float badgeY = y + (rowHeight - badgeHeight) / 2f;

        // Name on left
        float maxNameWidth = width - badgeWidth - 8f * scale;
        String name = setting.getDisplayName();
        if (Msdf.width(name, textSize) > maxNameWidth) {
            name = rendererContext.trimToWidth(name, maxNameWidth, textSize);
        }
        RenderUtil.text(context, x, y + (rowHeight - Msdf.height(textSize)) / 2f, name, textSize,
                rendererContext.withOpacity(RockstarColors.TEXT_SECONDARY, opacity));

        AnimatedFloat anim = bindAnims.computeIfAbsent(setting.getName(), k -> new AnimatedFloat(isBinding ? 1f : 0f, Motion.ROW));
        anim.setTarget(isBinding ? 1f : 0f);
        float progress = Math.clamp(anim.getValue(), 0f, 1f);

        Color accent = ThemeManager.getThemeColor();
        Color idleBadge = new Color(28, 25, 34, 180);
        Color badgeBg = RockstarColors.lerp(idleBadge, accent, progress);

        RenderUtil.shadow(badgeX, badgeY + 0.5f * scale, badgeWidth, badgeHeight, badgeRadius, 2.5f * scale, (0.12f + 0.12f * progress) * opacity, 0.6f * scale, new Color(0, 0, 0, 110));
        RenderUtil.rect(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius, rendererContext.withOpacity(badgeBg, opacity));

        float badgeGlassAlpha = (0.18f + 0.22f * progress) * opacity;
        Color badgeGlassTint = RockstarColors.lerp(Color.WHITE, accent, progress);
        RenderUtil.fluidGlass(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius, 0.35f, badgeGlassAlpha, rendererContext.withOpacity(badgeGlassTint, 0.25f * opacity));

        Color outlineCol = RockstarColors.lerp(new Color(255, 255, 255, 25), accent, progress);
        RenderUtil.outline(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius, 0.65f * scale,
                rendererContext.withOpacity(outlineCol, opacity));

        Color textCol = RockstarColors.lerp(RockstarColors.TEXT_MUTED, Color.WHITE, progress);
        RenderUtil.text(context, badgeX + badgeWidth / 2f, badgeY + (badgeHeight - Msdf.height(badgeTextSize)) / 2f,
                badgeStr, badgeTextSize, rendererContext.withOpacity(textCol, opacity), "center");
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