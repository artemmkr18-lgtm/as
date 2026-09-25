package ez.minar.system.menu;

import ez.minar.system.managers.UnhookManager;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class TitleScreenMenuRenderer {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long TIME_ANIMATION_MS = 720L;
    private static final Map<ClickableWidget, Float> HOVER_PROGRESS = new IdentityHashMap<>();
    private static DrawContext lastContext;
    private static long lastFrame;
    private static String displayedTime = "";
    private static String previousTime = "";
    private static long[] timeChangeStarted = new long[0];
    
    private static final String ALT_ICON = "U";
    private static final String LANG_ICON = "LANG";
    private static final String CLIENT_NAME = "Next Client";
    private static final Identifier LOGO_TEXTURE = Identifier.of("minar", "images/logo.png");
    private static final Identifier ICON_GLOBE = Identifier.of("minar", "icons/175_lucide_globe.png");
    private static final float CLOCK_Y = 150f;
    private static final float CLOCK_SIZE = 42f;

    // Interactive mouse movement glow shader state
    private static class GlowPoint {
        float x;
        float y;
        float life = 1.0f;
        float size;

        GlowPoint(float x, float y, float size) {
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    // Keep the effect bounded: allocation and overdraw here directly affect frame pacing.
    private static final int MAX_TRAIL_POINTS = 10;
    private static final List<GlowPoint> MOUSE_TRAIL = new ArrayList<>(MAX_TRAIL_POINTS);
    private static float lastMouseX = -1f;
    private static float lastMouseY = -1f;
    private static float currentFixedMouseX = 0f;
    private static float currentFixedMouseY = 0f;
    private static float mouseMotion = 0f;

    public static void updateMouse(int mouseX, int mouseY) {
        currentFixedMouseX = RenderUtil.convertX(mouseX);
        currentFixedMouseY = RenderUtil.convertY(mouseY);
    }

    public static void renderBackground() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        float sw = RenderUtil.getFixedScaledWidth();
        float sh = RenderUtil.getFixedScaledHeight();

        // Completely pitch-black background
        RenderUtil.rect(0, 0, sw, sh, 0, Color.BLACK);

        // Interactive mouse movement glow shader
        renderMouseGlow();
    }

    private static void renderMouseGlow() {
        long now = System.currentTimeMillis();
        float delta = lastFrame == 0L ? 0.016f : Math.min(50f, now - lastFrame) / 1000f;

        if (lastMouseX >= 0f && lastMouseY >= 0f) {
            float dx = currentFixedMouseX - lastMouseX;
            float dy = currentFixedMouseY - lastMouseY;
            float dist = (float) Math.hypot(dx, dy);

            float targetMotion = Math.clamp(dist / 12f, 0f, 1.5f);
            mouseMotion = mouseMotion + (targetMotion - mouseMotion) * (1.0f - (float) Math.exp(-delta * 14.0f));

            if (dist > 2.5f) {
                float size = 42f + Math.min(dist * 1.6f, 55f);
                MOUSE_TRAIL.add(new GlowPoint(currentFixedMouseX, currentFixedMouseY, size));
                if (MOUSE_TRAIL.size() > MAX_TRAIL_POINTS) {
                    MOUSE_TRAIL.remove(0);
                }
            }
        }
        lastMouseX = currentFixedMouseX;
        lastMouseY = currentFixedMouseY;

        // Smooth decay
        mouseMotion = Math.max(0f, mouseMotion - delta * 0.7f);

        // Update trail life
        for (int i = MOUSE_TRAIL.size() - 1; i >= 0; i--) {
            GlowPoint p = MOUSE_TRAIL.get(i);
            p.life -= delta * 2.4f;
            if (p.life <= 0.01f) {
                MOUSE_TRAIL.remove(i);
            }
        }

        Color theme = ThemeManager.getThemeColor();

        // Draw trail points (fading glowing comets)
        for (int i = 0; i < MOUSE_TRAIL.size(); i++) {
            GlowPoint p = MOUSE_TRAIL.get(i);
            if (p.life <= 0.02f) continue;
            float trailAlpha = p.life * (0.16f + 0.22f * mouseMotion);
            Color trailColor = withAlpha(theme, trailAlpha);
            RenderUtil.glowCircle(p.x, p.y, p.size * (0.4f + 0.6f * p.life), trailColor);
        }

        // Dynamic cursor orb that flares up when moving
        float factor = Math.clamp(mouseMotion, 0.0f, 1.5f);

        // Outer soft ambient dispersion
        float outerSize = 120f + 85f * factor;
        float outerAlpha = 0.09f + 0.18f * factor;
        RenderUtil.glowCircle(currentFixedMouseX, currentFixedMouseY, outerSize, withAlpha(theme, outerAlpha));

        // Mid vibrant glow
        float midSize = 60f + 45f * factor;
        float midAlpha = 0.14f + 0.26f * factor;
        RenderUtil.glowCircle(currentFixedMouseX, currentFixedMouseY, midSize, withAlpha(theme, midAlpha));

        // Inner bright white core
        float innerSize = 26f + 20f * factor;
        float innerAlpha = 0.10f + 0.22f * factor;
        RenderUtil.glowCircle(currentFixedMouseX, currentFixedMouseY, innerSize, withAlpha(Color.WHITE, innerAlpha));
    }

    public static void setContext(DrawContext context) {
        lastContext = context;
    }

    public static void hideTitleScreenExtras(TitleScreen titleScreen) {
        for (Element element : titleScreen.children()) {
            if (element instanceof ButtonWidget.Text) {
                continue;
            }

            if (element instanceof ClickableWidget widget) {
                widget.visible = false;
                widget.active = false;
            }
        }
    }

    public static boolean shouldHideTitleScreenText(String text) {
        if (UnhookManager.isUnhooked()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof TitleScreen) || text == null) {
            return false;
        }

        return text.contains("\u0422\u0435\u043a\u0443\u0449\u0438\u0439 \u0430\u043a\u043a\u0430\u0443\u043d\u0442") || text.contains("Current account");
    }

    public static void renderButtons() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof TitleScreen titleScreen)) {
            return;
        }

        renderTime();
        renderLogo();
        updateAnimations();

        ClickableWidget hoveredWidget = null;
        String hoveredTooltip = null;

        for (Element element : titleScreen.children()) {
            if (element instanceof ButtonWidget.Text textButton) {
                String icon = getTitleButtonIcon(textButton);
                if (icon != null) {
                    renderIconButton(textButton, icon);
                    if (textButton.isSelected()) {
                        hoveredWidget = textButton;
                        hoveredTooltip = getTitleButtonTooltip(icon);
                    }
                }
            }
        }

        if (hoveredWidget != null && hoveredTooltip != null) {
            renderTooltip(hoveredWidget, hoveredTooltip);
        }
    }

    private static void renderTime() {
        if (lastContext == null) {
            return;
        }

        String time = LocalTime.now().format(TIME_FORMATTER);
        updateTimeState(time);

        renderAnimatedTime(RenderUtil.getFixedScaledWidth() / 2f, CLOCK_Y, CLOCK_SIZE);
    }

    private static void renderLogo() {
        if (lastContext == null) {
            return;
        }

        float textSize = 15f;
        float badgeSize = 19f;
        float gap = 6.5f;

        MsdfFont font = Msdf.SF_BOLD;
        float textWidth = Msdf.width(font, CLIENT_NAME, textSize);
        float totalWidth = badgeSize + gap + textWidth;
        float centerX = RenderUtil.getFixedScaledWidth() / 2f;
        float x = centerX - totalWidth / 2f;
        float y = CLOCK_Y + Msdf.height(Msdf.SF_BOLD, CLOCK_SIZE) + 7f;
        float radius = 5.5f;

        RenderUtil.shadow(x + 0.5f, y + 1.2f, badgeSize, badgeSize, radius,
                9f, 0.28f, 2.2f, new Color(0, 0, 0, 170));
        RenderUtil.rect(x, y, badgeSize, badgeSize, radius,
                new Color(150, 150, 150, 20));
        RenderUtil.outline(x + 0.45f, y + 0.45f, badgeSize - 0.9f, badgeSize - 0.9f, radius,
                0.75f, new Color(255, 255, 255, 58));

        float logoSize = badgeSize - 5f;
        RenderUtil.texture(x + (badgeSize - logoSize) / 2f, y + (badgeSize - logoSize) / 2f,
                logoSize, LOGO_TEXTURE, 0f, Color.WHITE);

        float textHeight = Msdf.height(font, textSize);
        RenderUtil.text(lastContext, font, x + badgeSize + gap,
                y + (badgeSize - textHeight) / 2f, CLIENT_NAME, textSize, new Color(245, 247, 250));
    }

    private static void updateAnimations() {
        long now = System.currentTimeMillis();
        if (lastFrame == 0L) {
            lastFrame = now;
            return;
        }
        float delta = Math.min(50f, now - lastFrame);
        lastFrame = now;

        HOVER_PROGRESS.entrySet().removeIf(entry -> !entry.getKey().visible);
        for (Map.Entry<ClickableWidget, Float> entry : HOVER_PROGRESS.entrySet()) {
            ClickableWidget widget = entry.getKey();
            float target = widget.isSelected() ? 1f : 0f;
            float factor = 1f - (float) Math.exp(-0.018f * delta);
            float next = entry.getValue() + (target - entry.getValue()) * Math.clamp(factor, 0f, 1f);
            entry.setValue(Math.abs(next - target) < 0.001f ? target : next);
        }
    }

    private static void renderIconButton(ClickableWidget widget, String icon) {
        if (widget instanceof PressableTextWidget) {
            return;
        }

        float guiScale = RenderUtil.getScaleFactor();
        float alpha = widget.getAlpha();
        float progress = HOVER_PROGRESS.computeIfAbsent(widget, ignored -> 0f);
        float scale = 1f + easeOutCubic(progress) * 0.065f;
        float baseWidth = widget.getWidth() * guiScale;
        float baseHeight = widget.getHeight() * guiScale;
        float width = baseWidth * scale;
        float height = baseHeight * scale;
        float x = widget.getX() * guiScale + (baseWidth - width) / 2f;
        float y = widget.getY() * guiScale + (baseHeight - height) / 2f;
        float radius = 4f;

        RenderUtil.shadow(x + 0.5f, y + 1.2f, width - 1f, height - 0.4f, radius,
                9f, 0.28f * alpha, 2.2f, new Color(0, 0, 0, 170));
        RenderUtil.rect(x, y, width, height, radius,
                new Color(150, 150, 150, 20));

        Color textColor = widget.active
                ? new Color(248, 250, 252, Math.round(255f * alpha))
                : new Color(185, 190, 196, Math.round(210f * alpha));
        float iconSize = 22f * scale;
        if (LANG_ICON.equals(icon)) {
            float globeSize = 19f * scale;
            float iconX = x + (width - globeSize) / 2f;
            float iconY = y + (height - globeSize) / 2f;
            RenderUtil.texture(iconX, iconY, globeSize, ICON_GLOBE, 0f, textColor);
        } else {
            float iconWidth = Msdf.width(Msdf.WTMICO, icon, iconSize);
            float iconX = x + (width - iconWidth) / 2f;
            float iconY = y + height / 2f - iconSize * 0.47f;
            RenderUtil.text(lastContext, Msdf.WTMICO, iconX, iconY, icon, iconSize, textColor);
        }
    }

    private static void renderTooltip(ClickableWidget widget, String tooltip) {
        if (tooltip == null) {
            return;
        }

        float progress = HOVER_PROGRESS.getOrDefault(widget, 0f);
        if (progress <= 0.01f) {
            return;
        }

        float eased = easeOutCubic(progress);
        float alpha = widget.getAlpha() * eased;
        float guiScale = RenderUtil.getScaleFactor();
        float scale = 0.96f + eased * 0.04f;
        float textSize = 8.4f * scale;
        float paddingX = 7f * scale;
        float paddingY = 4.5f * scale;
        float textWidth = Msdf.width(Msdf.SF_BOLD, tooltip, textSize);
        float textHeight = Msdf.height(Msdf.SF_BOLD, textSize);
        float width = textWidth + paddingX * 2f;
        float height = textHeight + paddingY * 2f;
        float x = widget.getX() * guiScale + widget.getWidth() * guiScale / 2f - width / 2f;
        float y = widget.getY() * guiScale - height - 9f + (1f - eased) * 6f;
        float radius = 4f * scale;

        RenderUtil.shadow(x, y + 1.5f, width, height, radius,
                8f, 0.22f * alpha, 2.0f, new Color(0, 0, 0, 170));
        RenderUtil.rect(x, y, width, height, radius,
                new Color(150, 150, 150, 20));
        RenderUtil.outline(x + 0.45f, y + 0.45f, width - 0.9f, height - 0.9f, radius,
                0.75f, new Color(255, 255, 255, Math.round(58f * alpha)));
        RenderUtil.text(lastContext, Msdf.SF_BOLD, x + paddingX, y + paddingY - 0.5f,
                tooltip, textSize, new Color(245, 247, 250, Math.round(255f * alpha)));
    }

    private static void updateTimeState(String time) {
        long now = System.currentTimeMillis();
        if (displayedTime.length() != time.length()) {
            displayedTime = time;
            previousTime = time;
            timeChangeStarted = new long[time.length()];
            return;
        }

        if (timeChangeStarted.length != time.length()) {
            timeChangeStarted = new long[time.length()];
        }

        char[] current = displayedTime.toCharArray();
        char[] previous = previousTime.toCharArray();
        for (int i = 0; i < time.length(); i++) {
            char next = time.charAt(i);
            if (current[i] != next) {
                previous[i] = current[i];
                current[i] = next;
                timeChangeStarted[i] = now;
            }
        }
        displayedTime = new String(current);
        previousTime = new String(previous);
    }

    private static void renderAnimatedTime(float centerX, float y, float size) {
        if (displayedTime.isEmpty()) {
            return;
        }

        float digitWidth = Math.max(Msdf.width(Msdf.SF_BOLD, "0", size), Msdf.width(Msdf.SF_BOLD, "8", size));
        float colonWidth = Msdf.width(Msdf.SF_BOLD, ":", size) + 2f;
        float gap = 1.5f;
        float height = Msdf.height(Msdf.SF_BOLD, size);
        float totalWidth = 0f;

        for (int i = 0; i < displayedTime.length(); i++) {
            totalWidth += cellWidth(displayedTime.charAt(i), digitWidth, colonWidth);
            if (i < displayedTime.length() - 1) {
                totalWidth += gap;
            }
        }

        long now = System.currentTimeMillis();
        float x = centerX - totalWidth / 2f;
        Color color = new Color(245, 247, 250);

        for (int i = 0; i < displayedTime.length(); i++) {
            char current = displayedTime.charAt(i);
            char previous = previousTime.charAt(i);
            float cellWidth = cellWidth(current, digitWidth, colonWidth);
            float charWidth = Msdf.width(Msdf.SF_BOLD, String.valueOf(current), size);
            float charX = x + (cellWidth - charWidth) / 2f;

            if (current == ':' || timeChangeStarted[i] == 0L) {
                RenderUtil.text(lastContext, Msdf.SF_BOLD, charX, y, String.valueOf(current), size, color);
            } else {
                float progress = Math.clamp((now - timeChangeStarted[i]) / (float) TIME_ANIMATION_MS, 0f, 1f);
                float incoming = easeOutBack(progress);
                float outgoing = easeOutCubic(progress);
                float clipPad = 6f;

                Scissor.push(x - clipPad, y - clipPad, cellWidth + clipPad * 2f, height + clipPad * 2f);
                RenderUtil.text(lastContext, Msdf.SF_BOLD,
                        x + (cellWidth - Msdf.width(Msdf.SF_BOLD, String.valueOf(previous), size)) / 2f,
                        y - height * outgoing,
                        String.valueOf(previous), size, withAlpha(color, 1f - progress * 0.35f));
                RenderUtil.text(lastContext, Msdf.SF_BOLD,
                        charX,
                        y + height * (1f - incoming),
                        String.valueOf(current), size, color);
                Scissor.pop();

                if (progress >= 1f) {
                    timeChangeStarted[i] = 0L;
                    char[] previousChars = previousTime.toCharArray();
                    previousChars[i] = current;
                    previousTime = new String(previousChars);
                }
            }

            x += cellWidth + gap;
        }
    }

    private static float cellWidth(char c, float digitWidth, float colonWidth) {
        return c == ':' ? colonWidth : digitWidth;
    }

    private static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(255f * Math.clamp(alpha, 0f, 1f)));
    }

    private static String getTitleButtonIcon(ClickableWidget widget) {
        if (widget.getMessage().getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if ("menu.singleplayer".equals(key)) return "P";
            if ("menu.multiplayer".equals(key)) return "N";
            if ("menu.options".equals(key)) return "O";
            if ("menu.quit".equals(key)) return "Q";
        }

        String text = widget.getMessage().getString().toLowerCase();
        if (text.contains("\u043e\u0434\u0438\u043d\u043e\u0447") || text.contains("single") || text.contains("jednoosobow")) {
            return "P";
        }
        if (text.contains("\u0441\u0435\u0442\u0435\u0432") || text.contains("multi") || text.contains("wieloosobow")) {
            return "N";
        }
        if (text.contains("\u043d\u0430\u0441\u0442\u0440\u043e\u0439") || text.contains("option") || text.contains("opcj")) {
            return "O";
        }
        if (text.contains("\u0432\u044b\u0439\u0442\u0438") || text.contains("quit") || text.contains("wyjd") || text.contains("zako\u0144cz") || text.contains("zakoncz")) {
            return "Q";
        }
        if (text.contains("\u0430\u043b\u044c\u0442") || text.contains("alt")) {
            return ALT_ICON;
        }
        if (text.contains("\u044f\u0437\u044b\u043a") || text.contains("language") || text.contains("lang") || text.contains("j\u0119zyk") || text.contains("jezyk")) {
            return LANG_ICON;
        }
        return null;
    }

    private static String getTitleButtonTooltip(String icon) {
        if (icon == null) return null;
        boolean isRu = ez.minar.system.managers.LocalizationManager.isRussian();
        boolean isPl = ez.minar.system.managers.LocalizationManager.isPolish();

        if (icon.equals(ALT_ICON)) {
            if (isRu) return "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u0430\u043b\u044c\u0442\u043c\u0435\u043d\u0435\u0434\u0436\u0435\u0440";
            if (isPl) return "Mened\u017cer kont";
            return "Open Alt Manager";
        }
        if (icon.equals(LANG_ICON)) {
            if (isRu) return "\u0412\u044b\u0431\u043e\u0440 \u044f\u0437\u044b\u043a\u0430";
            if (isPl) return "Wyb\u00f3r j\u0119zyka";
            return "Choose Language";
        }
        return switch (icon) {
            case "P" -> isRu ? "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043e\u0434\u0438\u043d\u043e\u0447\u043d\u0443\u044e \u0438\u0433\u0440\u0443" : (isPl ? "Tryb jednoosobowy" : "Open Singleplayer");
            case "N" -> isRu ? "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043c\u0443\u043b\u044c\u0442\u0438\u043f\u043b\u0435\u0435\u0440" : (isPl ? "Tryb wieloosobowy" : "Open Multiplayer");
            case "O" -> isRu ? "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438" : (isPl ? "Opcje" : "Open Options");
            case "Q" -> isRu ? "\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 \u0438\u0433\u0440\u044b" : (isPl ? "Wyjd\u017a z gry" : "Quit Game");
            default -> null;
        };
    }

    private static float easeOutCubic(float progress) {
        float t = Math.clamp(progress, 0f, 1f) - 1f;
        return t * t * t + 1f;
    }

    private static float easeOutBack(float progress) {
        float t = Math.clamp(progress, 0f, 1f) - 1f;
        float c = 1.70158f;
        return 1f + (c + 1f) * t * t * t + c * t * t;
    }
}
