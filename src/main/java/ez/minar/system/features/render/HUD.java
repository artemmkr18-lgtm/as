package ez.minar.system.features.render;

import com.mojang.blaze3d.systems.RenderSystem;
import ez.minar.mixins.interfaces.IBossBarHud;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.features.combat.TriggerBot;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.utils.ColorHelper;
import ez.minar.utils.helpers.KeyHelper;
import ez.minar.utils.math.Easings;
import ez.minar.utils.media.MediaCommand;
import ez.minar.utils.media.MediaManager;
import ez.minar.utils.media.MediaSession;
import ez.minar.utils.media.LyricsManager;
import ez.minar.utils.render.HudRenderUtils;
import ez.minar.utils.render.AnimatedValue;
import ez.minar.utils.render.DigitRoll;
import ez.minar.utils.render.HudMotion;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.msdf.MsdfManager;
import ez.minar.utils.render.pipeline.TexturePipeline;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

@NewFunction(name = "HUD", desc = "Rockstar-стиль игровой интерфейс", category = Category.RENDER)
public class HUD extends Function {
    public static HUD Instance;

    // --- Rockstar Aesthetic Palette (15% more translucent than the opaque cards) ---
    public static final Color ROCKSTAR_BG = new Color(14, 15, 20, 185);
    public static final Color ROCKSTAR_CARD = new Color(16, 17, 24, 192);
    public static final Color ROCKSTAR_PILL = new Color(18, 19, 26, 200);
    public static final Color ROCKSTAR_BORDER = new Color(255, 255, 255, 20);
    public static final Color ROCKSTAR_SQUIRCLE_TOP = new Color(255, 255, 255, 24);
    public static final Color ROCKSTAR_SQUIRCLE_BOT = new Color(255, 255, 255, 0);

    // Legacy Aliases for Compatibility
    public static final Color GLASS_BG = ROCKSTAR_BG;
    public static final Color GLASS_CARD = ROCKSTAR_CARD;
    public static final Color GLASS_PILL = ROCKSTAR_PILL;
    public static final Color GLASS_OUTLINE = ROCKSTAR_BORDER;
    public static final Color GLASS_OUTLINE_ALT = ROCKSTAR_BORDER;
    public static final Color PANEL_BG = ROCKSTAR_BG;
    public static final Color CHIP_BG = ROCKSTAR_CARD;
    public static final Color CHIP_DARKER = ROCKSTAR_PILL;
    public static final Color CHIP_INNER = new Color(0x06000000, true);
    public static final Color BORDER_SOFT = ROCKSTAR_BORDER;
    public static final Color BORDER_FADE = new Color(0x03FFFFFF, true);
    public static final Color TEXT_MUTED = new Color(160, 162, 180);
    public static final Color TEXT_SEP = new Color(255, 255, 255, 50);
    public static final Color TEXT_CORAL = new Color(255, 115, 115);

    public static final Color COLOR_CARD_BG     = new Color(17, 18, 24, 191); // #111218
    public static final Color COLOR_PILL_BG     = new Color(24, 25, 34, 200); // #181922
    public static final Color COLOR_BORDER      = new Color(255, 255, 255, 20);
    public static final Color COLOR_PURPLE      = new Color(167, 139, 250);   // #A78BFA
    public static final Color COLOR_PURPLE_DARK = new Color(139, 92, 246);   // #8B5CF6
    public static final Color COLOR_ABSORB      = new Color(251, 191, 36);    // #FBBF24
    public static final Color COLOR_ADMIN       = new Color(239, 68, 68);     // #EF4444
    public static final Color COLOR_ONLINE      = new Color(16, 185, 129);    // #10B981
    public static final Color COLOR_MUTED       = new Color(156, 163, 175);   // #9CA3AF

    public static final float GLASS_BLUR = 7.0f;
    public static final float CARD_RADIUS = 7.0f;

    // --- Stacked card layout ---
    // The source client draws 16px rows with 3px gaps; at 7px text that reads loose on this HUD,
    // so the three list cards share one tighter set of metrics.
    private static final float CARD_HEADER_H = 14.0f;
    private static final float CARD_ROW_H = 13.0f;
    private static final float CARD_GAP = 2.0f;
    private static final float PILL_PAD = 8.0f;
    private static final float PILL_RADIUS = 4.0f;
    private static final float ICON_PILL = 14.0f;
    private static final float ICON_GLYPH = 11.0f;
    private static final float DOT_SIZE = 5.0f;
    // Card body: the source's squircle carries a top highlight that fades to the base tint.
    private static final Color CARD_TOP = new Color(28, 30, 39, 196);
    private static final Color CARD_MID = new Color(18, 19, 25, 191);
    private static final Color CARD_BOTTOM = new Color(11, 12, 17, 188);
    private static final Color PILL_TOP = new Color(38, 40, 51, 205);
    private static final Color PILL_BOTTOM = new Color(19, 20, 27, 198);

    // Dynamic Island statuses: an alert owns the pill for 1s, then cross-fades back for 0.5s.
    private static final long ISLAND_ALERT_MS = 1600L;
    private static final long ISLAND_FADE_MS = 500L;
    private static final Color ISLAND_SUCCESS = new Color(74, 222, 128);
    private static final Color ISLAND_ERROR = new Color(239, 68, 68);
    private static final Color ISLAND_INFO = new Color(234, 179, 8);
    private static final int[] ISLAND_PING_STEPS = {450, 300, 150, 75};

    // --- Rockstar icons authored for the size they are drawn at ---
    // The HUD slots these at 6-8px; the 128px sources minified to that with no mipmaps and
    // shimmered, so they ship as 16px like Rockstar's own keybinds/potion/staff icons.
    private static final Identifier ICON_WORLD = Identifier.of("minar", "icons/hud16/world.png");
    private static final Identifier ICON_PLAYER = Identifier.of("minar", "icons/hud16/player.png");
    private static final Identifier ICON_KEYBOARD = Identifier.of("minar", "icons/hud16/keyboard.png");
    private static final Identifier ICON_POTION = Identifier.of("minar", "icons/hud16/potion.png");
    private static final Identifier ICON_TARGET = Identifier.of("minar", "icons/hud16/target.png");
    private static final Identifier ICON_HOTBAR = Identifier.of("minar", "icons/hud16/hotbar.png");
    private static final Identifier ICON_ARMOR = Identifier.of("minar", "icons/hud16/armor.png");
    private static final Identifier ICON_DRAG = Identifier.of("minar", "icons/hud16/drag.png");
    private static final Identifier ICON_WHO_GLASS = Identifier.of("minar", "icons/hud/whoglass.png");
    private static final Identifier ICON_ISLAND = Identifier.of("minar", "icons/hud16/island.png");
    private static final Identifier ICON_STAFF = Identifier.of("minar", "icons/hud16/handcuffs.png");
    private static final Identifier ICON_CHECK = Identifier.of("minar", "icons/hud16/check.png");
    private static final Identifier ICON_COPY = Identifier.of("minar", "icons/hud16/copy.png");
    private static final Identifier ICON_MEDIA_PREV = Identifier.of("minar", "icons/hud16/media_prev.png");
    private static final Identifier ICON_MEDIA_NEXT = Identifier.of("minar", "icons/hud16/media_next.png");
    private static final Identifier ICON_MEDIA_PLAY = Identifier.of("minar", "icons/hud16/media_play.png");
    private static final Identifier ICON_MEDIA_PAUSE = Identifier.of("minar", "icons/hud16/media_pause.png");

    // --- Settings ---
    public final MultiListSetting elements = new MultiListSetting("Элементы",
            "Мир", "Игрок", "Бинды", "Зелья", "ТаргетХуд", "Хотбар", "Тотем", "Персонал", "Броня", "Остров", "Уведомления");
    public final BooleanSetting hotbar = new BooleanSetting("Хотбар", true);
    public final BooleanSetting worldCompact = new BooleanSetting("Компактный сервер", false);
    public final BooleanSetting speedY = new BooleanSetting("Скорость по Y", false);
    public final BooleanSetting targetHudLook = new BooleanSetting("Таргет по взгляду", true);
    public final ModeSetting targetHudArmor = new ModeSetting("Броня таргета", "Цифры", "Иконки", "Нет");
    public final TextSetting islandText = new TextSetting("Текст острова", "Next", 20);
    public final ModeSetting colorMode = new ModeSetting("Цвет", "Статик", "Градиент", "Тема", "Радуга", "Пульс");
    public final ColorSetting gradientColor1 = new ColorSetting("Цвет 1", new Color(0, 170, 255));
    public final ColorSetting gradientColor2 = new ColorSetting("Цвет 2", new Color(150, 0, 255));
    public final NumberSetting gradientSpeed = new NumberSetting("Скорость", 1.0, 0.1, 5.0, 0.1);
    public final NumberSetting gradientOffset = new NumberSetting("Смещение", 150.0, 10.0, 500.0, 10.0);

    // --- Draggable Handlers ---
    private final HudDrag dragWorld = new HudDrag("Мир", 4f, 4f);
    private final HudDrag dragPlayer = new HudDrag("Игрок", 4f, 22f);
    private final HudDrag dragKeybinds = new HudDrag("Бинды", 4f, 40f);
    private final HudDrag dragPotions = new HudDrag("Зелья", 4f, 110f);
    private final HudDrag dragTargetHud = new HudDrag("ТаргетХуд", 200f, 200f);
    private final HudDrag dragTotem = new HudDrag("Тотем", 500f, 500f);
    private final HudDrag dragStaff = new HudDrag("Персонал", 4f, 180f);
    private final HudDrag dragArmor = new HudDrag("Броня", 250f, 500f);
    private final HudDrag dragIsland = new HudDrag("Остров", 350f, 6f);

    private final Map<String, HudDrag> positions = new LinkedHashMap<>();

