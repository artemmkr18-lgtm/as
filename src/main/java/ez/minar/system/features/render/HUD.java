package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.features.combat.TriggerBot;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import ez.minar.utils.helpers.KeyHelper;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfManager;
import ez.minar.utils.render.pipeline.TexturePipeline;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NewFunction(name = "HUD", desc = "Игровой интерфейс", category = Category.RENDER)
public class HUD extends Function {
    public static HUD Instance;
    MinecraftClient mc() { return mc; }

    // --- Frosted glass blur styling (matching ksnip_20260914-184759.png) ---
    public static final Color GLASS_BG = new Color(24, 28, 38, 165);
    public static final Color GLASS_OUTLINE = new Color(255, 255, 255, 14);
    public static final float GLASS_BLUR = 14.0f;
    public static final float CARD_RADIUS = 8.0f;
    public static final float PILL_RADIUS = 10.0f;

    // Legacy color aliases for external compatibility (NameTags, etc.)
    public static final Color PANEL_BG = GLASS_BG;
    public static final Color CHIP_BG = new Color(0xB815161A, true);
    public static final Color CHIP_DARKER = new Color(0xA015161A, true);
    public static final Color CHIP_INNER = new Color(0x06000000, true);
    public static final Color BORDER_SOFT = GLASS_OUTLINE;
    public static final Color BORDER_FADE = new Color(0x03FFFFFF, true);
    public static final Color TEXT_MUTED = new Color(255, 255, 255, 120);

    public static final Color TEXT_SEP = new Color(255, 255, 255, 75);
    public static final Color TEXT_CORAL = new Color(255, 115, 115);
    public static final Color COLOR_ONLINE = new Color(119, 254, 130);
    public static final Color COLOR_OFFLINE = new Color(253, 116, 115);
    public static final Color COLOR_GOLD = new Color(245, 175, 45);

    // --- Texture Icons ---
    private static final Identifier ICON_USER = Identifier.of("minar", "icons/083_lucide_user.png");
    private static final Identifier ICON_GAMEPAD = Identifier.of("minar", "icons/gamepad_retro.png");
    private static final Identifier ICON_CLOCK = Identifier.of("minar", "icons/246_tabler_clock.png");
    private static final Identifier ICON_SERVER = Identifier.of("minar", "icons/159_lucide_server.png");
    private static final Identifier ICON_GLOBE = Identifier.of("minar", "icons/175_lucide_globe.png");
    private static final Identifier ICON_SPEED = Identifier.of("minar", "icons/098_lucide_gauge.png");
    private static final Identifier ICON_KEYBOARD = Identifier.of("minar", "icons/147_lucide_keyboard.png");
    private static final Identifier ICON_FLASK = Identifier.of("minar", "icons/166_lucide_flask_conical.png");
    private static final Identifier ICON_HEART = Identifier.of("minar", "icons/heart_crack.png");
    private static final Identifier ICON_STAFF = Identifier.of("minar", "icons/user_cog.png");
    private static final Identifier ICON_STRENGTH = Identifier.of("minar", "icons/hud_figma/strength.png");
    private static final Identifier ICON_POTION_SPEED = Identifier.of("minar", "icons/hud_figma/potion_speed.png");
    private static final Identifier ICON_CHECK = Identifier.of("minar", "icons/004_lucide_check.png");
    private static final Identifier ICON_X = Identifier.of("minar", "icons/003_lucide_x.png");

    // --- Settings ---
    public final MultiListSetting elements = new MultiListSetting("Элементы",
            "Инфо-панель", "Кейбинды", "Зелья", "TargetHUD", "Staff Online", "Уведомления");
    public final BooleanSetting hotbar = new BooleanSetting("Хотбар", true);
    public final BooleanSetting notifications = new BooleanSetting("Уведомления", true);

    // --- Draggable Anchors ---
    final HudDrag dragInfo = new HudDrag("Инфо-панель", 6, 6);
    final HudDrag dragBinds = new HudDrag("Кейбинды", 6, 60);
    final HudDrag dragPotions = new HudDrag("Зелья", 6, 150);
    final HudDrag dragTargetHud = new HudDrag("TargetHUD", 240, 220);
    final HudDrag dragStaff = new HudDrag("Staff Online", 6, 250);

    private final Map<String, HudDrag> positions = new LinkedHashMap<>();

    // --- Animations ---
    final XyAnim bindsAnim = new XyAnim(250f);
    final XyAnim potionsAnim = new XyAnim(250f);
    final XyAnim targetAnim = new XyAnim(250f);
    final XyAnim staffAnim = new XyAnim(250f);

    private float delta = 1f / 60f;
    private long lastFrame = 0;

    // --- Target HUD state ---
    private LivingEntity lastTarget;
    private String lastTargetName = "";
    private float smoothHp = 20f;
    private float smoothAbsorption = 0f;

    // --- Staff Rank Prefixes and Colors ---
    private static final Map<String, Color> STAFF_RANKS = new LinkedHashMap<>();
    static {
        STAFF_RANKS.put("YOUTUBE", new Color(0xE5, 0x4D, 0x42));
        STAFF_RANKS.put("YT", new Color(0xE5, 0x4D, 0x42));
        STAFF_RANKS.put("ML MODER", new Color(0x9E, 0x78, 0xE6));
        STAFF_RANKS.put("ST.MODER", new Color(0x79, 0x50, 0xF2));
        STAFF_RANKS.put("JR.MODER", new Color(0xB1, 0x97, 0xFC));
        STAFF_RANKS.put("MODER", new Color(0x9E, 0x78, 0xE6));
        STAFF_RANKS.put("МОДЕР", new Color(0x9E, 0x78, 0xE6));
        STAFF_RANKS.put("ADMIN", new Color(0xE0, 0x31, 0x31));
        STAFF_RANKS.put("АДМИН", new Color(0xE0, 0x31, 0x31));
        STAFF_RANKS.put("HELPER", new Color(0x4D, 0xAB, 0xF7));
        STAFF_RANKS.put("ХЕЛПЕР", new Color(0x4D, 0xAB, 0xF7));
        STAFF_RANKS.put("CURATOR", new Color(0xFF, 0xA9, 0x4D));
        STAFF_RANKS.put("КУРАТОР", new Color(0xFF, 0xA9, 0x4D));
        STAFF_RANKS.put("DEV", new Color(0x51, 0xCF, 0x66));
        STAFF_RANKS.put("РАЗРАБ", new Color(0x51, 0xCF, 0x66));
        STAFF_RANKS.put("OWNER", new Color(0xFF, 0x6B, 0x6B));
        STAFF_RANKS.put("ВЛАДЕЛЕЦ", new Color(0xFF, 0x6B, 0x6B));
        STAFF_RANKS.put("STAFF", new Color(0x74, 0xC0, 0xFC));
        STAFF_RANKS.put("СТАЖЕР", new Color(0x74, 0xC0, 0xFC));
    }

