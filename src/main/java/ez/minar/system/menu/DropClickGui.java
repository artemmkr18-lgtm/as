package ez.minar.system.menu;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.*;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.pipeline.HudBlurPipeline;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/** Fixed-column Drop interface; independent of the default ClickGui. */
public class DropClickGui extends Screen {
    private static final float PANEL_W = 100, PANEL_H = 216, PANEL_GAP = 6;
    private static final float HEADER_H = 22, ROW_H = 16, GAP = 0;
    private static final float GROUP_W = PANEL_W * 5 + PANEL_GAP * 4;
    private static final Color TEXT = new Color(215, 215, 225), DISABLED = new Color(191, 190, 197);
    private static final Identifier[] CATEGORY_ICONS = {
            Identifier.of("minar", "icons/nex_swords.png"),
            Identifier.of("minar", "icons/nex_rocket.png"),
            Identifier.of("minar", "icons/nex_monitor.png"),
            Identifier.of("minar", "icons/nex_user.png"),
            Identifier.of("minar", "icons/nex_blocks.png")
    };
    private static final Category[] CATEGORIES = {
            Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC
    };
    private final List<Panel> panels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Function, Float> reveals = new IdentityHashMap<>();
    private final Map<Function, Float> hovers = new IdentityHashMap<>();
    private final Map<Function, Float> toggles = new IdentityHashMap<>();
    private float groupX, groupY, baseScale = 1;
    private float layoutWidth, layoutHeight;
    private final Map<MultiSetting, Boolean> multiOpen = new IdentityHashMap<>();
    private final Map<MultiSetting, Float> multiReveal = new IdentityHashMap<>();
    private final Map<ChipKey, float[]> chipAnim = new HashMap<>();
    private final Map<BooleanSetting, Float> switches = new IdentityHashMap<>();
    private final Map<ColorSetting, float[]> colorStates = new IdentityHashMap<>();
    private final Map<ColorSetting, Integer> colorRgb = new IdentityHashMap<>();
    private Function expanded, bindingFunction;
    private KeybindSetting bindingSetting;
    private TextSetting editing;
    private Panel draggingPanel;
    private Hit draggingControl;
    private float grabX, grabY, scrollX, scrollY, targetX, targetY;
    private float screenW, screenH, scale = 2, opacity, dt, closeOpacity, closeScale;
    private float mouseX, mouseY;
    private long started, lastFrame;
    private boolean closing, laidOut, searching, selectAll;
    private String query = "";
    private int cursor;
    private Rect viewport, clip;
    private Panel ownerPanel;
    private Function ownerFunction;
    private Setting ownerSetting;
    private String description;
    private Color accent = new Color(255, 175, 226);
    private ColorSetting floatingColor;
    private float pickerX, pickerY;

    public DropClickGui() {
        super(Text.literal("Nexoria — Дроп"));
        for (Category category : CATEGORIES) panels.add(new Panel(category));
    }

    @Override
    public void onDisplayed() {
        closing = false;
        started = System.nanoTime();
        lastFrame = 0;
        scale = baseScale * .96f;
        opacity = 0;
        laidOut = false;
        query = "";
        searching = false;
        clearFocus();
        floatingColor = null;
        hits.clear();
        // Refresh the registry here rather than assuming modules exist at construction time.
        for (Panel panel : panels) {
            panel.modules = new ArrayList<>(FunctionManager.getFunctionsByCategory(panel.category));
            panel.modules.sort(Comparator.comparing(Function::getName, String.CASE_INSENSITIVE_ORDER));
            panel.modules.removeIf(f -> f instanceof ez.minar.system.features.render.HUD);
            panel.open = true;
        }
        super.onDisplayed();
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeOpacity = opacity;
        closeScale = scale;
        started = System.nanoTime();
        clearFocus();
        hits.clear();
    }

