package ez.minar.system.menu;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.events.EventManager;
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
 * DEFAULT Nexoria screen, ported from the local Untitled.fig (288 decoded nodes).
 * Reference canvas 1920x1080; node 1:4 window (445,220,923,624), r25, #181717.
 * Nodes 1:5/9/10 use white at 2%; fields 1:132/144/149/155 use white at 4%.
 * Bottom chips 1:262/266 are opaque #1D1C1C. Real module/settings/session data replace
 * the mock's duplicated modules, username and count. Theme accent remains functional.
 * Canonical expanded geometry is 1:9; collapsed header geometry is 1:10. Instances
 * in the source differ by 1–3px: those differences are not invented layout constraints.
 * Suisse Intl Medium/SemiBold is unavailable locally: dynamic text uses the existing
 * SF atlas as an explicit fallback, not as an exact font match. Numeric text is Inter.
 * The file has one static design, no prototype transitions: animation timing is native,
 * frame-rate independent, and every resting endpoint uses the decoded geometry.
 */
public class ClickGui extends Screen {

    private static final Category[] CATEGORIES = {
            Category.COMBAT,
            Category.MOVEMENT,
            Category.RENDER,
            Category.PLAYER,
            Category.MISC
    };

    // Source vector exports are wired below; all coordinates describe the source icon frame.
    private static final Identifier[] CATEGORY_ICONS = {
            Identifier.of("minar", "icons/fig_local_swords.png"),
            Identifier.of("minar", "icons/fig_local_rocket.png"),
            Identifier.of("minar", "icons/fig_local_screen.png"),
            Identifier.of("minar", "icons/fig_local_user.png"),
            Identifier.of("minar", "icons/fig_local_apps.png")
    };

    private static final Identifier ICON_AVATAR = Identifier.of("minar", "icons/fig_local_avatar.png");
    private static final Identifier ICON_MODULE_SWORDS = Identifier.of("minar", "icons/fig_local_module_swords.png");

    private float titleAvailableWidth(Function fn) {
        String key = bindingFunction == fn ? "..." : fn.getKeybind() > 0 ? getKeyName(fn.getKeybind()) : "-";
        float badge = Math.max(f(33), f(25) + ClickGuiFont.width(key, f(11)));
        return Math.max(0, f(233 - 11 - 33 - 6) - badge);
    }

    private Identifier moduleIcon(int category) {
        return category == 0 ? ICON_MODULE_SWORDS : CATEGORY_ICONS[category];
    }

