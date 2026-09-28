package ez.minar.system.menu;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.events.EventManager;
import ez.minar.system.features.render.ClickGuiSettings;
import ez.minar.system.managers.LocalizationManager;
import ez.minar.system.menu.components.ColorPickerWindow;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.*;
import ez.minar.utils.anim.Easing;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.*;

/**
 * ClickGUI implementation matching reference screenshot 1:1:
 *  - Left rail with Rockstar angular 'R' logo, 5 category icons (Combat helmet, Move, Bell, User, Dots), gear at bottom
 *  - Module list column with bold category title and clean module entries
 *  - Top bar with search input and user profile (name, "Media", avatar, mint sparkles)
 *  - Settings header: "Настройки <Module>", keybind pill, description, cross close icon
 *  - 2-column clean flat settings layout (no card frames):
 *    - Mode setting with clean text and mint capsule with dark text when selected
 *    - Slider with value suffix (e.g. "3 блока") and mint knob
 *    - Toggle switch with off/on states (dark with white knob vs mint with dark knob)
 *    - Multi-setting showing count e.g. "4 из 7" in mint green, expandable to chips
 *    - Color picker, keybind, text, and button settings
 */
public class ClickGui extends Screen {

    private static final Category[] CATEGORIES = {
            Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC
    };

    private static final Identifier[] CATEGORY_ICONS = {
            Identifier.of("minar", "icons/rockstar_hd/cat_combat.png"),
            Identifier.of("minar", "icons/rockstar_hd/cat_movement.png"),
            Identifier.of("minar", "icons/rockstar_hd/cat_visuals.png"),
            Identifier.of("minar", "icons/rockstar_hd/cat_player.png"),
            Identifier.of("minar", "icons/rockstar_hd/cat_other.png")
    };
    private static final Identifier ICON_LOGO = Identifier.of("minar", "icons/rockstar_hd/rockstar_logo.png");
    private static final Identifier ICON_SEARCH = Identifier.of("minar", "icons/rockstar_hd/rockstar_search.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("minar", "icons/rockstar_hd/rockstar_gear.png");
    private static final Identifier ICON_CROSS = Identifier.of("minar", "icons/rockstar_hd/rockstar_xmark.png");
    private static final Identifier ICON_AVATAR = Identifier.of("minar", "icons/fig_local_avatar.png");
    private static final Identifier ICON_SPARKLES = Identifier.of("minar", "icons/201_tabler_sparkles.png");

    // ---------------------------------------------------------------- palette
    private static final Color WIN_BG = new Color(13, 24, 21, 250);       // Dark pine teal slate (#0d1815)
    private static final Color WIN_BORDER = new Color(255, 255, 255, 14);  // Crisp subtle window border
    private static final Color DIVIDER = new Color(255, 255, 255, 14);     // Thin divider lines
    private static final Color TRACK_BG = new Color(255, 255, 255, 12);   // Slider dark track line
    private static final Color HOVER_BG = new Color(255, 255, 255, 8);    // Subtle row hover
    private static final Color CHIP_HOVER_BG = new Color(255, 255, 255, 12);
    private static final Color KEYBIND_BG = new Color(255, 255, 255, 10);
    private static final Color KEYBIND_BORDER = new Color(255, 255, 255, 20);

    private static final Color TEXT_WHITE = new Color(250, 254, 252);     // Clean bright text
    private static final Color TEXT_LABEL = new Color(195, 208, 204);     // Soft clean setting label
    private static final Color TEXT_MODULE_IDLE = new Color(145, 162, 157);// Non-enabled module
    private static final Color TEXT_MODULE_HOVER = new Color(230, 242, 238);
    private static final Color TEXT_DIM = new Color(135, 158, 152);        // Secondary/descriptions/search/unselected chips
    private static final Color TEXT_MUTED = new Color(90, 110, 105);

    // Accent: Luminous Neon Mint Green sampled from reference image (#0AFFB7 / 10, 255, 183)
    private static final Color ACCENT_DEFAULT = new Color(10, 255, 183);
    private static final Color ACCENT_TEXT_DARK = new Color(0, 32, 13);   // Dark text on active mint pills!

    // ---------------------------------------------------------------- geometry (reference px)
    private static final float W = 1040f, H = 660f, R_WIN = 16f;
    private static final float RAIL_W = 60f;
    private static final float LIST_X = 60f, LIST_W = 205f;
    private static final float DIVIDER_X = 265f;
    private static final float TOP_H = 48f;
    private static final float LIST_TOP = 56f, LIST_FIRST = 72f, LIST_PITCH = 34f;
    private static final float COL_L = 300f, COL_R = 670f, COL_W = 330f;
    private static final float SET_TOP = 116f, SET_FIRST = 138f;
    private static final float CHIP_H = 22f, CHIP_PAD = 9f, CHIP_GAP_X = 7f, CHIP_ROW = 28f;
    private static final float ANIM_OPEN_MS = 220f;

    // ---------------------------------------------------------------- hit boxes
    private static final int H_CATEGORY = 0, H_MODULE = 1, H_BIND = 2, H_CLOSE = 3, H_SEARCH = 4, H_GEAR = 5,
            H_MODE = 6, H_MULTI = 7, H_SLIDER = 8, H_BOOL = 9, H_COLOR = 10, H_BUTTON = 11, H_KEY = 12, H_TEXT = 13,
            H_MULTI_EXPAND = 14;

    private record Hit(int kind, float x, float y, float w, float h, Object a, Object b) {
        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    // ---------------------------------------------------------------- state
    private float winX, winY, d = 0.5f;
    private boolean layoutReady;
    private boolean draggingWindow;
    private float dragGrabX, dragGrabY;

    private int activeCategory = 0; // Starts in COMBAT matching reference screenshot
    private float categoryAnim = 1f;
    private float catIndicatorY = 0f;
    private Function selected;
    private float selectAnim = 1f;

    private float listScroll, listScrollTarget, listMaxScroll;
    private float setScroll, setScrollTarget, setMaxScroll;

    private boolean searchFocused;
    private String searchQuery = "";

    private Function bindingFunction;
    private KeybindSetting bindingSetting;
    private TextSetting editingText;
    private NumberSetting draggingSlider;
    private float dragTrackX, dragTrackW;

    private long animationStart, lastFrame;
    private boolean closing;
    private float dt = 1f / 60f;
    private float accR = 10f, accG = 245f, accB = 175f;
    private Color accent = ACCENT_DEFAULT;

    private final Map<Object, Float> anims = new HashMap<>();
    private final Set<MultiSetting> expandedMulti = new HashSet<>();

    private final ColorPickerWindow colorPicker = new ColorPickerWindow();

    public static void openColorPicker(ColorSetting setting, float preferredX, float preferredY) {
        if (EventManager.clickGui != null) {
            EventManager.clickGui.colorPicker.open(setting, preferredX, preferredY);
        }
    }

    public ColorPickerWindow getColorPicker() {
        return colorPicker;
    }

    public ClickGui() {
        super(Text.of("Minar"));
    }

    // ================================================================ lifecycle

    @Override
    public void onDisplayed() {
        closing = false;
        animationStart = System.currentTimeMillis();
        lastFrame = 0L;
        bindingFunction = null;
        bindingSetting = null;
        if (editingText != null) editingText.setEditing(false);
        editingText = null;
        draggingSlider = null;
        draggingWindow = false;
        searchFocused = false;
        searchQuery = "";
        colorPicker.reset();
        layoutReady = false;
        activeCategory = 0;
        catIndicatorY = 0f;
        categoryAnim = 1f;
        selectAnim = 0f;
        selected = null;
        ensureSelection();
        playSound("gui_open");
        super.onDisplayed();
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        draggingWindow = false;
        draggingSlider = null;
        animationStart = System.currentTimeMillis();
        colorPicker.close();
        if (editingText != null) editingText.setEditing(false);
        editingText = null;
        playSound("gui_close");
    }

    private float openProgress() {
        float p = Math.clamp((System.currentTimeMillis() - animationStart) / ANIM_OPEN_MS, 0f, 1f);
        return closing ? 1f - p : p;
    }

    // ================================================================ helpers

    private float f(float px) {
        return px * d;
    }

    private float approach(float cur, float target, float speed) {
        float v = cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(v - target) < 0.0005f ? target : v;
    }

    private float anim(Object key, boolean on, float speed) {
        float target = on ? 1f : 0f;
        float v = approach(anims.getOrDefault(key, target), target, speed);
        anims.put(key, v);
        return v;
    }

    private static String tr(String key) {
        return LocalizationManager.get(key);
    }

    private List<Function> visibleModules() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Function> out = new ArrayList<>();
        if (!q.isEmpty()) {
            for (Category c : CATEGORIES) {
                for (Function fn : FunctionManager.getFunctionsByCategory(c)) {
                    if (isHidden(fn)) continue;
                    String dn = fn.getName().equals("AttackAura") ? "aura" : fn.getDisplayName().toLowerCase(Locale.ROOT);
                    String name = fn.getName().toLowerCase(Locale.ROOT);
                    String desc = fn.getDisplayDesc() != null ? fn.getDisplayDesc().toLowerCase(Locale.ROOT) : "";
                    boolean match = name.contains(q) || dn.contains(q) || desc.contains(q);
                    if (!match) {
                        for (Setting s : fn.getSettings()) {
                            if (s.getName().toLowerCase(Locale.ROOT).contains(q) || s.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) {
                                match = true;
                                break;
                            }
                            if (s instanceof MultiSetting ms) {
                                for (String opt : ms.getOptionsList()) {
                                    if (opt.toLowerCase(Locale.ROOT).contains(q) || tr(opt).toLowerCase(Locale.ROOT).contains(q)) {
                                        match = true;
                                        break;
                                    }
                                }
                            }
                            if (s instanceof ModeSetting ms) {
                                for (String opt : ms.getModes()) {
                                    if (opt.toLowerCase(Locale.ROOT).contains(q) || tr(opt).toLowerCase(Locale.ROOT).contains(q)) {
                                        match = true;
                                        break;
                                    }
                                }
                            }
                            if (match) break;
                        }
                    }
                    if (match && !out.contains(fn)) out.add(fn);
                }
            }
            return out;
        }
        for (Function fn : FunctionManager.getFunctionsByCategory(CATEGORIES[activeCategory])) {
            if (!isHidden(fn)) out.add(fn);
        }
        return out;
    }

    private boolean isHidden(Function fn) {
        return fn instanceof ez.minar.system.features.render.HUD || "HUD".equalsIgnoreCase(fn.getName());
    }

    private void ensureSelection() {
        List<Function> mods = visibleModules();
        if (selected == null || !mods.contains(selected)) {
            Function aura = null;
            for (Function f : mods) {
                if (f instanceof ez.minar.system.features.combat.AttackAura || "AttackAura".equalsIgnoreCase(f.getName())) {
                    aura = f;
                    break;
                }
            }
            Function next = aura != null ? aura : (mods.isEmpty() ? null : mods.get(0));
            if (next != selected) select(next);
        }
    }

    private void select(Function fn) {
        if (fn == selected) return;
        selected = fn;
        selectAnim = 0f;
        setScroll = setScrollTarget = 0f;
        bindingSetting = null;
        if (editingText != null) editingText.setEditing(false);
        editingText = null;
        draggingSlider = null;
    }

    private void addHit(int kind, float x, float y, float w, float h, Object a, Object b, float[] clip) {
        if (clip != null) {
            float x1 = Math.max(x, clip[0]), y1 = Math.max(y, clip[1]);
            float x2 = Math.min(x + w, clip[0] + clip[2]), y2 = Math.min(y + h, clip[1] + clip[3]);
            if (x2 <= x1 || y2 <= y1) return;
            hits.add(new Hit(kind, x1, y1, x2 - x1, y2 - y1, a, b));
        } else {
            hits.add(new Hit(kind, x, y, w, h, a, b));
        }
    }

    private void textC(DrawContext ctx, MsdfFont font, float x, float cy, String s, float size, Color c) {
        RenderUtil.text(ctx, font, x, cy - Msdf.height(font, size) / 2f, s, size, c);
    }

    private void textR(DrawContext ctx, MsdfFont font, float right, float cy, String s, float size, Color c) {
        textC(ctx, font, right - Msdf.width(font, s, size), cy, s, size, c);
    }

    private static String fmt(NumberSetting n) {
        double step = n.getStep();
        int decimals = step >= 1.0 || step <= 0 ? 0 : Math.min(3, (int) Math.ceil(-Math.log10(step) - 1e-9));
        String s = String.format(Locale.US, "%." + decimals + "f", n.getValue());
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String fmtValue(NumberSetting num) {
        String name = num.getName().toLowerCase(Locale.ROOT);
        String val = fmt(num);
        if (name.contains("дистанция") || name.contains("радиус") || name.contains("range") || name.contains("distance")) {
            double v = num.getValue();
            if (Math.abs(v - 1.0) < 0.05) return val + " блок";
            if (v >= 2.0 && v <= 4.05) return val + " блока";
            return val + " блоков";
        }
        if (name.contains("flick каждые") || name.contains("ударов") || name.contains("hits")) {
            return val + " ударов";
        }
        if (name.contains("скорость") || name.contains("speed")) {
            return val + "x";
        }
        if (name.contains("шанс") || name.contains("chance")) {
            return val + "%";
        }
        if (name.contains("задержк") || name.contains("delay") || name.contains("time") || name.contains("время")) {
            return val + " мс";
        }
        return val;
    }

    // ================================================================ render

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float a = Easing.OUT_CUBIC.ease(openProgress());
        RenderUtil.rect(0, 0, RenderUtil.getFixedScaledWidth(), RenderUtil.getFixedScaledHeight(), 0,
                new Color(4, 8, 7, (int) (120 * a)));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float mx = RenderUtil.convertX(mouseX);
        float my = RenderUtil.convertY(mouseY);
        float open = openProgress();
        if (closing && open <= 0.01f) {
            if (client != null) client.setScreen(null);
            return;
        }
        float eased = Easing.SWING_OVERSHOOT.ease(open);
        float op = Easing.OUT_CUBIC.ease(open);

        float screenW = RenderUtil.getFixedScaledWidth();
        float screenH = RenderUtil.getFixedScaledHeight();
        ClickGuiSettings gui = FunctionManager.getFunction(ClickGuiSettings.class);
        float userScale = gui != null ? (float) gui.getScale().getValue() : 1.0f;
        float targetH = screenH * 0.88f * (userScale <= 0f ? 1f : userScale);
        float targetW = targetH * (W / H);
        if (targetW > screenW * 0.94f) {
            targetW = screenW * 0.94f;
            targetH = targetW * (H / W);
        }
        float nd = Math.clamp(targetH / H, 0.45f, 0.95f);
        if (Math.abs(nd - d) > 0.001f) layoutReady = false;
        d = nd;
        if (!layoutReady) {
            winX = (screenW - f(W)) / 2f;
            winY = (screenH - f(H)) / 2f;
            layoutReady = true;
        }

        tick();
        ensureSelection();
        hits.clear();

        float wx = winX;
        float wy = winY + (1f - eased) * f(20);

        // Window background & crisp subtle border (clean look, no blurry shadow or glow)
        RenderUtil.rect(wx, wy, f(W), f(H), f(R_WIN), withA(WIN_BG, op));
        RenderUtil.outline(wx, wy, f(W), f(H), f(R_WIN), 1f, withA(WIN_BORDER, op));

        // Exactly ONE vertical divider separating module list from settings panel (starts below header area)
        float divY1 = wy + f(60f);
        float divH = f(H - 84f);
        RenderUtil.rect(wx + f(DIVIDER_X), divY1, Math.max(1f, f(1)), divH, 0, withA(DIVIDER, op));

        renderRail(context, wx, wy, op, mx, my);
        renderList(context, wx, wy, op, mx, my);
        renderTopBar(context, wx, wy, op, mx, my);
        renderSettings(context, wx, wy, op, mx, my);

        if (colorPicker.isOpen()) colorPicker.render(context, mx, my, deltaTicks);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        dt = Math.min(50f, now - lastFrame) / 1000f;
        lastFrame = now;

        categoryAnim = Math.min(1f, categoryAnim + dt * 4.2f);
        selectAnim = Math.min(1f, selectAnim + dt * 3.4f);

        listScrollTarget = Math.clamp(listScrollTarget, 0f, listMaxScroll);
        setScrollTarget = Math.clamp(setScrollTarget, 0f, setMaxScroll);
        listScroll = approach(listScroll, listScrollTarget, 16f);
        setScroll = approach(setScroll, setScrollTarget, 16f);

        ClickGuiSettings gui = FunctionManager.getFunction(ClickGuiSettings.class);
        Color t = (gui != null && !gui.getColorMode().isEnabled(ClickGuiSettings.MODE_THEME))
                ? gui.getAccent(0f)
                : ACCENT_DEFAULT;
        if (t.getRed() > 240 && t.getGreen() > 240 && t.getBlue() > 240) {
            t = ACCENT_DEFAULT;
        }
        accR = approach(accR, t.getRed(), 16f);
        accG = approach(accG, t.getGreen(), 16f);
        accB = approach(accB, t.getBlue(), 16f);
        accent = new Color(Math.round(accR), Math.round(accG), Math.round(accB));
    }

    private Color accentAt(float position) {
        ClickGuiSettings gui = FunctionManager.getFunction(ClickGuiSettings.class);
        if (gui != null && !gui.getColorMode().isEnabled(ClickGuiSettings.MODE_THEME)) {
            Color c = gui.getAccent(position);
            if (c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240) {
                return ACCENT_DEFAULT;
            }
            return c;
        }
        return accent;
    }

    // ---------------------------------------------------------------- rail

    private void renderRail(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        float cx = wx + f(RAIL_W / 2f);
        float logo = f(24);
        RenderUtil.texture(cx - logo / 2f, wy + f(TOP_H / 2f) - logo / 2f, logo, ICON_LOGO, 0, withA(accent, op));

        for (int i = 0; i < CATEGORIES.length; i++) {
            float cy = wy + f(72 + i * 44);
            boolean hovered = inside(mx, my, wx, cy - f(20), f(RAIL_W), f(40));
            boolean active = searchQuery.isEmpty() && i == activeCategory;
            float a = anim("cat-a-" + i, active, 16f);
            float h = anim("cat-h-" + i, hovered, 16f);
            float s = f(20) + f(2) * a + f(1) * h;
            Color c = lerp(lerp(TEXT_MUTED, TEXT_WHITE, h), accentAt((float) i / (CATEGORIES.length - 1)), a);
            RenderUtil.texture(cx - s / 2f, cy - s / 2f, s, CATEGORY_ICONS[i], 0, withA(c, op));
            addHit(H_CATEGORY, wx, cy - f(20), f(RAIL_W), f(40), i, null, null);
        }

        // Bottom gear icon (Rockstar settings entry)
        float gearY = wy + f(H - 28f);
        boolean gearHover = inside(mx, my, wx, gearY - f(18), f(RAIL_W), f(36));
        float gh = anim("gear-h", gearHover, 16f);
        float gs = f(19) + f(1.5f) * gh;
        RenderUtil.texture(cx - gs / 2f, gearY - gs / 2f, gs, ICON_SETTINGS, 0,
                withA(lerp(TEXT_MUTED, TEXT_WHITE, gh), op));
        addHit(H_GEAR, wx, gearY - f(18), f(RAIL_W), f(36), null, null, null);
    }

    // ---------------------------------------------------------------- module list

    private void renderList(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        String title = searchQuery.isEmpty() ? CATEGORIES[activeCategory].getDisplayName() : tr("Search");
        textC(ctx, Msdf.SF_BOLD, wx + f(78), wy + f(TOP_H / 2f), title, f(20), withA(TEXT_WHITE, op));

        float clipX = wx + f(LIST_X), clipY = wy + f(LIST_TOP);
        float clipW = f(LIST_W), clipH = f(H - LIST_TOP - 12);
        float[] clip = {clipX, clipY, clipW, clipH};

        List<Function> mods = visibleModules();
        float contentH = f(LIST_FIRST - LIST_TOP) + mods.size() * f(LIST_PITCH);
        listMaxScroll = Math.max(0f, (contentH - clipH + f(12)) / d);

        float ce = Easings.OutCubic(categoryAnim);

        Scissor.push(clipX, clipY, clipW, clipH);
        for (int i = 0; i < mods.size(); i++) {
            Function fn = mods.get(i);
            float cy = wy + f(LIST_FIRST) + i * f(LIST_PITCH) - listScroll * d;
            if (cy < clipY - f(24) || cy > clipY + clipH + f(24)) continue;
            float lop = op * ce;

            float rowX = wx + f(70), rowW = f(LIST_W - 10), rowH = f(28);
            boolean hovered = inside(mx, my, rowX, cy - rowH / 2f, rowW, rowH) && inside(mx, my, clipX, clipY, clipW, clipH);
            float h = anim(fn.hashCode() + "-lh", hovered, 18f);
            float e = anim(fn.hashCode() + "-le", fn.isEnabled(), 18f);
            float s = anim(fn.hashCode() + "-ls", fn == selected, 18f);

            // Clean subtle highlight on hover / select (NO harsh borders or bars)
            if (s > 0.001f) {
                RenderUtil.rect(rowX, cy - rowH / 2f, rowW, rowH, f(6), withA(new Color(255, 255, 255, 8), lop * s));
            } else if (h > 0.001f) {
                RenderUtil.rect(rowX, cy - rowH / 2f, rowW, rowH, f(6), withA(HOVER_BG, lop * h));
            }

            Color idleCol = lerp(TEXT_MODULE_IDLE, TEXT_WHITE, h);
            Color nonEnabledCol = lerp(idleCol, TEXT_WHITE, s);
            Color col = lerp(nonEnabledCol, accentAt((float) i / Math.max(1, mods.size() - 1)), e);

            String name = bindingFunction == fn ? "..." : (fn.getName().equals("AttackAura") ? "Aura" : fn.getDisplayName());
            MsdfFont font = s > 0.5f ? Msdf.SF_SEMIBOLD : Msdf.SF_REGULAR;
            float textIndent = f(8);
            textC(ctx, font, rowX + textIndent, cy,
                    trim(name, rowW - textIndent - f(8), f(15.5f), font), f(15.5f), withA(col, lop));

            addHit(H_MODULE, rowX, cy - rowH / 2f, rowW, rowH, fn, null, clip);
        }
        if (mods.isEmpty()) {
            textC(ctx, Msdf.SF_REGULAR, wx + f(78), wy + f(LIST_FIRST), tr("Nothing found"), f(14), withA(TEXT_MUTED, op * ce));
        }
        Scissor.pop();
    }

    // ---------------------------------------------------------------- top bar

    private void renderTopBar(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        float cy = wy + f(TOP_H / 2f);
        float sw = f(230), sh = f(28);
        float sx = wx + f(300), sy = cy - sh / 2f;
        boolean hover = inside(mx, my, sx, sy, sw, sh);
        float focus = anim("search-focus", searchFocused, 16f);
        float h = anim("search-hover", hover, 16f);

        RenderUtil.rect(sx, sy, sw, sh, f(8), withA(new Color(255, 255, 255, (int) (8 + 10 * focus + 6 * h)), op));
        if (focus > 0.01f) {
            RenderUtil.outline(sx, sy, sw, sh, f(8), 1f, withA(accent, op * focus * 0.6f));
        }
        float is = f(13.5f);
        RenderUtil.texture(sx + f(8), cy - is / 2f, is, ICON_SEARCH, 0, withA(lerp(TEXT_DIM, TEXT_WHITE, focus), op));

        String shown;
        Color col;
        if (searchQuery.isEmpty() && !searchFocused) {
            shown = "Поиск";
            col = TEXT_DIM;
        } else {
            boolean caret = searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L;
            shown = searchQuery + (caret ? "|" : "");
            col = TEXT_WHITE;
            if (searchQuery.isEmpty() && !caret) {
                shown = "Поиск";
                col = TEXT_MUTED;
            }
        }
        textC(ctx, Msdf.SF_REGULAR, sx + f(28), cy, trim(shown, sw - f(52), f(14f), Msdf.SF_REGULAR), f(14f), withA(col, op));

        if (!searchQuery.isEmpty()) {
            float cxIcon = sx + sw - f(14);
            boolean xHover = inside(mx, my, cxIcon - f(8), cy - f(8), f(16), f(16));
            RenderUtil.texture(cxIcon - f(5.5f), cy - f(5.5f), f(11), ICON_CROSS, 0,
                    withA(xHover ? TEXT_WHITE : TEXT_DIM, op));
        }
        addHit(H_SEARCH, sx, sy, sw, sh, null, null, null);

        String user = "Player";
        try {
            user = MinecraftClient.getInstance().getSession().getUsername();
        } catch (Exception ignored) {
        }
        float avatarSize = f(28);
        float avatarX = wx + f(W - 68);
        float avatarY = cy - avatarSize / 2f;
        RenderUtil.texture(avatarX, avatarY, avatarSize, ICON_AVATAR, avatarSize / 2f, withA(Color.WHITE, op));

        // Green sparkles icon right next to avatar
        float sparkSize = f(15);
        RenderUtil.texture(wx + f(W - 30), cy - sparkSize / 2f, sparkSize, ICON_SPARKLES, 0, withA(accent, op));

        // Username and Role
        textR(ctx, Msdf.SF_SEMIBOLD, avatarX - f(10), cy - f(6), trim(user, f(160), f(13.5f), Msdf.SF_SEMIBOLD), f(13.5f), withA(TEXT_WHITE, op));
        textR(ctx, Msdf.SF_REGULAR, avatarX - f(10), cy + f(7), "Media", f(11.5f), withA(TEXT_DIM, op));
    }

    // ---------------------------------------------------------------- settings panel

    private void renderSettings(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        Function fn = selected;
        float se = Easings.OutCubic(selectAnim);
        float pop = op * se;
        float slideY = (1f - se) * f(12f);

        // close button aligned with title
        float xs = f(16);
        float xcx = wx + f(W - 36), xcy = wy + f(76);
        boolean xh = inside(mx, my, xcx - f(14), xcy - f(14), f(28), f(28));
        float xa = anim("close-h", xh, 16f);
        RenderUtil.texture(xcx - xs / 2f, xcy - xs / 2f, xs, ICON_CROSS, 0, withA(lerp(new Color(190, 210, 205), Color.WHITE, xa), op));
        addHit(H_CLOSE, xcx - f(14), xcy - f(14), f(28), f(28), null, null, null);

        if (fn == null) {
            textC(ctx, Msdf.SF_REGULAR, wx + f(COL_L), wy + f(76), tr("Select a module"), f(18), withA(TEXT_DIM, op));
            setMaxScroll = 0f;
            return;
        }

        // Header Title (smooth slide-up animation on selection)
        String moduleName = fn.getName().equals("AttackAura") ? "Aura" : fn.getDisplayName();
        String title = "Настройки " + moduleName;
        float titleSize = f(21);
        float tx = wx + f(COL_L);
        float tcy = wy + f(76) + slideY;
        String t = trim(title, f(550), titleSize, Msdf.SF_BOLD);
        textC(ctx, Msdf.SF_BOLD, tx, tcy, t, titleSize, withA(TEXT_WHITE, pop));

        // Keybind chip right next to title
        float titleW = Msdf.width(Msdf.SF_BOLD, t, titleSize);
        float kx = tx + titleW + f(12);
        boolean binding = bindingFunction == fn;
        String keyText = binding ? "..." : (fn.getKeybind() > 0 ? getKeyName(fn.getKeybind()) : "");
        if (!keyText.isEmpty()) {
            float kw = Msdf.width(Msdf.SF_REGULAR, keyText, f(13.5f));
            float pw = kw + f(14);
            float ph = f(20);
            float px = kx;
            float py = tcy - ph / 2f;
            RenderUtil.rect(px, py, pw, ph, f(5), withA(new Color(255, 255, 255, 12), pop));
            textC(ctx, Msdf.SF_REGULAR, px + f(7), tcy, keyText, f(13.5f), withA(binding ? accent : TEXT_LABEL, pop));
            addHit(H_BIND, px - f(4), py - f(4), pw + f(8), ph + f(8), fn, null, null);
        } else {
            addHit(H_BIND, kx, tcy - f(10), f(24), f(20), fn, null, null);
        }

        // Description
        String desc = fn.getDisplayDesc();
        if (desc != null && !desc.isEmpty()) {
            textC(ctx, Msdf.SF_REGULAR, tx, wy + f(100) + slideY, trim(desc, f(700), f(13), Msdf.SF_REGULAR), f(13),
                    withA(TEXT_DIM, pop));
        }

        // Settings Columns
        float clipX = wx + f(DIVIDER_X) + 1, clipY = wy + f(SET_TOP);
        float clipW = f(W - DIVIDER_X) - 6, clipH = f(H - SET_TOP - 14);
        float[] clip = {clipX, clipY, clipW, clipH};

        List<Setting> settings = fn.getSettings().stream().filter(Setting::isVisible).toList();
        if (settings.isEmpty()) {
            float cx = wx + f(DIVIDER_X) + f(W - DIVIDER_X) / 2f;
            float cy = wy + f(SET_TOP) + f(H - SET_TOP) / 2f;
            String noSet = "В этой функции нет настроек";
            textC(ctx, Msdf.SF_REGULAR, cx - Msdf.width(Msdf.SF_REGULAR, noSet, f(16)) / 2f, cy,
                    noSet, f(16), withA(TEXT_MUTED, pop));
            setMaxScroll = 0f;
            return;
        }

        Scissor.push(clipX, clipY, clipW, clipH);
        float[] colY = {f(SET_FIRST), f(SET_FIRST)};
        float[] colX = {wx + f(COL_L), wx + f(COL_R)};
        float colW = f(COL_W);
        float maxBottom = 0f;

        for (int i = 0; i < settings.size(); i++) {
            Setting s = settings.get(i);
            int col = i % 2;
            float bottom = contentBottom(s, colW);
            float cy = wy + colY[col] + slideY - setScroll * d;

            if (cy + bottom > clipY - f(20) && cy - f(20) < clipY + clipH) {
                renderSetting(ctx, s, colX[col], cy, colW, pop, mx, my, clip, bottom);
            }
            colY[col] += bottom + f(22f);
            maxBottom = Math.max(maxBottom, colY[col]);
        }
        Scissor.pop();
        setMaxScroll = Math.max(0f, (maxBottom - f(H) + f(24f)) / d);
    }

    private float contentBottom(Setting s, float colW) {
        if (s instanceof ModeSetting m) {
            int rows = chipRows(m.getModes(), colW);
            return f(26f) + (rows - 1) * f(CHIP_ROW);
        }
        if (s instanceof MultiSetting m) {
            float exp = anims.getOrDefault("m-exp-" + m.hashCode(), expandedMulti.contains(m) ? 1f : 0f);
            if (exp <= 0.001f) return f(12f);
            int rows = chipRows(m.getOptionsList(), colW);
            float fullH = f(26f) + (rows - 1) * f(CHIP_ROW);
            return f(12f) + (fullH - f(12f)) * Easing.OUT_CUBIC.ease(exp);
        }
        if (s instanceof NumberSetting) return f(28f);
        return f(14f);
    }

    private void renderSetting(DrawContext ctx, Setting s, float x, float cy, float w, float a,
                               float mx, float my, float[] clip, float bottom) {
        String label = s.getDisplayName();
        float labelSize = f(14.5f);

        if (s instanceof NumberSetting num) {
            String val = fmtValue(num);
            float vw = Msdf.width(Msdf.SF_REGULAR, val, labelSize);

            float ty = cy + f(20);
            float th = f(3f);
            double range = num.getMax() - num.getMin();
            float target = (float) Math.clamp((num.getValue() - num.getMin()) / Math.max(1e-6, range), 0.0, 1.0);
            Object pk = num.hashCode() + "-sv";
            float prog = draggingSlider == num ? target : approach(anims.getOrDefault(pk, target), target, 24f);
            anims.put(pk, prog);

            boolean hover = inside(mx, my, x, ty - f(10), w, f(20));
            boolean interacting = draggingSlider == num;
            float hv = anim(num.hashCode() + "-sh", hover || interacting, 18f);

            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - vw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(lerp(TEXT_LABEL, TEXT_WHITE, hv), a));
            textR(ctx, Msdf.SF_REGULAR, x + w, cy, val, labelSize, withA(accent, a));

            float r = f(4.5f) + f(1.5f) * hv;
            float thumbCx = x + f(4) + (w - f(8)) * prog;
            RenderUtil.rect(x, ty - th / 2f, w, th, th / 2f, withA(new Color(255, 255, 255, 14), a));
            RenderUtil.rect(x, ty - th / 2f, Math.max(th, thumbCx - x), th, th / 2f, withA(accent, a));
            RenderUtil.rect(thumbCx - r, ty - r, r * 2f, r * 2f, r, withA(accent, a));
            if (hv > 0.01f) {
                RenderUtil.circleOutline(thumbCx, ty, r + f(3f), 1.2f, withA(accent, a * hv * 0.55f));
            }

            addHit(H_SLIDER, x - f(4), ty - f(12), w + f(8), f(24), num, new float[]{x, w}, clip);
            return;
        }

        if (s instanceof BooleanSetting bool) {
            float tw = f(30), th = f(16);
            boolean hover = inside(mx, my, x - f(4), cy - f(12), w + f(8), f(24));
            float hv = anim(bool.hashCode() + "-bh", hover, 16f);
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - tw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(lerp(TEXT_LABEL, TEXT_WHITE, hv), a));
            float v = anim(bool.hashCode() + "-b", bool.isEnabled(), 20f);
            float easedV = Easing.SWING_OVERSHOOT.ease(v);
            float bx = x + w - tw, by = cy - th / 2f;
            Color trackCol = lerp(new Color(255, 255, 255, 14), accent, v);
            RenderUtil.rect(bx, by, tw, th, th / 2f, withA(trackCol, a));
            float k = f(10.5f);
            float pad = f(2.5f);
            float kx = bx + pad + (tw - k - pad * 2f) * Math.clamp(easedV, 0f, 1f);
            float ky = by + (th - k) / 2f;
            Color knobCol = lerp(Color.WHITE, ACCENT_TEXT_DARK, v);
            RenderUtil.rect(kx, ky, k, k, k / 2f, withA(knobCol, a));
            addHit(H_BOOL, x - f(4), cy - f(12), w + f(8), f(24), bool, null, clip);
            return;
        }