    // --- HUD motion: Rockstar's curves and durations ---
    /** UiInternal024 gives the island a 500ms bezier(0.27,1.09,0.49,1.06) layout motion. */
    private final AnimatedValue islandWidth = new AnimatedValue(500L, 0f, AnimatedValue.CARD);
    /** ScriptInternal116: the label crossfade shares the width's duration and curve, so the pill
     *  grows exactly as fast as the old label wipes out. */
    private final AnimatedValue islandMix = new AnimatedValue(500L, 0f, AnimatedValue.CARD);
    private final AnimatedValue fpsValue = new AnimatedValue(300L, 0f, AnimatedValue.SMOOTHSTEP);
    private final HudMotion worldMotion = new HudMotion();
    private final HudMotion playerMotion = new HudMotion();
    private final HudMotion targetMotion = new HudMotion();
    private final HudMotion keybindsMotion = new HudMotion();
    private final HudMotion potionsMotion = new HudMotion();
    private final HudMotion staffMotion = new HudMotion();
    private final HudMotion armorMotion = new HudMotion();
    private final HudMotion totemMotion = new HudMotion();
    private final HudMotion islandMotion = new HudMotion();
    /** Transport row revealed by right-clicking the island. */
    private boolean islandOpen;
    private final AnimatedValue islandOpenAnim = new AnimatedValue(300L, 0f, AnimatedValue.CARD);
    /** Boss HP eased like the target bar, so switching bars does not snap the island's progress line. */
    private final AnimatedValue islandBossHealth = new AnimatedValue(260L, 0f, AnimatedValue.DRAG);
    /** The countdown digits sit in a red badge beside the label and roll like the main menu clock. */
    private final DigitRoll pvpDigits = new DigitRoll();
    private static final float PVP_DIGIT_SIZE = 6.5f;
    /** A new lyric line fades and slides in on its own timestamp; the row fades out between lines. */
    private final AnimatedValue lyricSlide = new AnimatedValue(320L, 1f, AnimatedValue.DRAG);
    private final AnimatedValue lyricFade = new AnimatedValue(250L, 0f, AnimatedValue.DRAG);
    /** The panel's lyric band: it grows only while words are on screen, so no lyrics means no gap. */
    private final AnimatedValue lyricBand = new AnimatedValue(320L, 0f, AnimatedValue.CARD);
    private String lyricShown = "";
    private String lyricFading = "";
    /** Button rects as drawn last frame: HudDrag decides drag ownership before this frame's place(). */
    private final float[] islandButtons = new float[12];
    /** Per-button press spring: the glyph dips and its pill brightens, then eases back. */
    private final AnimatedValue[] islandPress = {
            new AnimatedValue(180L, 0f, AnimatedValue.CARD),
            new AnimatedValue(180L, 0f, AnimatedValue.CARD),
            new AnimatedValue(180L, 0f, AnimatedValue.CARD)};
    private final HudMotion noticesMotion = new HudMotion();
    /** Row key for the placeholder pills the editor shows over an empty card. */
    private static final String PREVIEW_ROW = "\u0000preview";
    /** Row payloads outliving their source list, so a row can fade out with its text intact. */
    private final Map<String, String[]> keybindRows = new LinkedHashMap<>();
    private final Map<String, Object[]> potionRows = new LinkedHashMap<>();
    private final Map<String, String[]> staffRows = new LinkedHashMap<>();
    private String cachedClock = "";
    private long clockStamp = -1L;
    private boolean islandPlayWanted;
    private long islandPlayAt;

    // --- TargetHUD State ---
    private LivingEntity currentTarget = null;
    private final AnimatedValue targetHudHealthAnim = new AnimatedValue(260L, 1.0f, AnimatedValue.DRAG);
    private final AnimatedValue targetHudAbsorbAnim = new AnimatedValue(260L, 0.0f, AnimatedValue.DRAG);

    // --- Hotbar Animations ---
    private final float[] hotbarSlotLifts = new float[9];
    private static float hotbarCurrentY = -1.0f;

    // --- Notifications ---
    public enum NoticeType {
        SUCCESS, ERROR, WARN, INFO
    }

    public static final class NoticeItem {
        public final String text;
        public final NoticeType type;
        public final long created;
        public final long duration;
        /** Ease of this notice's own slide-in / fade-out, replacing the old per-frame step. */
        final AnimatedValue motion = new AnimatedValue(500L, 0f, AnimatedValue.CARD);
        /** Stacks glide to their slot, so removing a notice does not snap the ones above it down. */
        final AnimatedValue slot = new AnimatedValue(500L, -1f, AnimatedValue.DRAG);

        public NoticeItem(String text, NoticeType type, long duration) {
            this.text = text;
            this.type = type;
            this.created = System.currentTimeMillis();
            this.duration = duration;
        }
    }

    private static final List<NoticeItem> NOTICES = new CopyOnWriteArrayList<>();
    private NoticeItem islandNotice;
    private String lastAlertText = "";

    public HUD() {
        Instance = this;
        addSettings(elements, hotbar, worldCompact, speedY, targetHudLook, targetHudArmor, islandText, colorMode, gradientColor1, gradientColor2, gradientSpeed, gradientOffset);
        positions.put("Мир", dragWorld);
        positions.put("Игрок", dragPlayer);
        positions.put("Бинды", dragKeybinds);
        positions.put("Зелья", dragPotions);
        positions.put("ТаргетХуд", dragTargetHud);
        positions.put("Тотем", dragTotem);
        positions.put("Персонал", dragStaff);
        positions.put("Броня", dragArmor);
        positions.put("Остров", dragIsland);

        for (HudDrag d : positions.values()) {
            addSettings(d.settings());
        }
    }

    @Override
    public void onEnable() {
        Instance = this;
        MediaManager.start();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        MediaManager.stop();
        super.onDisable();
    }

    public Map<String, Float> getElementPositions() {
        Map<String, Float> result = new LinkedHashMap<>();
        positions.forEach((name, drag) -> {
            result.put(name + ".x", drag.x());
            result.put(name + ".y", drag.y());
        });
        return result;
    }

    public void applyElementPositions(Properties properties, BiFunction<String, Float, Float> parser) {
        positions.forEach((name, drag) -> drag.position(
                parser.apply(properties.getProperty("hud.position." + name + ".x"), drag.x()),
                parser.apply(properties.getProperty("hud.position." + name + ".y"), drag.y())));
    }

    // --- Event Rendering ---
    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        DrawContext context = event.getContext();
        if (context == null) return;

        RenderUtil.beginBlurFrame();
        HudDrag.beginFrame();

        // Every element owns its fade, so all of them are called even while switched off: a tween
        // that stops being advanced mid-frame never reaches zero and the card is stuck on screen.
        renderWorld(context);
        renderPlayer(context);
        renderKeybinds(context);
        renderPotions(context);
        renderTargetHud(context);
        renderTotem(context);
        renderStaff(context);
        renderArmor(context);
        renderIsland(context);
        renderNotices(context);

