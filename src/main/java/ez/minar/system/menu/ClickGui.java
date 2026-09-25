package ez.minar.system.menu;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.events.EventManager;
import ez.minar.system.managers.LocalizationManager;
import ez.minar.system.menu.components.ColorPickerWindow;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.*;
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
 * ClickGui redesign (reference screenshot, 1218x791 window):
 *  - left icon rail (categories, logo, gear -> theme menu)
 *  - module list column with category title
 *  - top bar: search + user block
 *  - settings panel: title + keybind + description, two independent columns of setting cards
 *    (settings alternate left/right), chips for modes, sliders, toggles, color dots.
 * All geometry is in reference px and scaled by f(). Hit-testing uses boxes recorded during render,
 * so layout and input can never drift apart.
 *
 * Controls: LMB on module = toggle + open settings, RMB = open settings, MMB = bind.
 */
public class ClickGui extends Screen {

    private static final Category[] CATEGORIES = {
            Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC
    };

    private static final Identifier[] CATEGORY_ICONS = {
            Identifier.of("minar", "icons/fig_local_swords.png"),
            Identifier.of("minar", "icons/fig_local_rocket.png"),
            Identifier.of("minar", "icons/fig_local_screen.png"),
            Identifier.of("minar", "icons/fig_local_user.png"),
            Identifier.of("minar", "icons/fig_local_apps.png")
    };
    private static final Identifier ICON_LOGO = Identifier.of("minar", "icons/fig_local_logo.png");
    private static final Identifier ICON_SEARCH = Identifier.of("minar", "icons/fig_local_search.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("minar", "icons/fig_local_settings.png");
    private static final Identifier ICON_CROSS = Identifier.of("minar", "icons/fig_local_cross.png");
    private static final Identifier ICON_AVATAR = Identifier.of("minar", "icons/fig_local_avatar.png");

    // ---------------------------------------------------------------- palette
    private static final Color WIN_BG = new Color(21, 16, 32, 242);
    private static final Color RAIL_BG = new Color(0, 0, 0, 45);
    private static final Color LIST_BG = new Color(255, 255, 255, 4);
    private static final Color DIVIDER = new Color(255, 255, 255, 14);
    private static final Color CARD = new Color(255, 255, 255, 5);
    private static final Color TRACK = new Color(255, 255, 255, 16);
    private static final Color HOVER = new Color(255, 255, 255, 10);
    private static final Color TEXT = new Color(238, 235, 247);
    private static final Color TEXT_SOFT = new Color(238, 235, 247, 205);
    private static final Color TEXT_DIM = new Color(255, 255, 255, 125);
    private static final Color TEXT_MUTED = new Color(255, 255, 255, 85);
    private static final Color THUMB_CORE = new Color(30, 22, 46);
    private static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    // ---------------------------------------------------------------- geometry (reference px)
    private static final float W = 1218f, H = 791f, R_WIN = 24f;
    private static final float RAIL_W = 80f;
    private static final float LIST_X = 80f, LIST_R = 333f;
    private static final float TOP_H = 57f;
    private static final float LIST_TOP = 62f, LIST_FIRST = 80f, LIST_PITCH = 42.5f;
    private static final float COL_L = 384f, COL_R = 799f, COL_W = 357f;
    private static final float SET_TOP = 160f, SET_FIRST = 186f;
    private static final float CHIP_H = 24f, CHIP_PAD = 8f, CHIP_GAP_X = 5f, CHIP_ROW = 32f;
    private static final float ANIM_OPEN_MS = 260f;

    // ---------------------------------------------------------------- hit boxes
    private static final int H_CATEGORY = 0, H_MODULE = 1, H_BIND = 2, H_CLOSE = 3, H_SEARCH = 4, H_GEAR = 5,
            H_MODE = 6, H_MULTI = 7, H_SLIDER = 8, H_BOOL = 9, H_COLOR = 10, H_BUTTON = 11, H_KEY = 12, H_TEXT = 13;

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

    private int activeCategory = 2;
    private float categoryAnim = 1f;
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
    private float accR = 139f, accG = 92f, accB = 246f;
    private Color accent = new Color(139, 92, 246);

    private final Map<Object, Float> anims = new HashMap<>();

    private final ColorPickerWindow colorPicker = new ColorPickerWindow();
    private final ThemeMenuWindow themeMenu = new ThemeMenuWindow();

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
        themeMenu.setColorPicker(colorPicker);
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
        themeMenu.reset();
        layoutReady = false;
        categoryAnim = 1f;
        selectAnim = 0f;
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
        themeMenu.close();
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
                    if (fn.getName().toLowerCase(Locale.ROOT).contains(q)
                            || fn.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) out.add(fn);
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
            Function next = mods.isEmpty() ? null : mods.get(0);
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

    // ================================================================ render

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float a = Easings.InOutCubic(openProgress());
        RenderUtil.rect(0, 0, RenderUtil.getFixedScaledWidth(), RenderUtil.getFixedScaledHeight(), 0,
                new Color(6, 3, 12, (int) (90 * a)));
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
        float eased = Easings.OutCubic(open);
        float op = Easings.InOutCubic(open);

        float screenW = RenderUtil.getFixedScaledWidth();
        float screenH = RenderUtil.getFixedScaledHeight();
        float nd = Math.min(screenW * 0.86f / W, screenH * 0.88f / H);
        nd = Math.min(nd, 0.62f);
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
        float wy = winY + (1f - eased) * f(22);

        RenderUtil.shadow(wx, wy + f(6), f(W), f(H), f(R_WIN), f(40), 0.55f * op, f(4), new Color(0, 0, 0, 220));
        RenderUtil.rect(wx, wy, f(W), f(H), f(R_WIN), withA(WIN_BG, op));

        // rail: rounded on the left only (clip away the right corners)
        Scissor.push(wx, wy, f(RAIL_W), f(H));
        RenderUtil.rect(wx, wy, f(RAIL_W + R_WIN * 2), f(H), f(R_WIN), withA(RAIL_BG, op));
        Scissor.pop();
        RenderUtil.rect(wx + f(LIST_X), wy, f(LIST_R - LIST_X), f(H), 0, withA(LIST_BG, op));

        RenderUtil.rect(wx + f(RAIL_W), wy, Math.max(1f, f(1)), f(H), 0, withA(DIVIDER, op));
        RenderUtil.rect(wx + f(LIST_R), wy, Math.max(1f, f(1)), f(H), 0, withA(DIVIDER, op));
        RenderUtil.rect(wx + f(LIST_R), wy + f(TOP_H), f(W - LIST_R), Math.max(1f, f(1)), 0, withA(DIVIDER, op));

        renderRail(context, wx, wy, op, mx, my);
        renderList(context, wx, wy, op, mx, my);
        renderTopBar(context, wx, wy, op, mx, my);
        renderSettings(context, wx, wy, op, mx, my);

        if (themeMenu.isOpen()) themeMenu.render(context, mx, my, deltaTicks);
        if (colorPicker.isOpen()) colorPicker.render(context, mx, my, deltaTicks);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void tick() {
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

        Color t = ThemeManager.getGuiAccent();
        accR = approach(accR, t.getRed(), 12f);
        accG = approach(accG, t.getGreen(), 12f);
        accB = approach(accB, t.getBlue(), 12f);
        accent = new Color(Math.round(accR), Math.round(accG), Math.round(accB));
    }

    // ---------------------------------------------------------------- rail

    private void renderRail(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        float cx = wx + f(RAIL_W / 2f);
        float logo = f(26);
        RenderUtil.texture(cx - logo / 2f, wy + f(40) - logo / 2f, logo, ICON_LOGO, 0, withA(accent, op));

        for (int i = 0; i < CATEGORIES.length; i++) {
            float cy = wy + f(102 + i * 50);
            float s = f(18);
            boolean hovered = inside(mx, my, wx, cy - f(22), f(RAIL_W), f(44));
            boolean active = searchQuery.isEmpty() && i == activeCategory;
            float a = anim("cat-a-" + i, active, 14f);
            float h = anim("cat-h-" + i, hovered, 16f);
            Color c = lerp(lerp(TEXT_DIM, TEXT, h), accent, a);
            float grow = f(1.5f) * h;
            RenderUtil.texture(cx - (s + grow) / 2f, cy - (s + grow) / 2f, s + grow, CATEGORY_ICONS[i], 0, withA(c, op));
            addHit(H_CATEGORY, wx, cy - f(22), f(RAIL_W), f(44), i, null, null);
        }

        float gy = wy + f(752);
        float gs = f(18);
        boolean gh = inside(mx, my, wx, gy - f(20), f(RAIL_W), f(40));
        float g = anim("gear", gh || themeMenu.isOpen(), 16f);
        RenderUtil.texture(cx - gs / 2f, gy - gs / 2f, gs, ICON_SETTINGS, 0, withA(lerp(TEXT_DIM, TEXT, g), op));
        addHit(H_GEAR, wx, gy - f(20), f(RAIL_W), f(40), null, null, null);
    }

    // ---------------------------------------------------------------- module list

    private void renderList(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        String title = searchQuery.isEmpty() ? CATEGORIES[activeCategory].getDisplayName() : tr("Search");
        textC(ctx, Msdf.SF_BOLD, wx + f(102), wy + f(34), title, f(21), withA(TEXT, op));

        float clipX = wx + f(LIST_X) + 1, clipY = wy + f(LIST_TOP);
        float clipW = f(LIST_R - LIST_X) - 2, clipH = f(H - LIST_TOP - 12);
        float[] clip = {clipX, clipY, clipW, clipH};

        List<Function> mods = visibleModules();
        float contentH = f(LIST_FIRST - LIST_TOP) + mods.size() * f(LIST_PITCH);
        listMaxScroll = Math.max(0f, (contentH - clipH + f(12)) / d);

        float ce = Easings.OutCubic(categoryAnim);
        float lop = op * ce;
        float slide = (1f - ce) * f(10);

        Scissor.push(clipX, clipY, clipW, clipH);
        for (int i = 0; i < mods.size(); i++) {
            Function fn = mods.get(i);
            float cy = wy + f(LIST_FIRST) + i * f(LIST_PITCH) - listScroll * d + slide;
            if (cy < clipY - f(30) || cy > clipY + clipH + f(30)) continue;

            float rowX = wx + f(92), rowW = f(229), rowH = f(34);
            boolean hovered = inside(mx, my, rowX, cy - rowH / 2f, rowW, rowH) && inside(mx, my, clipX, clipY, clipW, clipH);
            float h = anim(fn.hashCode() + "-lh", hovered, 16f);
            float s = anim(fn.hashCode() + "-ls", fn == selected, 14f);
            float e = anim(fn.hashCode() + "-le", fn.isEnabled(), 14f);

            if (s > 0.01f) {
                RenderUtil.rect(rowX, cy - rowH / 2f, rowW, rowH, f(10), withA(new Color(255, 255, 255, 9), lop * s));
            }
            if (h > 0.01f) {
                RenderUtil.rect(rowX, cy - rowH / 2f, rowW, rowH, f(10), withA(new Color(255, 255, 255, 5), lop * h));
            }

            Color base = lerp(TEXT_SOFT, TEXT, Math.max(h, s));
            Color c = lerp(base, accent, e);
            String name = bindingFunction == fn ? "..." : fn.getDisplayName();
            textC(ctx, Msdf.SF_REGULAR, wx + f(104) + f(4) * h, cy,
                    trim(name, f(210), f(16.5f), Msdf.SF_REGULAR), f(16.5f), withA(c, lop));

            addHit(H_MODULE, rowX, cy - rowH / 2f, rowW, rowH, fn, null, clip);
        }
        if (mods.isEmpty()) {
            textC(ctx, Msdf.SF_REGULAR, wx + f(104), wy + f(LIST_FIRST), tr("Nothing found"), f(15), withA(TEXT_MUTED, lop));
        }
        Scissor.pop();
    }

    // ---------------------------------------------------------------- top bar

    private void renderTopBar(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        float cy = wy + f(29);
        boolean hover = inside(mx, my, wx + f(345), wy, f(360), f(TOP_H));
        float focus = anim("search-focus", searchFocused, 16f);
        float h = anim("search-hover", hover, 16f);
        if (focus > 0.01f || h > 0.01f) {
            RenderUtil.rect(wx + f(346), cy - f(15), f(340), f(30), f(9),
                    withA(new Color(255, 255, 255, 6), op * Math.max(focus, h * 0.6f)));
        }
        float is = f(13);
        RenderUtil.texture(wx + f(357), cy - is / 2f, is, ICON_SEARCH, 0, withA(lerp(TEXT_DIM, TEXT, focus), op));

        String shown;
        Color col;
        if (searchQuery.isEmpty() && !searchFocused) {
            shown = tr("Search");
            col = TEXT_DIM;
        } else {
            boolean caret = searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L;
            shown = searchQuery + (caret ? "|" : "");
            col = TEXT;
            if (searchQuery.isEmpty() && !caret) {
                shown = tr("Search");
                col = TEXT_MUTED;
            }
        }
        textC(ctx, Msdf.SF_REGULAR, wx + f(377), cy, trim(shown, f(300), f(16), Msdf.SF_REGULAR), f(16), withA(col, op));
        addHit(H_SEARCH, wx + f(345), wy, f(360), f(TOP_H), null, null, null);

        String user = "Player";
        try {
            user = MinecraftClient.getInstance().getSession().getUsername();
        } catch (Exception ignored) {
        }
        float right = wx + f(1163);
        textR(ctx, Msdf.SF_BOLD, right, wy + f(21), trim(user, f(160), f(14), Msdf.SF_BOLD), f(14), withA(TEXT, op));
        textR(ctx, Msdf.SF_REGULAR, right, wy + f(37), tr("User"), f(13), withA(TEXT_DIM, op));
        float av = f(32);
        RenderUtil.texture(wx + f(1186) - av / 2f, wy + f(28) - av / 2f, av, ICON_AVATAR, av / 2f, withA(Color.WHITE, op));
    }

    // ---------------------------------------------------------------- settings panel

    private void renderSettings(DrawContext ctx, float wx, float wy, float op, float mx, float my) {
        Function fn = selected;
        float se = Easings.OutCubic(selectAnim);
        float pop = op * se;
        float slide = (1f - se) * f(12);

        // close button (closes the GUI)
        float xs = f(17);
        float xcx = wx + f(1172), xcy = wy + f(100);
        boolean xh = inside(mx, my, xcx - f(16), xcy - f(16), f(32), f(32));
        float xa = anim("close-h", xh, 16f);
        if (xa > 0.01f) RenderUtil.rect(xcx - f(16), xcy - f(16), f(32), f(32), f(9), withA(HOVER, op * xa));
        RenderUtil.texture(xcx - xs / 2f, xcy - xs / 2f, xs, ICON_CROSS, 0, withA(lerp(TEXT_SOFT, Color.WHITE, xa), op));
        addHit(H_CLOSE, xcx - f(16), xcy - f(16), f(32), f(32), null, null, null);

        if (fn == null) {
            textC(ctx, Msdf.SF_REGULAR, wx + f(362), wy + f(100), tr("Select a module"), f(20), withA(TEXT_DIM, op));
            setMaxScroll = 0f;
            return;
        }

        // header
        String title = tr("Settings") + " " + fn.getDisplayName();
        float titleSize = f(26);
        float tx = wx + f(362);
        float tcy = wy + f(100) + slide;
        String t = trim(title, f(680), titleSize, Msdf.SF_REGULAR);
        textC(ctx, Msdf.SF_REGULAR, tx, tcy, t, titleSize, withA(TEXT, pop));

        float kx = tx + Msdf.width(Msdf.SF_REGULAR, t, titleSize) + f(18);
        String keyText = bindingFunction == fn ? "..." : getKeyName(fn.getKeybind());
        float kw = Msdf.width(Msdf.SF_REGULAR, keyText, f(16));
        boolean kh = inside(mx, my, kx - f(6), tcy - f(13), kw + f(12), f(26));
        float ka = anim("bind-h", kh || bindingFunction == fn, 16f);
        if (ka > 0.01f) RenderUtil.rect(kx - f(6), tcy - f(12), kw + f(12), f(24), f(7), withA(HOVER, pop * ka));
        textC(ctx, Msdf.SF_REGULAR, kx, tcy, keyText, f(16),
                withA(bindingFunction == fn ? accent : lerp(TEXT_SOFT, Color.WHITE, ka), pop));
        addHit(H_BIND, kx - f(6), tcy - f(13), kw + f(12), f(26), fn, null, null);

        String desc = fn.getDisplayDesc();
        if (desc != null && !desc.isEmpty() && !desc.equals(fn.getDesc())) {
            // localized description
        }
        if (desc != null && !desc.isEmpty()) {
            textC(ctx, Msdf.SF_REGULAR, tx, wy + f(124) + slide, trim(desc, f(760), f(15), Msdf.SF_REGULAR), f(15),
                    withA(TEXT_DIM, pop));
        }

        // settings columns
        float clipX = wx + f(LIST_R) + 2, clipY = wy + f(SET_TOP);
        float clipW = f(W - LIST_R) - 8, clipH = f(H - SET_TOP - 14);
        float[] clip = {clipX, clipY, clipW, clipH};

        List<Setting> settings = fn.getSettings().stream().filter(Setting::isVisible).toList();
        if (settings.isEmpty()) {
            textC(ctx, Msdf.SF_REGULAR, wx + f(COL_L), wy + f(SET_FIRST), tr("No settings"), f(17), withA(TEXT_MUTED, pop));
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
            float cy = wy + colY[col] - setScroll * d + slide;

            float stagger = Math.clamp(selectAnim * 1.6f - Math.min(i, 10) * 0.07f, 0f, 1f);
            float ie = Easings.OutCubic(stagger);
            float a = pop * ie;
            float iy = cy + (1f - ie) * f(8);

            if (iy + bottom > clipY - f(40) && iy - f(40) < clipY + clipH) {
                RenderUtil.rect(colX[col] - f(22), iy - f(28), colW + f(44), bottom + f(50), f(14), withA(CARD, a));
                renderSetting(ctx, s, colX[col], iy, colW, a, mx, my, clip);
            }
            colY[col] += bottom + f(66);
            maxBottom = Math.max(maxBottom, colY[col]);
        }
        Scissor.pop();
        setMaxScroll = Math.max(0f, (maxBottom - f(66) + f(40) - f(SET_TOP) - clipH) / d);
    }

    /** Distance from the label's center line to the bottom of the control, reference px scaled. */
    private float contentBottom(Setting s, float colW) {
        if (s instanceof ModeSetting m) {
            int rows = chipRows(m.getModes(), colW);
            return f(45) + (rows - 1) * f(CHIP_ROW);
        }
        if (s instanceof MultiSetting m) {
            int rows = chipRows(m.getOptionsList(), colW);
            return f(45) + (rows - 1) * f(CHIP_ROW);
        }
        if (s instanceof NumberSetting) return f(34);
        return f(12);
    }

    private void renderSetting(DrawContext ctx, Setting s, float x, float cy, float w, float a,
                               float mx, float my, float[] clip) {
        String label = s.getDisplayName();
        float labelSize = f(18);

        if (s instanceof NumberSetting num) {
            String val = fmt(num);
            float vw = Msdf.width(Msdf.SF_REGULAR, val, f(16));
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - vw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT, a));
            textR(ctx, Msdf.SF_REGULAR, x + w, cy, val, f(16), withA(accent, a));

            float ty = cy + f(27);
            float th = f(5);
            double range = num.getMax() - num.getMin();
            float target = (float) Math.clamp((num.getValue() - num.getMin()) / Math.max(1e-6, range), 0.0, 1.0);
            Object pk = num.hashCode() + "-sv";
            float prog = draggingSlider == num ? target : approach(anims.getOrDefault(pk, target), target, 22f);
            anims.put(pk, prog);

            boolean hover = inside(mx, my, x, ty - f(11), w, f(22));
            float hv = anim(num.hashCode() + "-sh", hover || draggingSlider == num, 16f);

            float r = f(7) + f(1.5f) * hv;
            float thumbCx = x + f(7) + (w - f(14)) * prog;
            RenderUtil.rect(x, ty - th / 2f, w, th, th / 2f, withA(TRACK, a));
            RenderUtil.rect(x, ty - th / 2f, Math.max(th, thumbCx - x), th, th / 2f, withA(accent, a));
            if (hv > 0.01f) {
                RenderUtil.rect(thumbCx - r - f(5), ty - r - f(5), (r + f(5)) * 2f, (r + f(5)) * 2f, r + f(5),
                        withA(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40), a * hv));
            }
            RenderUtil.rect(thumbCx - r, ty - r, r * 2f, r * 2f, r, withA(accent, a));
            float core = f(3);
            RenderUtil.rect(thumbCx - core, ty - core, core * 2f, core * 2f, core, withA(THUMB_CORE, a));