        if (s instanceof ModeSetting mode) {
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w, labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT_LABEL, a));
            List<String> opts = mode.getModes();
            List<float[]> chips = chips(opts, x, cy + f(20), w);
            for (int i = 0; i < chips.size(); i++) {
                String opt = opts.get(i);
                renderChip(ctx, mode.hashCode() + "-" + opt, opt, chips.get(i), mode.isEnabled(opt), a, mx, my, clip);
                float[] c = chips.get(i);
                addHit(H_MODE, c[0], c[1] - f(CHIP_H) / 2f, c[2], f(CHIP_H), mode, opt, clip);
            }
            return;
        }

        if (s instanceof MultiSetting multi) {
            int ec = multi.getEnabled().size();
            int tc = multi.getOptionsList().size();
            String countText = ec + " " + tr("of") + " " + tc;
            float cw = Msdf.width(Msdf.SF_REGULAR, countText, labelSize);
            boolean rowHover = inside(mx, my, x - f(6), cy - f(14), w + f(12), f(28));
            float rhv = anim("m-hov-" + multi.hashCode(), rowHover, 16f);
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - cw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(lerp(TEXT_LABEL, TEXT_WHITE, rhv), a));
            textR(ctx, Msdf.SF_REGULAR, x + w, cy, countText, labelSize, withA(accent, a));
            addHit(H_MULTI_EXPAND, x - f(6), cy - f(14), w + f(12), f(28), multi, null, clip);

            float exp = anim("m-exp-" + multi.hashCode(), expandedMulti.contains(multi), 14f);
            if (exp > 0.01f) {
                List<String> opts = multi.getOptionsList();
                List<float[]> chips = chips(opts, x, cy + f(20), w);
                for (int i = 0; i < chips.size(); i++) {
                    String opt = opts.get(i);
                    float[] c = chips.get(i);
                    float itemDelay = i * 0.03f;
                    float itemProg = Math.clamp((exp - itemDelay) / 0.7f, 0f, 1f);
                    float itemA = a * Easing.OUT_CUBIC.ease(itemProg);
                    float yOff = (1f - Easing.OUT_CUBIC.ease(itemProg)) * f(6f);
                    float[] itemGeom = new float[]{c[0], c[1] + yOff, c[2]};

                    renderChip(ctx, multi.hashCode() + "-" + opt, opt, itemGeom, multi.isEnabled(opt), itemA, mx, my, clip);
                    if (exp > 0.6f) {
                        addHit(H_MULTI, c[0], c[1] - f(CHIP_H) / 2f, c[2], f(CHIP_H), multi, opt, clip);
                    }
                }
            }
            return;
        }

        if (s instanceof ColorSetting col) {
            float dot = f(14);
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - dot - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT_LABEL, a));
            boolean hover = inside(mx, my, x + w - dot - f(8), cy - f(12), dot + f(16), f(24));
            float hv = anim(col.hashCode() + "-ch", hover, 16f);
            float grow = f(1.5f) * hv;
            Color c = col.getColor() != null ? col.getColor() : accent;
            float dx = x + w - dot / 2f;
            RenderUtil.rect(dx - dot / 2f - grow, cy - dot / 2f - grow, dot + grow * 2, dot + grow * 2, dot / 2f + grow,
                    withA(new Color(c.getRed(), c.getGreen(), c.getBlue()), a));
            RenderUtil.outline(dx - dot / 2f - grow, cy - dot / 2f - grow, dot + grow * 2, dot + grow * 2, dot / 2f + grow, 1f,
                    withA(new Color(255, 255, 255, 35), a));
            addHit(H_COLOR, x - f(6), cy - f(14), w + f(12), f(28), col, null, clip);
            return;
        }

        // keybind / text / button: label + pill on the right
        String value;
        Color valueColor = TEXT_WHITE;
        int kind;
        Object payload = s;
        if (s instanceof KeybindSetting key) {
            value = bindingSetting == key ? "..." : getKeyName(key.getKeybind());
            if (bindingSetting == key) valueColor = accent;
            kind = H_KEY;
        } else if (s instanceof TextSetting txt) {
            boolean editing = editingText == txt;
            boolean caret = editing && (System.currentTimeMillis() / 500L) % 2L == 0L;
            value = txt.getValue().isEmpty() && !editing ? tr("Type something") : txt.getValue() + (caret ? "|" : "");
            valueColor = txt.getValue().isEmpty() && !editing ? TEXT_MUTED : TEXT_WHITE;
            kind = H_TEXT;
        } else if (s instanceof ButtonSetting btn) {
            value = tr(btn.getButtonText());
            kind = H_BUTTON;
        } else {
            textC(ctx, Msdf.SF_REGULAR, x, cy, label, labelSize, withA(TEXT_LABEL, a));
            return;
        }
        float pillMax = w * 0.55f;
        value = trim(value, pillMax - f(16), f(13.5f), Msdf.SF_REGULAR);
        float pw = Math.max(f(38), Msdf.width(Msdf.SF_REGULAR, value, f(13.5f)) + f(16));
        float ph = f(22);
        float px = x + w - pw;
        textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - pw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT_LABEL, a));
        RenderUtil.rect(px, cy - ph / 2f, pw, ph, f(5), withA(KEYBIND_BG, a));
        RenderUtil.outline(px, cy - ph / 2f, pw, ph, f(5), 1f, withA(KEYBIND_BORDER, a));
        textC(ctx, Msdf.SF_REGULAR, px + f(8), cy, value, f(13.5f), withA(valueColor, a));
        addHit(kind, x - f(6), cy - f(14), w + f(12), f(28), payload, null, clip);
    }

    private void renderChip(DrawContext ctx, String key, String opt, float[] c, boolean sel, float a,
                            float mx, float my, float[] clip) {
        float x = c[0], cy = c[1], w = c[2], h = f(CHIP_H);
        boolean hover = inside(mx, my, x, cy - h / 2f, w, h) && inside(mx, my, clip[0], clip[1], clip[2], clip[3]);
        float sv = anim(key + "-cs", sel, 18f);
        float hv = anim(key + "-ch", hover, 18f);

        // Rectangles with subtle rounded corners (f(6f)), matching reference
        float radius = f(6f);

        // Bare text for unselected chips with subtle hover background
        if (hv > 0.001f && sv < 0.999f) {
            RenderUtil.rect(x, cy - h / 2f, w, h, radius, withA(new Color(255, 255, 255, 14), a * hv * (1f - sv)));
        }

        // Active selected chip: solid neon mint pill
        if (sv > 0.001f) {
            RenderUtil.rect(x, cy - h / 2f, w, h, radius, withA(accent, a * sv));
        }

        Color textCol = lerp(lerp(TEXT_DIM, TEXT_WHITE, hv), ACCENT_TEXT_DARK, sv);
        MsdfFont font = sv > 0.5f ? Msdf.SF_SEMIBOLD : Msdf.SF_REGULAR;
        float tw = Msdf.width(font, tr(opt), f(13.5f));
        textC(ctx, font, x + (w - tw) / 2f, cy, tr(opt), f(13.5f), withA(textCol, a));
    }

    private List<float[]> chips(List<String> opts, float x, float firstCy, float w) {
        List<float[]> out = new ArrayList<>();
        float cx = x;
        int row = 0;
        for (String o : opts) {
            float cw = Math.min(w, Msdf.width(Msdf.SF_REGULAR, tr(o), f(13.5f)) + f(CHIP_PAD) * 2f);
            if (cx > x && cx + cw > x + w) {
                cx = x;
                row++;
            }
            out.add(new float[]{cx, firstCy + row * f(CHIP_ROW), cw});
            cx += cw + f(CHIP_GAP_X);
        }
        return out;
    }

    private int chipRows(List<String> opts, float w) {
        if (opts.isEmpty()) return 1;
        List<float[]> c = chips(opts, 0f, 0f, w);
        return Math.round(c.get(c.size() - 1)[1] / f(CHIP_ROW)) + 1;
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing || openProgress() < 0.8f) return true;
        float mx = RenderUtil.convertX((float) click.x());
        float my = RenderUtil.convertY((float) click.y());
        int button = click.button();

        if (colorPicker.isOpen()) {
            if (colorPicker.isInside(mx, my)) return colorPicker.mouseClicked(mx, my, button);
            colorPicker.close();
            return true;
        }

        if (!inside(mx, my, winX, winY, f(W), f(H))) {
            searchFocused = false;
            return super.mouseClicked(click, doubled);
        }

        Hit hit = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mx, my)) {
                hit = hits.get(i);
                break;
            }
        }

        if (hit == null || hit.kind != H_SEARCH) searchFocused = false;
        if (hit == null || hit.kind != H_TEXT) {
            if (editingText != null) editingText.setEditing(false);
            editingText = null;
        }

        if (hit == null) {
            if (button == 0) {
                draggingWindow = true;
                dragGrabX = mx - winX;
                dragGrabY = my - winY;
            }
            return true;
        }

        switch (hit.kind) {
            case H_CATEGORY -> {
                int idx = (int) hit.a;
                if (button == 0 && (idx != activeCategory || !searchQuery.isEmpty())) {
                    activeCategory = idx;
                    searchQuery = "";
                    categoryAnim = 0f;
                    listScroll = listScrollTarget = 0f;
                    bindingFunction = null;
                    List<Function> mods = visibleModules();
                    select(mods.isEmpty() ? null : mods.get(0));
                    playSound("setting");
                }
            }
            case H_MODULE -> {
                Function fn = (Function) hit.a;
                if (button == 0) {
                    // ТОЛЬКО переключение на ЛКМ (без переключения панели настроек)
                    fn.toggle();
                    playSound(fn.isEnabled() ? "smooth_on" : "smooth_off");
                } else if (button == 1) {
                    // ТОЛЬКО настройки на ПКМ (без переключения вкл/выкл модуля)
                    if (fn != selected) {
                        select(fn);
                        playSound("setting");
                    }
                } else if (button == 2) {
                    bindingFunction = bindingFunction == fn ? null : fn;
                }
            }
            case H_BIND -> {
                if (button == 0) bindingFunction = bindingFunction == hit.a ? null : (Function) hit.a;
            }
            case H_CLOSE -> {
                if (button == 0) close();
            }
            case H_SEARCH -> {
                float sw = f(220);
                float sx = winX + f(290);
                if (button == 0) {
                    if (!searchQuery.isEmpty() && mx >= sx + sw - f(24)) {
                        searchQuery = "";
                        searchFocused = false;
                        ensureSelection();
                        playSound("setting");
                    } else {
                        searchFocused = true;
                    }
                } else if (button == 1) {
                    searchQuery = "";
                    searchFocused = false;
                    ensureSelection();
                    playSound("setting");
                }
            }
            case H_MODE -> {
                if (button == 0) {
                    ((ModeSetting) hit.a).setMode((String) hit.b);
                    playSound("setting");
                }
            }
            case H_MULTI -> {
                if (button == 0) {
                    ((MultiSetting) hit.a).toggle((String) hit.b);
                    playSound("setting");
                }
            }
            case H_MULTI_EXPAND -> {
                if (button == 0) {
                    MultiSetting m = (MultiSetting) hit.a;
                    if (expandedMulti.contains(m)) {
                        expandedMulti.remove(m);
                    } else {
                        expandedMulti.add(m);
                    }
                    playSound("setting");
                }
            }
            case H_SLIDER -> {
                if (button == 0) {
                    NumberSetting num = (NumberSetting) hit.a;
                    float[] g = (float[]) hit.b;
                    draggingSlider = num;
                    dragTrackX = g[0];
                    dragTrackW = g[1];
                    updateSlider(num, mx);
                }
            }
            case H_BOOL -> {
                if (button == 0) {
                    BooleanSetting b = (BooleanSetting) hit.a;
                    b.setEnabled(!b.isEnabled());
                    playSound("setting");
                }
            }
            case H_COLOR -> {
                if (button == 0) {
                    openColorPicker((ColorSetting) hit.a, mx, my);
                    playSound("setting");
                }
            }
            case H_BUTTON -> {
                if (button == 0) {
                    ((ButtonSetting) hit.a).press();
                    playSound("setting");
                }
            }
            case H_KEY -> {
                if (button == 0) {
                    KeybindSetting k = (KeybindSetting) hit.a;
                    bindingSetting = bindingSetting == k ? null : k;
                }
            }
            case H_TEXT -> {
                if (button == 0) {
                    TextSetting txt = (TextSetting) hit.a;
                    if (editingText == txt) {
                        txt.setEditing(false);
                        editingText = null;
                    } else {
                        if (editingText != null) editingText.setEditing(false);
                        txt.setEditing(true);
                        editingText = txt;
                    }
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void updateSlider(NumberSetting num, float mx) {
        float travel = Math.max(1f, dragTrackW - f(10));
        float p = Math.clamp((mx - dragTrackX - f(5)) / travel, 0f, 1f);
        double range = num.getMax() - num.getMin();
        double v = num.getMin() + p * range;
        double step = num.getStep();
        if (step > 0) v = Math.round((v - num.getMin()) / step) * step + num.getMin();
        num.setValue(Math.clamp(v, num.getMin(), num.getMax()));
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        float mx = RenderUtil.convertX((float) click.x());
        float my = RenderUtil.convertY((float) click.y());
        if (colorPicker.isOpen() && colorPicker.isDragging()) return colorPicker.mouseDragged(mx, my, click.button());
        if (closing) return true;
        if (draggingWindow) {
            float sw = RenderUtil.getFixedScaledWidth(), sh = RenderUtil.getFixedScaledHeight();
            winX = Math.clamp(mx - dragGrabX, -f(W) + f(120), sw - f(120));
            winY = Math.clamp(my - dragGrabY, 0f, sh - f(60));
            return true;
        }
        if (draggingSlider != null) {
            updateSlider(draggingSlider, mx);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingWindow = false;
        draggingSlider = null;
        colorPicker.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing || openProgress() < 0.8f) return true;
        float mx = RenderUtil.convertX((float) mouseX);
        float my = RenderUtil.convertY((float) mouseY);
        if (colorPicker.isOpen()) return true;
        if (inside(mx, my, winX + f(LIST_X), winY + f(LIST_TOP), f(LIST_W), f(H - LIST_TOP))) {
            listScrollTarget = Math.clamp(listScrollTarget - (float) verticalAmount * 48f, 0f, listMaxScroll);
            return true;
        }
        if (inside(mx, my, winX + f(DIVIDER_X), winY + f(SET_TOP), f(W - DIVIDER_X), f(H - SET_TOP))) {
            setScrollTarget = Math.clamp(setScrollTarget - (float) verticalAmount * 56f, 0f, setMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isCtrlDown() {
        if (client == null || client.getWindow() == null) return false;
        long h = client.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (colorPicker.isOpen()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) colorPicker.close();
            return true;
        }

        if (bindingFunction != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                bindingFunction.setKeybind(key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? 0 : key);
                playSound("setting");
            }
            bindingFunction = null;
            return true;
        }
        if (bindingSetting != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                bindingSetting.setKeybind(key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? 0 : key);
                playSound("setting");
            }
            bindingSetting = null;
            return true;
        }

        if (editingText != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                editingText.setEditing(false);
                editingText = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                editingText.backspace();
                playKeyboardSound();
            }
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                    ensureSelection();
                }
            } else if (isCtrlDown() && key == GLFW.GLFW_KEY_V) {
                try {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        searchQuery += clip.replace("\n", "").replace("\r", "");
                        if (searchQuery.length() > 32) searchQuery = searchQuery.substring(0, 32);
                        listScrollTarget = 0f;
                        ensureSelection();
                    }
                } catch (Exception ignored) {
                }
                return true;
            } else if (isCtrlDown() && key == GLFW.GLFW_KEY_A) {
                searchQuery = "";
                ensureSelection();
                return true;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                List<Function> mods = visibleModules();
                if (!mods.isEmpty()) select(mods.get(0));
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                listScrollTarget = 0f;
                ensureSelection();
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        if (colorPicker.isOpen()) return true;
        if (editingText != null) {
            editingText.append(input.asString());
            playKeyboardSound();
            return true;
        }
        if (searchFocused) {
            if (searchQuery.length() < 32) searchQuery += input.asString();
            listScrollTarget = 0f;
            categoryAnim = Math.min(categoryAnim, 0.6f);
            ensureSelection();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ================================================================ misc

    private static String getKeyName(int key) {
        if (key <= 0) return "NONE";
        if (key >= 1000) return "M" + (key - 1000);
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            default -> {
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                yield "KEY_" + key;
            }
        };
    }

    private String trim(String text, float maxW, float size, MsdfFont font) {
        if (text == null || text.isEmpty() || maxW <= 4f) return "";
        if (Msdf.width(font, text, size) <= maxW) return text;
        String dots = "...";
        float dw = Msdf.width(font, dots, size);
        String t = text;
        while (!t.isEmpty() && Msdf.width(font, t, size) + dw > maxW) t = t.substring(0, t.length() - 1);
        return t.isEmpty() ? "" : t + dots;
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static Color withA(Color c, float op) {
        if (c == null) return Color.WHITE;
        int a = Math.clamp((int) (c.getAlpha() * Math.clamp(op, 0f, 1f)), 0, 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.clamp(t, 0f, 1f);
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private void playSound(String sound) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("minar", sound)), 1.0f, 1.0f));
        } catch (Exception ignored) {
        }
    }

    private void playKeyboardSound() {
        playSound("keyboard_" + (1 + (int) (Math.random() * 7)));
    }
}
