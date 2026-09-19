package ez.minar.system.menu.components.settings;

import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.misc.Sounds;
import ez.minar.system.menu.GuiTheme;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class BooleanSettingRenderer implements SettingRenderer<BooleanSetting> {
    private static final float ROW_HEIGHT = 30f;
    private static final float BUTTON_SIZE = 20f;

    private final Map<BooleanSetting, Float> animMap = new HashMap<>();
    private final Map<BooleanSetting, Long> timeMap = new HashMap<>();

    @Override
    public boolean supports(Setting setting) {
        return setting instanceof BooleanSetting;
    }

    @Override
    public void render(DrawContext context, BooleanSetting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
        float rowHeight = ROW_HEIGHT * scale;
        float btnSize = BUTTON_SIZE * scale;
        float btnRadius = 5f * scale;
        float btnX = x + width - btnSize;
        float btnY = y + (rowHeight - btnSize) / 2f;
        float textSize = 10.5f * scale;

        long now = System.currentTimeMillis();
        long last = timeMap.getOrDefault(setting, now);
        float delta = Math.min(50f, now - last) / 1000f;
        timeMap.put(setting, now);

        float targetAnim = setting.isEnabled() ? 1f : 0f;
        float currentAnim = animMap.getOrDefault(setting, targetAnim);
        currentAnim = currentAnim + (targetAnim - currentAnim) * (1.0f - (float) Math.exp(-delta * 20.0f));
        animMap.put(setting, currentAnim);

        boolean hovered = rendererContext.isHovered(x, y, width, rowHeight);
        Color textColor = rendererContext.withOpacity(hovered ? new Color(255, 255, 255) : GuiTheme.TEXT_SETTING_LABEL, opacity);

        // Render setting name on left
        String name = setting.getDisplayName();
        float maxTextWidth = width - btnSize - 8f * scale;
        if (Msdf.width(name, textSize) > maxTextWidth) {
            name = rendererContext.trimToWidth(name, maxTextWidth, textSize);
        }
        RenderUtil.text(context, x, y + (rowHeight - Msdf.height(textSize)) / 2f + 0.5f * scale, name, textSize, textColor);

        // Interpolate button background & outline
        Color btnBg = lerp(GuiTheme.TOGGLE_OFF_BG, GuiTheme.TOGGLE_ON_BG, currentAnim);
        Color btnBorder = lerp(GuiTheme.TOGGLE_OFF_BORDER, GuiTheme.TOGGLE_ON_BORDER, currentAnim);

        // Compact glass depth for setting toggle
        RenderUtil.shadow(btnX, btnY + 0.6f * scale, btnSize, btnSize, btnRadius, 3f * scale, 0.18f * opacity, 0.8f * scale, new Color(0, 0, 0, 130));
        RenderUtil.rect(btnX, btnY, btnSize, btnSize, btnRadius, rendererContext.withOpacity(btnBg, opacity));

        float toggleGlassAlpha = (0.22f + 0.38f * currentAnim) * opacity;
        Color toggleGlassTint = rendererContext.withOpacity(ThemeManager.getThemeColor(), (0.15f + 0.35f * currentAnim) * opacity);
        RenderUtil.fluidGlass(btnX, btnY, btnSize, btnSize, btnRadius, 0.45f, toggleGlassAlpha, toggleGlassTint);

        RenderUtil.outline(btnX, btnY, btnSize, btnSize, btnRadius, 0.8f * scale,
                rendererContext.withOpacity(new Color(255, 255, 255, 75), opacity * 0.7f),
                rendererContext.withOpacity(btnBorder, opacity),
                rendererContext.withOpacity(ThemeManager.getThemeColor(), (0.20f + 0.40f * currentAnim) * opacity),
                rendererContext.withOpacity(new Color(255, 255, 255, 20), opacity * 0.5f));

        float cx = btnX + btnSize / 2f;
        float cy = btnY + btnSize / 2f;

        float crossFactor = 1f - currentAnim;
        if (crossFactor > 0.05f) {
            // Elegant modern cross (хрестик)
            float crossScale = Easings.OutBack(crossFactor);
            float arm = 3.6f * scale * crossScale;
            float crossThick = 1.75f * scale;
            Color crossColor = rendererContext.withOpacity(GuiTheme.TOGGLE_OFF_CROSS, opacity * crossFactor);
            drawLine(cx - arm, cy - arm, cx + arm, cy + arm, crossThick, crossColor);
            drawLine(cx + arm, cy - arm, cx - arm, cy + arm, crossThick, crossColor);
        }

        if (currentAnim > 0.05f) {
            // Elegant modern checkmark (галочка)
            float checkScale = Easings.OutBack(currentAnim);
            Color checkColor = rendererContext.withOpacity(GuiTheme.TOGGLE_ON_CHECK, opacity * currentAnim);
            float checkThick = 1.85f * scale;

            // Checkmark vertex (lowest point / corner)
            float vx = cx - 1.0f * scale;
            float vy = cy + 2.3f * scale;

            // Short arm (goes from top-left down-right to vertex)
            float p1x = vx - 3.4f * scale * checkScale;
            float p1y = vy - 3.4f * scale * checkScale;
            drawLine(p1x, p1y, vx, vy, checkThick, checkColor);

            // Long arm (goes from vertex up-right to high tip)
            float p2x = vx + 6.2f * scale * checkScale;
            float p2y = vy - 6.2f * scale * checkScale;
            drawLine(vx, vy, p2x, p2y, checkThick, checkColor);
        }
    }

    private void drawLine(float x1, float y1, float x2, float y2, float thickness, Color color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len <= 0.05f) return;
        float midX = (x1 + x2) / 2f;
        float midY = (y1 + y2) / 2f;
        float angle = (float) Math.atan2(dy, dx);
        Matrix4f mat = new Matrix4f(RenderUtil.createProjection());
        mat.translate(midX, midY, 0f);
        mat.rotateZ(angle);
        float halfLen = len / 2f;
        float halfThick = thickness / 2f;
        RenderUtil.rect(mat, -halfLen, -halfThick, len, thickness, halfThick, halfThick, halfThick, halfThick, false, color);
    }

    private Color lerp(Color c1, Color c2, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
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