    @Override
    public void removed() {
        clearFocus();
        hits.clear();
        RenderUtil.resetUiScale();
        super.removed();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(DrawContext context, int x, int y, float delta) { }

    @Override
    public void render(DrawContext context, int x, int y, float delta) {
        long now = System.nanoTime();
        dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        float progress = clamp((now - started) / 260_000_000f);
        if (closing && progress >= 1) {
            if (client != null && client.currentScreen == this) client.setScreen(null);
            return;
        }
        float eased = closing ? progress * progress : 1 - (1 - progress) * (1 - progress);
        opacity = closing ? closeOpacity * (1 - progress) : progress;
        screenW = RenderUtil.getFixedScaledWidth();
        screenH = RenderUtil.getFixedScaledHeight();
        baseScale = Math.max(.2f, Math.min(screenW * .685f / GROUP_W, screenH * .5f / PANEL_H));
        scale = closing ? closeScale + (baseScale * .96f - closeScale) * eased : baseScale * (.96f + .04f * eased);
        groupX = (screenW - GROUP_W) / 2f;
        groupY = (screenH - PANEL_H) / 2f;
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).x = groupX + i * (PANEL_W + PANEL_GAP);
            panels.get(i).y = groupY;
        }
        scrollX = approach(scrollX, targetX, 14);
        scrollY = approach(scrollY, targetY, 14);
        mouseX = inputX(x);
        mouseY = inputY(y);
        accent = ThemeManager.getGuiAccent();
        hits.clear();
        description = null;
        validateFocus();
        RenderUtil.resetUiScale();
        RenderUtil.rect(0, 0, screenW, screenH, 0, alpha(new Color(0, 0, 0, 90)));
        // Snapshot the dimmed backdrop once so panels blur the world, not each other.
        HudBlurPipeline.beginBackdrop();
        RenderUtil.setUiScale(screenW / 2, screenH / 2, scale);
        // Scissor already applies RenderUtil's UI transform. Pass logical coordinates once.
        viewport = new Rect(unscaleX(0), unscaleY(0), screenW / scale, screenH / scale);
        clip = viewport;
        Scissor.push(clip.x, clip.y, clip.w, clip.h);
        try {
            for (Panel panel : panels) renderPanel(context, panel);
            ownerPanel = null;
            ownerFunction = null;
            ownerSetting = null;
            if (floatingColor != null) {
                float px = Math.clamp(pickerX, viewport.x, Math.max(viewport.x, viewport.right() - 100));
                float py = Math.clamp(pickerY, viewport.y, Math.max(viewport.y, viewport.bottom() - 78));
                panel(px, py, 100, 78);
                ownerSetting = floatingColor;
                addHit(new Rect(px, py, 100, 78), b -> { }, null);
                drawSetting(context, floatingColor, px, py);
                ownerSetting = null;
            }
            renderSearch(context);
            if (description != null && !searching && floatingColor == null) renderDescription(context);
        } finally {
            Scissor.pop();
            RenderUtil.resetUiScale();
            HudBlurPipeline.endBackdrop();
        }
    }

    private void renderPanel(DrawContext context, Panel p) {
        ownerPanel = p;
        ownerFunction = null;
        ownerSetting = null;
        float content = 3;
        for (Function f : p.modules) {
            float target = f == expanded && p.open && matches(f) ? settingsHeight(f) : 0;
            float reveal = approach(reveals.getOrDefault(f, 0f), target, 22);
            reveals.put(f, reveal);
            if (matches(f)) content += ROW_H + reveal + GAP;
        }
        float viewHeight = PANEL_H - HEADER_H - 5;
        p.maxScroll = Math.max(0, content - viewHeight);
        p.targetScroll = Math.clamp(p.targetScroll, 0, p.maxScroll);
        p.scroll = approach(p.scroll, p.targetScroll, 18);
        float px = p.x, py = p.y;
        panel(px, py, PANEL_W, PANEL_H);
        text(context, p.category.getDisplayName(), px + 8, py + 7, 7.5f, TEXT, 74, false);
        int index = panels.indexOf(p);
        RenderUtil.texture(px + 85, py + 6, 7, CATEGORY_ICONS[index], 0, alpha(TEXT));
        Rect previous = pushClip(new Rect(px + 3, py + HEADER_H, PANEL_W - 6, viewHeight));
        try {
            float my = py + HEADER_H - p.scroll;
            for (Function f : p.modules) {
                if (!matches(f)) continue;
                ownerFunction = f;
                float reveal = reveals.getOrDefault(f, 0f);
                Rect row = new Rect(px + 2, my, 96, ROW_H);
                boolean hovered = !closing && floatingColor == null && p.open
                        && row.intersect(clip).contains(mouseX, mouseY);
                float hover = approach(hovers.getOrDefault(f, 0f), hovered ? 1 : 0, 18);
                hovers.put(f, hover);
                // Row background: enabled modules glow with accent color
                float enabled = approach(toggles.getOrDefault(f, f.isEnabled() ? 1f : 0f), f.isEnabled() ? 1 : 0, 20);
                toggles.put(f, enabled);
                Color rowBg = new Color(255, 255, 255, Math.round(10 * hover));
                if (f.isEnabled() || enabled > 0.01f) {
                    int accentAlpha = Math.round(28 * enabled);
                    rowBg = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.max(accentAlpha, Math.round(10 * hover)));
                }
                if (reveal > .01f) rect(px + 4, my, 92, ROW_H + reveal, 3, new Color(100, 96, 124, 30));
                rect(px + 4, my, 92, ROW_H, 3, rowBg);
                String label = bindingFunction == f ? "[" + keyName(f.getKeybind()) + "] " + ez.minar.system.managers.LocalizationManager.get("Bind...") : f.getDisplayName();
                Color nameColor = f.isEnabled() ? accent : DISABLED;
                text(context, label, px + 8, my + 5, 6.2f, nameColor, 88, false);
                if (hovered) description = f.getDisplayDesc();
                if (p.open) addHit(row, button -> {
                    if (button == 0) f.toggle();
                    else if (button == 1 && !f.getSettings().isEmpty()) {
                        clearFocus();
                        expanded = expanded == f ? null : f;
                    } else if (button == 2) {
                        clearFocus();
                        bindingFunction = f;
                    }
                }, null);
                if (reveal > .01f) {
                    Rect old = pushClip(new Rect(px + 2, my + ROW_H, 96, reveal));
                    try {
                        float sy = my + ROW_H;
                        for (Setting setting : f.getSettings()) {
                            if (!setting.isVisible()) continue;
                            ownerSetting = setting;
                            drawSetting(context, setting, px, sy);
                            sy += settingHeight(setting);
                        }
                    } finally { popClip(old); }
                }
                ownerSetting = null;
                my += ROW_H + reveal + GAP;
            }
        } finally { popClip(previous); }
    }

    private float settingsHeight(Function f) {
        float height = 1;
        for (Setting setting : f.getSettings()) if (setting.isVisible()) height += settingHeight(setting);
        return height;
    }

    private float settingHeight(Setting s) {
        if (s instanceof NumberSetting) return 33;
        if (s instanceof ModeSetting mode) {
            List<Chip> options = optionChips(mode.getModes());
            return options.isEmpty() ? 18 : options.getLast().y + 14;
        }
        if (s instanceof MultiSetting multi) {
            return 18 + (chipsHeight(multi) - 18) * multiReveal.getOrDefault(multi, 0f);
        }
        if (s instanceof ColorSetting) return 78;
        if (s instanceof TextSetting || s instanceof ButtonSetting) return 33;
        return 18;
    }

    private void drawSetting(DrawContext c, Setting setting, float x, float y) {
        if (setting instanceof BooleanSetting s) {
            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 70, false);
            float a = approach(switches.getOrDefault(s, s.isEnabled() ? 1f : 0f), s.isEnabled() ? 1 : 0, 22);
            switches.put(s, a);
            rect(x + 79, y + 5, 16, 8, 4, mix(new Color(24, 24, 28), accent, .23f));
            rect(x + 80 + a * 8, y + 6, 6, 6, 3, accent);
            addHit(new Rect(x + 5, y, 90, 18), b -> { if (b == 0) s.setEnabled(!s.isEnabled()); }, null);
        } else if (setting instanceof NumberSetting s) {
            String value = number(s.getValue());
            float valueWidth = Msdf.width(Msdf.SF_REGULAR, value, 6);
            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 86 - valueWidth, false);
            text(c, value, x + 95 - valueWidth, y + 5, 6, Color.WHITE, valueWidth, false);
            float fraction = s.getMax() <= s.getMin() ? 0 : clamp((float) ((s.getValue() - s.getMin()) / (s.getMax() - s.getMin())));
            rect(x + 5, y + 16, 90, 2, 1, new Color(40, 40, 40));
            rect(x + 5, y + 16, fraction * 90, 2, 1, accent);
            rect(x + 3 + fraction * 90, y + 15, 4, 4, 2, accent);
            text(c, number(s.getMin()), x + 5, y + 23, 6, DISABLED, 40, false);
            String max = number(s.getMax());
            text(c, max, x + 95 - Msdf.width(Msdf.SF_REGULAR, max, 6), y + 23, 6, DISABLED, 44, false);
            addHit(new Rect(x + 5, y + 12, 90, 9), b -> { }, (mx, my) -> {
                double raw = s.getMin() + clamp((mx - x - 5) / 90) * (s.getMax() - s.getMin());
                double step = s.getStep();
                if (step > 0 && Double.isFinite(step)) raw = Math.round(raw / step) * step;
                s.setValue(Math.clamp(raw, s.getMin(), s.getMax()));
            });
        } else if (setting instanceof ModeSetting s) {
            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 90, false);
            for (Chip chip : optionChips(s.getModes())) {
                drawChip(c, s, chip, x, y, 0, 1, s.isEnabled(chip.label));
                addHit(new Rect(x + chip.x, y + chip.y, chip.w, 10),
                        b -> { if (b == 0) s.setMode(chip.label); }, null);
            }
        } else if (setting instanceof MultiSetting s) {
            boolean open = multiOpen.getOrDefault(s, false);
            float reveal = approach(multiReveal.getOrDefault(s, 0f), open ? 1 : 0, 18);
            multiReveal.put(s, reveal);

            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 66, false);
            float toggleHover = chipHover(s, "toggle", x + 74, y + 3, 21, 10);
            rect(x + 74, y + 3, 21, 10, 3, tint(accent, .35f + .18f * toggleHover));
            String toggleLabel = open ? "CLOSE" : "OPEN";
            float toggleW = Msdf.width(Msdf.SF_REGULAR, toggleLabel, 5);
            text(c, toggleLabel, x + 74 + (21 - toggleW) / 2f,
                    y + 3 + (10 - Msdf.height(Msdf.SF_REGULAR, 5)) / 2f, 5, TEXT, 21, false);
            addHit(new Rect(x + 74, y + 3, 21, 10), b -> { if (b == 0 || b == 1) multiOpen.put(s, !open); }, null);

            if (reveal > .01f) {
                List<Chip> chips = chips(s);
                Rect clip = pushClip(new Rect(x, y + 16, 95, (chipsHeight(s) - 16) * reveal));
                try {
                    for (Chip chip : chips) {
                        drawChip(c, s, chip, x, y, (1 - reveal) * 4, reveal, s.isEnabled(chip.label));
                        addHit(new Rect(x + chip.x, y + chip.y, chip.w, 10),
                                b -> { if (b == 0) s.toggle(chip.label); }, null);
                    }
                } finally {
                    popClip(clip);
                }
            }
        } else if (setting instanceof ColorSetting s) {
            drawColor(c, s, x, y);
        } else if (setting instanceof KeybindSetting s) {
            String key = bindingSetting == s ? ez.minar.system.managers.LocalizationManager.get("Bind...") : keyName(s.getKeybind());
            float w = Math.min(44, Msdf.width(Msdf.SF_REGULAR, key, 6) + 6);
            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 86 - w, false);
            rect(x + 95 - w, y + 4, w, 10, 2, tint(accent, .3f));
            text(c, key, x + 98 - w, y + 5, 6, TEXT, w - 6, false);
            addHit(new Rect(x + 95 - w, y + 4, w, 10), b -> {
                if (b <= 2) { clearFocus(); bindingSetting = s; }
            }, null);
        } else if (setting instanceof TextSetting s) {
            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 90, false);
            rect(x + 5, y + 16, 90, 13, 2, tint(accent, editing == s ? .35f : .15f));
            drawEditor(c, s.getValue(), editing == s, x + 8, y + 19, 84);
            addHit(new Rect(x + 5, y + 16, 90, 13), b -> {
                if (b == 0) { clearFocus(); editing = s; s.setEditing(true); cursor = s.getValue().length(); }
            }, null);
        } else if (setting instanceof ButtonSetting s) {
            text(c, s.getDisplayName(), x + 5, y + 5, 6, TEXT, 90, false);
            rect(x + 5, y + 16, 90, 13, 2, tint(accent, .25f));
            text(c, ez.minar.system.managers.LocalizationManager.get(s.getButtonText()), x + 9, y + 19, 6, TEXT, 82, false);
            addHit(new Rect(x + 5, y + 16, 90, 13), b -> { if (b == 0) s.press(); }, null);
        } else text(c, setting.getDisplayName(), x + 5, y + 5, 6, DISABLED, 90, false);
    }

    private void drawColor(DrawContext c, ColorSetting s, float x, float y) {
        Color color = s.getColor();
        if (color == null) return;
        float[] hsv = colorStates.get(s);
        if (hsv == null || colorRgb.getOrDefault(s, 0) != color.getRGB()) {
            float oldHue = hsv == null ? 0 : hsv[0];
            hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            if (hsv[1] == 0) hsv[0] = oldHue;
            colorStates.put(s, hsv);
            colorRgb.put(s, color.getRGB());
        }
        final float[] state = hsv;
        text(c, s.getName(), x + 5, y + 5, 6, TEXT, 73, false);
        rect(x + 83, y + 4, 12, 8, 2, color);
        // RectPipeline samples a row-major 3x3 grid with quintic interpolation.
        // The same curve is used for picking and inverted for the pointer below.
        Color hue = Color.getHSBColor(hsv[0], 1, 1);
        Color white = alpha(Color.WHITE), middle = alpha(mix(Color.WHITE, hue, .5f)), end = alpha(hue);
        RenderUtil.rect(x + 7, y + 16, 86, 45, 0,
                white, middle, end, white, middle, end, white, middle, end);
        Color transparent = alpha(new Color(0, 0, 0, 0));
        Color halfBlack = alpha(new Color(0, 0, 0, 128)), black = alpha(Color.BLACK);
        RenderUtil.rect(x + 7, y + 16, 86, 45, 0,
                transparent, transparent, transparent, halfBlack, halfBlack, halfBlack, black, black, black);
        for (int i = 0; i < 42; i++) {
            rect(x + 8 + i * 2, y + 65, 2, 2, 0, Color.getHSBColor(i / 41f, 1, 1));
        }
        float svX = x + 7 + inverseGradient(hsv[1]) * 86;
        float svY = y + 16 + inverseGradient(1 - hsv[2]) * 45;
        rect(svX - 2, svY - 2, 4, 4, 2, Color.BLACK);
        rect(svX - 1.5f, svY - 1.5f, 3, 3, 1.5f, Color.WHITE);
        rect(x + 8 + hsv[0] * 84 - 1, y + 64, 2, 4, 1, Color.WHITE);
        addHit(new Rect(x + 7, y + 16, 86, 45), b -> { }, (mx, my) -> {
            state[1] = gradient(clamp((mx - x - 7) / 86));
            state[2] = 1 - gradient(clamp((my - y - 16) / 45));
            writeColor(s, state);
        });
        addHit(new Rect(x + 8, y + 63, 84, 6), b -> { }, (mx, my) -> {
            state[0] = clamp((mx - x - 8) / 84);
            writeColor(s, state);
        });
    }

    private void writeColor(ColorSetting s, float[] hsv) {
        Color rgb = Color.getHSBColor(hsv[0], hsv[1], hsv[2]);
        Color result = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), s.getColor().getAlpha());
        s.setColor(result);
        colorRgb.put(s, result.getRGB());
    }

    /** A dedicated inline-sized picker also works for colors owned by an external theme menu. */
    public void openColorPicker(ColorSetting setting, float preferredX, float preferredY) {
        if (closing || setting == null) return;
        clearFocus();
        floatingColor = setting;
        pickerX = preferredX;
        pickerY = preferredY;
        hits.clear();
    }

    private List<Chip> chips(MultiSetting s) {
        return optionChips(s.getOptionsList());
    }

    private float chipsHeight(MultiSetting s) {
        List<Chip> list = chips(s);
        return list.isEmpty() ? 18 : list.getLast().y + 14;
    }

    private float chipHover(Setting setting, String option, float x, float y, float w, float h) {
        float[] anim = chipAnim.computeIfAbsent(new ChipKey(setting, option), k -> new float[2]);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        anim[0] = approach(anim[0], hovered ? 1 : 0, 24);
        return anim[0];
    }

    /** Shared look for Mode/Multi options: label centered in the pill, eased hover and selection. */
    private void drawChip(DrawContext c, Setting setting, Chip chip, float x, float y, float slide, float reveal, boolean selected) {
        String display = ez.minar.system.managers.LocalizationManager.get(chip.label);
        float cx = x + chip.x, cy = y + chip.y + slide;
        float hover = chipHover(setting, chip.label, cx, cy, chip.w, 10);

        float[] anim = chipAnim.get(new ChipKey(setting, chip.label));
        anim[1] = approach(anim[1], selected ? 1 : 0, 16);
        float on = anim[1];

        Color bg = mix(new Color(66, 64, 78), accent, .78f * on);
        rect(cx, cy, chip.w, 10, 3, new Color(
                Math.min(255, bg.getRed() + Math.round(26 * hover)),
                Math.min(255, bg.getGreen() + Math.round(26 * hover)),
                Math.min(255, bg.getBlue() + Math.round(26 * hover)),
                Math.round(Math.clamp((58 + 165 * on + 34 * hover) * reveal, 0f, 255f))));

        MsdfFont font = Msdf.SF_REGULAR;
        float textW = Msdf.width(font, display, 6);
        Color label = mix(new Color(126, 122, 139), Color.WHITE, Math.max(on, .55f * hover));
        text(c, display, cx + (chip.w - textW) / 2f, cy + (10 - Msdf.height(font, 6)) / 2f, 6,
                new Color(label.getRed(), label.getGreen(), label.getBlue(), Math.round(255 * reveal)), chip.w, false);
    }

    private List<Chip> optionChips(List<String> options) {
        List<Chip> result = new ArrayList<>();
        float x = 5, y = 16;
        for (String option : options) {
            float w = Math.min(90, Math.max(16, Msdf.width(Msdf.SF_REGULAR, option, 6) + 8));
            if (x > 5 && x + w > 95) { x = 5; y += 12; }
            result.add(new Chip(option, x, y, w));
            x += w + 2;
        }
        return result;
    }

    private void renderSearch(DrawContext c) {
        ownerPanel = null; ownerFunction = null; ownerSetting = null;
        float x = screenW / 2 - 40, y = groupY + PANEL_H + 10;
        rect(x, y, 80, 16, 5, new Color(24, 23, 27, 224));
        if (query.isEmpty() && !searching) text(c, "Поиск по модулям", x + 6, y + 5, 5.8f, new Color(108, 105, 115), 68, false);
        else drawEditor(c, query, searching, x + 5, y + 5, 70);
        addHit(new Rect(x, y, 80, 16), b -> {
            if (b == 0) { clearFocus(); searching = true; cursor = query.length(); }
        }, null);
    }

    private void renderDescription(DrawContext c) {
        if (description.isBlank()) return;
        String label = description;
        while (!label.isEmpty() && Msdf.width(Msdf.SF_REGULAR, label, 7.5f) > GROUP_W - 16) {
            label = label.substring(0, label.offsetByCodePoints(label.length(), -1));
        }
        float width = Msdf.width(Msdf.SF_REGULAR, label, 7.5f);
        text(c, label, screenW / 2 - width / 2, groupY - 13, 7.5f, Color.WHITE, GROUP_W - 16, false);
    }

    private void drawEditor(DrawContext c, String value, boolean focused, float x, float y, float w) {
        int caret = focused ? Math.min(cursor, value.length()) : value.length();
        int start = 0;
        while (start < caret && Msdf.width(Msdf.SF_REGULAR, value.substring(start, caret), 6) > w - 3)
            start = value.offsetByCodePoints(start, 1);
        String display = value.substring(start);
        if (focused && selectAll) rect(x, y - 1, Math.min(w, Msdf.width(Msdf.SF_REGULAR, display, 6)), 9, 0, tint(accent, .35f));
        text(c, display, x, y, 6, TEXT, w, false);
        if (focused && (System.nanoTime() / 500_000_000L) % 2 == 0) {
            float cx = x + Msdf.width(Msdf.SF_REGULAR, value.substring(start, caret), 6);
            rect(cx, y, .5f, 7, 0, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        mouseX = inputX((float) click.x()); mouseY = inputY((float) click.y());
        validateFocus();
        if (bindingFunction != null || bindingSetting != null) {
            setBinding(KeybindSetting.toMouseKey(click.button()));
            return true;
        }
        Hit hit = hitAt(mouseX, mouseY);
        if (floatingColor != null && (hit == null || hit.setting != floatingColor)) {
            floatingColor = null;
            draggingControl = null;
            hits.clear();
            return true;
        }
        if (hit == null || hit.setting != editing) stopEditing();
        searching = false;
        selectAll = false;
        if (hit != null) {
            if (hit.drag != null && click.button() == 0) {
                draggingControl = hit;
                hit.drag.accept(mouseX, mouseY);
            } else hit.action.accept(click.button());
            hits.clear();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (closing) return true;
        mouseX = inputX((float) click.x()); mouseY = inputY((float) click.y());
        if (click.button() != 0) return true;
        if (draggingPanel != null) {
            draggingPanel.x = mouseX - grabX - scrollX;
            draggingPanel.y = mouseY - grabY - scrollY;
        } else if (draggingControl != null && valid(draggingControl)) {
            // Keep a captured drag usable outside the track, but never through hidden settings.
            draggingControl.drag.accept(mouseX, mouseY);
        }
        hits.clear();
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingPanel = null;
        draggingControl = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (closing || floatingColor != null || draggingControl != null || draggingPanel != null) return true;
        float mx = inputX((float) x), my = inputY((float) y);
        for (Panel p : panels) {
            if (new Rect(p.x, p.y, PANEL_W, PANEL_H).contains(mx, my)) {
                p.targetScroll = Math.clamp(p.targetScroll - (float) vertical * 28, 0, p.maxScroll);
                break;
            }
        }
        hits.clear();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (closing) return true;
        validateFocus();
        int key = input.key();
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (bindingFunction != null || bindingSetting != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { bindingFunction = null; bindingSetting = null; }
            else setBinding(key == GLFW.GLFW_KEY_DELETE ? 0 : key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (floatingColor != null) { floatingColor = null; hits.clear(); }
            else if (editing != null || searching) { stopEditing(); searching = false; }
            else close();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            clearFocus(); searching = true; cursor = query.length(); selectAll = true;
            return true;
        }
        if (searching || editing != null) {
            String value = editorValue();
            cursor = Math.min(cursor, value.length());
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
                stopEditing(); searching = false; selectAll = false;
            } else if (ctrl && key == GLFW.GLFW_KEY_A) selectAll = true;
            else if (ctrl && (key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_X)) {
                if (selectAll && client != null) {
                    client.keyboard.setClipboard(value);
                    if (key == GLFW.GLFW_KEY_X) replaceSelection("");
                }
            } else if (ctrl && key == GLFW.GLFW_KEY_V && client != null) replaceSelection(client.keyboard.getClipboard());
            else if (key == GLFW.GLFW_KEY_HOME) { cursor = 0; selectAll = false; }
            else if (key == GLFW.GLFW_KEY_END) { cursor = value.length(); selectAll = false; }
            else if (key == GLFW.GLFW_KEY_LEFT) { cursor = cursor > 0 ? value.offsetByCodePoints(cursor, -1) : 0; selectAll = false; }
            else if (key == GLFW.GLFW_KEY_RIGHT) { cursor = cursor < value.length() ? value.offsetByCodePoints(cursor, 1) : cursor; selectAll = false; }
            else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                if (selectAll) replaceSelection("");
                else if (key == GLFW.GLFW_KEY_BACKSPACE && cursor > 0) {
                    int before = value.offsetByCodePoints(cursor, -1);
                    setEditorValue(value.substring(0, before) + value.substring(cursor)); cursor = before;
                } else if (key == GLFW.GLFW_KEY_DELETE && cursor < value.length()) {
                    setEditorValue(value.substring(0, cursor) + value.substring(value.offsetByCodePoints(cursor, 1)));
                }
            }
            hits.clear();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (closing) return true;
        validateFocus();
        if ((searching || editing != null) && input.isValidChar()) {
            replaceSelection(input.asString()); hits.clear(); return true;
        }
        return super.charTyped(input);
    }

    private void replaceSelection(String inserted) {
        String value = editorValue();
        inserted = inserted.replaceAll("[\\p{Cntrl}]", " ");
        int at = selectAll ? 0 : Math.min(cursor, value.length());
        String before = selectAll ? "" : value.substring(0, at);
        String after = selectAll ? "" : value.substring(at);
        int max = searching ? 128 : editing.getMaxLength();
        int available = Math.max(0, max - before.length() - after.length());
        if (inserted.length() > available) {
            int end = available;
            if (end > 0 && Character.isHighSurrogate(inserted.charAt(end - 1))) end--;
            inserted = inserted.substring(0, end);
        }
        setEditorValue(before + inserted + after);
        cursor = before.length() + inserted.length();
        selectAll = false;
    }

    private String editorValue() { return searching ? query : editing.getValue(); }
    private void setEditorValue(String value) { if (searching) query = value; else if (editing != null) editing.setValue(value); }
    private void setBinding(int key) {
        if (bindingFunction != null) bindingFunction.setKeybind(key);
        if (bindingSetting != null) bindingSetting.setKeybind(key);
        bindingFunction = null; bindingSetting = null;
    }
    private void stopEditing() { if (editing != null) editing.setEditing(false); editing = null; selectAll = false; }
    private void clearFocus() {
        stopEditing(); bindingFunction = null; bindingSetting = null;
        draggingPanel = null; draggingControl = null;
    }
    private boolean settingAvailable(Setting setting) {
        if (setting == floatingColor) return setting.isVisible();
        return expanded != null && matches(expanded) && setting.isVisible() && expanded.getSettings().contains(setting)
                && panels.stream().anyMatch(p -> p.open && p.modules.contains(expanded));
    }
    private void validateFocus() {
        if (editing != null && !settingAvailable(editing)) stopEditing();
        if (bindingSetting != null && !settingAvailable(bindingSetting)) bindingSetting = null;
        if (bindingFunction != null && (!matches(bindingFunction) || panels.stream().noneMatch(p -> p.open && p.modules.contains(bindingFunction)))) bindingFunction = null;
        if (draggingControl != null && !valid(draggingControl)) draggingControl = null;
    }
    private Hit hitAt(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (valid(h) && h.bounds.contains(x, y)) return h;
        }
        return null;
    }
    private boolean valid(Hit h) {
        if (h.setting != null && !settingAvailable(h.setting)) return false;
        return h.function == null || (h.panel.open && matches(h.function) && (h.setting == null || expanded == h.function));
    }
    private void addHit(Rect bounds, IntConsumer action, Drag drag) {
        Rect visible = bounds.intersect(clip);
        if (visible.w > 0 && visible.h > 0 && !closing)
            hits.add(new Hit(visible, ownerPanel, ownerFunction, ownerSetting, action, drag));
    }
    private Rect pushClip(Rect bounds) {
        Rect old = clip;
        clip = clip.intersect(bounds);
        Scissor.push(clip.x, clip.y, clip.w, clip.h);
        return old;
    }
    private void popClip(Rect old) { Scissor.pop(); clip = old; }
    private void panel(float x, float y, float w, float h) {
        RenderUtil.shadow(x, y + 2, w, h, 7, 10, .28f * opacity, 2, new Color(0, 0, 0, 160));
        RenderUtil.hudBlur(x, y, w, h, 7, 14, opacity, new Color(27, 26, 31, 215));
        rect(x, y, w, h, 7, new Color(23, 22, 27, 45));
    }
    private void rect(float x, float y, float w, float h, float radius, Color color) {
        if (w > 0 && h > 0) RenderUtil.rect(x, y, w, h, radius, alpha(color));
    }
    private void text(DrawContext c, String value, float x, float y, float size, Color color, float width, boolean bold) {
        MsdfFont font = bold ? Msdf.SF_BOLD : Msdf.SF_REGULAR;
        if (width <= 0) return;
        String result = value;
        if (Msdf.width(font, result, size) > width) {
            while (!result.isEmpty() && Msdf.width(font, result + "...", size) > width)
                result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
            result += "...";
        }
        RenderUtil.text(c, font, x, y, result, size, alpha(color));
    }
    private Color alpha(Color c) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * clamp(opacity))); }
    private static Color tint(Color c, float a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * clamp(a))); }
    private static Color mix(Color a, Color b, float f) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * f), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * f), Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * f), a.getAlpha());
    }
    private float approach(float value, float target, float speed) {
        float result = target + (value - target) * (float) Math.exp(-speed * dt);
        return Math.abs(result - target) < .01f ? target : result;
    }
    private boolean matches(Function f) { return normalize(f.getName()).contains(normalize(query)); }
    private static String normalize(String s) { return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private static float gradient(float value) {
        float segment = value < .5f ? 0 : .5f;
        float t = (value - segment) * 2;
        return segment + .5f * t * t * t * (t * (t * 6 - 15) + 10);
    }
    private static float inverseGradient(float value) {
        float low = 0, high = 1;
        for (int i = 0; i < 16; i++) {
            float mid = (low + high) / 2;
            if (gradient(mid) < value) low = mid; else high = mid;
        }
        return (low + high) / 2;
    }
    private static float clamp(float value) { return Math.clamp(value, 0, 1); }
    private static String number(double value) { return BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private float unscaleX(float x) { return screenW / 2 + (x - screenW / 2) / scale; }
    private float unscaleY(float y) { return screenH / 2 + (y - screenH / 2) / scale; }
    private float inputX(float x) { return unscaleX(RenderUtil.convertX(x)); }
    private float inputY(float y) { return unscaleY(RenderUtil.convertY(y)); }
    private boolean keyDown(int key) { return client != null && GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS; }
    private static String keyName(int key) {
        if (key <= 0) return "None";
        if (KeybindSetting.isMouseKey(key)) return "M" + (KeybindSetting.getMouseButton(key) + 1);
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            default -> key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25 ? "F" + (key - GLFW.GLFW_KEY_F1 + 1) : "Key " + key;
        };
    }
    private static final class Panel {
        final Category category;
        List<Function> modules = List.of();
        float x, y, scroll, targetScroll, maxScroll;
        boolean open = true;
        Panel(Category category) { this.category = category; }
    }
    @FunctionalInterface private interface Drag { void accept(float x, float y); }
    private record Hit(Rect bounds, Panel panel, Function function, Setting setting, IntConsumer action, Drag drag) { }
    private record Chip(String label, float x, float y, float w) { }

    private record ChipKey(Setting setting, String option) { }
    private record Rect(float x, float y, float w, float h) {
        float right() { return x + w; }
        float bottom() { return y + h; }
        boolean contains(float px, float py) { return w > 0 && h > 0 && px >= x && py >= y && px < right() && py < bottom(); }
        Rect intersect(Rect b) {
            float nx = Math.max(x, b.x), ny = Math.max(y, b.y);
            return new Rect(nx, ny, Math.max(0, Math.min(right(), b.right()) - nx), Math.max(0, Math.min(bottom(), b.bottom()) - ny));
        }
    }
}
