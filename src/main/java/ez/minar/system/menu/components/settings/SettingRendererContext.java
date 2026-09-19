package ez.minar.system.menu.components.settings;

import ez.minar.system.menu.GuiTheme;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.Setting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class SettingRendererContext {
    public static final float SETTING_HEIGHT = 24f;
    private static final float SCROLL_SPEED = 26f;
    private static final float SCROLL_SIDE_PADDING = 6f;

    private final Predicate<Setting> expandedPredicate;
    private final java.util.function.Function<Setting, Float> expansionProvider;
    private final java.util.function.Consumer<Setting> expansionToggle;
    private final Map<String, Float> scrollOffsets = new HashMap<>();
    private final Map<String, Long> scrollUpdateTimes = new HashMap<>();
    private final Map<String, Float> scrollElapsed = new HashMap<>();
    private float mouseX;
    private float mouseY;

    public SettingRendererContext(Predicate<Setting> expandedPredicate,
                                  java.util.function.Function<Setting, Float> expansionProvider,
                                  java.util.function.Consumer<Setting> expansionToggle) {
        this.expandedPredicate = expandedPredicate;
        this.expansionProvider = expansionProvider;
        this.expansionToggle = expansionToggle;
    }

    public boolean isExpanded(Setting setting) {
        return expandedPredicate.test(setting);
    }

    public float getExpansion(Setting setting) {
        return Math.clamp(expansionProvider.apply(setting), 0f, 1f);
    }

    public void toggleExpanded(Setting setting) {
        expansionToggle.accept(setting);
    }

    public void setMouse(float mouseX, float mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public float getMouseX() {
        return mouseX;
    }

    public float getMouseY() {
        return mouseY;
    }

    public void renderRow(DrawContext context, Setting setting, String value, float x, float y, float width, float scale, float opacity) {
        Color label = withOpacity(GuiTheme.TEXT_SETTING_LABEL, opacity);
        Color valueColor = withOpacity(GuiTheme.TEXT_VALUE, opacity);
        float textSize = 10f * scale;

        renderBoundedText(context, setting, "name", setting.getDisplayName(), x, y + 4.5f * scale,
                width * 0.55f, SETTING_HEIGHT * scale, textSize, label, false, isHovered(x, y, width, SETTING_HEIGHT * scale));
        renderBoundedText(context, setting, "value", value, x + width - width * 0.45f, y + 4.5f * scale,
                width * 0.45f, SETTING_HEIGHT * scale, textSize, valueColor, true, isHovered(x, y, width, SETTING_HEIGHT * scale));
    }

    public void renderOptions(DrawContext context, List<String> options, Predicate<String> selected, float x, float y, float width, float scale, float opacity, float expansion) {
        float optionY = y + SETTING_HEIGHT * scale;
        Color selectedColor = withOpacity(ThemeManager.getThemeColor(), opacity * expansion);
        Color idleColor = withOpacity(new Color(163, 155, 175), opacity * expansion);
        float textSize = 9.5f * scale;

        Scissor.push(x, optionY, width, options.size() * SETTING_HEIGHT * scale * expansion);
        for (String option : options) {
            Color color = selected.test(option) ? selectedColor : idleColor;
            String displayOption = ez.minar.system.managers.LocalizationManager.get(option);
            renderBoundedText(context, null, "option:" + option, displayOption, x + 8f * scale, optionY + 4.2f * scale,
                    width - 8f * scale, SETTING_HEIGHT * scale, textSize, color, false,
                    isHovered(x, optionY, width, SETTING_HEIGHT * scale));
            optionY += SETTING_HEIGHT * scale;
        }
        Scissor.pop();
    }

    public Color withOpacity(Color color, float opacity) {
        int alpha = (int) (color.getAlpha() * Math.clamp(opacity, 0f, 1f));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public String trimToWidth(String text, float maxWidth, float size) {
        if (Msdf.width(text, size) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && Msdf.width(trimmed + ellipsis, size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    public void renderBoundedText(DrawContext context, Setting setting, String part, String text,
                                  float x, float y, float maxWidth, float rowHeight, float size,
                                  Color color, boolean rightAligned, boolean hovered) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) {
            return;
        }

        float textWidth = Msdf.width(text, size);
        if (textWidth <= maxWidth) {
            RenderUtil.text(context, rightAligned ? x + maxWidth : x, y, text, size, color, rightAligned ? "right" : "left");
            resetScroll(key(setting, part, text));
            return;
        }

        String key = key(setting, part, text);
        float overflow = textWidth - maxWidth + SCROLL_SIDE_PADDING;
        float offset = updateScroll(key, overflow, hovered);

        Scissor.push(x, y - 2f, maxWidth, size + 6f);
        RenderUtil.text(context, x - offset, y, text, size, color);
        Scissor.pop();
    }

    public boolean isHovered(float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private float updateScroll(String key, float overflow, boolean hovered) {
        long now = System.currentTimeMillis();
        long last = scrollUpdateTimes.getOrDefault(key, now);
        float delta = Math.min(50f, now - last) / 1000f;
        scrollUpdateTimes.put(key, now);

        float offset = scrollOffsets.getOrDefault(key, 0f);
        if (!hovered) {
            offset = approach(offset, 0f, SCROLL_SPEED * 1.8f * delta);
            scrollElapsed.remove(key);
            scrollOffsets.put(key, offset);
            return offset;
        }

        float elapsed = scrollElapsed.getOrDefault(key, 0f) + delta;
        float cycleDuration = Math.max(1.8f, overflow * 2f / SCROLL_SPEED);
        float phase = (elapsed % cycleDuration) / cycleDuration;
        offset = overflow * (0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2f));

        scrollElapsed.put(key, elapsed);
        scrollOffsets.put(key, offset);
        return offset;
    }

    private float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        if (current > target) {
            return Math.max(target, current - step);
        }
        return current;
    }

    private void resetScroll(String key) {
        scrollOffsets.remove(key);
        scrollUpdateTimes.remove(key);
        scrollElapsed.remove(key);
    }

    private String key(Setting setting, String part, String text) {
        return (setting == null ? "options" : System.identityHashCode(setting)) + ":" + part + ":" + text;
    }
}