    private static final Identifier ICON_LOGO = Identifier.of("minar", "icons/fig_local_logo.png");
    private static final Identifier ICON_CHECK = Identifier.of("minar", "icons/nex_check.png");
    private static final Identifier ICON_CROSS = Identifier.of("minar", "icons/fig_local_cross.png");
    private static final Identifier ICON_SEARCH = Identifier.of("minar", "icons/fig_local_search.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("minar", "icons/fig_local_settings.png");
    private static final Identifier ICON_USER = Identifier.of("minar", "icons/fig_local_user.png");
    private static final Identifier ICON_CROWN = Identifier.of("minar", "icons/fig_local_crown.png");
    private static final Identifier ICON_FILE = Identifier.of("minar", "icons/fig_local_file.png");
    private static final Identifier ICON_PALETTE = Identifier.of("minar", "icons/fig_local_palette.png");
    private static final Identifier ICON_SCRIPT = Identifier.of("minar", "icons/fig_local_script.png");
    private static final Identifier ICON_CHEVRON_DOWN = Identifier.of("minar", "icons/fig_local_chevron.png");
    private static final Identifier ICON_CHEVRON_UP = Identifier.of("minar", "icons/fig_local_chevron.png");
    private static final Identifier ICON_LIST = Identifier.of("minar", "icons/fig_local_list.png");
    private static final Identifier ICON_CURSOR_CLICK = Identifier.of("minar", "icons/fig_local_cursor.png");
    private static final Identifier ICON_PAINT = Identifier.of("minar", "icons/fig_local_paint.png");
    private static final Identifier ICON_PEN = Identifier.of("minar", "icons/fig_local_pen.png");
    private static final Identifier ICON_KEYBOARD = Identifier.of("minar", "icons/fig_local_keyboard.png");

    // ------------------------------------------------------------------
    // Local Figma paint layers; live theme accent defaults to source #FFAFE2.
    // ------------------------------------------------------------------
    private static final Color WIN_BG = new Color(0x18, 0x17, 0x17);         // 1:4
    private static final Color CARD = new Color(255, 255, 255, 5);            // 1:9, 1:10: white @2%
    private static final Color FIELD = new Color(255, 255, 255, 10);          // 1:132: white @4%
    private static final Color CHIP_BG = new Color(0x1D, 0x1C, 0x1C);        // 1:262, 1:266
    private static final Color SURFACE_8 = new Color(255, 255, 255, 20);      // white @8% (scroll thumb, hover rows)
    private static final Color TEXT_25 = new Color(255, 255, 255, 64);        // white @25% (desc, dim icons)
    private static final Color TEXT_45 = new Color(255, 255, 255, 115);       // white @45% (field values)

    // ------------------------------------------------------------------
    // Exact Figma geometry (window-relative px, scaled by f())
    // ------------------------------------------------------------------
    private static final float W_WIN = 923f, H_WIN = 624f, R_WIN = 25f;        // Rectangle 1
    private static final float PAD = 15f;                                      // window inner margin
    private static final float LOGO = 52f, R_LOGO = 13f;                       // Rectangle 3
    private static final float RAIL_W = 52f, RAIL_Y = 72f, RAIL_H = 221f, R_RAIL = 15f; // Rectangle 2
    private static final float RAIL_ICON = 14f, RAIL_ICON_Y0 = 25f, RAIL_PITCH = 40f;  // two-swords/rocket/screen/user/apps frames
    private static final float RAIL_BOT_Y = 505f, RAIL_BOT_H = 104f;           // Rectangle 173
    private static final float COL_X0 = 78f, COL_GAP = 9f, COL_W = 272f;       // Rectangle 7 x=523-445
    private static final float COL_Y0 = 15f, COL_BOTTOM = 624f;                // source cards 1:73/74 continue to the window bottom
    private static final float CONTENT_RIGHT = 912f;                           // 640+272 (Rectangle 42 column)
    private static final float CARD_H = 70f, R_CARD = 15f;                     // Rectangle 8
    private static final float TOGGLE_W = 27f, TOGGLE_H = 19f, KNOB = 13f;     // Rectangle 13/16 + 14/17
    private static final float KB_BADGE_H = 18f, R_FIELD = 7f;                 // Rectangle 15 / fields
    private static final float FIELD_H = 27f;                                  // Rectangle 23-27, 62-66
    private static final float SB_X = 918f;                                    // Rectangle 11/12 x=1363-445

    // Settings vertical rhythm (Rectangle 7 children, exact)
    private static final float SET_FIRST = 19f;      // header bottom (70) -> "Slider" label (324-235=89)
    private static final float SET_LBL2CTRL = 23f;   // label -> control (Slider 324 -> track 347; Mode 398 -> dropdown 421)
    private static final float SET_CTRL2NEXT = 15f;  // control bottom -> next label (351->366, 448->463, 513->528, 578->593)
    private static final float SET_BOTTOM = 16f;     // last field bottom 643 -> panel bottom 659
    private static final float SET_X = 14f;          // settings block left inset (fields 537 on card 523)
    private static final float SET_W = 250f;         // fields/rows width (Rectangle 23-27, 62-66)
    private static final float TRACK_W = 248f;       // slider track (Rectangle 19)
    private static final float CHECKBOX = 17f;       // Rectangle 22
    private static final float CHIP_H = 20f, CHIP_GAP_X = 6f, CHIP_GAP_Y = 5f, R_CHIP = 6f; // Rectangle 46-53

    private static final float ANIMATION_DURATION = 240f;

    private float winX;
    private float winY;
    private boolean layoutReady = false;
    private float d = 0.5f; // figma px -> gui px

    private int activeCategory = 0;
    // Smooth category transition state; kept frame-rate independent and allocation-free.
    private float categoryTransition = 1f;
    private int transitionFromCategory = 0;
    private boolean draggingWindow = false;
    private float dragGrabX;
    private float dragGrabY;

    private boolean searching = false;
    private String searchQuery = "";

    private float scrollY = 0f;
    private float targetScrollY = 0f;

    private final Set<Function> expandedModules = new HashSet<>();
    private Function bindingFunction = null;
    private KeybindSetting bindingSetting = null;
    private TextSetting editingTextSetting = null;

    private NumberSetting draggingNumberSetting = null;
    private float draggingSliderStartX = 0f;
    private float draggingSliderWidth = 1f;

    private long animationStart;
    private boolean closing;
    private long lastFrameTime;
    private float frameDelta = 1f / 60f;
    private float accentRed = 255f, accentGreen = 175f, accentBlue = 226f;
    private float railPosition;
    private final Map<String, Float> iconAnims = new HashMap<>();
    private final Map<MultiSetting, Map<String, Float>> chipAnims = new HashMap<>();
    private float dropdownScroll;
    private final Map<Function, Float> moduleToggleAnims = new HashMap<>();
    private final Map<Function, Float> moduleHoverAnims = new HashMap<>();
    private final Map<Function, Float> moduleExpandAnims = new HashMap<>();
    private final Map<NumberSetting, Float> sliderVisualAnims = new HashMap<>();
    private final Map<NumberSetting, Float> sliderHoverAnims = new HashMap<>();
    private final Map<BooleanSetting, Float> booleanAnims = new HashMap<>();

    // Mode dropdown ("Вид киллауры" etc.): one open list, per-setting rollout animation
    private ModeSetting openDropdown = null;
    private Function openDropdownFn = null;
    private final Map<ModeSetting, Float> dropdownAnims = new HashMap<>();
    private final List<Object[]> pendingDropdowns = new ArrayList<>(); // {ModeSetting, fieldX, fieldY, fieldW}
    private Object[] dropdownGeom = null; // last rendered overlay: {mode, fieldX, fieldY, fieldW, listY, listH}

    private final ColorPickerWindow colorPicker = new ColorPickerWindow();
    private final ThemeMenuWindow themeMenu = new ThemeMenuWindow();
    // Live accent: eases toward the active theme color so switching themes is smooth
    private Color accentCurrent = new Color(0xFF, 0xAF, 0xE2);

    public static void openColorPicker(ColorSetting setting, float preferredX, float preferredY) {
        if (EventManager.clickGui != null) {
            EventManager.clickGui.colorPicker.open(setting, preferredX, preferredY);
        }
    }

    public ColorPickerWindow getColorPicker() {
        return colorPicker;
    }

    public ClickGui() {
        super(Text.of("Nexoria"));
        themeMenu.setColorPicker(colorPicker);
    }

    private float approach(float current, float target, float speed) {
        float value = current + (target - current) * (1f - (float) Math.exp(-speed * frameDelta));
        return Math.abs(value - target) < 0.0001f ? target : value;
    }

    private Color accent() {
        return accentCurrent;
    }

    private Color accentTint() {
        return new Color(accentCurrent.getRed(), accentCurrent.getGreen(), accentCurrent.getBlue(), 36);
    }

    private float f(float figmaPx) {
        return figmaPx * d;
    }

    @Override
    public void onDisplayed() {
        closing = false;
        animationStart = System.currentTimeMillis();
        lastFrameTime = 0L;
        bindingFunction = null;
        bindingSetting = null;
        editingTextSetting = null;
        draggingWindow = false;
        draggingNumberSetting = null;
        searching = false;
        searchQuery = "";
        colorPicker.reset();
        themeMenu.reset();
        closeDropdown();
        dropdownAnims.clear();
        categoryTransition = 1f;
        layoutReady = false;

        playSound("gui_open");
        super.onDisplayed();
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        draggingWindow = false;
        animationStart = System.currentTimeMillis();
        colorPicker.close();
        themeMenu.close();
        closeDropdown();
        playSound("gui_close");
    }

    private float getAnimation() {
        float progress = (System.currentTimeMillis() - animationStart) / ANIMATION_DURATION;
        progress = Math.clamp(progress, 0f, 1f);
        return closing ? 1f - progress : progress;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float screenW = RenderUtil.getFixedScaledWidth();
        float screenH = RenderUtil.getFixedScaledHeight();
        float opacity = Easings.InOutCubic(getAnimation());
        RenderUtil.rect(0, 0, screenW, screenH, 0, new Color(5, 3, 8, (int) (70 * opacity)));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float fixedMouseX = RenderUtil.convertX(mouseX);
        float fixedMouseY = RenderUtil.convertY(mouseY);
        float animation = getAnimation();

        if (closing && animation <= 0.01f) {
            if (client != null) client.setScreen(null);
            return;
        }

        float eased = Easings.InOutCubic(animation);
        float opacity = eased;

        updateAnimations(fixedMouseX, fixedMouseY);

        float screenW = RenderUtil.getFixedScaledWidth();
        float screenH = RenderUtil.getFixedScaledHeight();

        // RenderUtil uses half the physical framebuffer: 0.5 preserves 923x624 at 1080p.
        // Smaller displays fit the reference canvas uniformly, never independently stretching axes.
        float nextScale = 0.5f * Math.min(1f, Math.min(screenW / 960f, screenH / 540f));
        if (Math.abs(nextScale - d) > 0.001f) layoutReady = false;
        d = nextScale;

        ensureLayout(screenW, screenH);

        float w = f(W_WIN);
        float h = f(H_WIN);
        float yOff = (1f - eased) * f(24);

        // Node 1:4 has no shadow effect.
        RenderUtil.rect(winX, winY + yOff, w, h, f(R_WIN), withOpacity(WIN_BG, opacity)); // Rectangle 1

        renderContent(context, winX, winY + yOff, opacity, fixedMouseX, fixedMouseY);
        renderScrollbar(context, winX, winY + yOff, opacity);
        renderRail(context, winX, winY + yOff, opacity, fixedMouseX, fixedMouseY);
        renderLogo(context, winX, winY + yOff, opacity);
        renderBottomBar(context, winX, winY + yOff, opacity, fixedMouseX, fixedMouseY);

        renderSearch(context, opacity);
        renderDropdownOverlays(context, opacity, fixedMouseX, fixedMouseY);

        if (themeMenu.isOpen()) {
            themeMenu.render(context, fixedMouseX, fixedMouseY, deltaTicks);
        }

        if (colorPicker.isOpen()) {
            colorPicker.render(context, fixedMouseX, fixedMouseY, deltaTicks);
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void ensureLayout(float screenW, float screenH) {
        if (layoutReady) return;
        winX = (screenW - f(1920)) / 2f + f(445);
        winY = (screenH - f(1080)) / 2f + f(220);
        layoutReady = true;
    }

    private void updateAnimations(float mouseX, float mouseY) {
        long now = System.currentTimeMillis();
        if (lastFrameTime == 0L) {
            lastFrameTime = now;
            return;
        }
        float delta = Math.min(50f, now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        frameDelta = delta;
        railPosition = approach(railPosition, activeCategory, 18f);

        scrollY = approach(scrollY, targetScrollY, 16f);

        // category crossfade progress
        if (categoryTransition < 1f) {
            categoryTransition = Math.min(1f, categoryTransition + delta * 3.6f);
        }

        Color accentTarget = ThemeManager.getGuiAccent();
        accentRed = approach(accentRed, accentTarget.getRed(), 12f);
        accentGreen = approach(accentGreen, accentTarget.getGreen(), 12f);
        accentBlue = approach(accentBlue, accentTarget.getBlue(), 12f);
        accentCurrent = new Color(Math.round(accentRed), Math.round(accentGreen), Math.round(accentBlue));

        for (Function fn : FunctionManager.getFunctions()) {
            float targetToggle = fn.isEnabled() ? 1f : 0f;
            float curToggle = moduleToggleAnims.getOrDefault(fn, targetToggle);
            curToggle = approach(curToggle, targetToggle, 18f);
            moduleToggleAnims.put(fn, curToggle);

            float targetExpand = expandedModules.contains(fn) ? 1f : 0f;
            float curExpand = moduleExpandAnims.getOrDefault(fn, targetExpand);
            curExpand = approach(curExpand, targetExpand, 14f);
            moduleExpandAnims.put(fn, curExpand);
        }

        Iterator<Map.Entry<ModeSetting, Float>> it = dropdownAnims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ModeSetting, Float> e = it.next();
            float target = openDropdown == e.getKey() ? 1f : 0f;
            float cur = approach(e.getValue(), target, 18f);
            if (cur <= 0.001f && target == 0f) it.remove();
            else e.setValue(cur);
        }
        float maxScroll = Math.max(0f, (contentTotalHeight(activeCategory) - f(COL_BOTTOM - COL_Y0)) / d);
        targetScrollY = Math.clamp(targetScrollY, 0f, maxScroll);
    }

    // ------------------------------------------------------------------
    // Logo — Rectangle 3 + Text "A": 52x52 r13 #FFAFE2, "A" 32px 400 white
    // ------------------------------------------------------------------

    private void renderLogo(DrawContext context, float wx, float wy, float opacity) {
        float x = wx + f(PAD);
        float y = wy + f(PAD);
        float s = f(LOGO);
        RenderUtil.rect(x, y, s, s, f(R_LOGO), withOpacity(accent(), opacity));
        // 1:8: original outlined fox glyph, exact 32px text frame at (25,25).
        RenderUtil.texture(wx + f(25), wy + f(25), f(32), ICON_LOGO, 0, withOpacity(Color.WHITE, opacity));
    }

    // ------------------------------------------------------------------
    // Rail — Rectangle 2 + icons + Rectangle 173 (bottom) + Rectangle 169 (avatar)
    // ------------------------------------------------------------------

    private void renderRail(DrawContext context, float wx, float wy, float opacity, float mouseX, float mouseY) {
        float railX = wx + f(PAD);
        float railY = wy + f(RAIL_Y);
        RenderUtil.rect(railX, railY, f(RAIL_W), f(RAIL_H), f(R_RAIL), withOpacity(CARD, opacity));

        float rbY = wy + f(RAIL_BOT_Y);
        // 1:264 is the opaque rail backing; 1:6 contributes its 2% overlay.
        RenderUtil.rect(railX, rbY, f(RAIL_W), f(RAIL_BOT_H), f(R_RAIL), withOpacity(CHIP_BG, opacity));
        RenderUtil.rect(railX, rbY, f(RAIL_W), f(RAIL_BOT_H), f(R_RAIL), withOpacity(CARD, opacity));

        float searchS = f(15);
        boolean searchHot = searching || isInside(mouseX, mouseY, railX, rbY, f(RAIL_W), f(36));
        RenderUtil.texture(railX + f(19), rbY + f(18), searchS, ICON_SEARCH, 0,
                withOpacity(lerp(TEXT_25, Color.WHITE, iconTransition("search", searchHot)), opacity));

        // Avatar 40x40 r13 at (6,60) — accent tinted with user glyph
        float av = f(40);
        float avX = railX + f(6);
        float avY = rbY + f(60);
        RenderUtil.texture(avX, avY, av, ICON_AVATAR, f(13), withOpacity(Color.WHITE, opacity));

        RenderUtil.rect(railX + f(3), wy + f(98) + railPosition * f(RAIL_PITCH),
                f(2), f(7), f(1), withOpacity(accent(), opacity));
        // Category icons
        for (int i = 0; i < CATEGORIES.length; i++) {
            float iconY = wy + f(i == 0 ? 94 : 137 + (i - 1) * 40);
            float iconS = f(i == 0 ? 17 : RAIL_ICON);
            float iconX = wx + f(i == 0 ? 33 : 34);
            boolean hovered = isInside(mouseX, mouseY, railX, iconY - f(8), f(RAIL_W), iconS + f(16));
            float active = iconTransition("category-selected-" + i, i == activeCategory);
            float hover = iconTransition("category-hover-" + i, hovered);
            Color tint = lerp(lerp(TEXT_25, TEXT_45, hover), accent(), active);
            RenderUtil.texture(iconX, iconY, iconS, CATEGORY_ICONS[i], 0, withOpacity(tint, opacity));
        }
    }

    // ------------------------------------------------------------------
    // Bottom bar — Rectangle 199 (user chip) + Rectangle 171 (action chip)
    // ------------------------------------------------------------------

    private void renderBottomBar(DrawContext context, float wx, float wy, float opacity, float mouseX, float mouseY) {
        // 1:266: opaque user chip; no mock credentials.
        float ucX = wx + f(67);
        float ucY = wy + f(557);
        RenderUtil.rect(ucX, ucY, f(208), f(52), f(15), withOpacity(CHIP_BG, opacity));

        RenderUtil.texture(ucX + f(9), ucY + f(21), f(14), ICON_USER, 0, withOpacity(TEXT_25, opacity));

        String userName = "Player";
        try {
            userName = MinecraftClient.getInstance().getSession().getUsername();
        } catch (Exception ignored) {
        }
        RenderUtil.text(context, Msdf.SF_BOLD, ucX + f(35), ucY + f(20),
                trimToWidth(userName, f(80), f(14), Msdf.SF_BOLD), f(14), withOpacity(TEXT_25, opacity));

        long enabledCount = FunctionManager.getFunctions().stream()
                .filter(fn -> !(fn instanceof ez.minar.system.features.render.HUD) && !"HUD".equalsIgnoreCase(fn.getName()))
                .filter(Function::isEnabled).count();
        RenderUtil.texture(ucX + f(117), ucY + f(21), f(14), ICON_CROWN, 0, withOpacity(TEXT_25, opacity));
        RenderUtil.text(context, Msdf.SF_BOLD, ucX + f(140), ucY + f(20), String.valueOf(enabledCount), f(14),
                withOpacity(TEXT_25, opacity));
        RenderUtil.texture(ucX + f(178), ucY + f(21), f(14), ICON_SETTINGS, 0, withOpacity(TEXT_25, opacity));

        // Action row: file / THEMES (palette, opens the theme menu) / leaf
        float acX = wx + f(769);
        float acY = wy + f(556);
        RenderUtil.rect(acX, acY, f(138), f(52), f(15), withOpacity(CHIP_BG, opacity));

        Identifier[] actions = {ICON_FILE, ICON_PALETTE, ICON_SCRIPT};
        for (int i = 0; i < actions.length; i++) {
            float ix = acX + f(22) + i * f(40);
            float iy = acY + f(19);
            boolean hovered = isInside(mouseX, mouseY, ix - f(6), iy - f(6), f(26), f(26));
            RenderUtil.texture(ix, iy, f(14), actions[i], 0,
                    withOpacity(lerp(TEXT_25, Color.WHITE, iconTransition("action-" + i, hovered || (i == 1 && themeMenu.isOpen()))), opacity));
        }
    }

    // ------------------------------------------------------------------
    // Module columns
    // ------------------------------------------------------------------

    private List<Function> visibleModules(int category) {
        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<Function> list = new ArrayList<>();
        for (Function fn : FunctionManager.getFunctionsByCategory(CATEGORIES[category])) {
            if (fn instanceof ez.minar.system.features.render.HUD || "HUD".equalsIgnoreCase(fn.getName())) continue;
            if (!q.isEmpty()
                    && !fn.getName().toLowerCase(Locale.ROOT).contains(q)
                    && !fn.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(fn);
        }
        return list;
    }

    /** Control height per setting kind (Figma: track 4, checkbox 17, dropdown/fields 27, chips rows). */
    private float controlHeight(Setting setting) {
        if (setting instanceof NumberSetting) return f(4);
        if (setting instanceof BooleanSetting) return f(CHECKBOX);
        if (setting instanceof MultiSetting) return chipsHeight(((MultiSetting) setting).getOptionsList());
        return f(FIELD_H); // ModeSetting dropdown, ColorPicker/Button/Keybind/Text fields
    }

    /**
     * Label line -> next label line advance (exact Figma rhythm):
     * stacked controls 23 + control + 15; the checkbox sits on the label line, so 17 + 15.
     */
    private float settingAdvance(Setting setting) {
        if (setting instanceof BooleanSetting) return f(CHECKBOX + SET_CTRL2NEXT);
        return f(SET_LBL2CTRL) + controlHeight(setting) + f(SET_CTRL2NEXT);
    }

    /** Full inline settings height: 19 + Σ(advances) − 15 + 16. */
    private float settingsBlockHeight(Function fn) {
        float total = f(SET_FIRST) + f(SET_BOTTOM) - f(SET_CTRL2NEXT);
        boolean hasVisibleSettings = false;
        for (Setting setting : fn.getSettings()) {
            if (!setting.isVisible()) continue;
            hasVisibleSettings = true;
            total += settingAdvance(setting);
        }
        return hasVisibleSettings ? total : f(30);
    }

    private float moduleHeight(Function fn) {
        float expand = moduleExpandAnims.getOrDefault(fn, expandedModules.contains(fn) ? 1f : 0f);
        if (expand <= 0.001f) return f(CARD_H);
        return f(CARD_H) + settingsBlockHeight(fn) * expand;
    }

    // Column membership must not change while a card expands.
    private List<List<Function>> layoutColumns(int category) {
        List<Function> modules = visibleModules(category);
        List<List<Function>> cols = new ArrayList<>();
        int rows = Math.max(1, (modules.size() + 2) / 3);
        for (int i = 0; i < modules.size(); i += rows) {
            cols.add(modules.subList(i, Math.min(i + rows, modules.size())));
        }
        return cols;
    }

    private float contentTotalHeight(int category) {
        float max = 0f;
        for (List<Function> col : layoutColumns(category)) {
            float h = 0f;
            for (Function fn : col) h += moduleHeight(fn) + f(COL_GAP);
            if (!col.isEmpty()) h -= f(COL_GAP);
            max = Math.max(max, h);
        }
        return max;
    }

    private void renderContent(DrawContext context, float wx, float wy, float opacity, float mouseX, float mouseY) {
        float colX0 = wx + f(COL_X0);
        float colY0 = wy + f(COL_Y0);
        float viewH = f(COL_BOTTOM - COL_Y0);

        pendingDropdowns.clear();
        dropdownGeom = null;

        Scissor.push(wx + f(COL_X0) - f(2), colY0, f(CONTENT_RIGHT - COL_X0) + f(4), viewH);

        if (categoryTransition < 1f) {
            // crossfade + vertical slide between the old and new category lists
            float t = Easings.InOutCubic(categoryTransition);
            boolean forward = activeCategory > transitionFromCategory;
            float slide = f(16);
            if (1f - t > 0.01f) {
                float off = forward ? -t * slide : t * slide;
                renderColumns(context, layoutColumns(transitionFromCategory), colX0, colY0 + off, viewH, opacity * (1f - t), mouseX, mouseY, transitionFromCategory);
            }
            if (t > 0.01f) {
                float off = forward ? (1f - t) * slide : -(1f - t) * slide;
                renderColumns(context, layoutColumns(activeCategory), colX0, colY0 + off, viewH, opacity * t, mouseX, mouseY, activeCategory);
            }
        } else {
            renderColumns(context, layoutColumns(activeCategory), colX0, colY0, viewH, opacity, mouseX, mouseY, activeCategory);
        }

        Scissor.pop();
    }

    private void renderColumns(DrawContext context, List<List<Function>> cols, float colX0, float colY0, float viewH,
                               float opacity, float mouseX, float mouseY, int category) {
        for (int c = 0; c < cols.size(); c++) {
            float x = colX0 + c * f(COL_W + COL_GAP);
            float y = colY0 - scrollY * d;
            for (Function fn : cols.get(c)) {
                float h = moduleHeight(fn);
                if (y + h >= colY0 - f(30) && y <= colY0 + viewH + f(30)) {
                    renderCard(context, fn, x, y, opacity, mouseX, mouseY, category);
                }
                y += h + f(COL_GAP);
            }
        }
    }

    // Dropdown lists render above every card (and the bottom chips) so they can overlap
    // the modules below — called at the end of the frame pass.

    /**
     * Rolling-out mode list: #1D1C1C panel r7 under the field (shadow), height reveal +
     * per-item staggered fade, hover row, selected mode in #FFAFE2 with the check glyph.
     */
    private void renderDropdownOverlays(DrawContext context, float opacity, float mouseX, float mouseY) {
        boolean openRendered = false;
        for (Object[] p : pendingDropdowns) {
            ModeSetting mode = (ModeSetting) p[0];
            float fx = (float) p[1];
            float fy = (float) p[2];
            float fw = (float) p[3];
            float anim = dropdownAnims.getOrDefault(mode, 0f);
            if (mode == openDropdown) openRendered = true;
            if (anim <= 0.001f) continue;

            float e = Easings.OutCubic(anim);
            List<String> modes = mode.getModes();
            float itemH = f(FIELD_H);
            float listH = Math.min(modes.size() * itemH, Math.min(f(6 * FIELD_H), RenderUtil.getFixedScaledHeight() - f(24)));
            float ly = fy + f(FIELD_H) + f(4);
            if (ly + listH > RenderUtil.getFixedScaledHeight() - f(8)) ly = Math.max(f(8), fy - f(4) - listH);
            dropdownScroll = Math.clamp(dropdownScroll, 0f, Math.max(0f, modes.size() * itemH - listH));

            dropdownGeom = new Object[]{mode, fx, fy, fw, ly, listH};

            RenderUtil.shadow(fx, ly + f(2), fw, listH, f(R_FIELD), f(16), 0.45f * opacity * e, f(2),
                    new Color(0, 0, 0, 200));

            Scissor.push(fx, ly, fw, listH * e + f(1));
            RenderUtil.rect(fx, ly, fw, listH, f(R_FIELD), withOpacity(CHIP_BG, opacity * e));
            RenderUtil.outline(fx, ly, fw, listH, f(R_FIELD), Math.max(0.6f, f(1)),
                    withOpacity(new Color(255, 255, 255, 14), opacity * e));

            for (int i = 0; i < modes.size(); i++) {
                String item = modes.get(i);
                float iy = ly + i * itemH - dropdownScroll;
                float itemAlpha = e * opacity;
                boolean selected = item.equals(mode.getActiveMode());
                boolean hovered = isInside(mouseX, mouseY, fx, iy, fw, itemH);

                if (hovered) {
                    RenderUtil.rect(fx + f(3), iy + f(2), fw - f(6), itemH - f(4), f(5),
                            withOpacity(SURFACE_8, itemAlpha));
                }
                String displayItem = ez.minar.system.managers.LocalizationManager.get(item);
                RenderUtil.text(context, Msdf.SF_REGULAR, fx + f(12), iy + (itemH - Msdf.height(Msdf.SF_REGULAR, f(11))) / 2f,
                        trimToWidth(displayItem, fw - f(34), f(11), Msdf.SF_REGULAR), f(11),
                        withOpacity(selected || hovered ? Color.WHITE : TEXT_45, itemAlpha));
                if (selected) {
                    RenderUtil.texture(fx + fw - f(16), iy + (itemH - f(8)) / 2f, f(8), ICON_CHECK, 0,
                            withOpacity(accent(), itemAlpha));
                }
            }
            Scissor.pop();
        }
        if (openDropdown != null && !openRendered) {
            // its card got culled/scrolled away — nothing to anchor the list to
            closeDropdown();
        }
    }

    private void closeDropdown() {
        if (openDropdown == null) return;
        openDropdown = null;
        openDropdownFn = null;
    }

    private void renderCard(DrawContext context, Function fn, float x, float y, float opacity, float mouseX, float mouseY, int category) {
        float w = f(COL_W);
        float totalH = moduleHeight(fn);
        float expand = moduleExpandAnims.getOrDefault(fn, 0f);

        boolean hovered = isInside(mouseX, mouseY, x, y, w, f(CARD_H));
        float curHover = moduleHoverAnims.getOrDefault(fn, 0f);
        curHover = approach(curHover, hovered ? 1f : 0f, 16f);
        moduleHoverAnims.put(fn, curHover);

        // 1:9/10: translucent white 2% card layer, grows with actual visible settings.
        RenderUtil.rect(x, y, w, totalH, f(R_CARD), withOpacity(CARD, opacity));
        if (curHover > 0.01f && expand < 0.01f) {
            RenderUtil.rect(x, y, w, totalH, f(R_CARD),
                    withOpacity(new Color(255, 255, 255, (int) (5 * curHover)), opacity));
        }

        renderCardHeader(context, fn, x, y, w, opacity, category);

        if (expand > 0.01f) {
            renderSettings(context, fn, x, y + f(CARD_H), w, expand, opacity, mouseX, mouseY);
        }
    }

    private void renderCardHeader(DrawContext context, Function fn, float x, float y, float w, float opacity, int category) {
        float toggleE = moduleToggleAnims.getOrDefault(fn, fn.isEnabled() ? 1f : 0f);
        float expand = moduleExpandAnims.getOrDefault(fn, 0f);

        // Module icon 13x13 accent at (12,16)
        RenderUtil.texture(x + f(11 + 2 * expand), y + f(16 + 2 * expand), f(13), moduleIcon(category), 0, withOpacity(accent(), opacity));

        // Title 14px at (33,15): 600 collapsed (AttackAura), 500 expanded (AntiBot)
        float titleSize = f(14);
        MsdfFont titleFont = expand > 0.5f ? Msdf.SF_REGULAR : Msdf.SF_BOLD;
        RenderUtil.text(context, titleFont, x + f(33 - expand), y + f(14 + 2 * expand),
                trimToWidth(fn.getDisplayName(), titleAvailableWidth(fn), titleSize, titleFont), titleSize, withOpacity(Color.WHITE, opacity));

        // Desc 11px white@25% at (13,34), 244 wide, 2 lines
        String desc = fn.getDisplayDesc() != null && !fn.getDisplayDesc().isEmpty() ? fn.getDisplayDesc() : fn.getDisplayName();
        List<String> lines = wrapText(desc, f(244), f(11), Msdf.SF_REGULAR, 2);
        float lineStep = f(12.74f);
        for (int i = 0; i < lines.size(); i++) {
            RenderUtil.text(context, Msdf.SF_REGULAR, x + f(11 + 2 * expand), y + f(33 + expand) + i * lineStep, lines.get(i), f(11),
                    withOpacity(TEXT_25, opacity));
        }

        // Keybind badge 33x18 r7 white@4% (Rectangle 15/18): "B" text FIRST, keyboard icon after
        String keyText;
        if (bindingFunction == fn) keyText = "...";
        else if (fn.getKeybind() > 0) keyText = getKeyName(fn.getKeybind());
        else keyText = "-";

        float toggleX = x + w - f(12) - f(TOGGLE_W);
        float toggleY = y + f(12 + 2 * expand);

        if (keyText != null) {
            float ks = f(11);
            float kw = ClickGuiFont.width(keyText, ks);
            float badgeW = Math.max(f(33), f(7) + kw + f(3) + f(8) + f(7));
            float badgeX = toggleX - f(11) - badgeW;
            float badgeY = y + f(13 + 2 * expand);
            boolean bindingNow = bindingFunction == fn;
            RenderUtil.rect(badgeX, badgeY, badgeW, f(KB_BADGE_H), f(R_FIELD), withOpacity(FIELD, opacity));
            ClickGuiFont.draw(keyText, badgeX + f(7), badgeY + f(3 + 10.5f), ks,
                    withOpacity(bindingNow ? accent() : TEXT_25, opacity));
            RenderUtil.texture(badgeX + f(7) + kw + f(3), badgeY + (f(KB_BADGE_H) - f(8)) / 2f, f(8), ICON_KEYBOARD, 0,
                    withOpacity(bindingNow ? accent() : TEXT_25, opacity));
        }

        // 1:106/107 off: knob x3; 1:34/35 on: knob x12, not x11.
        Color pill = lerp(FIELD, accent(), toggleE);
        RenderUtil.rect(toggleX, toggleY, f(TOGGLE_W), f(TOGGLE_H), f(TOGGLE_H) / 2f, withOpacity(pill, opacity));
        float inset = f(3);
        float knobX = toggleX + inset + f(9) * toggleE;
        RenderUtil.rect(knobX, toggleY + (f(TOGGLE_H) - f(KNOB)) / 2f, f(KNOB), f(KNOB), f(KNOB) / 2f,
                withOpacity(Color.WHITE, opacity));
    }

    // ------------------------------------------------------------------
    // Inline settings — exact rhythm from Rectangle 7 children
    // ------------------------------------------------------------------

    private void renderSettings(DrawContext context, Function fn, float x, float y, float w, float expand, float opacity, float mouseX, float mouseY) {
        List<Setting> settings = fn.getSettings().stream().filter(Setting::isVisible).toList();

        Scissor.push(x, y, w, settingsBlockHeight(fn) * expand + f(2));

        float innerX = x + f(SET_X);
        float innerW = f(SET_W);
        float curY = y + f(SET_FIRST);

        float revealStart = Math.clamp((expand - 0.08f) / 0.92f, 0f, 1f);

        if (settings.isEmpty()) {
            RenderUtil.text(context, Msdf.SF_REGULAR, x + w / 2f, curY, ez.minar.system.managers.LocalizationManager.get("No settings"), f(11),
                    withOpacity(TEXT_25, opacity * expand), "center");
        } else {
            for (int i = 0; i < settings.size(); i++) {
                Setting setting = settings.get(i);
                float itemProgress = Math.clamp((revealStart - Math.min(i, 8) * 0.02f) / Math.max(0.01f, 1f - Math.min(i, 8) * 0.02f), 0f, 1f);
                float itemEase = Easings.OutCubic(itemProgress);
                float itemAlpha = itemEase * opacity;

                renderSetting(context, setting, innerX, curY + (1f - itemEase) * f(6), innerW, itemAlpha, mouseX, mouseY);
                curY += settingAdvance(setting);
            }
        }

        Scissor.pop();
    }

    private void renderSetting(DrawContext context, Setting setting, float x, float y, float w, float opacity, float mouseX, float mouseY) {
        float delta = frameDelta;
        if (!(setting instanceof NumberSetting) && !(setting instanceof BooleanSetting)
                && !(setting instanceof ModeSetting) && !(setting instanceof MultiSetting)) {
            RenderUtil.text(context, Msdf.SF_REGULAR, x, y,
                    trimToWidth(setting.getDisplayName(), w, f(12), Msdf.SF_REGULAR), f(12), withOpacity(Color.WHITE, opacity));
        }

        if (setting instanceof NumberSetting num) {
            // "Slider" 12px white + "15.1" 12px 600 #FFAFE2 right (Inter 600 in file)
            RenderUtil.text(context, Msdf.SF_REGULAR, x, y, trimToWidth(num.getDisplayName(), w - f(34), f(12), Msdf.SF_REGULAR),
                    f(12), withOpacity(Color.WHITE, opacity));
            String valStr = String.format(Locale.US, (num.getStep() >= 1.0) ? "%.0f" : "%.1f", num.getValue());
            float valW = ClickGuiFont.width(valStr, f(12));
            ClickGuiFont.draw(valStr, x + w - valW, y - f(1) + f(11.863636f), f(12), withOpacity(accent(), opacity));

            // Track 248x4 white@4% at label+23; fill #FFAFE2; thumb 8x8 pink w/ 1px white stroke
            float trackY = y + f(SET_LBL2CTRL);
            float trackH = f(4);
            float trackW = f(TRACK_W);
            RenderUtil.rect(x, trackY, trackW, trackH, trackH / 2f, withOpacity(FIELD, opacity));

            double range = num.getMax() - num.getMin();
            float targetProg = (float) Math.clamp((num.getValue() - num.getMin()) / Math.max(0.0001, range), 0.0, 1.0);
            float curProg = sliderVisualAnims.getOrDefault(num, targetProg);
            curProg = draggingNumberSetting == num ? targetProg : approach(curProg, targetProg, 24f);
            sliderVisualAnims.put(num, curProg);

            float thumbD = f(8);
            float thumbX = x + Math.max(0f, trackW - thumbD) * curProg;
            float fillW = Math.max(trackH, thumbX - x + thumbD / 2f);
            RenderUtil.rect(x, trackY, Math.min(fillW, trackW), trackH, trackH / 2f, withOpacity(accent(), opacity));
            float thumbY = trackY + trackH / 2f - thumbD / 2f;
            RenderUtil.rect(thumbX, thumbY, thumbD, thumbD, thumbD / 2f, withOpacity(accent(), opacity));
            RenderUtil.outline(thumbX, thumbY, thumbD, thumbD, thumbD / 2f, Math.max(0.6f, f(1)), withOpacity(Color.WHITE, opacity));
            return;
        }

        if (setting instanceof BooleanSetting bool) {
            // Checkbox 17x17 r4 on the label line, right-aligned (Rectangle 22 at 769,366)
            float boxSize = f(CHECKBOX);
            float boxX = x + w - boxSize - f(1);
            float boxY = y;

            float targetSw = bool.isEnabled() ? 1f : 0f;
            float curSw = booleanAnims.getOrDefault(bool, targetSw);
            curSw = approach(curSw, targetSw, 20f);
            booleanAnims.put(bool, curSw);

            RenderUtil.text(context, Msdf.SF_REGULAR, x, y,
                    trimToWidth(bool.getDisplayName(), w - boxSize - f(8), f(12), Msdf.SF_REGULAR), f(12),
                    withOpacity(Color.WHITE, opacity));

            RenderUtil.rect(boxX, boxY, boxSize, boxSize, f(4), withOpacity(lerp(FIELD, accent(), curSw), opacity));
            // Off: cross white@45% (cross-small 1); On: white check
            if (curSw > 0.5f) {
                float ic = f(9) * Math.min(1f, (curSw - 0.5f) * 2f);
                RenderUtil.texture(boxX + (boxSize - ic) / 2f, boxY + (boxSize - ic) / 2f, ic, ICON_CHECK, 0,
                        withOpacity(Color.WHITE, opacity));
            } else {
                RenderUtil.texture(boxX + f(4), boxY + f(4), f(10), ICON_CROSS, 0,
                        withOpacity(TEXT_45, (1f - curSw) * opacity));
            }
            return;
        }

        if (setting instanceof ModeSetting mode) {
            // Label, then dropdown field 27h: list icon (9,10), value 11px white@45% at (23,8), angle-down right
            RenderUtil.text(context, Msdf.SF_REGULAR, x, y, trimToWidth(mode.getDisplayName(), w, f(12), Msdf.SF_REGULAR),
                    f(12), withOpacity(Color.WHITE, opacity));

            float fieldY = y + f(SET_LBL2CTRL);
            renderFieldBg(context, x, fieldY, w, opacity);
            RenderUtil.texture(x + f(9), fieldY + f(10), f(8), ICON_LIST, 0, withOpacity(TEXT_45, opacity));
            String value = ez.minar.system.managers.LocalizationManager.get(mode.getActiveMode());
            RenderUtil.text(context, Msdf.SF_REGULAR, x + f(23), fieldY + f(7),
                    trimToWidth(value, w - f(40), f(11), Msdf.SF_REGULAR), f(11), withOpacity(TEXT_45, opacity));

            // angle-down crossfades to angle-up while the list is rolled out
            float dAnim = dropdownAnims.getOrDefault(mode, openDropdown == mode ? 1f : 0f);
            if (dAnim < 0.99f) {
                RenderUtil.texture(x + w - f(15), fieldY + f(11), f(6), ICON_CHEVRON_DOWN, 0,
                        withOpacity(TEXT_45, (1f - dAnim) * opacity));
            }
            if (dAnim > 0.01f) {
                RenderUtil.texture(x + w - f(15), fieldY + f(11), f(6), ICON_CHEVRON_UP, 0,
                        withOpacity(TEXT_45, dAnim * opacity));
            }
            if (dAnim > 0.01f || openDropdown == mode) {
                pendingDropdowns.add(new Object[]{mode, x, fieldY, w});
            }
            return;
        }

        if (setting instanceof MultiSetting multi) {
            // Label, then chips h20 r6: selected #FFAFE2 + white text; idle white@4% + white@25%
            RenderUtil.text(context, Msdf.SF_REGULAR, x, y, trimToWidth(multi.getDisplayName(), w, f(12), Msdf.SF_REGULAR),
                    f(12), withOpacity(Color.WHITE, opacity));

            List<String> options = multi.getOptionsList();
            List<float[]> chips = computeChips(options, x, y + f(SET_LBL2CTRL), w);
            for (int i = 0; i < chips.size(); i++) {
                renderChip(context, multi, options.get(i), chips.get(i), multi.isEnabled(options.get(i)), opacity, mouseX, mouseY);
            }
            return;
        }

        if (setting instanceof ColorSetting col) {
            // Field: paint icon (8,10), "Open" centered, swatch 11x11 r4 at right (Rectangle 26)
            float fieldY = y + f(SET_LBL2CTRL);
            renderFieldBg(context, x, fieldY, w, opacity);
            RenderUtil.texture(x + f(8), fieldY + f(10), f(8), ICON_PAINT, 0, withOpacity(TEXT_45, opacity));
            RenderUtil.text(context, Msdf.SF_REGULAR, x + w / 2f - f(3.5f), fieldY + f(7), ez.minar.system.managers.LocalizationManager.get("Open"), f(11),
                    withOpacity(TEXT_45, opacity), "center");
            Color c = col.getColor() != null ? col.getColor() : accent();
            RenderUtil.rect(x + w - f(19), fieldY + f(9), f(11), f(11), f(4), withOpacity(c, opacity));
            return;
        }

        if (setting instanceof ButtonSetting btn) {
            // Field: cursor icon + label centered group (Button Preview)
            float fieldY = y + f(SET_LBL2CTRL);
            renderFieldBg(context, x, fieldY, w, opacity);
            String label = trimToWidth(ez.minar.system.managers.LocalizationManager.get(btn.getButtonText()), w - f(32), f(11), Msdf.SF_REGULAR);
            float tw = Msdf.width(Msdf.SF_REGULAR, label, f(11));
            float groupW = f(8) + f(6) + tw;
            float gx = x + (w - groupW) / 2f;
            RenderUtil.texture(gx, fieldY + f(10), f(8), ICON_CURSOR_CLICK, 0, withOpacity(TEXT_45, opacity));
            RenderUtil.text(context, Msdf.SF_REGULAR, gx + f(8) + f(6), fieldY + f(7), label, f(11),
                    withOpacity(TEXT_45, opacity));
            return;
        }

        if (setting instanceof KeybindSetting key) {
            float fieldY = y + f(SET_LBL2CTRL);
            renderFieldBg(context, x, fieldY, w, opacity);
            RenderUtil.texture(x + f(8), fieldY + f(10), f(8), ICON_KEYBOARD, 0, withOpacity(TEXT_45, opacity));
            String label = bindingSetting == key ? ez.minar.system.managers.LocalizationManager.get("Press a key...") : getKeyName(key.getKeybind());
            RenderUtil.text(context, Msdf.SF_REGULAR, x + w / 2f, fieldY + f(7),
                    trimToWidth(label, w - f(30), f(11), Msdf.SF_REGULAR), f(11),
                    withOpacity(bindingSetting == key ? accent() : TEXT_45, opacity), "center");
            return;
        }

        if (setting instanceof TextSetting txt) {
            // Field: pen icon (8,10), value/placeholder at (24,8) — "Type Something" white@45%
            float fieldY = y + f(SET_LBL2CTRL);
            renderFieldBg(context, x, fieldY, w, opacity);
            RenderUtil.texture(x + f(8), fieldY + f(10), f(8), ICON_PEN, 0, withOpacity(TEXT_45, opacity));
            boolean editing = editingTextSetting == txt;
            String placeholder = ez.minar.system.managers.LocalizationManager.get("Type Something");
            String content = txt.getValue().isEmpty() && !editing ? placeholder : txt.getValue() + (editing ? "_" : "");
            RenderUtil.text(context, Msdf.SF_REGULAR, x + f(24), fieldY + f(7),
                    trimToWidth(content, w - f(32), f(11), Msdf.SF_REGULAR), f(11),
                    withOpacity(txt.getValue().isEmpty() && !editing ? TEXT_45 : Color.WHITE, opacity));
            return;
        }

        RenderUtil.text(context, Msdf.SF_REGULAR, x, y, setting.getDisplayName(), f(12), withOpacity(Color.WHITE, opacity));
    }

    private void renderFieldBg(DrawContext context, float x, float y, float w, float opacity) {
        RenderUtil.rect(x, y, w, f(FIELD_H), f(R_FIELD), withOpacity(FIELD, opacity));
    }

    private List<float[]> computeChips(List<String> labels, float x, float y, float w) {
        List<float[]> chips = new ArrayList<>();
        float curX = x;
        float curY = y;
        for (String label : labels) {
            String displayLabel = ez.minar.system.managers.LocalizationManager.get(label);
            float textW = Msdf.width(Msdf.SF_REGULAR, displayLabel, f(11));
            float chipW = Math.min(textW + f(18), w);
            if (curX > x && curX + chipW > x + w) {
                curX = x;
                curY += f(CHIP_H + CHIP_GAP_Y);
            }
            chips.add(new float[]{curX, curY, chipW, f(CHIP_H)});
            curX += chipW + f(CHIP_GAP_X);
        }
        return chips;
    }

    private float chipsHeight(List<String> labels) {
        if (labels.isEmpty()) return 0f;
        List<float[]> chips = computeChips(labels, 0f, 0f, f(SET_W));
        float lastY = 0f;
        for (float[] c : chips) lastY = Math.max(lastY, c[1]);
        return lastY + f(CHIP_H);
    }

    private void renderChip(DrawContext context, MultiSetting setting, String label, float[] rect, boolean selected,
                            float opacity, float mouseX, float mouseY) {
        float px = rect[0], py = rect[1], pw = rect[2], ph = rect[3];
        Map<String, Float> states = chipAnims.computeIfAbsent(setting, key -> new HashMap<>());
        float state = approach(states.getOrDefault(label, selected ? 1f : 0f), selected ? 1f : 0f, 20f);
        states.put(label, state);
        boolean hovered = isInside(mouseX, mouseY, px, py, pw, ph);
        RenderUtil.rect(px, py, pw, ph, f(R_CHIP), withOpacity(lerp(FIELD, accent(), state), opacity));
        if (hovered && state < 1f) {
            RenderUtil.rect(px, py, pw, ph, f(R_CHIP), withOpacity(FIELD, opacity * (1f - state)));
        }
        String displayLabel = ez.minar.system.managers.LocalizationManager.get(label);
        RenderUtil.text(context, Msdf.SF_REGULAR, px + pw / 2f, py + f(3),
                trimToWidth(displayLabel, pw - f(8), f(11), Msdf.SF_REGULAR), f(11),
                withOpacity(lerp(TEXT_25, Color.WHITE, state), opacity), "center");
    }

    private void renderSearch(DrawContext context, float opacity) {
        if (!searching) return;
        float x = winX + f(285), y = winY + f(566);
        RenderUtil.rect(x, y, f(260), f(32), f(7), withOpacity(CHIP_BG, opacity));
        RenderUtil.texture(x + f(10), y + f(9), f(14), ICON_SEARCH, 0, withOpacity(TEXT_45, opacity));
        String qText = searchQuery.isEmpty() ? ez.minar.system.managers.LocalizationManager.get("Search...") : searchQuery + "_";
        RenderUtil.text(context, Msdf.SF_REGULAR, x + f(32), y + f(9),
                trimToWidth(qText, f(220), f(12), Msdf.SF_REGULAR), f(12),
                withOpacity(searchQuery.isEmpty() ? TEXT_45 : Color.WHITE, opacity));
    }

    private float iconTransition(String id, boolean active) {
        float value = approach(iconAnims.getOrDefault(id, active ? 1f : 0f), active ? 1f : 0f, 18f);
        iconAnims.put(id, value);
        return value;
    }

    // ------------------------------------------------------------------
    // Scrollbar — Rectangle 11 (track 2px white@4%) / Rectangle 12 (thumb white@8%) at x=918
    // ------------------------------------------------------------------

    private void renderScrollbar(DrawContext context, float wx, float wy, float opacity) {
        float totalH = contentTotalHeight(activeCategory);
        float viewH = f(COL_BOTTOM - COL_Y0);
        if (totalH <= viewH + 1f) return;

        float trackX = wx + f(SB_X);
        float trackY = wy + f(26); // 1:30 (1363,246,2,235)
        float trackH = f(235);
        RenderUtil.rect(trackX, trackY, f(2), trackH, f(1), withOpacity(FIELD, opacity));

        float maxScroll = (totalH - viewH) / d;
        float thumbH = Math.max(f(20), trackH * (viewH / totalH));
        float ratio = Math.clamp(scrollY / Math.max(0.001f, maxScroll), 0f, 1f);
        float thumbY = trackY + (trackH - thumbH) * ratio;
        RenderUtil.rect(trackX, thumbY, f(2), thumbH, f(1), withOpacity(SURFACE_8, opacity));
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    private List<String> wrapText(String text, float maxWidth, float size, MsdfFont font, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String word : text.split(" ")) {
            String candidate = lines.size() >= maxLines ? lines.get(maxLines - 1) : null;
            if (lines.isEmpty()) {
                lines.add(word);
            } else if (lines.size() < maxLines) {
                String joined = lines.get(lines.size() - 1) + " " + word;
                if (Msdf.width(font, joined, size) <= maxWidth) {
                    lines.set(lines.size() - 1, joined);
                } else {
                    lines.add(word);
                }
            } else {
                // overflow on last line: trim with ellipsis
                String joined = candidate + " " + word;
                if (Msdf.width(font, joined, size) <= maxWidth) {
                    lines.set(maxLines - 1, joined);
                } else {
                    lines.set(maxLines - 1, trimToWidth(joined, maxWidth, size, font));
                }
            }
        }
        while (lines.size() > maxLines) lines.remove(lines.size() - 1);
        if (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (Msdf.width(font, last, size) > maxWidth) {
                lines.set(lines.size() - 1, trimToWidth(last, maxWidth, size, font));
            }
        }
        return lines;
    }

    private String trimToWidth(String text, float maxWidth, float size, MsdfFont font) {
        if (text == null || text.isEmpty() || maxWidth <= 4f) return "";
        if (Msdf.width(font, text, size) <= maxWidth) return text;
        String ellipsis = "...";
        float ellipsisW = Msdf.width(font, ellipsis, size);
        if (ellipsisW >= maxWidth) return "";
        String trimmed = text;
        while (!trimmed.isEmpty() && Msdf.width(font, trimmed, size) + ellipsisW > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + ellipsis;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing || getAnimation() < 0.85f) return true;

        float mouseX = RenderUtil.convertX((float) click.x());
        float mouseY = RenderUtil.convertY((float) click.y());
        int button = click.button();

        if (colorPicker.isOpen()) {
            if (colorPicker.isInside(mouseX, mouseY)) {
                return colorPicker.mouseClicked(mouseX, mouseY, button);
            } else {
                colorPicker.close();
            }
        }

        // An open mode list swallows the click: select item / toggle field / close on outside click
        if (handleDropdownClick(mouseX, mouseY, button)) {
            return true;
        }

        // Theme menu is a floating window: clicks inside go to it, outside closes it
        // (except the palette icon itself, which toggles it)
        if (themeMenu.isOpen()) {
            if (themeMenu.isInside(mouseX, mouseY)) {
                return themeMenu.mouseClicked(mouseX, mouseY, button);
            }
            if (!isPaletteIconHit(winX, winY, mouseX, mouseY)) {
                themeMenu.close();
                return true;
            }
        }

        float wx = winX;
        float wy = winY;
        if (!isInside(mouseX, mouseY, wx, wy, f(W_WIN), f(H_WIN))) {
            searching = false;
            return super.mouseClicked(click, doubled);
        }

        // Rail: category icons (hit row = icon ±8)
        float railX = wx + f(PAD);
        float railY = wy + f(RAIL_Y);
        for (int i = 0; i < CATEGORIES.length; i++) {
            float iconY = railY + f(RAIL_ICON_Y0) + i * f(RAIL_PITCH);
            if (isInside(mouseX, mouseY, railX, iconY - f(8), f(RAIL_W), f(RAIL_ICON) + f(16))) {
                if (button == 0 && activeCategory != i) {
                    transitionFromCategory = activeCategory;
                    activeCategory = i;
                    categoryTransition = 0f;
                    draggingNumberSetting = null;
                    bindingFunction = null;
                    bindingSetting = null;
                    if (editingTextSetting != null) editingTextSetting.setEditing(false);
                    editingTextSetting = null;
                    targetScrollY = 0f;
                    scrollY = 0f;
                    closeDropdown();
                    playSound("setting");
                }
                return true;
            }
        }

        // Rail bottom: search toggle
        float rbY = wy + f(RAIL_BOT_Y);
        if (isInside(mouseX, mouseY, railX, rbY, f(RAIL_W), f(36))) {
            if (button == 0) {
                searching = !searching;
                if (!searching) searchQuery = "";
                playSound("setting");
            }
            return true;
        }

        // Avatar/settings icon area in rail bottom
        if (isInside(mouseX, mouseY, railX, rbY + f(48), f(RAIL_W), f(56))) {
            return true;
        }


        // Bottom action row: file / themes palette / leaf
        float acX = wx + f(769);
        float acY = wy + f(556);
        for (int i = 0; i < 3; i++) {
            float ix = acX + f(22) + i * f(40);
            float iy = acY + f(19);
            if (isInside(mouseX, mouseY, ix - f(6), iy - f(6), f(26), f(26))) {
                if (button == 0 && i == 1) {
                    if (themeMenu.isOpen()) themeMenu.close();
                    else {
                        searching = false;
                        bindingSetting = null;
                        bindingFunction = null;
                        if (editingTextSetting != null) editingTextSetting.setEditing(false);
                        editingTextSetting = null;
                        themeMenu.open(ix, iy);
                    }
                }
                return true;
            }
        }

        if (searching && isInside(mouseX, mouseY, wx + f(285), wy + f(566), f(260), f(32))) return true;
        // Opaque footer chips intercept covered module rows.
        if (isInside(mouseX, mouseY, wx + f(67), wy + f(557), f(208), f(52))
                || isInside(mouseX, mouseY, wx + f(769), wy + f(556), f(138), f(52))) return true;
        if (handleContentClick(wx, wy, mouseX, mouseY, button)) return true;

        // Window background: drag
        if (button == 0) {
            draggingWindow = true;
            dragGrabX = mouseX - winX;
            dragGrabY = mouseY - winY;
        }
        return true;
    }

    private boolean isPaletteIconHit(float wx, float wy, float mouseX, float mouseY) {
        float ix = wx + f(769) + f(22) + f(40); // middle action icon
        float iy = wy + f(556) + f(19);
        return isInside(mouseX, mouseY, ix - f(6), iy - f(6), f(26), f(26));
    }

    private boolean handleContentClick(float wx, float wy, float mouseX, float mouseY, int button) {
        float colX0 = wx + f(COL_X0);
        float colY0 = wy + f(COL_Y0);
        float viewH = f(COL_BOTTOM - COL_Y0);

        if (!isInside(mouseX, mouseY, colX0 - f(2), colY0, f(CONTENT_RIGHT - COL_X0) + f(4), viewH)) {
            return false;
        }

        if (categoryTransition < 0.98f) return true;
        List<List<Function>> cols = layoutColumns(activeCategory);
        for (int c = 0; c < cols.size(); c++) {
            float x = colX0 + c * f(COL_W + COL_GAP);
            float y = colY0 - scrollY * d;
            for (Function fn : cols.get(c)) {
                float h = moduleHeight(fn);

                if (isInside(mouseX, mouseY, x, y, f(COL_W), h)) {
                    float expand = moduleExpandAnims.getOrDefault(fn, 0f);
                    if (mouseY > y + f(CARD_H)) {
                        if (expand < 0.98f || !expandedModules.contains(fn)) return true;
                        handleSettingsClick(fn, x, y + f(CARD_H), mouseX, mouseY, button);
                        return true;
                    }
                    handleHeaderClick(fn, x, y, mouseX, mouseY, button);
                    return true;
                }
                y += h + f(COL_GAP);
            }
        }
        return false;
    }

    private void handleHeaderClick(Function fn, float x, float y, float mouseX, float mouseY, int button) {
        float w = f(COL_W);
        float toggleX = x + w - f(12) - f(TOGGLE_W);

        // Keybind badge hit area (text + icon, right before toggle)
        String keyText = bindingFunction == fn ? "..." : (fn.getKeybind() > 0 ? getKeyName(fn.getKeybind()) : "-");
        if (keyText != null && button == 0) {
            float ks = f(11);
            float kw = ClickGuiFont.width(keyText, ks);
            float badgeW = Math.max(f(33), f(7) + kw + f(3) + f(8) + f(7));
            float badgeX = toggleX - f(11) - badgeW;
            if (isInside(mouseX, mouseY, badgeX, y + f(13 + 2 * moduleExpandAnims.getOrDefault(fn, 0f)), badgeW, f(KB_BADGE_H))) {
                bindingFunction = (bindingFunction == fn) ? null : fn;
                return;
            }
        }

        if (button == 0) {
            fn.toggle();
            playSound(fn.isEnabled() ? "smooth_on" : "smooth_off");
        } else if (button == 1) {
            if (!fn.getSettings().isEmpty()) {
                toggleExpand(fn);
            }
        } else if (button == 2) {
            bindingFunction = (bindingFunction == fn) ? null : fn;
        }
    }

    private boolean handleDropdownClick(float mouseX, float mouseY, int button) {
        if (openDropdown == null) return false;

        Object[] g = dropdownGeom;
        if (g != null && g[0] == openDropdown && button == 0) {
            float fx = (float) g[1];
            float fy = (float) g[2];
            float fw = (float) g[3];
            float ly = (float) g[4];
            float listH = (float) g[5];

            if (isInside(mouseX, mouseY, fx, fy, fw, f(FIELD_H)) || isInside(mouseX, mouseY, fx, ly, fw, listH)) {
                if (isInside(mouseX, mouseY, fx, ly, fw, listH * Easings.OutCubic(dropdownAnims.getOrDefault(openDropdown, 0f)))) {
                    int idx = (int) ((mouseY - ly + dropdownScroll) / f(FIELD_H));
                    List<String> modes = openDropdown.getModes();
                    if (idx >= 0 && idx < modes.size()) {
                        openDropdown.setMode(modes.get(idx));
                        playSound("setting");
                    }
                }
                closeDropdown();
                return true;
            }
        }

        closeDropdown();
        return true; // the click that dismisses the list is consumed
    }

    private void toggleExpand(Function fn) {
        if (expandedModules.contains(fn)) {
            expandedModules.remove(fn);
            if (openDropdownFn == fn) closeDropdown();
            playSound("settingclose");
        } else {
            expandedModules.add(fn);
            playSound("setting");
        }
    }

    private void handleSettingsClick(Function fn, float x, float settingsTop, float mouseX, float mouseY, int button) {
        float innerX = x + f(SET_X);
        float innerW = f(SET_W);

        List<Setting> settings = fn.getSettings().stream().filter(Setting::isVisible).toList();
        float curY = settingsTop + f(SET_FIRST);

        for (Setting setting : settings) {
            boolean inline = setting instanceof BooleanSetting; // checkbox sits on the label line
            float rowTop = inline ? curY : curY + f(SET_LBL2CTRL) - (setting instanceof NumberSetting ? f(5) : 0f);
            float rowH = inline ? f(CHECKBOX) : controlHeight(setting) + (setting instanceof NumberSetting ? f(10) : 0f);
            boolean inRow = isInside(mouseX, mouseY, innerX, rowTop, innerW, rowH);

            if (inRow && button == 0) {
                if (setting instanceof NumberSetting num) {
                    draggingNumberSetting = num;
                    draggingSliderStartX = innerX;
                    draggingSliderWidth = f(TRACK_W);
                    updateSliderValue(num, innerX, f(TRACK_W), mouseX);
                    return;
                }
                if (setting instanceof BooleanSetting bool) {
                    bool.setEnabled(!bool.isEnabled());
                    playSound("setting");
                    return;
                }
                if (setting instanceof ModeSetting mode) {
                    // Field click rolls the mode list out under the field
                    if (openDropdown == mode) {
                        closeDropdown();
                        playSound("settingclose");
                    } else {
                        openDropdown = mode;
                        openDropdownFn = fn;
                        dropdownAnims.putIfAbsent(mode, 0f);
                        dropdownScroll = 0f;
                        playSound("setting");
                    }
                    return;
                }
                if (setting instanceof MultiSetting multi) {
                    List<float[]> chips = computeChips(multi.getOptionsList(), innerX, curY + f(SET_LBL2CTRL), innerW);
                    for (int i = 0; i < chips.size(); i++) {
                        float[] p = chips.get(i);
                        if (isInside(mouseX, mouseY, p[0], p[1], p[2], p[3])) {
                            multi.toggle(multi.getOptionsList().get(i));
                            playSound("setting");
                            return;
                        }
                    }
                    return;
                }
                if (setting instanceof ColorSetting col) {
                    openColorPicker(col, mouseX, mouseY);
                    playSound("setting");
                    return;
                }
                if (setting instanceof ButtonSetting btn) {
                    btn.press();
                    playSound("setting");
                    return;
                }
                if (setting instanceof KeybindSetting key) {
                    bindingSetting = (bindingSetting == key) ? null : key;
                    return;
                }
                if (setting instanceof TextSetting txt) {
                    if (editingTextSetting == txt) {
                        txt.setEditing(false);
                        editingTextSetting = null;
                    } else {
                        if (editingTextSetting != null) editingTextSetting.setEditing(false);
                        txt.setEditing(true);
                        editingTextSetting = txt;
                    }
                    return;
                }
                return;
            }
            curY += settingAdvance(setting);
        }
    }

    private void updateSliderValue(NumberSetting num, float x, float w, float mouseX) {
        float thumbD = f(8);
        float travelW = Math.max(1f, w - thumbD);
        float progress = Math.clamp((mouseX - x - thumbD / 2f) / travelW, 0f, 1f);
        double range = num.getMax() - num.getMin();
        double step = num.getStep();
        double val = num.getMin() + progress * range;
        if (step > 0) {
            val = Math.round((val - num.getMin()) / step) * step + num.getMin();
        }
        val = Math.clamp(val, num.getMin(), num.getMax());
        num.setValue(val);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        float mouseX = RenderUtil.convertX((float) click.x());
        float mouseY = RenderUtil.convertY((float) click.y());
        int button = click.button();

        if (colorPicker.isOpen() && colorPicker.isDragging()) {
            return colorPicker.mouseDragged(mouseX, mouseY, button);
        }

        if (closing) return true;
        if (draggingWindow) {
            float screenW = RenderUtil.getFixedScaledWidth();
            float screenH = RenderUtil.getFixedScaledHeight();
            winX = Math.clamp(mouseX - dragGrabX, -f(W_WIN) / 2f, screenW - f(100));
            winY = Math.clamp(mouseY - dragGrabY, 2f, screenH - f(60));
            return true;
        }

        if (draggingNumberSetting != null) {
            updateSliderValue(draggingNumberSetting, draggingSliderStartX, draggingSliderWidth, mouseX);
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingWindow = false;
        draggingNumberSetting = null;
        colorPicker.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing || getAnimation() < 0.85f) return true;

        float convertedMouseX = RenderUtil.convertX((float) mouseX);
        float convertedMouseY = RenderUtil.convertY((float) mouseY);
        if (colorPicker.isOpen()) return true;
        if (themeMenu.isOpen()) {
            themeMenu.mouseScrolled(convertedMouseX, convertedMouseY, verticalAmount);
            return true;
        }
        if (openDropdown != null && dropdownGeom != null) {
            Object[] g = dropdownGeom;
            float max = Math.max(0f, openDropdown.getModes().size() * f(FIELD_H) - (float) g[5]);
            dropdownScroll = Math.clamp(dropdownScroll - (float) verticalAmount * f(FIELD_H), 0f, max);
            return true;
        }

        float colX0 = winX + f(COL_X0);
        float colY0 = winY + f(COL_Y0);
        if (isInside(convertedMouseX, convertedMouseY, colX0 - f(2), colY0,
                f(CONTENT_RIGHT - COL_X0) + f(4), f(COL_BOTTOM - COL_Y0))) {
            float totalH = contentTotalHeight(activeCategory);
            float viewH = f(COL_BOTTOM - COL_Y0);
            float maxScroll = Math.max(0f, (totalH - viewH) / d);
            targetScrollY = Math.clamp(targetScrollY - (float) verticalAmount * 44f, 0f, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (colorPicker.isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            colorPicker.close();
            return true;
        }

        if (themeMenu.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (themeMenu.isTyping()) themeMenu.backToList();
                else themeMenu.close();
            } else themeMenu.keyPressed(keyCode);
            return true;
        }
        if (colorPicker.isOpen()) return true;

        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                searchQuery = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searching = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (themeMenu.isOpen()) {
                if (themeMenu.isTyping()) {
                    themeMenu.backToList();
                } else {
                    themeMenu.close();
                }
                return true;
            }
            if (openDropdown != null) {
                closeDropdown();
                return true;
            }
            if (bindingFunction != null) {
                bindingFunction = null;
                return true;
            }
            if (bindingSetting != null) {
                bindingSetting = null;
                return true;
            }
            if (editingTextSetting != null) {
                editingTextSetting.setEditing(false);
                editingTextSetting = null;
                return true;
            }
            close();
            return true;
        }

        if (bindingFunction != null) {
            bindingFunction.setKeybind(keyCode == GLFW.GLFW_KEY_DELETE ? 0 : keyCode);
            bindingFunction = null;
            playSound("setting");
            return true;
        }

        if (bindingSetting != null) {
            bindingSetting.setKeybind(keyCode == GLFW.GLFW_KEY_DELETE ? 0 : keyCode);
            bindingSetting = null;
            playSound("setting");
            return true;
        }

        if (editingTextSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                editingTextSetting.setEditing(false);
                editingTextSetting = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                editingTextSetting.backspace();
                playKeyboardSound();
                return true;
            }
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

        if (searching) {
            searchQuery += input.asString();
            return true;
        }

        if (editingTextSetting != null) {
            editingTextSetting.append(input.asString());
            playKeyboardSound();
            return true;
        }

        return super.charTyped(input);
    }

    private static String getKeyName(int key) {
        if (key <= 0) return "None";
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
            default -> "KEY_" + key;
        };
    }

    private boolean isInside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private Color withOpacity(Color color, float opacity) {
        if (color == null) return Color.WHITE;
        int a = Math.clamp((int) (color.getAlpha() * Math.clamp(opacity, 0f, 1f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private Color lerp(Color c1, Color c2, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = (int) (c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        int a = (int) (c1.getAlpha() + t * (c2.getAlpha() - c1.getAlpha()));
        return new Color(r, g, b, a);
    }

    private void playSound(String sound) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("minar", sound)), 1.0f, 1.0f)
            );
        } catch (Exception ignored) {}
    }

    private void playKeyboardSound() {
        try {
            int randomId = 1 + (int) (Math.random() * 7);
            playSound("keyboard_" + randomId);
        } catch (Exception ignored) {}
    }
}
