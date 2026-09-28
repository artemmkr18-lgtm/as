package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.utils.anim.AnimatedFloat;
import ez.minar.utils.anim.Motion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.RockstarColors;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModeSettingRenderer implements SettingRenderer<ModeSetting> {
    private static final float HEADER_HEIGHT = 20f;
    private static final float CHIP_HEIGHT = 20f;
    private static final float CHIP_RADIUS = 5f;
    private static final float CHIP_GAP_X = 5f;
    private static final float CHIP_GAP_Y = 4f;
    private static final float CHIP_PAD_H = 8f;

    private static class ChipLayout {
        String mode;
        String displayMode;
        float relX;
        float relY;
        float width;
        float height;
    }

    private float lastWidth = 170f;
    private final Map<String, AnimatedFloat> chipAnims = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof ModeSetting;
    }

    private List<ChipLayout> computeLayout(ModeSetting setting, float width, float scale) {
        List<ChipLayout> result = new ArrayList<>();
        List<String> modes = setting.getModes();
        float textScale = 9.5f * scale;
        float curX = 0f;
        float curY = HEADER_HEIGHT * scale;
        float chipH = CHIP_HEIGHT * scale;
        float gapX = CHIP_GAP_X * scale;
        float gapY = CHIP_GAP_Y * scale;
        float padH = CHIP_PAD_H * scale;

        for (String mode : modes) {
            String displayMode = ez.minar.system.managers.LocalizationManager.get(mode);
            float textW = Msdf.width(displayMode, textScale);
            float chipW = textW + padH * 2f;

            if (curX + chipW > width && curX > 0f) {
                curX = 0f;
                curY += chipH + gapY;
            }

            ChipLayout layout = new ChipLayout();
            layout.mode = mode;
            layout.displayMode = displayMode;
            layout.relX = curX;
            layout.relY = curY;
            layout.width = chipW;
            layout.height = chipH;
            result.add(layout);

            curX += chipW + gapX;
        }

        return result;
    }

    @Override
    public void render(DrawContext context, ModeSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        this.lastWidth = width;
        float textSize = 10f * scale;
        float chipTextSize = 9f * scale;

        // Header: Setting name
        String name = setting.getDisplayName();
        RenderUtil.text(context, x, y + 1f * scale, name, textSize, rendererContext.withOpacity(RockstarColors.TEXT_SECONDARY, opacity));

        Color accent = ThemeManager.getThemeColor();
        List<ChipLayout> chips = computeLayout(setting, width, scale);
        for (ChipLayout chip : chips) {
            float cx = x + chip.relX;
            float cy = y + chip.relY;
            boolean isSelected = setting.isEnabled(chip.mode);
            boolean hovered = rendererContext.isHovered(cx, cy, chip.width, chip.height);

            String animKey = setting.getName() + ":" + chip.mode;
            AnimatedFloat anim = chipAnims.computeIfAbsent(animKey, k -> new AnimatedFloat(isSelected ? 1f : 0f, Motion.ROW));
            anim.setTarget(isSelected ? 1f : 0f);
            float progress = Math.clamp(anim.getValue(), 0f, 1f);

            // Interpolate colors between idle dark surface and accent
            Color idleBg = hovered ? new Color(38, 33, 46, 180) : new Color(28, 25, 34, 160);
            Color chipBg = RockstarColors.lerp(idleBg, accent, progress);

            RenderUtil.shadow(cx, cy + 0.5f * scale, chip.width, chip.height, CHIP_RADIUS * scale, 2.5f * scale, (0.10f + 0.10f * progress) * opacity, 0.6f * scale, new Color(0, 0, 0, 110));
            RenderUtil.rect(cx, cy, chip.width, chip.height, CHIP_RADIUS * scale, rendererContext.withOpacity(chipBg, opacity));

            // Fluid glass reflection
            float glassAlpha = (0.15f + 0.25f * progress) * opacity;
            Color glassTint = RockstarColors.lerp(Color.WHITE, accent, progress);
            RenderUtil.fluidGlass(cx, cy, chip.width, chip.height, CHIP_RADIUS * scale, 0.35f, glassAlpha, rendererContext.withOpacity(glassTint, 0.25f * opacity));

            // Outline
            Color outlineCol = RockstarColors.lerp(new Color(255, 255, 255, hovered ? 45 : 20), accent, progress);
            RenderUtil.outline(cx, cy, chip.width, chip.height, CHIP_RADIUS * scale, 0.65f * scale, rendererContext.withOpacity(outlineCol, opacity));

            // Text
            Color textCol = RockstarColors.lerp(hovered ? Color.WHITE : RockstarColors.TEXT_MUTED, Color.WHITE, progress);
            RenderUtil.text(context, cx + CHIP_PAD_H * scale, cy + (chip.height - Msdf.height(chipTextSize)) / 2f,
                    chip.displayMode, chipTextSize, rendererContext.withOpacity(textCol, opacity));
        }
    }

    @Override
    public boolean click(ModeSetting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        if (button != 0) return false;
        List<ChipLayout> chips = computeLayout(setting, width, 1.0f);
        for (ChipLayout chip : chips) {
            if (localX >= chip.relX && localX <= chip.relX + chip.width &&
                localY >= chip.relY && localY <= chip.relY + chip.height) {
                setting.setMode(chip.mode);
                try {
                    ez.minar.system.api.Function sounds = ez.minar.system.api.FunctionManager.getFunction(ez.minar.system.features.misc.Sounds.class);
                    if (sounds != null && sounds.isEnabled()) {
                        ez.minar.system.features.misc.Sounds.playToggleSound(true);
                    }
                } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    @Override
    public float getHeight(ModeSetting setting, SettingRendererContext rendererContext) {
        List<ChipLayout> chips = computeLayout(setting, Math.max(120f, lastWidth), 1.0f);
        if (chips.isEmpty()) return HEADER_HEIGHT;
        ChipLayout last = chips.get(chips.size() - 1);
        return last.relY + last.height + 4f;
    }

    @Override
    public String getValue(ModeSetting setting) {
        return setting.getActiveMode();
    }
}