    // --- Notifications ---
    public enum NoticeType {
        ENABLE, DISABLE, WARNING, INFO
    }

    public static final class NoticeItem {
        public final String text;
        public final NoticeType type;
        public final long created;
        public final long duration;
        public final XyAnim anim = new XyAnim(300f);
        public float x, y;
        public boolean posInit = false;

        NoticeItem(String text, NoticeType type) {
            this(text, type, 1500L);
        }

        NoticeItem(String text, NoticeType type, long duration) {
            this.text = text;
            this.type = type;
            this.duration = duration;
            this.created = System.currentTimeMillis();
            this.anim.setDirection(1);
        }
    }

    private final List<NoticeItem> activeNotices = new ArrayList<>();
    DrawContext drawContext;

    public HUD() {
        Instance = this;
        addSettings(elements, hotbar, notifications);

        positions.put("watermark", dragInfo);
        positions.put("binds", dragBinds);
        positions.put("potions", dragPotions);
        positions.put("targethud", dragTargetHud);
        positions.put("staff", dragStaff);

        positions.values().forEach(drag -> addSettings(drag.settings()));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        bindsAnim.reset();
        potionsAnim.reset();
        targetAnim.reset();
        staffAnim.reset();
        activeNotices.clear();
        previewNotice = null;
        smoothHp = 20f;
        smoothAbsorption = 0f;
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        drawContext = event.getContext();
        if (drawContext == null || mc.player == null || mc.world == null) return;

        MsdfManager.init();

        long now = System.nanoTime();
        delta = lastFrame == 0 ? 1f / 60f : Math.clamp((now - lastFrame) / 1_000_000_000f, 0.001f, 0.1f);
        lastFrame = now;

        boolean chat = mc.currentScreen instanceof ChatScreen;

        HudDrag.beginFrame();
        RenderUtil.resetUiScale();

        try {
            if (elements.isEnabled("Инфо-панель") || elements.isEnabled("Ватермарка")) {
                renderInfoPills();
            }
            if (elements.isEnabled("Кейбинды")) {
                renderBinds(chat);
            }
            if (elements.isEnabled("Зелья")) {
                renderPotions(chat);
            }
            if (elements.isEnabled("TargetHUD")) {
                renderTargetHud(chat);
            }
            if (elements.isEnabled("Staff Online")) {
                renderStaffOnline(chat);
            }
            if (notifications.isEnabled() && elements.isEnabled("Уведомления")) {
                renderNotices(chat);
            }
        } finally {
            RenderUtil.resetUiScale();
            HudDrag.drawGrid(drawContext);
            HudDrag.endFrame();
        }
    }

    // ==========================================
    // Unified Glass Renderer
    // ==========================================
    private void renderGlassCard(float x, float y, float width, float height, float radius, float alpha) {
        if (alpha <= 0.01f || width <= 0f || height <= 0f) return;
        RenderUtil.blur(x, y, width, height, radius, GLASS_BLUR);
        RenderUtil.rect(x, y, width, height, radius, withAlpha(GLASS_BG, alpha));
        RenderUtil.outline(x, y, width, height, radius, 0.5f, withAlpha(GLASS_OUTLINE, alpha));
    }