        HudDrag.drawGrid(context);
        HudDrag.endFrame();
        RenderUtil.resetUiScale();
    }

    // --- Helper Methods for Drawing & Fonts ---
    // The Rockstar HUD sets every element in SF Pro Display Medium at 7px, so the shared
    // helpers draw MSDF text instead of the vanilla bitmap font. `y` stays "top of line",
    // which keeps every existing call-site offset valid.
    private static final float HUD_TEXT = 7.0f;

    private static void drawText(DrawContext context, String text, float x, float y, Color color) {
        Msdf.text(context, Msdf.SF_MEDIUM, text, x, y, HUD_TEXT, color.getRGB(), false);
    }

    private static final Map<String, Float> WIDTH_CACHE = new HashMap<>(256);

    private static float getTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;
        Float cached = WIDTH_CACHE.get(text);
        if (cached != null) return cached;
        float w = Msdf.width(Msdf.SF_MEDIUM, text, HUD_TEXT);
        if (WIDTH_CACHE.size() > 500) WIDTH_CACHE.clear();
        WIDTH_CACHE.put(text, w);
        return w;
    }

    private static void drawCard(float x, float y, float w, float h, float radius, float blur) {
        drawCard(x, y, w, h, radius, blur, 1.0f);
    }

    /** The rect shader samples a 3x3 colour grid indexed iy*3+ix, so a vertical ramp is three rows. */
    private static Color[] vGradient(Color top, Color mid, Color bottom) {
        return new Color[]{top, top, top, mid, mid, mid, bottom, bottom, bottom};
    }

    /** Same card, faded by an element's presence so it can ease in and out instead of popping. */
    private static void drawCard(float x, float y, float w, float h, float radius, float blur, float alpha) {
        RenderUtil.blur(x, y, w, h, radius, blur * alpha);
        RenderUtil.rect(x, y, w, h, radius, vGradient(withAlpha(CARD_TOP, alpha),
                withAlpha(CARD_MID, alpha), withAlpha(CARD_BOTTOM, alpha)));
        RenderUtil.outline(x, y, w, h, radius, 0.5f, withAlpha(COLOR_BORDER, alpha));
    }

    private static void drawPill(float x, float y, float w, float h, float radius) {
        drawPill(x, y, w, h, radius, 1.0f);
    }

    /** A row pill faded by its own alpha, so rows can slide in and out one at a time. */
    private static void drawPill(float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.004f) return;
        RenderUtil.rect(x, y, w, h, radius, vGradient(withAlpha(PILL_TOP, alpha),
                withAlpha(COLOR_PILL_BG, alpha), withAlpha(PILL_BOTTOM, alpha)));
        RenderUtil.outline(x, y, w, h, radius, 0.5f, withAlpha(COLOR_BORDER, alpha));
    }

    /** Text top for a card row: centred on the glyph box with Rockstar's half-pixel optical lift. */
    private static float cardTextY(float rowTop, float rowH) {
        return rowTop + (rowH - HUD_TEXT) / 2f - 0.5f;
    }

    private void drawAccentDot(float cx, float cy) {
        RenderUtil.rect(cx - 1.25f, cy - 1.25f, 2.5f, 2.5f, 1.25f, getAccentColor());
    }

    // --- Rockstar row cards (UiInternal025): SF Pro Display Medium 7px, 18px tall, radius 6 ---
    private static final float ROW_TEXT = 7.0f;
    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_RADIUS = 6.0f;
    private static final float ROW_INSET = 5.0f;
    private static final float ROW_ICON = 7.5f;
    private static final float ROW_ICON_GAP = 4.0f;
    private static final float ROW_SEPARATOR = 10.0f;

    /** Rockstar's contextual gate: the second tween, independent of the user's toggle. */
    private boolean hudVisible() {
        return mc.player != null && mc.world != null && !mc.options.hudHidden;
    }

    private static float rowWidth(String text) {
        return Msdf.width(Msdf.SF_MEDIUM, text, ROW_TEXT);
    }

    /** Rockstar centres a row on the font ascent, not the line box, so digits sit on the middle line. */
    private static float rowTextTop(float centerY) {
        float ascent = Msdf.SF_MEDIUM != null && Msdf.SF_MEDIUM.isLoaded()
                ? Msdf.SF_MEDIUM.getAscender() * ROW_TEXT
                : Msdf.height(Msdf.SF_MEDIUM, ROW_TEXT);
        return centerY - ascent / 2f;
    }

    /** A 10px wide slot holding a 2px dot at 50% text alpha, exactly like Rockstar's separator. */
    private static float drawRowSeparator(float x, float centerY, float alpha) {
        RenderUtil.rect(x + (ROW_SEPARATOR - 2f) / 2f, centerY - 1f, 2f, 2f, 1f,
                withAlpha(Color.WHITE, alpha * 0.5f));
        return x + ROW_SEPARATOR;
    }

    private static Color withAlpha(Color color, float alpha) {
        int a = Math.clamp(Math.round(color.getAlpha() * Math.clamp(alpha, 0f, 1f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    /** Rockstar-style animated gradient color. index/total gives each element a unique phase. */
    public Color getAccentColor(int index, int total) {
        double time = System.currentTimeMillis() / 1000.0 * gradientSpeed.getValue();
        double position = total > 1 ? (double) index / (total - 1) : 0;
        double offset = gradientOffset.getValue() / 1000.0;

        Color c1 = gradientColor1.getColor();
        Color c2 = gradientColor2.getColor();

        switch (colorMode.getActiveMode()) {
            case "Градиент": {
                float progress = (float) ((Math.sin(time + position * Math.PI * 2 * offset) + 1) / 2.0);
                return lerpColor(c1, c2, progress);
            }
            // The theme's own two colours drive the sweep, so the HUD follows whatever the ClickGUI
            // is wearing; a single-colour theme gets the Watermark treatment of a lifted second stop.
            case "Тема": {
                Color t1 = ThemeManager.Theme_Color;
                Color t2 = ThemeManager.twoColors ? ThemeManager.Theme_Color2 : lerpColor(t1, Color.WHITE, 0.25f);
                float progress = (float) ((Math.sin(time + position * Math.PI * 2 * offset) + 1) / 2.0);
                return lerpColor(t1, t2, progress);
            }
            case "Радуга":
                return Color.getHSBColor((float) ((time + position * offset) % 1.0), 0.7f, 1.0f);
            case "Пульс": {
                float pulse = (float) ((Math.sin(time * 2) + 1) / 2.0);
                return lerpColor(c1, c2, pulse);
            }
            case "Статик":
            default:
                return c1;
        }
    }

    /** Overload without index: uses a single global phase. */
    public Color getAccentColor() {
        return getAccentColor(0, 1);
    }

    private static Color lerpColor(Color a, Color b, float t) {
        t = Math.clamp(t, 0f, 1f);
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
            (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)
        );
    }

    private void drawStatusEffectIcon(DrawContext context, RegistryEntry<StatusEffect> effect, float x, float y, float size,
                                      float popCx, float popCy, float popS) {
        try {
            Identifier texture = InGameHud.getEffectTexture(effect);
            if (texture != null) {
                // Vanilla GUI draws ignore the HUD projection, so the element's pop has to go
                // through the matrix stack or the icon sits still while its card grows.
                Matrix3x2fStack matrices = context.getMatrices();
                HudRenderUtils.scaleAround(matrices, popCx, popCy, popS);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, (int) x, (int) y, (int) size, (int) size);
                HudRenderUtils.popMatrix(matrices);
                return;
            }
        } catch (Exception ignored) {}
        RenderUtil.texture(x, y, size, size, ICON_POTION, 0f, getAccentColor());
    }

    // --- Component 1: World Info (hud.world) ---
    private void renderWorld(DrawContext context) {
        if (mc.player == null) return;

        int px = (int) Math.round(mc.player.getX());
        int py = (int) Math.round(mc.player.getY());
        int pz = (int) Math.round(mc.player.getZ());
        String coords = px + " " + py + " " + pz;

        String server = "Single";
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            server = mc.getCurrentServerEntry().address;
            if (worldCompact.isEnabled() && server.contains(".")) {
                String[] parts = server.split("\\.");
                if (parts.length >= 2) {
                    server = parts[parts.length - 2] + "." + parts[parts.length - 1];
                }
            }
        }

        String tpsNum = "20";
        String tpsLabel = " TPS";

        float cW = rowWidth(coords);
        float sW = rowWidth(server);
        float tNumW = rowWidth(tpsNum);
        float tLblW = rowWidth(tpsLabel);

        float h = ROW_HEIGHT;
        float radius = ROW_RADIUS;
        float w = ROW_INSET + ROW_ICON + ROW_ICON_GAP + cW
                + ROW_SEPARATOR + sW
                + ROW_SEPARATOR + tNumW + tLblW
                + ROW_INSET;

        float[] pos = dragWorld.place(w, h);
        worldMotion.update(elements.isEnabled("Мир"), hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = worldMotion.alpha();
        if (a < 0.004f) return;
        float x = pos[0], y = pos[1];
        RenderUtil.setPopScale(x + w / 2f, y + h / 2f, worldMotion.scale());
        float centerY = y + h / 2f;
        float textY = rowTextTop(centerY);

        drawCard(x, y, w, h, radius, 4.0f * worldMotion.blur(), a);
        RenderUtil.texture(x + ROW_INSET, centerY - ROW_ICON / 2f, ROW_ICON, ROW_ICON, ICON_WORLD, 0f, withAlpha(getAccentColor(), a));

        float curX = x + ROW_INSET + ROW_ICON + ROW_ICON_GAP;

        drawText(context, coords, curX, textY, withAlpha(Color.WHITE, a));
        curX += cW;

        curX = drawRowSeparator(curX, centerY, a);
        drawText(context, server, curX, textY, withAlpha(Color.WHITE, a));
        curX += sW;

        curX = drawRowSeparator(curX, centerY, a);
        drawText(context, tpsNum, curX, textY, withAlpha(Color.WHITE, a));
        drawText(context, tpsLabel, curX + tNumW, textY, withAlpha(Color.WHITE, a * 0.5f));
    }

    // --- Component 2: Player Info (hud.player) ---
    private void renderPlayer(DrawContext context) {
        if (mc.player == null) return;

        int fps = Math.round(fpsValue.to(mc.getCurrentFps()));
        String fpsNum = String.valueOf(fps);
        String fpsLabel = " FPS";

        double dx = mc.player.getX() - mc.player.lastRenderX;
        double dz = mc.player.getZ() - mc.player.lastRenderZ;
        double dist = speedY.isEnabled()
                ? Math.hypot(mc.player.getY() - mc.player.lastRenderY, Math.hypot(dx, dz))
                : Math.hypot(dx, dz);
        String speedNum = String.format(Locale.ROOT, "%.2f", dist * 20.0);
        String speedLabel = " BPS";

        float fNumW = rowWidth(fpsNum);
        float fLblW = rowWidth(fpsLabel);
        float sNumW = rowWidth(speedNum);
        float sLblW = rowWidth(speedLabel);

        float h = ROW_HEIGHT;
        float radius = ROW_RADIUS;
        float w = ROW_INSET + ROW_ICON + ROW_ICON_GAP + fNumW + fLblW
                + ROW_SEPARATOR + sNumW + sLblW
                + ROW_INSET;

        float[] pos = dragPlayer.place(w, h);
        playerMotion.update(elements.isEnabled("Игрок"), hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = playerMotion.alpha();
        if (a < 0.004f) return;
        float x = pos[0], y = pos[1];
        RenderUtil.setPopScale(x + w / 2f, y + h / 2f, playerMotion.scale());
        float centerY = y + h / 2f;
        float textY = rowTextTop(centerY);

        drawCard(x, y, w, h, radius, 4.0f * playerMotion.blur(), a);
        RenderUtil.texture(x + ROW_INSET, centerY - ROW_ICON / 2f, ROW_ICON, ROW_ICON, ICON_PLAYER, 0f, withAlpha(getAccentColor(), a));

        float curX = x + ROW_INSET + ROW_ICON + ROW_ICON_GAP;

        drawText(context, fpsNum, curX, textY, withAlpha(Color.WHITE, a));
        drawText(context, fpsLabel, curX + fNumW, textY, withAlpha(Color.WHITE, a * 0.5f));
        curX += fNumW + fLblW;

        curX = drawRowSeparator(curX, centerY, a);
        drawText(context, speedNum, curX, textY, withAlpha(Color.WHITE, a));
        drawText(context, speedLabel, curX + sNumW, textY, withAlpha(Color.WHITE, a * 0.5f));
    }

    // --- Component 3: Keybinds (hud.keybinds) ---
    private void renderKeybinds(DrawContext context) {
        List<Function> activeBinds = new ArrayList<>();
        for (Function f : FunctionManager.getFunctions()) {
            if (f.isEnabled() && f.getKeybind() > 0 && !(f instanceof HUD)) {
                activeBinds.add(f);
            }
        }
        boolean editing = mc.currentScreen instanceof ChatScreen;
        List<String> lingering = keybindsMotion.lingeringRows();
        boolean showing = elements.isEnabled("Бинды") && hudVisible() && (!activeBinds.isEmpty() || !lingering.isEmpty() || editing);

        float headerH = CARD_HEADER_H;
        float rowH = CARD_ROW_H;
        float spacing = CARD_GAP;

        float headerTitleW = getTextWidth("Клавиши");
        float headerW = 5f + 8f + 3f + headerTitleW + 6f;

        // Rows draw from a cached payload, so a module switched off keeps its label on screen while
        // the pill fades out. The cache is pruned against the tracked rows each frame.
        List<String> keys = new ArrayList<>();
        if (activeBinds.isEmpty()) {
            if (editing && lingering.isEmpty()) {
                keys.add(PREVIEW_ROW);
                keybindRows.put(PREVIEW_ROW, new String[]{"Aura", "R"});
            }
        } else {
            for (Function f : activeBinds) {
                keys.add(f.getName());
                keybindRows.put(f.getName(), new String[]{f.getName(), KeyHelper.getKeyName(f.getKeybind())});
            }
        }

        float[] rowAlpha = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            rowAlpha[i] = keybindsMotion.rowAlpha(keys.get(i), showing);
        }
        List<String> fading = new ArrayList<>(keybindsMotion.lingeringRows(keys));
        float[] fadingAlpha = new float[fading.size()];
        for (int i = 0; i < fading.size(); i++) {
            fadingAlpha[i] = keybindsMotion.rowAlpha(fading.get(i), false);
        }

        float maxRowW = headerW;
        for (String key : keys) maxRowW = Math.max(maxRowW, keybindRowWidth(keybindRows.get(key)));
        for (String key : fading) maxRowW = Math.max(maxRowW, keybindRowWidth(keybindRows.get(key)));

        // Rockstar collapses a leaving row's height with its own alpha (UiNode.collapseScale), so
        // the card's height is the sum of what is actually on screen right now.
        float stack = 0f;
        for (float ra : rowAlpha) stack += (rowH + spacing) * ra;
        for (float ra : fadingAlpha) stack += (rowH + spacing) * ra;

        float[] pos = dragKeybinds.place(maxRowW, headerH + spacing + stack);

        keybindsMotion.update(showing, hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = keybindsMotion.alpha();
        if (a < 0.004f) {
            keybindsMotion.endRows();
            keybindRows.keySet().retainAll(keybindsMotion.rowKeys());
            return;
        }

        float x = pos[0], y = pos[1];
        float popCx = x + maxRowW / 2f, popCy = y + (headerH + spacing + stack) / 2f;
        float popS = keybindsMotion.scale(0.86f);
        RenderUtil.setPopScale(popCx, popCy, popS);

        drawCard(x, y, headerW, headerH, 5.0f, 4.0f * keybindsMotion.blur(), a);
        RenderUtil.texture(x + 5f, y + (headerH - 8f) / 2f, 8f, 8f, ICON_KEYBOARD, 0f, withAlpha(getAccentColor(), a));
        drawText(context, "Клавиши", x + 5f + 8f + 3f, cardTextY(y, headerH), withAlpha(Color.WHITE, a));

        float curY = y + headerH + spacing;
        int totalRows = Math.max(1, keys.size() + fading.size());
        for (int i = 0; i < keys.size(); i++) {
            curY = drawKeybindRow(context, x, curY, keybindRows.get(keys.get(i)), rowAlpha[i] * a, rowH, spacing, i, totalRows, false);
        }
        for (int i = 0; i < fading.size(); i++) {
            curY = drawKeybindRow(context, x, curY, keybindRows.get(fading.get(i)), fadingAlpha[i] * a, rowH, spacing, keys.size() + i, totalRows, true);
        }

        keybindsMotion.endRows();
        keybindRows.keySet().retainAll(keybindsMotion.rowKeys());
    }

    private static float keybindRowWidth(String[] row) {
        return getTextWidth(row[0]) + PILL_PAD + 3f + getTextWidth(row[1]) + PILL_PAD;
    }

    /** Rockstar row motion: entering rows slide in horizontally, leaving rows slide downward with smooth collapse. */
    private float drawKeybindRow(DrawContext context, float x, float y, String[] row, float alpha, float rowH, float spacing, int rowIndex, int totalRows, boolean isLeaving) {
        if (alpha <= 0.004f) return y;
        float left = isLeaving ? x : (x - HudMotion.rowSlide(alpha));
        float rowY = isLeaving ? (y + (1.0f - alpha) * 8.0f) : y;
        float nameW = getTextWidth(row[0]);
        float keyW = getTextWidth(row[1]);
        float textY = cardTextY(rowY, rowH);

        drawPill(left, rowY, nameW + PILL_PAD, rowH, PILL_RADIUS, alpha);
        drawText(context, row[0], left + PILL_PAD / 2f, textY, withAlpha(Color.WHITE, alpha));

        drawPill(left + nameW + PILL_PAD + 3f, rowY, keyW + PILL_PAD, rowH, PILL_RADIUS, alpha);
        Color keyAccent = getAccentColor(rowIndex, totalRows);
        drawText(context, row[1], left + nameW + PILL_PAD + 3f + PILL_PAD / 2f, textY, withAlpha(keyAccent, alpha));

        return y + (rowH + spacing) * alpha;
    }

    // --- Component 4: Potions / Status Effects (hud.effects) ---
    private void renderPotions(DrawContext context) {
        if (mc.player == null) return;
        var effects = mc.player.getStatusEffects();
        boolean editing = mc.currentScreen instanceof ChatScreen;
        boolean showing = elements.isEnabled("Зелья") && hudVisible() && (!effects.isEmpty() || editing);

        float headerH = CARD_HEADER_H;
        float rowH = CARD_ROW_H;
        float spacing = CARD_GAP;

        float headerTitleW = getTextWidth("Эффекты");
        float headerW = 5f + 8f + 3f + headerTitleW + 6f;

        List<String> keys = new ArrayList<>();
        if (effects.isEmpty()) {
            String[][] preview = {
                {"Fire Resistance", "04:44"},
                {"Absorption 4", "01:44"},
                {"Resistance", "04:44"}
            };
            RegistryEntry<StatusEffect>[] previewEffects = new RegistryEntry[]{
                StatusEffects.FIRE_RESISTANCE,
                StatusEffects.ABSORPTION,
                StatusEffects.RESISTANCE
            };
            for (int i = 0; i < preview.length; i++) {
                String key = PREVIEW_ROW + i;
                keys.add(key);
                potionRows.put(key, new Object[]{preview[i][0], preview[i][1], previewEffects[i]});
            }
        } else {
            for (StatusEffectInstance inst : effects) {
                RegistryEntry<StatusEffect> type = inst.getEffectType();
                String id = Registries.STATUS_EFFECT.getId(type.value()).getPath();
                String name = id.replace('_', ' ');
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                if (inst.getAmplifier() > 0) name += " " + (inst.getAmplifier() + 1);

                int secs = inst.getDuration() / 20;
                String dur = String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60);
                keys.add(id);
                potionRows.put(id, new Object[]{name, dur, type});
            }
        }

        float[] rowAlpha = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            rowAlpha[i] = potionsMotion.rowAlpha(keys.get(i), showing);
        }
        List<String> fading = new ArrayList<>(potionsMotion.lingeringRows(keys));
        float[] fadingAlpha = new float[fading.size()];
        for (int i = 0; i < fading.size(); i++) {
            fadingAlpha[i] = potionsMotion.rowAlpha(fading.get(i), false);
        }

        float maxRowW = headerW;
        for (String key : keys) maxRowW = Math.max(maxRowW, potionRowWidth(potionRows.get(key)));
        for (String key : fading) maxRowW = Math.max(maxRowW, potionRowWidth(potionRows.get(key)));

        float stack = 0f;
        for (float ra : rowAlpha) stack += (rowH + spacing) * ra;
        for (float ra : fadingAlpha) stack += (rowH + spacing) * ra;

        float[] pos = dragPotions.place(maxRowW, headerH + spacing + stack);

        potionsMotion.update(showing, hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = potionsMotion.alpha();
        if (a < 0.004f) {
            potionsMotion.endRows();
            potionRows.keySet().retainAll(potionsMotion.rowKeys());
            return;
        }

        float x = pos[0], y = pos[1];
        float popCx = x + maxRowW / 2f, popCy = y + (headerH + spacing + stack) / 2f;
        float popS = potionsMotion.scale(0.86f);
        RenderUtil.setPopScale(popCx, popCy, popS);

        drawCard(x, y, headerW, headerH, 5.0f, 4.0f * potionsMotion.blur(), a);
        RenderUtil.texture(x + 5f, y + (headerH - 8f) / 2f, 8f, 8f, ICON_POTION, 0f, withAlpha(getAccentColor(), a));
        drawText(context, "Эффекты", x + 5f + 8f + 3f, cardTextY(y, headerH), withAlpha(Color.WHITE, a));

        float curY = y + headerH + spacing;
        int totalRows = Math.max(1, keys.size() + fading.size());
        for (int i = 0; i < keys.size(); i++) {
            curY = drawPotionRow(context, x, curY, potionRows.get(keys.get(i)), rowAlpha[i] * a, rowH, spacing, popCx, popCy, popS, i, totalRows);
        }
        for (int i = 0; i < fading.size(); i++) {
            curY = drawPotionRow(context, x, curY, potionRows.get(fading.get(i)), fadingAlpha[i] * a, rowH, spacing, popCx, popCy, popS, keys.size() + i, totalRows);
        }

        potionsMotion.endRows();
        potionRows.keySet().retainAll(potionsMotion.rowKeys());
    }

    @SuppressWarnings("unchecked")
    private float drawPotionRow(DrawContext context, float x, float y, Object[] row, float alpha, float rowH, float spacing,
                                float popCx, float popCy, float popS, int rowIndex, int totalRows) {
        if (alpha <= 0.004f) return y;
        String name = (String) row[0];
        String dur = (String) row[1];
        float left = x - HudMotion.rowSlide(alpha);
        float nameW = getTextWidth(name);
        float durW = getTextWidth(dur);
        float textY = cardTextY(y, rowH);

        drawPill(left, y, ICON_PILL, rowH, PILL_RADIUS, alpha);
        drawStatusEffectIcon(context, (RegistryEntry<StatusEffect>) row[2],
                left + (ICON_PILL - ICON_GLYPH) / 2f, y + (rowH - ICON_GLYPH) / 2f, ICON_GLYPH, popCx, popCy, popS);

        drawPill(left + ICON_PILL + 3f, y, nameW + PILL_PAD, rowH, PILL_RADIUS, alpha);
        drawText(context, name, left + ICON_PILL + 3f + PILL_PAD / 2f, textY, withAlpha(Color.WHITE, alpha));

        float durX = left + ICON_PILL + 3f + nameW + PILL_PAD + 3f;
        drawPill(durX, y, durW + PILL_PAD, rowH, PILL_RADIUS, alpha);
        Color durAccent = getAccentColor(rowIndex, totalRows);
        drawText(context, dur, durX + PILL_PAD / 2f, textY, withAlpha(durAccent, alpha));

        return y + (rowH + spacing) * alpha;
    }

    private static float potionRowWidth(Object[] row) {
        return ICON_PILL + 3f + getTextWidth((String) row[0]) + PILL_PAD + 3f + getTextWidth((String) row[1]) + PILL_PAD;
    }

    // --- Component 5: TargetHUD (hud.targethud 116x30) ---
    private void renderTargetHud(DrawContext context) {
        LivingEntity target = findTarget();
        boolean editing = mc.currentScreen instanceof ChatScreen;
        if (target != null) currentTarget = target;
        else if (editing && currentTarget == null) currentTarget = mc.player;

        float w = 116.0f;
        float h = 30.0f;
        // Registered every frame on purpose: while no entity is targeted the card has no
        // size, so the HUD editor could not grab it to move it.
        float[] pos = dragTargetHud.place(w, h);
        targetMotion.update(elements.isEnabled("ТаргетХуд") && (target != null || editing), hudVisible(),
                HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = targetMotion.alpha();
        if (a < 0.004f) {
            currentTarget = null;
            return;
        }
        if (currentTarget == null) return;

        float x = pos[0], y = pos[1];
        float cx = x + w / 2f, cy = y + h / 2f, s = targetMotion.scale();
        RenderUtil.setPopScale(cx, cy, s);

        // Draw Card matching ksnip_20260923-183243.png
        drawCard(x, y, w, h, 8.0f, 7.0f * targetMotion.blur(), a);

        // Avatar: 22x22 at (x + 4, y + 4), radius 4px
        if (currentTarget instanceof PlayerEntity player) {
            drawSkinFace(context, player, x + 4f, y + 4f, 22f, 4.0f, cx, cy, s);
        } else {
            RenderUtil.rect(x + 4f, y + 4f, 22f, 22f, 4.0f, withAlpha(new Color(30, 32, 40), a));
            RenderUtil.texture(x + 4f, y + 4f, 22f, 22f, ICON_WHO_GLASS, 4.0f, withAlpha(getAccentColor(), a));
        }

        // Target Name
        String name = currentTarget.getName().getString();
        if (editing && currentTarget == mc.player) name = "Player507";
        if (name.length() > 11) name = name.substring(0, 10) + "…";
        drawText(context, name, x + 30f, y + 5.0f, withAlpha(Color.WHITE, a));

        // Click to copy name in chat screen
        if (editing) {
            float nameW = getTextWidth(name);
            float copyIconX = x + 30f + nameW + 3f;
            float copyIconY = y + 5.5f;
            boolean hovered = HudDrag.mouseX() >= x + 30f && HudDrag.mouseX() <= copyIconX + 10f
                    && HudDrag.mouseY() >= y + 3f && HudDrag.mouseY() <= y + 15f;
            if (hovered) {
                RenderUtil.texture(copyIconX, copyIconY, 6f, 6f, ICON_COPY, 0f, getAccentColor());
                if (HudDrag.wasLeftPressed()) {
                    mc.keyboard.setClipboard(currentTarget.getName().getString());
                    pushNotice("Имя " + currentTarget.getName().getString() + " скопировано!", NoticeType.SUCCESS);
                }
            }
        }

        // Target HP text (right-aligned, purple)
        float hp = currentTarget.getHealth();
        float maxHp = currentTarget.getMaxHealth();
        String hpStr = String.format(Locale.ROOT, "%.1f", hp);
        float hpW = getTextWidth(hpStr);
        drawText(context, hpStr, x + w - 6f - hpW, y + 5.0f, withAlpha(getAccentColor(), a));

        // Health bar
        float barX = x + 30f;
        float barY = y + 19f;
        float barW = w - 36f;
        float barH = 4.5f;

        float hpFrac = Math.clamp(hp / Math.max(1f, maxHp), 0f, 1f);
        float healthFill = targetHudHealthAnim.to(hpFrac);

        // Bar background track
        RenderUtil.rect(barX, barY, barW, barH, 2.25f, withAlpha(new Color(25, 26, 35, 220), a));

        // Health fill
        RenderUtil.rect(barX, barY, barW * healthFill, barH, 2.25f, withAlpha(getAccentColor(), a));

        // Absorption fill: retargeted even at zero, or the yellow segment would stick once
        // absorption wears off.
        float absFrac = Math.clamp(currentTarget.getAbsorptionAmount() / 20.0f, 0f, 1f);
        float absorbFill = targetHudAbsorbAnim.to(absFrac);
        if (absorbFill > 0f) {
            float healthW = barW * healthFill;
            float absW = Math.min(barW - healthW, barW * absorbFill);
            if (absW > 0.5f) {
                RenderUtil.rect(barX + healthW, barY, absW, barH, 2.25f, withAlpha(COLOR_ABSORB, a));
            }
        }
    }

    private LivingEntity findTarget() {
        if (targetHudLook.isEnabled() && mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        TriggerBot trigger = FunctionManager.getFunction(TriggerBot.class);
        if (trigger != null && trigger.isEnabled() && trigger.getTarget() instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    // --- Component 6: Custom Hotbar (individual rounded slots 22x22, 3px spacing) ---
    public void renderHotbarXyeta(DrawContext context, RenderTickCounter tickCounter, HotbarItemRenderer itemRenderer) {
        if (mc.player == null) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        ItemStack offhand = mc.player.getOffHandStack();
        boolean hasOffhand = !offhand.isEmpty();

        float slotSize = 22.0f;
        float gap = 3.0f;
        float totalW = 9 * slotSize + 8 * gap; // 222px

        float startX = (screenW - totalW) / 2.0f;
        float baseY = screenH - 26.0f;
        hotbarCurrentY = baseY;

        // Render hearts / food cleanly above hotbar if enabled
        renderHotbarStatusBars(context, startX, baseY);

        int selectedSlot = mc.player.getInventory().getSelectedSlot();

        // Hotbar backdrop blur once for all slots
        RenderUtil.blur(startX - 2f, baseY - 4f, totalW + 4f, slotSize + 8f, 7.0f, 5.0f);

        // Render 9 slots (matching ksnip_20260923-183252.png)
        for (int i = 0; i < 9; ++i) {
            boolean isSel = (i == selectedSlot);
            float targetLift = isSel ? 1.0f : 0.0f;
            hotbarSlotLifts[i] += (targetLift - hotbarSlotLifts[i]) * 0.25f;

            float lift = hotbarSlotLifts[i];
            float slotX = startX + i * (slotSize + gap);
            float slotY = baseY - 3.0f * lift;

            // Slot background
            RenderUtil.rect(slotX, slotY, slotSize, slotSize, 6.0f, COLOR_CARD_BG);

            // Slot outline
            if (isSel) {
                RenderUtil.outline(slotX, slotY, slotSize, slotSize, 6.0f, 1.0f, getAccentColor());
            } else {
                RenderUtil.outline(slotX, slotY, slotSize, slotSize, 6.0f, 0.5f, COLOR_BORDER);
            }

            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                context.getMatrices().pushMatrix();
                if (lift > 0.01f) {
                    float sc = 1.0f + 0.1f * lift;
                    context.getMatrices().translate(slotX + slotSize / 2f, slotY + slotSize / 2f);
                    context.getMatrices().scale(sc, sc);
                    context.getMatrices().translate(-(slotX + slotSize / 2f), -(slotY + slotSize / 2f));
                }
                itemRenderer.render((int) (slotX + 3f), (int) (slotY + 3f), tickCounter, mc.player, stack, i);
                context.getMatrices().popMatrix();
            }
        }

        // Render Offhand slot
        if (hasOffhand) {
            float offhandX = startX - slotSize - 6.0f;
            float offhandY = baseY;

            RenderUtil.blur(offhandX, offhandY, slotSize, slotSize, 6.0f, 5.0f);
            RenderUtil.rect(offhandX, offhandY, slotSize, slotSize, 6.0f, COLOR_CARD_BG);
            RenderUtil.outline(offhandX, offhandY, slotSize, slotSize, 6.0f, 0.5f, COLOR_BORDER);

            itemRenderer.render((int) (offhandX + 3f), (int) (offhandY + 3f), tickCounter, mc.player, offhand, 99);
        }
    }

    private void renderHotbarStatusBars(DrawContext context, float hotbarX, float hotbarY) {
        if (mc.player == null) return;

        float hp = mc.player.getHealth();
        int food = mc.player.getHungerManager().getFoodLevel();

        float pillH = 14.0f;
        float pillY = hotbarY - 17.0f;

        // Left pill: Hearts / HP
        String hpText = String.format(Locale.ROOT, "%.1f HP", hp);
        float hpW = getTextWidth(hpText) + 10.0f;
        drawPill(hotbarX, pillY, hpW, pillH, 4.0f);
        drawText(context, hpText, hotbarX + 5.0f, pillY + 3.0f, new Color(255, 90, 90));

        // Right pill: Hunger
        String foodText = food + " Food";
        float foodW = getTextWidth(foodText) + 10.0f;
        float foodX = hotbarX + 222.0f - foodW;
        drawPill(foodX, pillY, foodW, pillH, 4.0f);
        drawText(context, foodText, foodX + 5.0f, pillY + 3.0f, new Color(255, 175, 50));
    }

    // --- Component 7: Totem Counter (hud.totem_counter 28x27) ---
    private void renderTotem(DrawContext context) {
        if (mc.player == null) return;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                count += stack.getCount();
            }
        }
        if (count == 0 && !(mc.currentScreen instanceof ChatScreen)) return;
        boolean showing = elements.isEnabled("Тотем") && hudVisible() && (count > 0 || mc.currentScreen instanceof ChatScreen);

        float w = 28.0f;
        float h = 28.0f;

        float[] pos = dragTotem.place(w, h);
        totemMotion.update(showing, hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = totemMotion.alpha();
        if (a < 0.004f) return;
        float x = pos[0], y = pos[1];
        float cx = x + w / 2f, cy = y + h / 2f, s = totemMotion.scale();
        RenderUtil.setPopScale(cx, cy, s);

        drawCard(x, y, w, h, 8.0f, 7.0f * totemMotion.blur(), a);
        // Vanilla item draws carry no alpha, so the totem swaps on where the blur does instead of
        // floating half-visible over a card that is still fading in.
        if (a > 0.5f) {
            Matrix3x2fStack matrices = context.getMatrices();
            HudRenderUtils.scaleAround(matrices, cx, cy, s);
            context.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), (int) (x + 6.0f), (int) (y + 3.0f));
            HudRenderUtils.popMatrix(matrices);

            String str = String.valueOf(count == 0 && mc.currentScreen instanceof ChatScreen ? 1 : count);
            float strW = getTextWidth(str);
            drawText(context, str, x + (w - strW) / 2.0f, y + 18.0f, withAlpha(Color.WHITE, a));
        }
    }

    private static final String[] STAFF_KEYWORDS = {
        "admin", "administrator", "moder", "moderator", "helper", "media", "owner", "staff",
        "support", "yt", "youtube", "youtuber", "tiktok", "хелпер", "модер", "админ",
        "сотрудник", "стажер", "стажёр", "куратор", "поддержка", "спек", "spectator"
    };

    // --- Component 8: Staff List (hud.staff_list) ---
    private void renderStaff(DrawContext context) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        List<String> staff = new ArrayList<>();
        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            String pName = entry.getProfile().name();
            String fullStr = pName.toLowerCase(Locale.ROOT);
            Team team = entry.getScoreboardTeam();
            if (team != null) {
                fullStr += " " + team.getPrefix().getString().toLowerCase(Locale.ROOT)
                        + " " + team.getSuffix().getString().toLowerCase(Locale.ROOT);
            }
            for (String kw : STAFF_KEYWORDS) {
                if (fullStr.contains(kw)) {
                    staff.add(pName);
                    break;
                }
            }
        }
        boolean editing = mc.currentScreen instanceof ChatScreen;
        boolean showing = elements.isEnabled("Персонал") && hudVisible() && (!staff.isEmpty() || editing);

        float headerH = CARD_HEADER_H;
        float rowH = CARD_ROW_H;
        float spacing = CARD_GAP;

        float headerTitleW = getTextWidth("Персонал");
        float headerW = 5f + 8f + 3f + headerTitleW + 6f;

        List<String> keys = new ArrayList<>();
        if (staff.isEmpty()) {
            keys.add(PREVIEW_ROW);
            staffRows.put(PREVIEW_ROW, new String[]{"[ADMIN]", "Бабайка"});
        } else {
            for (String s : staff) {
                keys.add(s);
                staffRows.put(s, new String[]{"[STAFF]", s});
            }
        }

        float[] rowAlpha = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            rowAlpha[i] = staffMotion.rowAlpha(keys.get(i), showing);
        }
        List<String> fading = new ArrayList<>(staffMotion.lingeringRows(keys));
        float[] fadingAlpha = new float[fading.size()];
        for (int i = 0; i < fading.size(); i++) {
            fadingAlpha[i] = staffMotion.rowAlpha(fading.get(i), false);
        }

        float maxRowW = headerW;
        for (String key : keys) maxRowW = Math.max(maxRowW, staffRowWidth(staffRows.get(key)));
        for (String key : fading) maxRowW = Math.max(maxRowW, staffRowWidth(staffRows.get(key)));

        float stack = 0f;
        for (float ra : rowAlpha) stack += (rowH + spacing) * ra;
        for (float ra : fadingAlpha) stack += (rowH + spacing) * ra;

        float[] pos = dragStaff.place(maxRowW, headerH + spacing + stack);

        staffMotion.update(showing, hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = staffMotion.alpha();
        if (a < 0.004f) {
            staffMotion.endRows();
            staffRows.keySet().retainAll(staffMotion.rowKeys());
            return;
        }

        float x = pos[0], y = pos[1];
        RenderUtil.setPopScale(x + maxRowW / 2f, y + (headerH + spacing + stack) / 2f, staffMotion.scale(0.86f));

        drawCard(x, y, headerW, headerH, 5.0f, 4.0f * staffMotion.blur(), a);
        RenderUtil.texture(x + 5f, y + (headerH - 8f) / 2f, 8f, 8f, ICON_STAFF, 0f, withAlpha(getAccentColor(), a));
        drawText(context, "Персонал", x + 5f + 8f + 3f, cardTextY(y, headerH), withAlpha(Color.WHITE, a));

        float curY = y + headerH + spacing;
        for (int i = 0; i < keys.size(); i++) {
            curY = drawStaffRow(context, x, curY, staffRows.get(keys.get(i)), rowAlpha[i] * a, rowH, spacing);
        }
        for (int i = 0; i < fading.size(); i++) {
            curY = drawStaffRow(context, x, curY, staffRows.get(fading.get(i)), fadingAlpha[i] * a, rowH, spacing);
        }

        staffMotion.endRows();
        staffRows.keySet().retainAll(staffMotion.rowKeys());
    }

    private static float staffRowWidth(String[] row) {
        return getTextWidth(row[0]) + PILL_PAD + 3f + getTextWidth(row[1]) + PILL_PAD + 3f + ICON_PILL;
    }

    /** Prefix pill, name pill and the online dot, slid in and faded by the row's own alpha. */
    private float drawStaffRow(DrawContext context, float x, float y, String[] row, float alpha, float rowH, float spacing) {
        if (alpha <= 0.004f) return y;
        float left = x - HudMotion.rowSlide(alpha);
        float pW = getTextWidth(row[0]);
        float nW = getTextWidth(row[1]);
        float textY = cardTextY(y, rowH);

        drawPill(left, y, pW + PILL_PAD, rowH, PILL_RADIUS, alpha);
        drawText(context, row[0], left + PILL_PAD / 2f, textY, withAlpha(COLOR_ADMIN, alpha));

        float nameX = left + pW + PILL_PAD + 3f;
        drawPill(nameX, y, nW + PILL_PAD, rowH, PILL_RADIUS, alpha);
        drawText(context, row[1], nameX + PILL_PAD / 2f, textY, withAlpha(Color.WHITE, alpha));

        float dotX = nameX + nW + PILL_PAD + 3f;
        drawPill(dotX, y, ICON_PILL, rowH, PILL_RADIUS, alpha);
        RenderUtil.rect(dotX + (ICON_PILL - DOT_SIZE) / 2f, y + (rowH - DOT_SIZE) / 2f, DOT_SIZE, DOT_SIZE,
                DOT_SIZE / 2f, withAlpha(COLOR_ONLINE, alpha));

        return y + (rowH + spacing) * alpha;
    }

    // --- Component 9: Armor Status (96x30 rounded card, 4 slots) ---
    private void renderArmor(DrawContext context) {
        if (mc.player == null) return;

        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        boolean hasAny = false;
        for (EquipmentSlot sl : slots) {
            if (!mc.player.getEquippedStack(sl).isEmpty()) {
                hasAny = true;
                break;
            }
        }
        if (!hasAny && !(mc.currentScreen instanceof ChatScreen)) return;
        boolean showing = elements.isEnabled("Броня") && hudVisible() && (hasAny || mc.currentScreen instanceof ChatScreen);

        float w = 96.0f;
        float h = 30.0f;

        float[] pos = dragArmor.place(w, h);
        armorMotion.update(showing, hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float a = armorMotion.alpha();
        if (a < 0.004f) return;
        float x = pos[0], y = pos[1];
        float cx = x + w / 2f, cy = y + h / 2f, s = armorMotion.scale();
        RenderUtil.setPopScale(cx, cy, s);

        // Draw unified dark card matching ksnip_20260923-183239.png
        drawCard(x, y, w, h, 8.0f, 7.0f * armorMotion.blur(), a);
        if (a <= 0.5f) return;

        ItemStack[] shown = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            shown[i] = mc.player.getEquippedStack(slots[i]);
            if (shown[i].isEmpty() && mc.currentScreen instanceof ChatScreen) {
                shown[i] = switch (i) {
                    case 0 -> new ItemStack(Items.NETHERITE_BOOTS);
                    case 1 -> new ItemStack(Items.NETHERITE_LEGGINGS);
                    case 2 -> new ItemStack(Items.NETHERITE_CHESTPLATE);
                    default -> new ItemStack(Items.NETHERITE_HELMET);
                };
            }
        }

        Matrix3x2fStack matrices = context.getMatrices();
        HudRenderUtils.scaleAround(matrices, cx, cy, s);
        for (int i = 0; i < 4; i++) {
            if (!shown[i].isEmpty()) context.drawItem(shown[i], (int) (x + i * 24.0f + 4f), (int) (y + 2f));
        }
        HudRenderUtils.popMatrix(matrices);

        for (int i = 0; i < 4; i++) {
            ItemStack stack = shown[i];
            if (stack.isEmpty()) continue;
            int pct = stack.isDamageable()
                    ? Math.round((1.0f - (float) stack.getDamage() / stack.getMaxDamage()) * 100f)
                    : 100;
            String pctStr = pct + "%";
            float pctW = getTextWidth(pctStr);
            drawText(context, pctStr, x + i * 24.0f + 12f - pctW / 2f, y + 19f, withAlpha(Color.WHITE, a));
        }
    }

    // --- Component 10: Dynamic Island (hud.dynamic_island) ---
    private void renderIsland(DrawContext context) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();

        NoticeItem alert = newestIslandNotice();
        if (alert != islandNotice) islandNotice = alert;

        boolean pvp = ez.minar.utils.render.PvpTracker.isInPvp() || ez.minar.utils.render.PvpTracker.getPvpSeconds() > 0;
        int pvpSec = ez.minar.utils.render.PvpTracker.getPvpSeconds();

        MediaSession media = MediaManager.current();
        ClientBossBar boss = pvp ? null : bossBarForIsland();
        boolean bossState = boss != null;
        float bossHealth = islandBossHealth.to(bossState ? boss.getPercent() : 0f);
        boolean music = !pvp && !bossState && media.hasTrack();

        String baseText;
        Color baseColor;
        Identifier baseIcon;
        if (pvp) {
            baseText = "В бою";
            baseColor = TEXT_CORAL;
            baseIcon = ICON_TARGET;
        } else if (bossState) {
            // Vanilla hides the bar itself while the island is on (BossBarHudMixin cancels it), so
            // the bar's own name, colour and HP have to live in this pill.
            baseText = plainText(boss.getName().getString());
            baseColor = bossBarColor(boss.getColor());
            baseIcon = null;
        } else if (music) {
            baseText = media.label();
            baseColor = getAccentColor();
            // No music glyph ships with the HUD set, and a fake one would age badly, so the
            // indicator is three bars drawn here: moving while playing, parked while paused.
            baseIcon = null;
        } else {
            baseText = islandText.getValue().isBlank() ? "Next" : islandText.getValue();
            baseColor = getAccentColor();
            baseIcon = ICON_ISLAND;
        }

        String activeAlertText = islandNotice != null ? islandNotice.text : (lastAlertText != null ? lastAlertText : "");
        Color alertColor = COLOR_PURPLE;
        float alertProgress = 1f;
        boolean alertOn = false;
        if (islandNotice != null) {
            long age = now - islandNotice.created;
            if (age > ISLAND_ALERT_MS + ISLAND_FADE_MS) {
                islandNotice = null;
            } else {
                alertOn = true;
                lastAlertText = islandNotice.text;
                alertColor = switch (islandNotice.type) {
                    case SUCCESS -> ISLAND_SUCCESS;
                    case ERROR -> ISLAND_ERROR;
                    default -> ISLAND_INFO;
                };
                alertProgress = Math.clamp(1f - age / (float) ISLAND_ALERT_MS, 0f, 1f);
            }
        }
        float alertAlpha = islandMix.to(alertOn ? 1f : 0f);

        // Track titles are unbounded (a YouTube title runs to 80 chars), so the music label keeps a
        // fixed window and the text scrolls through it instead of stretching the pill off screen.
        // Boss names come from the server and are just as unbounded, so they share the window.
        boolean scrolled = music || bossState;
        float baseLabelW = scrolled ? Math.min(getTextWidth(baseText), ISLAND_MUSIC_W) : getTextWidth(baseText);
        // A scrolled label carries no leading glyph: the pill is narrow and the name is the point.
        float leadIcon = scrolled ? 0f : 8f + 3f;
        // The countdown lives in a red badge beside the label, its digits rolling on every tick.
        String pvpNum = pvp && pvpSec > 0 ? String.valueOf(pvpSec) : "";
        pvpDigits.update(pvpNum);
        float pvpBadgeW = pvpNum.isEmpty() ? 0f : 3f + 6f + pvpDigits.width(Msdf.SF_BOLD, PVP_DIGIT_SIZE, 0.5f);
        float baseW = 4f + leadIcon + baseLabelW + pvpBadgeW + 5f;
        // The transport row only means something over a track, so leaving the music state closes it.
        // Leaving the editor closes it too: outside chat there is no way to press the buttons.
        if (!media.hasTrack() || !(mc.currentScreen instanceof ChatScreen)) islandOpen = false;
        float open = islandOpenAnim.to(islandOpen ? 1f : 0f);
        // Chrome (transport row, track line) has to leave early: the alert only dwells ~1.5s, and a
        // fade keyed to the peak of a 500ms mix meant it never visibly went away.
        float chrome = 1f - Math.clamp(alertAlpha * 2.5f, 0f, 1f);
        float reveal = open * chrome;
        // The lyric band is a permanent part of the island while the song sings: it shows outside
        // the editor too. Only the transport buttons below it are editor-gated via islandOpen.
        double posMs = MediaManager.positionSeconds() * 1000.0;
        LyricsManager.Active lyric = music ? LyricsManager.lineAt(media.artist(), media.title(), posMs) : null;
        String lyricText = lyric != null ? lyric.text() : "";
        boolean span = music && LyricsManager.inLyricSpan(media.artist(), media.title(), posMs);
        // An alert owns the island: the lyric band closes while a notification is up and reopens
        // when it leaves, as long as the song is still singing.
        float band = lyricBand.to(span && !alertOn ? 1f : 0f);
        float alertW = 4f + 6f + 3f + getTextWidth(activeAlertText) + 5f;
        // A long line widens the pill while the lyric band is up, so the row never scrolls for no reason.
        float lyricW = band > 0.01f && !lyricText.isEmpty()
                ? Math.min(getTextWidth(lyricText) + 16f, 240f) : 0f;
        // The alert owns the width while it is up: taking max() with the music label left the pill
        // stretched to the track title and the notification never shortened it.
        float targetW = alertOn ? alertW : Math.max(baseW, lyricW);
        if (islandWidth.get() <= 0f) islandWidth.snap(targetW);
        float w = islandWidth.to(targetW);
        float baseH = 15.0f;
        float h = baseH + (ISLAND_TITLE_H + 3f) * band + ISLAND_ROW_H * reveal;
        float radius = 7.0f;
        long minuteStamp = System.currentTimeMillis() / 60000L;
        if (minuteStamp != clockStamp) {
            clockStamp = minuteStamp;
            Calendar cal = Calendar.getInstance();
            cachedClock = String.format(Locale.ROOT, "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        }
        String clock = cachedClock;
        float clockW = getTextWidth(clock);
        int[] bars = pingBars();
        float barsW = bars.length + bars.length - 1;

        // Calculate idle and expanded center so the pill expands equally to both sides from its center
        // The drag anchor must not depend on the current track title, or every longer song slides
        // the whole island sideways. Anchor on the widest label the pill can ever show.
        float anchorLabelW = Math.max(baseLabelW, ISLAND_MUSIC_W);
        float idleContentW = clockW + 6f + (4f + leadIcon + anchorLabelW + 5f) + 6f + barsW;
        // Claim the click before place() does, or the island starts dragging instead of pressing a button.
        if (open > 0.5f && pointerOnIslandButtons()) HudDrag.setOverlayBlocking(true);
        float[] pos = dragIsland.place(idleContentW, h);
        islandMotion.update(elements.isEnabled("Остров"), hudVisible(), HudDrag.isEditing() && HudDrag.isLeftDown());
        float ia = islandMotion.alpha();
        if (ia < 0.004f) return;

        float cx = pos[0] + idleContentW / 2f;
        float y = pos[1];

        // Central pill is centered at cx
        float x = cx - w / 2f;
        float left = x - 6f - clockW;
        float barsX = x + w + 6f;

        // Right-click opens the transport row, and only in the editor: outside it the island must
        // stay inert so nothing pauses his music by accident.
        if (HudDrag.isEditing() && HudDrag.wasRightPressed() && pointerInRect(x, y, w, baseH)) {
            islandOpen = !islandOpen;
        }

        RenderUtil.setPopScale(cx, y + h / 2f, islandMotion.scale());

        RenderUtil.blur(x, y, w, h, radius, 5.0f * islandMotion.blur());
        RenderUtil.rect(x, y, w, h, radius, withAlpha(COLOR_CARD_BG, ia));
        RenderUtil.outline(x, y, w, h, radius, 0.5f, withAlpha(COLOR_BORDER, ia));
        // Track progress only belongs to the music state; it leaves with the rest of the chrome.
        if (music && media.durationSeconds() > 0L && chrome > 0.01f) {
            RenderUtil.rect(x + 3f, y + h - 2f, (w - 6f) * media.progress(), 1f, 0.5f,
                    withAlpha(baseColor, ia * chrome * 0.85f));
        }
        // A boss bar is a bar: a thick full-width fill riding a visible track, not a media hairline.
        if (bossState && chrome > 0.01f) {
            float barH = 3.0f;
            float barY = y + h - barH - 1.5f;
            RenderUtil.rect(x + 3f, barY, w - 6f, barH, 1.5f, withAlpha(new Color(25, 26, 35, 220), ia * chrome));
            RenderUtil.rect(x + 3f, barY, (w - 6f) * Math.clamp(bossHealth, 0f, 1f), barH, 1.5f,
                    withAlpha(baseColor, ia * chrome));
        }

        drawText(context, clock, left, y + 3f, withAlpha(TEXT_MUTED, ia));

        float iconX = x + 4f;
        float outAlpha = Math.clamp(1f - alertAlpha * 1.5f, 0f, 1f) * ia;
        float inAlpha = Math.clamp((alertAlpha - 0.2f) / 0.8f, 0f, 1f) * ia;
        if (outAlpha > 0f) {
            float baseContentW = leadIcon + baseLabelW + pvpBadgeW;
            float baseStartX = x + (w - baseContentW) / 2f;
            if (baseIcon != null) {
                RenderUtil.texture(baseStartX, y + (baseH - 8f) / 2f, 8f, 8f, baseIcon, 0f, withAlpha(baseColor, outAlpha));
            }
            float textX = baseStartX + leadIcon;
            float fullW = getTextWidth(baseText);
            if (fullW > baseLabelW + 0.5f) {
                Scissor.push(textX, y, baseLabelW, baseH);
                drawText(context, baseText, textX - marqueeOffset(fullW, baseLabelW), y + 3f, withAlpha(Color.WHITE, outAlpha));
                Scissor.pop();
            } else {
                drawText(context, baseText, textX, y + 3f, withAlpha(Color.WHITE, outAlpha));
            }
            if (!pvpNum.isEmpty()) {
                float badgeW = pvpBadgeW - 3f;
                float badgeX = baseStartX + leadIcon + baseLabelW + 3f;
                float badgeH = 11f;
                float badgeY = y + (baseH - badgeH) / 2f;
                RenderUtil.rect(badgeX, badgeY, badgeW, badgeH, 3f, withAlpha(COLOR_ADMIN, outAlpha));
                float digitH = Msdf.height(Msdf.SF_BOLD, PVP_DIGIT_SIZE);
                pvpDigits.render(context, Msdf.SF_BOLD, badgeX + 3f, badgeY + (badgeH - digitH) / 2f,
                        PVP_DIGIT_SIZE, 0.5f, withAlpha(Color.WHITE, outAlpha));
            }
        }
        if (inAlpha > 0f && !activeAlertText.isEmpty()) {
            float alertContentW = 6f + 4f + getTextWidth(activeAlertText);
            float alertStartX = x + (w - alertContentW) / 2f;
            float ringCx = alertStartX + 3f;
            RenderUtil.circleOutline(ringCx, y + baseH / 2f, 3.5f, 1.2f, 0f, 1f, withAlpha(Color.WHITE, inAlpha * 0.16f));
            RenderUtil.circleOutline(ringCx, y + baseH / 2f, 3.5f, 1.2f, -90f, alertProgress, withAlpha(alertColor, inAlpha));
            drawText(context, activeAlertText, alertStartX + 6f + 4f, y + 3f, withAlpha(Color.WHITE, inAlpha));
        }

        // The label row keeps its own centre so opening the panel does not slide the top row down.
        float baseline = y + baseH / 2f + 4.5f;
        for (int i = 0; i < bars.length; i++) {
            float barH = 3f + i * 1.5f;
            RenderUtil.rect(barsX + i * 2f, baseline - barH, 1f, barH, 0.5f,
                    withAlpha(bars[i] == 1 ? Color.WHITE : new Color(255, 255, 255, 45), 0.85f * ia));
        }

        if (band > 0.01f) drawIslandLyric(context, x, y, w, baseH, band, ia, lyric, lyricText);
        if (reveal > 0.01f) drawIslandControls(context, x, y, w, baseH, reveal, media, ia, band);
    }

    private static final float ISLAND_BUTTON_W = 26.0f;
    private static final float ISLAND_BUTTON_H = 12.0f;
    /** Compact transport row: the lyric band it used to share space with is now the island's own. */
    private static final float ISLAND_ROW_H = 16.0f;
    private static final float ISLAND_TITLE_H = 9.0f;

    /**
     * The lyric band under the pill: a line fades in on its timestamp and each word inside it
     * appears one by one across the line's window, so the row is sung, not dumped. It belongs to
     * the island itself rather than the editor panel, so it keeps singing after chat closes.
     */
    private void drawIslandLyric(DrawContext context, float x, float y, float w, float baseH,
                                 float band, float alpha, LyricsManager.Active lyric, String lyricText) {
        double posMs = MediaManager.positionSeconds() * 1000.0;
        float fade = lyricFade.to(lyricText.isEmpty() ? 0f : 1f);
        if (!lyricText.isEmpty() && !lyricText.equals(lyricShown)) {
            if (!lyricShown.isEmpty()) lyricFading = lyricShown;
            lyricShown = lyricText;
            lyricSlide.snap(0f);
        }
        if (lyricText.isEmpty() && fade < 0.02f) {
            lyricShown = "";
            lyricFading = "";
        }
        float slide = lyricSlide.to(1f);
        float lineAlpha = band * alpha * fade * (slide >= 0.999f ? 1f : slide);
        float rowSlide = slide >= 0.999f ? 0f : (1f - slide) * 5f;
        if (!lyricText.isEmpty() && lineAlpha > 0.01f) {
            drawLyricWords(context, lyricText, x, y + baseH + 1.5f * band + rowSlide, w, lineAlpha,
                    lyric.startMs(), lyric.endMs(), posMs, false);
        }
        if (!lyricFading.isEmpty() && slide < 0.999f && lineAlpha > 0.01f) {
            // The outgoing line wipes upward through the same window while the new one rises in.
            float fadingW = getTextWidth(lyricFading);
            Scissor.push(x + 6f, y + baseH, Math.max(w - 12f, 40f), ISLAND_TITLE_H + 3f);
            drawText(context, lyricFading, x + (w - fadingW) / 2f,
                    y + baseH + 1.5f * band - slide * 5f,
                    withAlpha(Color.WHITE, band * alpha * fade * (1f - slide)));
            Scissor.pop();
        } else if (slide >= 0.999f) {
            lyricFading = "";
        }
    }

    /**
     * Transport row under the label. Its width is derived from the live pill width, so a short
     * notification pulls the buttons in with it instead of leaving them past the pill's edges. A
     * button the player cannot honour is dimmed and inert, so a browser page never offers a dead "next".
     */
    private void drawIslandControls(DrawContext context, float x, float y, float w, float baseH, float reveal,
                                    MediaSession media, float alpha, float band) {
        float gap = 5.0f;
        float natural = ISLAND_BUTTON_W * 3 + gap * 2;
        float rowW = Math.min(natural, Math.max(w - 6f, ISLAND_BUTTON_W));
        float scale = rowW / natural;
        float buttonW = ISLAND_BUTTON_W * scale;
        float step = buttonW + gap * scale;
        float drawnRow = buttonW * 3 + gap * scale * 2;
        float startX = x + (w - drawnRow) / 2f;
        float rowH = ISLAND_ROW_H * reveal;
        // Buttons sit under the lyric band, which has its own height independent of the panel.
        float bandH = (ISLAND_TITLE_H + 3f) * band;
        // The buttons grow with the pill instead of appearing at full height while it is still
        // short: thin slivers that thicken into the transport row, matching the pill's own ease.
        float buttonH = ISLAND_BUTTON_H * reveal;
        float buttonY = y + baseH + bandH + (rowH - buttonH) / 2f;
        // 32px sources with no mip chain: below ~9px the strokes collapse into mush, so the glyph keeps
        // a floor and the button grows around it rather than the icon shrinking into noise.
        float glyph = Math.min(Math.max(11.0f * scale, 9.0f), buttonH);

        // SMTC reports the new state a beat after the command, which reads as a dead button. Hold the
        // pressed state until the session agrees, so the glyph flips in the same frame as the click.
        boolean playing = media.playing();
        if (islandPlayAt != 0L) {
            if (playing == islandPlayWanted || System.currentTimeMillis() - islandPlayAt > 1200L) islandPlayAt = 0L;
            else playing = islandPlayWanted;
        }

        Identifier[] icons = {ICON_MEDIA_PREV, playing ? ICON_MEDIA_PAUSE : ICON_MEDIA_PLAY, ICON_MEDIA_NEXT};
        MediaCommand[] commands = {MediaCommand.PREVIOUS,
                playing ? MediaCommand.PAUSE : MediaCommand.PLAY, MediaCommand.NEXT};
        boolean[] enabled = {media.canPrevious(), media.canPlayPause(), media.canNext()};

        for (int i = 0; i < 3; i++) {
            float press = islandPress[i].to(0f);
            float shrink = 1f - 0.16f * press;
            float bx = startX + i * step;
            float bw = buttonW * shrink;
            float bh = buttonH * shrink;
            float bxp = bx + (buttonW - bw) / 2f;
            float byp = buttonY + (buttonH - bh) / 2f;

            islandButtons[i * 4] = bx;
            islandButtons[i * 4 + 1] = buttonY;
            islandButtons[i * 4 + 2] = buttonW;
            islandButtons[i * 4 + 3] = buttonH;

            boolean hovered = enabled[i] && pointerInRect(bx, buttonY, buttonW, buttonH);
            drawPill(bxp, byp, bw, bh, 3.5f, reveal * alpha * (hovered ? 1.5f : 1f) + press * 0.4f);
            float gs = glyph * shrink;
            RenderUtil.texture(bxp + (bw - gs) / 2f, byp + (bh - gs) / 2f, gs, gs, icons[i], 0f,
                    withAlpha(enabled[i] ? Color.WHITE : new Color(150, 152, 168),
                            reveal * alpha * (enabled[i] ? (hovered ? 1f : 0.85f) : 0.4f)));

            if (hovered && HudDrag.wasLeftPressed()) {
                islandPress[i].snap(1f);
                if (i == 1) {
                    islandPlayWanted = !playing;
                    islandPlayAt = System.currentTimeMillis();
                }
                MediaManager.control(commands[i]);
            }
        }
    }

    /**
     * Karaoke reveal: lrclib times whole lines, so the words inside the active line are spread
     * evenly across its window and each fades in at its own share. A line without synced timing
     * (the no-lyrics fallback) draws whole at the row's own alpha.
     */
    private void drawLyricWords(DrawContext context, String text, float x, float y, float w, float alpha,
                                long startMs, long endMs, double posMs, boolean staticText) {
        String[] words = text.split(" ");
        float space = Math.max(getTextWidth("—") * 0.25f, 2f);
        float[] widths = new float[words.length];
        float total = 0f;
        for (int i = 0; i < words.length; i++) {
            widths[i] = getTextWidth(words[i]);
            total += widths[i] + (i > 0 ? space : 0f);
        }
        float winW = Math.max(w - 12f, 40f);
        boolean scroll = total > winW;
        float curX = scroll ? x + 6f - marqueeOffset(total, winW) : x + (w - total) / 2f;
        if (scroll) Scissor.push(x + 6f, y - 1f, winW, ISLAND_TITLE_H + 2f);
        double span = Math.max(1L, endMs - startMs);
        for (int i = 0; i < words.length; i++) {
            float wordAlpha = alpha;
            if (!staticText) {
                double wordStart = startMs + span * i / words.length;
                wordAlpha *= Math.clamp((posMs - wordStart) / 160.0, 0f, 1f);
            }
            if (wordAlpha > 0.01f && !words[i].isEmpty()) {
                drawText(context, words[i], curX, y, withAlpha(Color.WHITE, wordAlpha));
            }
            curX += widths[i] + space;
        }
        if (scroll) Scissor.pop();
    }

    private boolean pointerInRect(float x, float y, float w, float h) {
        return HudDrag.isEditing() && HudDrag.mouseX() >= x && HudDrag.mouseX() <= x + w
                && HudDrag.mouseY() >= y && HudDrag.mouseY() <= y + h;
    }

    /** Hover test against last frame's rects: this frame's are not laid out until after place(). */
    private boolean pointerOnIslandButtons() {
        for (int i = 0; i < 3; i++) {
            if (pointerInRect(islandButtons[i * 4], islandButtons[i * 4 + 1],
                    islandButtons[i * 4 + 2], islandButtons[i * 4 + 3])) return true;
        }
        return false;
    }

    /** Widest track label the island holds before the text starts scrolling through the window. */
    private static final float ISLAND_MUSIC_W = 130.0f;

    /**
     * Looping scroll for a label wider than its window, holding at each end so the first and last
     * words stay readable instead of whipping past.
     */
    private static float marqueeOffset(float textW, float windowW) {
        float travel = textW - windowW;
        if (travel <= 0f) return 0f;
        long holdMs = 1200L;
        long scrollMs = Math.max(1L, (long) (travel / 18f * 1000f));
        long cycle = holdMs * 2 + scrollMs;
        long at = System.currentTimeMillis() % cycle;
        if (at < holdMs) return 0f;
        if (at < holdMs + scrollMs) return (at - holdMs) / (float) scrollMs * travel;
        return travel;
    }

    private ClientBossBar bossBarForIsland() {
        if (mc.inGameHud == null || mc.inGameHud.getBossBarHud() == null) return null;
        for (ClientBossBar bar : ((IBossBarHud) mc.inGameHud.getBossBarHud()).minar$getBossBars().values()) {
            if (bar.getName() != null) return bar;
        }
        return null;
    }

    /** Server boss titles carry private-use font glyphs; strip them like the PvP bar parser does. */
    private static String plainText(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '\ue000' && c <= '\uf8ff') continue;
            sb.append(Character.isSpaceChar(c) ? ' ' : c);
        }
        return sb.toString().trim();
    }

    private static Color bossBarColor(BossBar.Color color) {
        return switch (color) {
            case PINK -> new Color(247, 157, 217);
            case BLUE -> new Color(101, 101, 255);
            case RED -> new Color(255, 62, 58);
            case GREEN -> new Color(133, 255, 110);
            case YELLOW -> new Color(255, 204, 0);
            case PURPLE -> new Color(165, 62, 255);
            case WHITE -> new Color(255, 255, 255);
        };
    }

    private NoticeItem newestIslandNotice() {
        NoticeItem newest = null;
        for (NoticeItem item : NOTICES) {
            if (item.type == NoticeType.INFO) continue;
            if (newest == null || item.created > newest.created) newest = item;
        }
        return newest;
    }

    private int[] pingBars() {
        int ping = -1;
        if (mc.player != null && mc.player.networkHandler != null) {
            var entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
            ping = entry != null ? entry.getLatency() : -1;
        }
        int[] bars = new int[4];
        for (int i = 0; i < bars.length; i++) {
            bars[i] = ping >= 0 && ping < ISLAND_PING_STEPS[i] ? 1 : 0;
        }
        return bars;
    }

    // --- Component 11: Notifications ---
    private void renderNotices(DrawContext context) {
        if (NOTICES.isEmpty()) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // Notices are pinned to the screen instead of a HudDrag, so they must opt out of the scale
        // the previously placed element left on the shared projection state.
        RenderUtil.resetUiScale();

        // Advanced even while the element is off, so queued notices still expire instead of
        // freezing at whatever alpha the toggle caught them at.
        noticesMotion.update(elements.isEnabled("Уведомления"), hudVisible(), false);
        float groupAlpha = noticesMotion.alpha();

        long now = System.currentTimeMillis();
        float slotY = screenH - 35.0f;

        for (NoticeItem item : NOTICES) {
            long age = now - item.created;
            boolean live = age <= item.duration;
            float life = Math.clamp(item.motion.to(live ? 1f : 0f), 0f, 1f);
            if (!live && life <= 0.01f) {
                NOTICES.remove(item);
                continue;
            }
            float a = life * groupAlpha;
            if (a <= 0.004f) {
                slotY -= 22.0f;
                continue;
            }

            float textW = getTextWidth(item.text);
            float w = textW + 24.0f;
            float h = 18.0f;

            if (item.slot.get() < 0f) item.slot.snap(slotY);
            float y = item.slot.to(slotY);
            float x = screenW - 10.0f - w + HudMotion.rowSlide(a);

            drawCard(x, y, w, h, 5.0f, 5.0f * a, a);

            Color iconCol = switch (item.type) {
                case SUCCESS -> new Color(46, 204, 113);
                case ERROR -> new Color(235, 35, 35);
                case WARN -> new Color(245, 166, 35);
                case INFO -> getAccentColor();
            };

            RenderUtil.rect(x + 4f, y + 4f, 10f, 10f, 2f, withAlpha(iconCol, a));
            drawText(context, item.text, x + 18f, y + 5.0f, withAlpha(Color.WHITE, a));

            // Progress bar
            float prog = 1.0f - Math.clamp((float) age / item.duration, 0.0f, 1.0f);
            RenderUtil.rect(x + 4f, y + h - 1.5f, (w - 8f) * prog, 1.0f, 0.5f, withAlpha(iconCol, a));

            slotY -= 22.0f * life;
        }
    }

    // --- External Compatibility Methods ---
    public static void pushModuleNotification(Function function) {
        if (Instance == null) return;
        boolean notices = Instance.elements.isEnabled("Уведомления");
        boolean island = Instance.elements.isEnabled("Остров");
        if (!notices && !island) return;
        boolean on = function.isEnabled();
        String name = function.getName().replace(" ", "");
        String msg = name + " " + (on ? "включен" : "выключен") + genderSuffix(name);
        Instance.addNotice(msg, on ? NoticeType.SUCCESS : NoticeType.ERROR);
    }

    // Rockstar agrees the verb ending with the last vowel of the module name.
    private static String genderSuffix(String name) {
        if (name.isEmpty()) return "";
        char last = Character.toLowerCase(name.charAt(name.length() - 1));
        if (last == 'а' || last == 'a') return "а";
        if (last == 'у' || last == 'ю' || last == 'y') return "о";
        if (last == 'ы' || last == 'и') return "ы";
        return "";
    }

    public void addNotice(String text) {
        addNotice(text, NoticeType.INFO, 2500L);
    }

    public void addNotice(String text, NoticeType type) {
        addNotice(text, type, 2500L);
    }

    public void addNotice(String text, NoticeType type, long duration) {
        NOTICES.add(new NoticeItem(text, type, duration));
    }

    public void pushNotice(String text, NoticeType type) {
        addNotice(text, type, 2500L);
    }

    public static boolean isIslandShowing() {
        return Instance != null && Instance.isEnabled() && Instance.elements.isEnabled("Остров");
    }

    public void renderElementPanel(Object element, float x, float y, float width, float height, float radius, float opacity) {
        int a = Math.round(opacity * 255f);
        RenderUtil.rect(x, y, width, height, radius, new Color(14, 15, 20, Math.min(255, Math.max(0, a))));
        RenderUtil.outline(x, y, width, height, radius, 0.75f, new Color(255, 255, 255, Math.min(50, Math.max(0, (int) (a * 0.2f)))));
    }

    public static boolean shouldDisableDefaultHotbar() {
        return Instance != null && Instance.isEnabled() && Instance.hotbar.isEnabled();
    }

    public static boolean shouldHideStatusBars() {
        return Instance != null && Instance.isEnabled() && Instance.hotbar.isEnabled();
    }

    public static boolean shouldDisableDefaultScoreboard() {
        return false;
    }

    public static boolean shouldHideDefaultStatusEffectIcons() {
        return Instance != null && Instance.isEnabled() && Instance.elements.isEnabled("Зелья");
    }

    public static float getHotbarShift() {
        if (Instance == null || !Instance.isEnabled() || !Instance.hotbar.isEnabled() || hotbarCurrentY < 0.0f) {
            return 0.0f;
        }
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float defY = screenH - 29.0f;
        return Math.max(0.0f, defY - hotbarCurrentY);
    }

    private void drawSkinFace(DrawContext context, PlayerEntity player, float x, float y, float size, float radius,
                              float popCx, float popCy, float popS) {
        RenderUtil.blur(x, y, size, size, radius, 4.0f);
        RenderUtil.rect(x, y, size, size, radius, COLOR_CARD_BG);
        Matrix3x2fStack matrices = context.getMatrices();
        HudRenderUtils.scaleAround(matrices, popCx, popCy, popS);
        try {
            Identifier texture = (player instanceof AbstractClientPlayerEntity clientPlayer)
                    ? clientPlayer.getSkin().body().texturePath()
                    : DefaultSkinHelper.getSkinTextures(player.getUuid()).body().texturePath();
            // Draw head base (8, 8, 8, 8 on 64x64 skin texture: u=8/64, v=8/64, w=8/64, h=8/64)
            RenderUtil.texture(x, y, size, size, texture, radius, Color.WHITE, 8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f);
            // Draw outer hat/layer (40, 8, 8, 8: u=40/64, v=8/64, w=8/64, h=8/64)
            RenderUtil.texture(x, y, size, size, texture, radius, Color.WHITE, 40f / 64f, 8f / 64f, 8f / 64f, 8f / 64f);
        } catch (Exception e) {
            RenderUtil.texture(x, y, size, size, ICON_PLAYER, radius, getAccentColor());
        }
        RenderUtil.outline(x, y, size, size, radius, 0.5f, COLOR_BORDER);
        HudRenderUtils.popMatrix(matrices);
    }

    @FunctionalInterface
    public interface HotbarItemRenderer {
        void render(int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed);
    }
}
