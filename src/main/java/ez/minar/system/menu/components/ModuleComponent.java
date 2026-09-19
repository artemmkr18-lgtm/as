package ez.minar.system.menu.components;

import ez.minar.system.api.Function;
import ez.minar.system.menu.GuiTheme;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.menu.components.settings.KeybindSettingRenderer;
import ez.minar.system.menu.components.settings.SettingRenderer;
import ez.minar.system.menu.components.settings.SettingRendererContext;
import ez.minar.system.menu.components.settings.SettingRendererRegistry;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleComponent {
    public static final float ITEM_HEIGHT = 30f;
    private static final SettingRendererRegistry SETTING_RENDERERS = new SettingRendererRegistry();

    private final Function function;
    private final SettingRendererContext settingRendererContext;

    private float toggleAnim;
    private float hoverAnim;
    private long lastAnimTime;

    // Inline settings expansion
    private boolean settingsExpanded;
    private float expandAnim;
    private final Map<Setting, Float> settingRevealAnims = new HashMap<>();

    private boolean binding;
    private Setting draggingSetting;

    private final Set<Setting> expandedSettings = new HashSet<>();
    private final Map<Setting, Float> settingExpandProgress = new HashMap<>();

    public ModuleComponent(Function function) {
        this.function = function;
        this.toggleAnim = function.isEnabled() ? 1f : 0f;
        this.lastAnimTime = System.currentTimeMillis();
        this.settingRendererContext = new SettingRendererContext(
                expandedSettings::contains,
                setting -> settingExpandProgress.getOrDefault(setting, 0f),
                this::toggleExpandedSetting
        );
    }

    public Function getFunction() {
        return function;
    }

    public SettingRendererContext getSettingRendererContext() {
        return settingRendererContext;
    }

    public boolean isBinding() {
        return binding;
    }

    public void setBinding(boolean binding) {
        this.binding = binding;
    }

    public boolean isSettingsExpanded() {
        return settingsExpanded;
    }

    public void setSettingsExpanded(boolean expanded) {
        if (this.settingsExpanded == expanded) return;
        this.settingsExpanded = expanded;
    }

    private void updateAnimations(float mouseX, float mouseY, float x, float y, float width, float height, float delta) {
        float targetToggle = function.isEnabled() ? 1f : 0f;
        toggleAnim = toggleAnim + (targetToggle - toggleAnim) * (1.0f - (float) Math.exp(-delta * 18.0f));

        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        float targetHover = hovered ? 1f : 0f;
        hoverAnim = hoverAnim + (targetHover - hoverAnim) * (1.0f - (float) Math.exp(-delta * 18.0f));

        // Expansion easing: frame-rate independent smooth unfold
        float targetExpand = settingsExpanded ? 1f : 0f;
        float speed = settingsExpanded ? 18.0f : 15.0f;
        expandAnim = expandAnim + (targetExpand - expandAnim) * (1.0f - (float) Math.exp(-delta * speed));

        // Staggered cascade reveal for settings inside
        float revealStart = Math.max(0f, (expandAnim - 0.08f) / 0.92f);
        List<Setting> settings = visibleSettings();
        for (int i = 0; i < settings.size(); i++) {
            float delay = i * 0.045f;
            float progress = Math.clamp((revealStart - delay) / Math.max(0.01f, 1f - delay), 0f, 1f);
            float eased = Easings.OutCubic(progress);
            settingRevealAnims.put(settings.get(i), settingsExpanded ? eased : eased * expandAnim);
        }
    }

    /**
     * Total height of the module row plus its inline settings block (animation-aware).
     */
    public float getTotalHeight(float scale) {
        return ITEM_HEIGHT * scale + getSettingsBlockHeight(scale);
    }

    /**
     * Current animated height of the inline settings block.
     */
    public float getSettingsBlockHeight(float scale) {
        if (expandAnim <= 0.001f) return 0f;
        float gap = 7f * scale;
        float total = 0f;
        List<Setting> settings = visibleSettings();
        for (int i = 0; i < settings.size(); i++) {
            SettingRenderer<Setting> renderer = SETTING_RENDERERS.get(settings.get(i));
            total += renderer.getHeight(settings.get(i), settingRendererContext) * scale + gap;
        }
        total += 8f * scale; // padding
        return total * Easings.InOutQuad(expandAnim);
    }

    public void renderModule(DrawContext context, float x, float y, float width, float height,
                              float mouseX, float mouseY, float scale, float opacity) {
        updateAnimations(mouseX, mouseY, x, y, width, height, lastDelta());

        float radius = 8f * scale;
        float textSize = Math.max(7.8f, 11f * scale);
        float toggleE = Easings.OutCubic(toggleAnim);
        float hoverE = Easings.OutCubic(hoverAnim);

        // Off state: flat dark field; On state: lavender -> violet vertical gradient (photo-matched)
        Color offTop = lerp(GuiTheme.FIELD_BG, GuiTheme.FIELD_HOVER, hoverE);
        Color offBottom = lerp(GuiTheme.FIELD_BG, GuiTheme.FIELD_HOVER, hoverE);
        Color onTop = lerp(GuiTheme.FIELD_ON_TOP, GuiTheme.FIELD_ON_TOP_HOVER, hoverE);
        Color onMid = GuiTheme.FIELD_ON_MID;
        Color onBottom = lerp(GuiTheme.FIELD_ON_BOTTOM, GuiTheme.FIELD_ON_BOTTOM_HOVER, hoverE);

        Color top = lerp(offTop, onTop, toggleE);
        Color mid = lerp(offTop, onMid, toggleE);
        Color bottom = lerp(offBottom, onBottom, toggleE);
        // 3x3 gradient grid: tl tc tr / ml mc mr / bl bc br

        // Purple bloom behind the row while enabled
        if (toggleE > 0.02f) {
            RenderUtil.glow(x, y, width, height, 0f, radius, 9f * scale,
                    0.5f * toggleE * opacity, 1.6f * scale, withOpacity(GuiTheme.FIELD_ON_GLOW, toggleE * opacity));
        }

        // Compact shadow under button for depth
        RenderUtil.shadow(x, y + 0.8f * scale, width, height, radius, 4.5f * scale, 0.20f * opacity, 1.2f * scale, new Color(0, 0, 0, 140));
        RenderUtil.rect(x, y, width, height, radius,
                withOpacity(top, opacity), withOpacity(top, opacity), withOpacity(top, opacity),
                withOpacity(mid, opacity), withOpacity(mid, opacity), withOpacity(mid, opacity),
                withOpacity(bottom, opacity), withOpacity(bottom, opacity), withOpacity(bottom, opacity));

        // Fluid glass kept subtle while enabled so the gradient stays readable
        float btnGlassAlpha = (0.32f + 0.12f * hoverAnim) * opacity * (1f - 0.75f * toggleE);
        Color btnTint = withOpacity(ThemeManager.getThemeColor(), (0.18f + 0.28f * toggleAnim) * opacity * (1f - 0.75f * toggleE));
        RenderUtil.fluidGlass(x, y, width, height, radius, 0.65f, btnGlassAlpha, btnTint);

        // Multi-stop glass outline
        Color outlineColor = lerp(GuiTheme.FIELD_BORDER_OFF, GuiTheme.FIELD_BORDER_ON, Easings.OutCubic(toggleAnim));
        RenderUtil.outline(x, y, width, height, radius, 0.85f * scale,
                withOpacity(new Color(255, 255, 255, 65), opacity * 0.75f),
                withOpacity(new Color(255, 255, 255, 25), opacity * 0.75f),
                withOpacity(outlineColor, opacity * 0.85f),
                withOpacity(new Color(255, 255, 255, 14), opacity * 0.5f));

        float textX = x + 10f * scale + 2f * scale * Easings.OutCubic(hoverAnim);
        float textY = y + (height - Msdf.height(textSize)) / 2f + 0.5f * scale;

        if (binding) {
            RenderUtil.text(context, textX, textY, ez.minar.system.managers.LocalizationManager.get("[Нажмите клавишу]"), textSize, withOpacity(new Color(245, 248, 255), opacity));
        } else {
            Color idleColor = new Color(240, 240, 248);
            Color textColor = withOpacity(lerp(idleColor, Color.WHITE, Easings.OutCubic(toggleAnim)), opacity);
            boolean hasSettings = !function.getSettings().isEmpty();
            float rightArea = hasSettings ? 22f * scale : 10f * scale;
            float maxTextW = width - rightArea;
            String displayName = function.getDisplayName();
            if (Msdf.width(displayName, textSize) > maxTextW) {
                displayName = trimToWidth(displayName, maxTextW, textSize);
            }
            RenderUtil.text(context, textX, textY, displayName, textSize, textColor);

            // Three horizontal dots "..." on the right
            if (hasSettings) {
                Color dotsColor = withOpacity(lerp(GuiTheme.TEXT_MORE_DOTS, Color.WHITE, Easings.OutCubic(toggleAnim)), opacity);
                if (settingsExpanded || expandAnim > 0.05f) {
                    dotsColor = withOpacity(lerp(dotsColor, Color.WHITE, expandAnim), opacity);
                }
                float dotSize = 2.2f * scale;
                float dotGap = 3.6f * scale;
                float centerDotX = x + width - 13f * scale;
                float dotY = y + height / 2f;

                RenderUtil.rect(centerDotX - dotGap - dotSize / 2f, dotY - dotSize / 2f, dotSize, dotSize, dotSize / 2f, dotsColor);
                RenderUtil.rect(centerDotX - dotSize / 2f, dotY - dotSize / 2f, dotSize, dotSize, dotSize / 2f, dotsColor);
                RenderUtil.rect(centerDotX + dotGap - dotSize / 2f, dotY - dotSize / 2f, dotSize, dotSize, dotSize / 2f, dotsColor);
            }
        }
    }

    private void drawDot(float x, float y, float size, Color color) {
        RenderUtil.rect(x - size / 2f, y - size / 2f, size, size, size / 2f, color);
    }

    private float lastDelta() {
        long now = System.currentTimeMillis();
        float delta = Math.min(50f, now - lastAnimTime) / 1000f;
        lastAnimTime = now;
        return delta;
    }

    public boolean isClickingDots(float mouseX, float x, float width, float scale) {
        return mouseX >= x + width - 22f * scale;
    }

    public boolean mouseClicked(float mouseX, float mouseY, float x, float y, float width, float height, int button) {
        if (!isInside(mouseX, mouseY, x, y, width, height)) return false;

        if (binding) {
            if (KeybindSetting.isBindableMouseButton(button)) {
                function.setKeybind(KeybindSetting.toMouseKey(button));
                binding = false;
                return true;
            }
            return false;
        }

        boolean hasSettings = !function.getSettings().isEmpty();

        // Clicking the three dots on the right with left click toggles settings
        if (hasSettings && button == 0 && mouseX >= x + width - 22f) {
            return false; // let ClickGui handle toggle or handle here
        }

        if (button == 0) {
            function.toggle();
            try {
                ez.minar.system.api.Function sounds = ez.minar.system.api.FunctionManager.getFunction(ez.minar.system.features.misc.Sounds.class);
                if (sounds != null && sounds.isEnabled()) {
                    ez.minar.system.features.misc.Sounds.playToggleSound(function.isEnabled());
                }
            } catch (Exception ignored) {}
            return true;
        } else if (button == 1) {
            return true; // ClickGui handles inline expansion
        } else if (button == 2) {
            binding = !binding;
            return true;
        }

        return false;
    }

    public boolean keyPressed(int keyCode) {
        TextSetting editingText = getEditingTextSetting();
        if (editingText != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                editingText.setEditing(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                editingText.backspace();
                return true;
            }
            return true;
        }

        KeybindSetting settingToBind = KeybindSettingRenderer.getBindingSetting();
        if (settingToBind != null) {
            settingToBind.setKeybind(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? 0 : keyCode);
            KeybindSettingRenderer.setBindingSetting(null);
            return true;
        }

        if (binding) {
            function.setKeybind(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? 0 : keyCode);
            binding = false;
            return true;
        }

        return false;
    }

    public boolean charTyped(String text) {
        TextSetting editingText = getEditingTextSetting();
        if (editingText != null) {
            editingText.append(text);
            return true;
        }
        return false;
    }

    /**
     * Renders the inline settings block directly under the module row, clipped to its animated height.
     */
    public void renderInlineSettings(DrawContext context, float x, float y, float width,
                                     float mouseX, float mouseY, float scale, float opacity) {
        float blockH = getSettingsBlockHeight(scale);
        if (blockH <= 0.1f) return;

        float pad = 8f * scale;
        float gap = 7f * scale;

        // Settings container card: sleek inline unfolding with fluid glass
        float containerX = x;
        float containerW = width;
        float containerH = blockH - 2f * scale;
        float containerRadius = 8f * scale;

        RenderUtil.shadow(containerX, y + 0.8f * scale, containerW, containerH, containerRadius,
                4f * scale, 0.20f * opacity * expandAnim, 1.0f * scale, new Color(0, 0, 0, 150));
        RenderUtil.rect(containerX, y, containerW, containerH, containerRadius,
                withOpacity(GuiTheme.SETTINGS_BG, opacity * Math.min(1f, expandAnim * 1.5f)));
        RenderUtil.fluidGlass(containerX, y, containerW, containerH, containerRadius, 0.55f,
                0.26f * opacity * expandAnim, withOpacity(ThemeManager.getThemeColor(), 0.15f * opacity * expandAnim));
        RenderUtil.outline(containerX, y, containerW, containerH, containerRadius, 0.8f * scale,
                withOpacity(new Color(255, 255, 255, 60), opacity * expandAnim * 0.7f),
                withOpacity(new Color(255, 255, 255, 20), opacity * expandAnim * 0.6f),
                withOpacity(ThemeManager.getThemeColor(), opacity * expandAnim * 0.45f),
                withOpacity(new Color(255, 255, 255, 12), opacity * expandAnim * 0.4f));

        settingRendererContext.setMouse(mouseX, mouseY);

        Scissor.push(containerX, y, containerW, containerH);
        float contentX = containerX + pad;
        float contentW = containerW - pad * 2f;

        float curY = y + pad;
        List<Setting> settings = visibleSettings();
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            SettingRenderer<Setting> renderer = SETTING_RENDERERS.get(setting);
            float h = renderer.getHeight(setting, settingRendererContext) * scale;
            float reveal = settingRevealAnims.getOrDefault(setting, 0f);
            if (reveal > 0.01f) {
                float offY = (1f - reveal) * 5f * scale;
                renderer.render(context, setting, settingRendererContext, contentX, curY + offY, contentW, scale, opacity * reveal);
            }
            // Subtle divider between settings (Figma style)
            if (i < settings.size() - 1) {
                float divY = curY + h + gap / 2f - 0.4f * scale;
                RenderUtil.rect(contentX, divY, contentW, 0.8f * scale, 0.4f * scale,
                        withOpacity(GuiTheme.DIVIDER, opacity * reveal * 0.9f));
            }
            curY += h + gap;
        }
        Scissor.pop();
    }

    public boolean clickInlineSettings(float localX, float localY, int button, float width, float scale) {
        if (expandAnim < 0.05f) return false;
        float pad = 8f * scale;
        float gap = 7f * scale;
        float curY = pad;

        List<Setting> settings = visibleSettings();
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            SettingRenderer<Setting> renderer = SETTING_RENDERERS.get(setting);
            float h = renderer.getHeight(setting, settingRendererContext) * scale;
            if (localY >= curY && localY <= curY + h) {
                float unscaledX = (localX - pad) / scale;
                float unscaledY = (localY - curY) / scale;
                float unscaledW = (width - pad * 2f) / scale;
                if (renderer.click(setting, settingRendererContext, button, unscaledX, unscaledY, unscaledW)) {
                    draggingSetting = setting;
                    return true;
                }
            }
            curY += h + gap;
        }
        return false;
    }

    public boolean dragInlineSettings(float localX, float localY, int button, float width, float scale) {
        if (draggingSetting == null) return false;
        SettingRenderer<Setting> draggingRenderer = SETTING_RENDERERS.get(draggingSetting);

        float pad = 8f * scale;
        float gap = 7f * scale;
        float curY = pad;
        for (Setting setting : visibleSettings()) {
            SettingRenderer<Setting> renderer = SETTING_RENDERERS.get(setting);
            float h = renderer.getHeight(setting, settingRendererContext) * scale;
            if (setting == draggingSetting) {
                float unscaledX = (localX - pad) / scale;
                float unscaledY = (localY - curY) / scale;
                float unscaledW = (width - pad * 2f) / scale;
                return draggingRenderer.drag(setting, settingRendererContext, button, unscaledX, unscaledY, unscaledW);
            }
            curY += h + gap;
        }
        return false;
    }

    public void releaseMouse() {
        draggingSetting = null;
    }

    public float getTotalSettingsHeight(float scale) {
        float total = 0f;
        float gap = 7f * scale;
        for (Setting setting : visibleSettings()) {
            SettingRenderer<Setting> renderer = SETTING_RENDERERS.get(setting);
            total += renderer.getHeight(setting, settingRendererContext) * scale + gap;
        }
        return total;
    }

    public List<Setting> visibleSettings() {
        return function.getSettings().stream().filter(Setting::isVisible).toList();
    }

    private void toggleExpandedSetting(Setting setting) {
        if (expandedSettings.contains(setting)) {
            expandedSettings.remove(setting);
        } else {
            expandedSettings.add(setting);
        }
    }

    private TextSetting getEditingTextSetting() {
        for (Setting setting : function.getSettings()) {
            if (setting instanceof TextSetting textSetting && textSetting.isEditing()) {
                return textSetting;
            }
        }
        return null;
    }

    private boolean isInside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void playSound(String sound) {
        try {
            ez.minar.system.api.Function sounds = ez.minar.system.api.FunctionManager.getFunction(ez.minar.system.features.misc.Sounds.class);
            if (sounds != null && sounds.isEnabled()) {
                MinecraftClient.getInstance().getSoundManager().play(
                        PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("minar", sound)), 1.0f, 1.0f)
                );
            }
        } catch (Exception ignored) {}
    }

    private Color lerp(Color c1, Color c2, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
    }

    private Color withOpacity(Color color, float opacity) {
        int alpha = (int) (color.getAlpha() * Math.clamp(opacity, 0f, 1f));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private String trimToWidth(String text, float maxWidth, float size) {
        if (Msdf.width(text, size) <= maxWidth) return text;
        String ellipsis = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && Msdf.width(trimmed + ellipsis, size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }
}