    private static Color withAlpha(Color color, float a) {
        int alpha = Math.clamp(Math.round(color.getAlpha() * Math.clamp(a, 0f, 1f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private float fast(float current, float target, float speed) {
        return current + (target - current) * Math.min(1f, speed * delta);
    }

    // ==========================================
    // 1. Info / Status Capsules (ksnip_20260913-211006.png)
    // ==========================================
    private void renderInfoPills() {
        String username = mc.player.getName().getString();
        String fpsNum = String.valueOf(mc.getCurrentFps());
        Calendar cal = Calendar.getInstance();
        String time = String.format(Locale.ROOT, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

        float tpsValue = mc.world != null ? mc.world.getTickManager().getTickRate() : 20.0f;
        String tps = String.format(Locale.ROOT, "%.1f", (double) tpsValue).replace('.', ',');

        int posX = (int) Math.floor(mc.player.getX());
        int posY = (int) Math.floor(mc.player.getY());
        int posZ = (int) Math.floor(mc.player.getZ());

        double bps = calcBps();
        String speed = String.format(Locale.ROOT, "%.2f", bps).replace('.', ',');

        float pad = 9f;
        float iconSize = 9.5f;
        float h = 20f;
        float r = 10f;
        float sepW = Msdf.width(Msdf.SF_REGULAR, "/", 8f);
        float arrowW = Msdf.width(Msdf.SF_REGULAR, "»", 7.5f);

        // --- Pill 1: [user] User / [gamepad] FPS fps / [clock] Time ---
        float userW = Msdf.width(Msdf.SF_BOLD, username, 8f);
        float fpsValW = Msdf.width(Msdf.SF_BOLD, fpsNum, 8f);
        float fpsLabelW = Msdf.width(Msdf.SF_REGULAR, "fps", 7.5f);
        float timeW = Msdf.width(Msdf.SF_BOLD, time, 8f);

        float pill1W = pad + iconSize + 4f + userW + 6f + sepW + 6f
                + iconSize + 4f + fpsValW + 2.5f + fpsLabelW + 6f + sepW + 6f
                + iconSize + 4f + timeW + pad;

        // --- Pill 2: [server] / TPS tps ---
        float tpsValW = Msdf.width(Msdf.SF_BOLD, tps, 8f);
        float tpsLabelW = Msdf.width(Msdf.SF_REGULAR, "tps", 7.5f);
        float pill2W = pad + iconSize + 6f + sepW + 6f + tpsValW + 2.5f + tpsLabelW + pad;

        // --- Pill 3: [globe] x X » y Y » z Z ---
        String sx = String.valueOf(posX), sy = String.valueOf(posY), sz = String.valueOf(posZ);
        float coordW = Msdf.width(Msdf.SF_REGULAR, "x", 7.5f) + 3f + Msdf.width(Msdf.SF_BOLD, sx, 8f) + 5f + arrowW + 5f
                + Msdf.width(Msdf.SF_REGULAR, "y", 7.5f) + 3f + Msdf.width(Msdf.SF_BOLD, sy, 8f) + 5f + arrowW + 5f
                + Msdf.width(Msdf.SF_REGULAR, "z", 7.5f) + 3f + Msdf.width(Msdf.SF_BOLD, sz, 8f);
        float pill3W = pad + iconSize + 5f + coordW + pad;

        // --- Pill 4: [gauge] » Speed b/s ---
        float spdValW = Msdf.width(Msdf.SF_BOLD, speed, 8f);
        float spdLabelW = Msdf.width(Msdf.SF_REGULAR, "b/s", 7.5f);
        float pill4W = pad + iconSize + 5f + arrowW + 5f + spdValW + 2.5f + spdLabelW + pad;

        float totalW = Math.max(pill1W + 6f + pill2W, pill3W + 6f + pill4W);
        float totalH = 44f;

        float[] p = dragInfo.place(totalW, totalH);
        float x = p[0], y = p[1];

        // --- Render Row 1: Pill 1 ---
        renderGlassCard(x, y, pill1W, h, r, 1f);
        float cx = x + pad;
        RenderUtil.texture(cx, y + 5.25f, iconSize, ICON_USER, 0f, Color.WHITE);
        cx += iconSize + 4f;
        Msdf.text(drawContext, Msdf.SF_BOLD, username, cx, y + 6f, 8f, Color.WHITE.getRGB(), false);
        cx += userW + 6f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "/", cx, y + 6f, 8f, TEXT_SEP.getRGB(), false);
        cx += sepW + 6f;
        RenderUtil.texture(cx, y + 5.25f, iconSize, ICON_GAMEPAD, 0f, Color.WHITE);
        cx += iconSize + 4f;
        Msdf.text(drawContext, Msdf.SF_BOLD, fpsNum, cx, y + 6f, 8f, Color.WHITE.getRGB(), false);
        cx += fpsValW + 2.5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "fps", cx, y + 6f, 7.5f, TEXT_MUTED.getRGB(), false);
        cx += fpsLabelW + 6f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "/", cx, y + 6f, 8f, TEXT_SEP.getRGB(), false);
        cx += sepW + 6f;
        RenderUtil.texture(cx, y + 5.25f, iconSize, ICON_CLOCK, 0f, Color.WHITE);
        cx += iconSize + 4f;
        Msdf.text(drawContext, Msdf.SF_BOLD, time, cx, y + 6f, 8f, Color.WHITE.getRGB(), false);

        // --- Render Row 1: Pill 2 ---
        float p2x = x + pill1W + 6f;
        renderGlassCard(p2x, y, pill2W, h, r, 1f);
        float p2cx = p2x + pad;
        RenderUtil.texture(p2cx, y + 5.25f, iconSize, ICON_SERVER, 0f, Color.WHITE);
        p2cx += iconSize + 6f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "/", p2cx, y + 6f, 8f, TEXT_SEP.getRGB(), false);
        p2cx += sepW + 6f;
        Msdf.text(drawContext, Msdf.SF_BOLD, tps, p2cx, y + 6f, 8f, Color.WHITE.getRGB(), false);
        p2cx += tpsValW + 2.5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "tps", p2cx, y + 6f, 7.5f, TEXT_MUTED.getRGB(), false);

        // --- Render Row 2: Pill 3 ---
        float r2y = y + 24f;
        renderGlassCard(x, r2y, pill3W, h, r, 1f);
        float p3cx = x + pad;
        RenderUtil.texture(p3cx, r2y + 5.25f, iconSize, ICON_GLOBE, 0f, Color.WHITE);
        p3cx += iconSize + 5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "x", p3cx, r2y + 6f, 7.5f, TEXT_MUTED.getRGB(), false);
        p3cx += Msdf.width(Msdf.SF_REGULAR, "x", 7.5f) + 3f;
        Msdf.text(drawContext, Msdf.SF_BOLD, sx, p3cx, r2y + 6f, 8f, Color.WHITE.getRGB(), false);
        p3cx += Msdf.width(Msdf.SF_BOLD, sx, 8f) + 5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "»", p3cx, r2y + 6f, 7.5f, TEXT_SEP.getRGB(), false);
        p3cx += arrowW + 5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "y", p3cx, r2y + 6f, 7.5f, TEXT_MUTED.getRGB(), false);
        p3cx += Msdf.width(Msdf.SF_REGULAR, "y", 7.5f) + 3f;
        Msdf.text(drawContext, Msdf.SF_BOLD, sy, p3cx, r2y + 6f, 8f, Color.WHITE.getRGB(), false);
        p3cx += Msdf.width(Msdf.SF_BOLD, sy, 8f) + 5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "»", p3cx, r2y + 6f, 7.5f, TEXT_SEP.getRGB(), false);
        p3cx += arrowW + 5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "z", p3cx, r2y + 6f, 7.5f, TEXT_MUTED.getRGB(), false);
        p3cx += Msdf.width(Msdf.SF_REGULAR, "z", 7.5f) + 3f;
        Msdf.text(drawContext, Msdf.SF_BOLD, sz, p3cx, r2y + 6f, 8f, Color.WHITE.getRGB(), false);

        // --- Render Row 2: Pill 4 ---
        float p4x = x + pill3W + 6f;
        renderGlassCard(p4x, r2y, pill4W, h, r, 1f);
        float p4cx = p4x + pad;
        RenderUtil.texture(p4cx, r2y + 5.25f, iconSize, ICON_SPEED, 0f, Color.WHITE);
        p4cx += iconSize + 5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "»", p4cx, r2y + 6f, 7.5f, TEXT_SEP.getRGB(), false);
        p4cx += arrowW + 5f;
        Msdf.text(drawContext, Msdf.SF_BOLD, speed, p4cx, r2y + 6f, 8f, Color.WHITE.getRGB(), false);
        p4cx += spdValW + 2.5f;
        Msdf.text(drawContext, Msdf.SF_REGULAR, "b/s", p4cx, r2y + 6f, 7.5f, TEXT_MUTED.getRGB(), false);
    }

    private double lastPosX = Double.NaN, lastPosZ = Double.NaN;
    private double currentBps = 0.0;

    private double calcBps() {
        if (mc.player == null) return 0.0;
        double currentX = mc.player.getX();
        double currentZ = mc.player.getZ();
        if (!Double.isNaN(lastPosX) && !Double.isNaN(lastPosZ)) {
            double dx = currentX - lastPosX;
            double dz = currentZ - lastPosZ;
            double instBps = delta > 0.0001f ? Math.hypot(dx, dz) / delta : 0.0;
            currentBps = fast((float) currentBps, (float) instBps, 8f);
        }
        lastPosX = currentX;
        lastPosZ = currentZ;
        return currentBps;
    }

    // ==========================================
    // 2. Keybinds (ksnip_20260914-184759.png)
    // ==========================================
    private record BindItem(String name, String key) {}

    private void renderBinds(boolean chat) {
        List<BindItem> items = new ArrayList<>();
        for (Function f : FunctionManager.getFunctions()) {
            if (f == this || f.getKeybind() == 0) continue;
            if (f.isEnabled()) {
                items.add(new BindItem(f.getDisplayName(), KeyHelper.getKeyName(f.getKeybind())));
            }
        }
        if (items.isEmpty() && chat) {
            items.add(new BindItem(ez.minar.system.managers.LocalizationManager.get("Module example"), "R"));
            items.add(new BindItem(ez.minar.system.managers.LocalizationManager.get("Module example 2"), "Z"));
        }

        bindsAnim.setDirection(items.isEmpty() ? -1 : 1);
        float anim = bindsAnim.getOutput();
        if (anim < 0.01f && items.isEmpty()) {
            dragBinds.cancel();
            return;
        }

        float maxW = 140f;
        for (BindItem item : items) {
            float lineW = Msdf.width(Msdf.SF_REGULAR, item.name, 8.5f)
                    + Msdf.width(Msdf.SF_BOLD, item.key, 8.5f) + 36f;
            if (lineW > maxW) maxW = lineW;
        }

        float cardW = maxW;
        float cardH = 22f + items.size() * 16f + 6f;

        float[] p = dragBinds.place(cardW, cardH);
        float x = p[0], y = p[1];

        renderGlassCard(x, y, cardW, cardH, CARD_RADIUS, anim);

        // Header
        Msdf.text(drawContext, Msdf.SF_BOLD, ez.minar.system.managers.LocalizationManager.get("hud.keybinds"), x + 10f, y + 8f, 9f,
                withAlpha(Color.WHITE, anim).getRGB(), false);
        RenderUtil.texture(x + cardW - 10f - 11f, y + 7.5f, 11f, ICON_KEYBOARD, 0f,
                withAlpha(Color.WHITE, anim));

        // Rows
        float ry = y + 24f;
        for (BindItem item : items) {
            Msdf.text(drawContext, Msdf.SF_REGULAR, item.name, x + 10f, ry, 8.5f,
                    withAlpha(Color.WHITE, anim).getRGB(), false);
            RenderUtil.text(drawContext, Msdf.SF_BOLD, x + cardW - 10f, ry, item.key, 8.5f,
                    withAlpha(Color.WHITE, anim), "right");
            ry += 16f;
        }
    }

    // ==========================================
    // 3. Active Potions (ksnip_20260914-184854.png)
    // ==========================================
    private record PotionEntry(String name, String level, String duration, Identifier icon, RegistryEntry<StatusEffect> effectType) {
        PotionEntry(String name, String level, String duration, Identifier icon) {
            this(name, level, duration, icon, null);
        }
    }

    private void renderPotions(boolean chat) {
        List<PotionEntry> items = new ArrayList<>();
        if (mc.player != null) {
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                String rawName = effect.getEffectType().value().getName().getString();
                String cleanName = parseRoman(rawName);
                int amp = effect.getAmplifier() + 1;
                String time = StatusEffectUtil.getDurationText(effect, 1.0f,
                        mc.world != null ? mc.world.getTickManager().getTickRate() : 20.0f).getString();
                if (time.length() > 5 || time.length() < 2) time = "--:--";
                items.add(new PotionEntry(cleanName, String.valueOf(amp), time, null, effect.getEffectType()));
            }
        }
        if (items.isEmpty() && chat) {
            items.add(new PotionEntry(ez.minar.system.managers.LocalizationManager.get("Сила"), "2", "0:30", ICON_STRENGTH));
            items.add(new PotionEntry(ez.minar.system.managers.LocalizationManager.get("Скорость"), "1", "1:25", ICON_POTION_SPEED));
        }

        potionsAnim.setDirection(items.isEmpty() ? -1 : 1);
        float anim = potionsAnim.getOutput();
        if (anim < 0.01f && items.isEmpty()) {
            dragPotions.cancel();
            return;
        }

        float maxW = 145f;
        for (PotionEntry item : items) {
            float lineW = 12f + 6f + Msdf.width(Msdf.SF_REGULAR, item.name, 8.5f)
                    + Msdf.width(Msdf.SF_BOLD, " " + item.level, 8.5f)
                    + Msdf.width(Msdf.SF_REGULAR, item.duration, 8.5f) + 32f;
            if (lineW > maxW) maxW = lineW;
        }

        float cardW = maxW;
        float cardH = 22f + items.size() * 18f + 6f;

        float[] p = dragPotions.place(cardW, cardH);
        float x = p[0], y = p[1];

        renderGlassCard(x, y, cardW, cardH, CARD_RADIUS, anim);

        // Header
        Msdf.text(drawContext, Msdf.SF_BOLD, ez.minar.system.managers.LocalizationManager.get("hud.potions"), x + 10f, y + 8f, 9f,
                withAlpha(Color.WHITE, anim).getRGB(), false);
        RenderUtil.texture(x + cardW - 10f - 11f, y + 7.5f, 11f, ICON_FLASK, 0f,
                withAlpha(Color.WHITE, anim));

        // Rows
        float ry = y + 24f;
        for (PotionEntry item : items) {
            if (item.effectType != null) {
                Identifier sprite = InGameHud.getEffectTexture(item.effectType);
                if (sprite != null) {
                    float dcs = RenderUtil.getDrawContextScale();
                    var matrices = drawContext.getMatrices();
                    matrices.pushMatrix();
                    matrices.translate((x + 10f) / dcs, (ry + 1.5f) / dcs);
                    float scale = 12f / 18f;
                    matrices.scale(scale, scale);
                    drawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 18, 18, withAlpha(Color.WHITE, anim).getRGB());
                    matrices.popMatrix();
                }
            } else if (item.icon != null) {
                RenderUtil.texture(x + 10f, ry + 1.5f, 12f, item.icon, 0f, withAlpha(Color.WHITE, anim));
            }
            float textX = x + 26f;
            Msdf.text(drawContext, Msdf.SF_REGULAR, item.name, textX, ry + 1f, 8.5f,
                    withAlpha(Color.WHITE, anim).getRGB(), false);
            float nameW = Msdf.width(Msdf.SF_REGULAR, item.name, 8.5f);
            Msdf.text(drawContext, Msdf.SF_BOLD, " " + item.level, textX + nameW, ry + 1f, 8.5f,
                    withAlpha(TEXT_CORAL, anim).getRGB(), false);

            RenderUtil.text(drawContext, Msdf.SF_REGULAR, x + cardW - 10f, ry + 1f, item.duration, 8.5f,
                    withAlpha(Color.WHITE, anim), "right");

            ry += 18f;
        }
    }

    private static final Pattern ROMAN_PATTERN = Pattern.compile("\\s+[IVXLCDM]+\\b");
    private String parseRoman(String text) {
        Matcher matcher = ROMAN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.replaceFirst("").trim();
        }
        return text;
    }

    // ==========================================
    // 4. TargetHUD (ksnip_20260914-184903.png)
    // ==========================================
    private void renderTargetHud(boolean chat) {
        LivingEntity target = findTarget();
        boolean isDemo = false;
        if (target != null) {
            targetAnim.setDirection(1);
            lastTarget = target;
            lastTargetName = target.getName().getString();
        } else if (chat && mc.player != null) {
            targetAnim.setDirection(1);
            isDemo = true;
            if (lastTarget == null) {
                lastTarget = mc.player;
                lastTargetName = "SqwezZ";
            }
        } else {
            targetAnim.setDirection(-1);
        }

        float anim = targetAnim.getOutput();
        if (anim < 0.01f && targetAnim.direction < 0) {
            lastTarget = null;
            dragTargetHud.cancel();
            return;
        }
        if (lastTarget == null) {
            dragTargetHud.cancel();
            return;
        }

        LivingEntity t = lastTarget;
        if (!t.isAlive() && !isDemo) {
            t = mc.player != null ? mc.player : t;
        }

        float cardW = 155f;
        float cardH = 38f;
        float slotSize = 15f;
        float slotGap = 3f;
        float slotsTotalW = 6 * slotSize + 5 * slotGap; // 105f

        // Bounding box includes slots above card
        float totalH = cardH + 18f;
        float[] p = dragTargetHud.place(cardW, totalH);
        float x = p[0];
        float slotY = p[1];
        float cardY = slotY + 18f;

        // --- 6 Equipment slots centered above main card ---
        float slotStartX = x + (cardW - slotsTotalW) / 2f;
        ItemStack[] stacks = new ItemStack[6];
        if (isDemo) {
            stacks[0] = Items.DIAMOND_HELMET.getDefaultStack();
            stacks[1] = Items.DIAMOND_CHESTPLATE.getDefaultStack();
            stacks[2] = ItemStack.EMPTY; // Empty leggings -> dash "-"
            stacks[3] = Items.DIAMOND_BOOTS.getDefaultStack();
            stacks[4] = Items.DIAMOND_SWORD.getDefaultStack();
            stacks[5] = Items.TOTEM_OF_UNDYING.getDefaultStack();
        } else {
            stacks[0] = t.getEquippedStack(EquipmentSlot.HEAD);
            stacks[1] = t.getEquippedStack(EquipmentSlot.CHEST);
            stacks[2] = t.getEquippedStack(EquipmentSlot.LEGS);
            stacks[3] = t.getEquippedStack(EquipmentSlot.FEET);
            stacks[4] = t.getMainHandStack();
            stacks[5] = t.getOffHandStack();
        }

        for (int i = 0; i < 6; i++) {
            float sx = slotStartX + i * (slotSize + slotGap);
            renderGlassCard(sx, slotY, slotSize, slotSize, 4f, anim);
            renderSlotItem(stacks[i], sx, slotY, anim);
        }

        // --- Main Card ---
        renderGlassCard(x, cardY, cardW, cardH, CARD_RADIUS, anim);

        // Avatar
        float avatarX = x + 6f, avatarY = cardY + 6f, avatarSize = 26f;
        Color headColor = withAlpha(Color.WHITE, anim);
        if (t.hurtTime > 0) {
            int f = Math.min(180, (int) (t.hurtTime / 10f * 90f));
            headColor = new Color(255, 255 - f, 255 - f, headColor.getAlpha());
        }

        if (t instanceof PlayerEntity player) {
            drawSkinFace(player, avatarX, avatarY, avatarSize, 4.5f, headColor);
        } else {
            RenderUtil.rect(avatarX, avatarY, avatarSize, avatarSize, 4.5f,
                    withAlpha(new Color(40, 45, 55), anim));
            Msdf.text(drawContext, Msdf.SF_BOLD, "?", avatarX + 9f, avatarY + 7f, 12f,
                    withAlpha(Color.WHITE, anim).getRGB(), false);
        }

        // Vertical divider between avatar and info
        RenderUtil.rect(x + 35f, cardY + 5f, 0.5f, cardH - 10f, 0f,
                withAlpha(new Color(255, 255, 255, 18), anim));

        // Target Name & Health
        String name = isDemo ? "SqwezZ" : lastTargetName;
        Msdf.text(drawContext, Msdf.SF_BOLD, name, x + 39f, cardY + 7f, 9.5f,
                withAlpha(Color.WHITE, anim).getRGB(), false);

        float targetHp = isDemo ? 10f : t.getHealth();
        float targetMaxHp = isDemo ? 20f : Math.max(1f, t.getMaxHealth());
        float targetAbs = isDemo ? 8.2f : t.getAbsorptionAmount();

        smoothHp = fast(smoothHp, targetHp, 10f);
        smoothAbsorption = fast(smoothAbsorption, targetAbs, 10f);

        int displayHp = isDemo ? 20 : (int) Math.ceil(targetHp + targetAbs);
        String hpStr = String.valueOf(displayHp);
        float hpW = Msdf.width(Msdf.SF_BOLD, hpStr, 9.5f);
        float heartSize = 8f;

        float heartX = x + cardW - 8f - heartSize;
        float hpTextX = heartX - 3.5f - hpW;

        Msdf.text(drawContext, Msdf.SF_BOLD, hpStr, hpTextX, cardY + 7f, 9.5f,
                withAlpha(Color.WHITE, anim).getRGB(), false);
        RenderUtil.texture(heartX, cardY + 7.5f, heartSize, ICON_HEART, 0f,
                withAlpha(Color.WHITE, anim));

        // --- Health Bar ---
        float trackX = x + 38f;
        float trackY = cardY + 23f;
        float trackW = cardW - 46f;
        float trackH = 6f;

        RenderUtil.rect(trackX, trackY, trackW, trackH, 3f,
                withAlpha(new Color(15, 18, 24, 190), anim));

        float maxPool = Math.max(targetMaxHp, targetHp + targetAbs);
        float healthRatio = Math.clamp(smoothHp / maxPool, 0f, 1f);
        float absRatio = Math.clamp(smoothAbsorption / maxPool, 0f, 1f);
        float healthBarW = healthRatio * trackW;
        float absBarW = absRatio * trackW;

        if (healthBarW > 1f) {
            RenderUtil.rect(trackX, trackY, healthBarW, trackH, 3f,
                    withAlpha(new Color(180, 185, 195), anim), withAlpha(Color.WHITE, anim));
        }
        if (absBarW > 1f) {
            float absX = trackX + trackW - absBarW;
            RenderUtil.rect(absX, trackY, absBarW, trackH, 3f,
                    withAlpha(new Color(248, 180, 50), anim), withAlpha(new Color(210, 150, 45), anim));
        }
    }

    private void renderSlotItem(ItemStack item, float x, float y, float alpha) {
        if (item.isEmpty()) {
            RenderUtil.text(drawContext, Msdf.SF_BOLD, x + 7.5f, y + 3.5f, "-", 8f,
                    withAlpha(new Color(255, 255, 255, 140), alpha), "center");
            return;
        }
        float dcs = RenderUtil.getDrawContextScale();
        var matrices = drawContext.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x / dcs, y / dcs);
        float itemScale = 14f / 16f;
        matrices.scale(itemScale, itemScale);
        drawContext.drawItem(item, 1, 1);
        matrices.popMatrix();
    }

    private LivingEntity findTarget() {
        if (mc.currentScreen instanceof ChatScreen) return null;
        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() != null && aura.getTarget().isAlive()) {
            return aura.getTarget();
        }
        TriggerBot triggerBot = FunctionManager.getFunction(TriggerBot.class);
        if (triggerBot != null && triggerBot.isEnabled() && triggerBot.getTarget() != null
                && triggerBot.getTarget().isAlive()) {
            return triggerBot.getTarget();
        }
        return null;
    }

    private void drawSkinFace(PlayerEntity player, float x, float y, float size, float radius, Color color) {
        Identifier skinPath = null;
        try {
            skinPath = player instanceof net.minecraft.client.network.AbstractClientPlayerEntity clientPlayer
                    ? clientPlayer.getSkin().body().texturePath()
                    : DefaultSkinHelper.getSkinTextures(player.getUuid()).body().texturePath();
        } catch (Exception ignored) {}
        drawSkinFace(skinPath, x, y, size, radius, color);
    }

    private void drawSkinFace(Identifier skinPath, float x, float y, float size, float radius, Color color) {
        try {
            if (skinPath == null) {
                RenderUtil.rect(x, y, size, size, radius, new Color(45, 45, 55));
                return;
            }
            var texture = mc.getTextureManager().getTexture(skinPath).getGlTextureView();
            TexturePipeline.draw(RenderUtil.createProjection(), x, y, size, texture, color.getRGB(), radius, 0f,
                    8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, false);
            TexturePipeline.draw(RenderUtil.createProjection(), x, y, size, texture, color.getRGB(), radius, 0f,
                    40f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, false);
        } catch (Exception e) {
            RenderUtil.rect(x, y, size, size, radius, new Color(45, 45, 55));
        }
    }

    // ==========================================
    // 5. Staff Online (ksnip_20260914-184918.png)
    // ==========================================
    private record StaffMember(String name, String prefix, Color prefixColor, boolean online, Identifier skin) {}

    private void renderStaffOnline(boolean chat) {
        List<StaffMember> members = getOnlineStaff(chat);

        staffAnim.setDirection(members.isEmpty() ? -1 : 1);
        float anim = staffAnim.getOutput();
        if (anim < 0.01f && members.isEmpty()) {
            dragStaff.cancel();
            return;
        }

        float maxW = 150f;
        for (StaffMember m : members) {
            float lineW = 11f + 6f + Msdf.width(Msdf.SF_BOLD, m.prefix, 8.5f)
                    + Msdf.width(Msdf.SF_BOLD, " " + m.name, 8.5f) + 32f;
            if (lineW > maxW) maxW = lineW;
        }

        float cardW = maxW;
        float cardH = 22f + members.size() * 16f + 6f;

        float[] p = dragStaff.place(cardW, cardH);
        float x = p[0], y = p[1];

        renderGlassCard(x, y, cardW, cardH, CARD_RADIUS, anim);

        // Header
        Msdf.text(drawContext, Msdf.SF_BOLD, ez.minar.system.managers.LocalizationManager.get("hud.staff"), x + 10f, y + 8f, 9f,
                withAlpha(Color.WHITE, anim).getRGB(), false);
        RenderUtil.texture(x + cardW - 10f - 11f, y + 7.5f, 11f, ICON_STAFF, 0f,
                withAlpha(Color.WHITE, anim));

        // Rows
        float ry = y + 24f;
        for (StaffMember m : members) {
            // Skin Head
            drawSkinFace(m.skin, x + 10f, ry + 1.5f, 11f, 2.5f, withAlpha(Color.WHITE, anim));

            // Prefix + Name
            float textX = x + 25f;
            Msdf.text(drawContext, Msdf.SF_BOLD, m.prefix, textX, ry + 1.5f, 8.5f,
                    withAlpha(m.prefixColor, anim).getRGB(), false);
            float prefixW = Msdf.width(Msdf.SF_BOLD, m.prefix, 8.5f);
            Msdf.text(drawContext, Msdf.SF_BOLD, " " + m.name, textX + prefixW, ry + 1.5f, 8.5f,
                    withAlpha(Color.WHITE, anim).getRGB(), false);

            // Status Indicator Circle (Green / Red with black inner center)
            float dotSize = 8f;
            float dotX = x + cardW - 10f - dotSize;
            float dotY = ry + 3.5f;
            Color dotColor = m.online ? COLOR_ONLINE : COLOR_OFFLINE;
            RenderUtil.rect(dotX, dotY, dotSize, dotSize, dotSize / 2f, withAlpha(dotColor, anim));
            RenderUtil.rect(dotX + 2f, dotY + 2f, dotSize - 4f, dotSize - 4f, (dotSize - 4f) / 2f,
                    withAlpha(new Color(15, 18, 24), anim));

            ry += 16f;
        }
    }

    private List<StaffMember> getOnlineStaff(boolean chat) {
        List<StaffMember> result = new ArrayList<>();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().name();
                if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) continue;
                Team team = entry.getScoreboardTeam();
                String prefix = team != null ? team.getPrefix().getString().trim() : "";
                String display = entry.getDisplayName() != null ? entry.getDisplayName().getString().trim() : "";
                String combined = (prefix + " " + display + " " + name).toUpperCase(Locale.ROOT);

                String matchedRank = null;
                Color rankColor = null;
                for (Map.Entry<String, Color> rank : STAFF_RANKS.entrySet()) {
                    if (combined.contains(rank.getKey())) {
                        matchedRank = rank.getKey();
                        rankColor = rank.getValue();
                        break;
                    }
                }

                if (matchedRank != null) {
                    boolean online = entry.getGameMode() != GameMode.SPECTATOR;
                    Identifier skin = null;
                    try {
                        skin = entry.getSkinTextures().body().texturePath();
                    } catch (Exception ignored) {}
                    String cleanPrefix = matchedRank;
                    if (prefix.length() > 0 && prefix.length() <= 16) {
                        cleanPrefix = prefix;
                    }
                    result.add(new StaffMember(name, cleanPrefix, rankColor, online, skin));
                }
            }
        }

        if (result.isEmpty() && chat) {
            Identifier demoSkin = null;
            if (mc.player != null) {
                try {
                    demoSkin = DefaultSkinHelper.getSkinTextures(mc.player.getUuid()).body().texturePath();
                } catch (Exception ignored) {}
            }
            result.add(new StaffMember("IluoneR", "YouTube", new Color(0xE5, 0x4D, 0x42), true, demoSkin));
            result.add(new StaffMember("x1get_", "ML MODER", new Color(0x9E, 0x78, 0xE6), false, demoSkin));
        }

        return result;
    }

    // ==========================================
    // 6. Notifications (Matching ksnip_20260919-202350.png)
    // ==========================================
    private NoticeItem previewNotice;

    private void renderNotices(boolean chat) {
        long now = System.currentTimeMillis();
        activeNotices.removeIf(n -> now - n.created > n.duration + 300L);

        List<NoticeItem> list = new ArrayList<>(activeNotices);
        boolean isPreview = false;
        if (list.isEmpty() && chat) {
            String previewText = ez.minar.system.managers.LocalizationManager.get("hud.preview");
            if (previewNotice == null || !previewText.equals(previewNotice.text)) {
                previewNotice = new NoticeItem(previewText, NoticeType.INFO);
            }
            list.add(previewNotice);
            isPreview = true;
        }

        float offset = 0;
        List<NoticeItem> reversed = new ArrayList<>(list);
        java.util.Collections.reverse(reversed);

        for (NoticeItem notify : reversed) {
            float anim = (isPreview && chat) ? 1f : notify.anim.getOutput();
            if (anim <= 0.005f) continue;

            float height = 18.0f;
            float radius = 9.0f;
            float badgeSize = 10.0f;
            float padLeft = 5.0f;
            float gap1 = 5.0f;
            float sepW = 0.75f;
            float gap2 = 5.5f;
            float padRight = 6.0f;

            float textW = Msdf.width(Msdf.SF_BOLD, notify.text, 8.0f);
            float width = padLeft + badgeSize + gap1 + sepW + gap2 + textW + padRight;

            float targetX = RenderUtil.getFixedScaledWidth() / 2f - width / 2f;
            float targetY = RenderUtil.getFixedScaledHeight() / 2f + 16f + offset;

            if (!notify.posInit || (isPreview && chat)) {
                notify.x = targetX;
                notify.y = isPreview ? targetY : (targetY - 16f);
                notify.posInit = true;
            }
            if (!isPreview) {
                notify.x = fast(notify.x, targetX, 10f);
                notify.y = fast(notify.y, targetY, 10f);
            }

            // 1. Frosted Glass Capsule Background (matching base HUD style)
            renderGlassCard(notify.x, notify.y, width, height, radius, anim);

            // 2. Icon Badge (colored circle with cutout glyph)
            float badgeX = notify.x + padLeft;
            float badgeY = notify.y + (height - badgeSize) / 2f;
            float cx = badgeX + badgeSize / 2f;
            float cy = badgeY + badgeSize / 2f;

            Color badgeColor;
            if (notify.type == NoticeType.ENABLE) {
                badgeColor = new Color(46, 204, 113);
            } else if (notify.type == NoticeType.WARNING) {
                badgeColor = new Color(245, 166, 35);
            } else {
                // NoticeType.DISABLE, NoticeType.INFO or default: vibrant red matching screenshot
                badgeColor = new Color(235, 35, 35);
            }

            RenderUtil.rect(badgeX, badgeY, badgeSize, badgeSize, badgeSize / 2f, withAlpha(badgeColor, anim));

            // Inner glyph / cutout (dark slate style matching ksnip_20260919-202350.png)
            Color glyphColor = withAlpha(new Color(24, 28, 38, 220), anim);
            if (notify.type == NoticeType.ENABLE) {
                RenderUtil.texture(cx - 3.75f, cy - 3.75f, 7.5f, ICON_CHECK, 0f, glyphColor);
            } else if (notify.type == NoticeType.DISABLE) {
                RenderUtil.texture(cx - 3.75f, cy - 3.75f, 7.5f, ICON_X, 0f, glyphColor);
            } else if (notify.type == NoticeType.WARNING) {
                // Exclamation mark '!'
                RenderUtil.rect(cx - 0.6f, cy - 2.5f, 1.2f, 3.0f, 0.5f, glyphColor);
                RenderUtil.rect(cx - 0.6f, cy + 1.5f, 1.2f, 1.2f, 0.5f, glyphColor);
            } else {
                // Info 'i' (exact match to ksnip_20260919-202350.png)
                RenderUtil.rect(cx - 0.6f, cy - 2.5f, 1.2f, 1.2f, 0.5f, glyphColor);
                RenderUtil.rect(cx - 0.6f, cy - 0.5f, 1.2f, 3.2f, 0.5f, glyphColor);
            }

            // 3. Vertical Separator Line '|'
            float sepX = badgeX + badgeSize + gap1;
            float sepH = 8.0f;
            float sepY = notify.y + (height - sepH) / 2f;
            RenderUtil.rect(sepX, sepY, sepW, sepH, 0.35f, withAlpha(new Color(255, 255, 255, 45), anim));

            // 4. Text
            float textX = sepX + sepW + gap2;
            float textY = notify.y + 4.5f;
            Msdf.text(drawContext, Msdf.SF_BOLD, notify.text, textX, textY, 8.0f,
                    withAlpha(new Color(245, 245, 250), anim).getRGB(), false);

            if (!isPreview && now - notify.created > notify.duration) {
                notify.anim.setDirection(-1);
            }
            offset += 22.0f * anim;
        }
    }

    public static void pushModuleNotification(Function function) {
        if (Instance != null && Instance.isEnabled() && Instance.notifications.isEnabled()) {
            boolean enabled = function.isEnabled();
            String status = ez.minar.system.managers.LocalizationManager.get(enabled ? "hud.module.enabled" : "hud.module.disabled");
            Instance.addNotice(function.getDisplayName() + " " + status,
                    enabled ? NoticeType.ENABLE : NoticeType.DISABLE);
        }
    }

    public void addNotice(String text, NoticeType type) {
        activeNotices.add(new NoticeItem(text, type));
    }

    public void addNotice(String text) {
        addNotice(text, NoticeType.INFO);
    }

    public void addNotice(String text, NoticeType type, long duration) {
        activeNotices.add(new NoticeItem(text, type, duration));
    }

    // ==========================================
    // Animation Helper
    // ==========================================
    public static final class XyAnim {
        private final float ms;
        private long start;
        private int lastDirection = 0;
        public int direction = -1;

        XyAnim(float ms) {
            this.ms = ms;
            this.start = System.currentTimeMillis();
        }

        public void setDirection(int dir) {
            if (dir != lastDirection) {
                lastDirection = dir;
                start = System.currentTimeMillis();
            }
            direction = dir;
        }

        public void reset() {
            start = System.currentTimeMillis();
            lastDirection = 0;
            direction = -1;
        }

        public float getOutput() {
            if (lastDirection == 0 && direction <= 0) return 0f;
            float t = Math.clamp((System.currentTimeMillis() - start) / ms, 0f, 1f);
            return direction > 0 ? ez.minar.utils.math.Easings.OutBack(t) : Math.clamp(1f - ez.minar.utils.math.Easings.OutCubic(t), 0f, 1f);
        }
    }

    // ==========================================
    // Hotbar (Matching frosted blur style)
    // ==========================================
    public float mainSlot = -1f;

    public interface HotbarItemRenderer {
        void draw(int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed);
    }

    public void renderHotbarXyeta(DrawContext context, RenderTickCounter tickCounter, HotbarItemRenderer itemRenderer) {
        PlayerEntity player = mc.player;
        if (player == null) return;

        int centerX = RenderUtil.getFixedScaledWidth() / 2;
        int bottom = RenderUtil.getFixedScaledHeight();
        float dcs = RenderUtil.getDrawContextScale();

        ItemStack offhand = player.getOffHandStack();
        var arm = player.getMainArm().getOpposite();

        if (mainSlot < 0f) mainSlot = centerX - 91f - 1f;

        // Panel
        RenderUtil.blur(centerX - 102f, bottom - 27f, 202, 26, 8, GLASS_BLUR);
        RenderUtil.rect(centerX - 102f, bottom - 27f, 202, 26, 8, GLASS_BG);
        RenderUtil.outline(centerX - 102f, bottom - 27f, 202, 26, 8, 0.5f, GLASS_OUTLINE);

        // Selection
        mainSlot = fast(mainSlot, centerX - 91f - 1f + player.getInventory().getSelectedSlot() * 22f, 10f);
        float selLeft = mainSlot - 7f;
        float selY = bottom - 24f;
        RenderUtil.rect(selLeft, selY, 20, 20, 5, withAlpha(Color.WHITE, 0.15f));
        RenderUtil.outline(selLeft, selY, 20, 20, 5, 0.5f, withAlpha(Color.WHITE, 0.3f));

        // Slots + items
        int o = bottom - 21;
        for (int m = 0; m < 9; m++) {
            float n = centerX - 100f + m * 22f + 2f;
            RenderUtil.rect(n - 1f, o - 5f, 20, 20, 4, withAlpha(new Color(15, 18, 24), 0.5f));
            RenderUtil.outline(n - 1f, o - 5f, 20, 20, 4, 0.5f, GLASS_OUTLINE);

            float itemFixedX = n + 1.92f;
            float itemFixedY = o + 1f;
            itemRenderer.draw((int) (itemFixedX / dcs), (int) (itemFixedY / dcs), tickCounter, player,
                    player.getInventory().getStack(m), m + 1);
        }

        // Offhand
        if (!offhand.isEmpty()) {
            if (arm == net.minecraft.util.Arm.LEFT) {
                float ox = centerX - 91f - 29f - 12f;
                RenderUtil.blur(ox, bottom - 27f, 26, 26, 8, GLASS_BLUR);
                RenderUtil.rect(ox, bottom - 27f, 26, 26, 8, GLASS_BG);
                RenderUtil.outline(ox, bottom - 27f, 26, 26, 8, 0.5f, GLASS_OUTLINE);
                RenderUtil.rect(ox + 3f, bottom - 24f, 20, 20, 4, withAlpha(new Color(15, 18, 24), 0.5f));

                float itemFixedX = centerX - 91f - 26f - 9f;
                float itemFixedY = bottom - 20f;
                itemRenderer.draw((int) (itemFixedX / dcs), (int) (itemFixedY / dcs), tickCounter, player,
                        offhand, 1);
            } else {
                float itemFixedX = centerX + 91f + 10f;
                float itemFixedY = bottom - 19f;
                itemRenderer.draw((int) (itemFixedX / dcs), (int) (itemFixedY / dcs), tickCounter, player,
                        offhand, 1);
            }
        }
    }

    // ==========================================
    // Vanilla replacement hooks & config
    // ==========================================
    public void renderElementPanel(Object element, float x, float y, float width, float height, float radius, float opacity) {
        renderGlassCard(x, y, width, height, radius, opacity);
    }

    public static boolean shouldDisableDefaultHotbar() {
        return Instance != null && Instance.isEnabled() && Instance.hotbar.isEnabled();
    }

    public static boolean shouldHideStatusBars() {
        return shouldDisableDefaultHotbar();
    }

    public static boolean shouldDisableDefaultScoreboard() {
        return false;
    }

    public static boolean shouldHideDefaultStatusEffectIcons() {
        return Instance != null && Instance.isEnabled() && Instance.elements.isEnabled("Зелья");
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
}