            addHit(H_SLIDER, x - f(4), ty - f(11), w + f(8), f(22), num, new float[]{x, w}, clip);
            return;
        }

        if (s instanceof BooleanSetting bool) {
            float tw = f(32), th = f(18);
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - tw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT, a));
            float v = anim(bool.hashCode() + "-b", bool.isEnabled(), 16f);
            float bx = x + w - tw, by = cy - th / 2f;
            RenderUtil.rect(bx, by, tw, th, th / 2f, withA(lerp(TRACK, accent, v), a));
            float k = f(12);
            RenderUtil.rect(bx + f(3) + (tw - k - f(6)) * v, by + (th - k) / 2f, k, k, k / 2f, withA(Color.WHITE, a));
            addHit(H_BOOL, x - f(6), cy - f(15), w + f(12), f(30), bool, null, clip);
            return;
        }

        if (s instanceof ModeSetting mode) {
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w, labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT, a));
            List<String> opts = mode.getModes();
            List<float[]> chips = chips(opts, x, cy + f(33), w);
            for (int i = 0; i < chips.size(); i++) {
                String opt = opts.get(i);
                renderChip(ctx, mode.hashCode() + "-" + opt, opt, chips.get(i), mode.isEnabled(opt), a, mx, my, clip);
                float[] c = chips.get(i);
                addHit(H_MODE, c[0], c[1] - f(CHIP_H) / 2f, c[2], f(CHIP_H), mode, opt, clip);
            }
            return;
        }

        if (s instanceof MultiSetting multi) {
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w, labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT, a));
            List<String> opts = multi.getOptionsList();
            List<float[]> chips = chips(opts, x, cy + f(33), w);
            for (int i = 0; i < chips.size(); i++) {
                String opt = opts.get(i);
                renderChip(ctx, multi.hashCode() + "-" + opt, opt, chips.get(i), multi.isEnabled(opt), a, mx, my, clip);
                float[] c = chips.get(i);
                addHit(H_MULTI, c[0], c[1] - f(CHIP_H) / 2f, c[2], f(CHIP_H), multi, opt, clip);
            }
            return;
        }

        if (s instanceof ColorSetting col) {
            float dot = f(16);
            textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - dot - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT, a));
            boolean hover = inside(mx, my, x + w - dot - f(8), cy - f(14), dot + f(16), f(28));
            float hv = anim(col.hashCode() + "-ch", hover, 16f);
            float grow = f(2) * hv;
            Color c = col.getColor() != null ? col.getColor() : accent;
            float dx = x + w - dot / 2f;
            RenderUtil.rect(dx - dot / 2f - grow, cy - dot / 2f - grow, dot + grow * 2, dot + grow * 2, dot / 2f + grow,
                    withA(new Color(c.getRed(), c.getGreen(), c.getBlue()), a));
            addHit(H_COLOR, x - f(6), cy - f(15), w + f(12), f(30), col, null, clip);
            return;
        }

        // keybind / text / button: label + pill on the right
        String value;
        Color valueColor = TEXT_SOFT;
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
            valueColor = txt.getValue().isEmpty() && !editing ? TEXT_MUTED : TEXT;
            kind = H_TEXT;
        } else if (s instanceof ButtonSetting btn) {
            value = tr(btn.getButtonText());
            kind = H_BUTTON;
        } else {
            textC(ctx, Msdf.SF_REGULAR, x, cy, label, labelSize, withA(TEXT, a));
            return;
        }
        float pillMax = w * 0.55f;
        value = trim(value, pillMax - f(20), f(15), Msdf.SF_REGULAR);
        float pw = Math.max(f(44), Msdf.width(Msdf.SF_REGULAR, value, f(15)) + f(20));
        float ph = f(26);
        float px = x + w - pw;
        textC(ctx, Msdf.SF_REGULAR, x, cy, trim(label, w - pw - f(12), labelSize, Msdf.SF_REGULAR), labelSize, withA(TEXT, a));
        boolean hover = inside(mx, my, px, cy - ph / 2f, pw, ph);
        float hv = anim(s.hashCode() + "-ph", hover, 16f);
        RenderUtil.rect(px, cy - ph / 2f, pw, ph, f(8), withA(lerp(TRACK, new Color(255, 255, 255, 26), hv), a));
        textC(ctx, Msdf.SF_REGULAR, px + f(10), cy, value, f(15), withA(valueColor, a));
        addHit(kind, x - f(6), cy - f(15), w + f(12), f(30), payload, null, clip);
    }

    private void renderChip(DrawContext ctx, String key, String opt, float[] c, boolean sel, float a,
                            float mx, float my, float[] clip) {
        float x = c[0], cy = c[1], w = c[2], h = f(CHIP_H);
        boolean hover = inside(mx, my, x, cy - h / 2f, w, h) && inside(mx, my, clip[0], clip[1], clip[2], clip[3]);
        float sv = anim(key + "-cs", sel, 16f);
        float hv = anim(key + "-ch", hover, 16f);
        if (hv > 0.01f && sv < 0.99f) {
            RenderUtil.rect(x, cy - h / 2f, w, h, f(6), withA(HOVER, a * hv * (1f - sv)));
        }
        if (sv > 0.01f) {
            RenderUtil.rect(x, cy - h / 2f, w, h, f(6), withA(accent, a * sv));
        }
        textC(ctx, Msdf.SF_REGULAR, x + f(CHIP_PAD), cy, tr(opt), f(16), withA(lerp(TEXT_SOFT, Color.WHITE, Math.max(sv, hv)), a));
    }

    private List<float[]> chips(List<String> opts, float x, float firstCy, float w) {
        List<float[]> out = new ArrayList<>();
        float cx = x;
        int row = 0;
        for (String o : opts) {
            float cw = Math.min(w, Msdf.width(Msdf.SF_REGULAR, tr(o), f(16)) + f(CHIP_PAD) * 2f);
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

        Hit gearHit = null;
        for (Hit h : hits) if (h.kind == H_GEAR) gearHit = h;
        if (themeMenu.isOpen()) {
            if (themeMenu.isInside(mx, my)) return themeMenu.mouseClicked(mx, my, button);
            if (gearHit == null || !gearHit.contains(mx, my)) {
                themeMenu.close();
                return true;
            }
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
                    fn.toggle();
                    select(fn);
                    playSound(fn.isEnabled() ? "smooth_on" : "smooth_off");
                } else if (button == 1) {
                    if (fn != selected) playSound("setting");
                    select(fn);
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
                if (button == 0) searchFocused = true;
                else if (button == 1) {
                    searchQuery = "";
                    ensureSelection();
                }
            }
            case H_GEAR -> {
                if (button == 0) {
                    if (themeMenu.isOpen()) themeMenu.close();
                    else themeMenu.open(gearHit.x + gearHit.w, gearHit.y);
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
        float travel = Math.max(1f, dragTrackW - f(14));
        float p = Math.clamp((mx - dragTrackX - f(7)) / travel, 0f, 1f);
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
        if (themeMenu.isOpen()) {
            themeMenu.mouseScrolled(mx, my, verticalAmount);
            return true;
        }
        if (inside(mx, my, winX + f(LIST_X), winY + f(LIST_TOP), f(LIST_R - LIST_X), f(H - LIST_TOP))) {
            listScrollTarget = Math.clamp(listScrollTarget - (float) verticalAmount * 48f, 0f, listMaxScroll);
            return true;
        }
        if (inside(mx, my, winX + f(LIST_R), winY + f(SET_TOP), f(W - LIST_R), f(H - SET_TOP))) {
            setScrollTarget = Math.clamp(setScrollTarget - (float) verticalAmount * 56f, 0f, setMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (colorPicker.isOpen()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) colorPicker.close();
            return true;
        }
        if (themeMenu.isOpen()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (themeMenu.isTyping()) themeMenu.backToList();
                else themeMenu.close();
            } else themeMenu.keyPressed(key);
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
                searchQuery = "";
                ensureSelection();
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
        if (themeMenu.isOpen()) {
            if (themeMenu.isTyping()) themeMenu.charTyped(input.asString().charAt(0));
            return true;
        }
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
