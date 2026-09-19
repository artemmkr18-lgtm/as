package ez.minar.system.menu;

import ez.minar.system.menu.components.ColorPickerWindow;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.function.Consumer;

/** Flat theme selector. Picker changes affect only the draft until Accept. */
public class ThemeMenuWindow {
    private static final float WINDOW_WIDTH = 208f;
    private static final float MAX_HEIGHT = 320f;
    private static final float HEADER_HEIGHT = 26f;
    private static final float FOOTER_HEIGHT = 40f;
    private static final float PADDING = 10f;
    private static final float ROW_H = 26f;
    private static final float ROW_PITCH = 30f;
    private static final float RADIUS = 15f;
    private static final float R_FIELD = 7f;
    private static final Color PANEL = new Color(28, 28, 28);
    private static final Color HEADER = new Color(23, 23, 23);
    private static final Color FIELD = new Color(37, 37, 37);
    private static final Color HOVER = new Color(45, 45, 45);
    private static final Color TEXT_DIM = new Color(255, 255, 255, 150);
    private static final Color TEXT_FADED = new Color(255, 255, 255, 80);
    private static final Identifier ICON_CHECK = Identifier.of("minar", "icons/ui_check.png");
    private static final Identifier ICON_PAINTBRUSH = Identifier.of("minar", "icons/ui_paintbrush.png");

    private enum State { LIST, CREATE }
    private State state = State.LIST;
    private boolean open;
    private float anim;
    private long lastAnimTime;
    private float x = 100f;
    private float y = 100f;
    private float height;
    private float scroll;
    private float targetScroll;
    private String nameBuffer = "";
    private Color draftColor = ThemeManager.DEFAULT_ACCENT;
    private ColorPickerWindow colorPicker;
    private Consumer<Color> draftConsumer;
    private long draftSession;

    public void setColorPicker(ColorPickerWindow picker) {
        cancelPicker();
        colorPicker = picker;
    }

    public void open(float anchorX, float anchorY) {
        if (!open) {
            discardDraft();
            state = State.LIST;
            scroll = targetScroll = 0f;
        }
        height = targetHeight();
        x = anchorX - WINDOW_WIDTH + 24f;
        y = anchorY - height - 10f;
        clampLayout();
        open = true;
        lastAnimTime = System.nanoTime();
        playSound("setting");
    }

    public void close() {
        if (!open) return;
        open = false;
        cancelPicker();
        // Keep the exit frame's layout/text; this invalidated draft cannot be accepted.
        playSound("settingclose");
    }

    public void reset() {
        open = false;
        anim = 0f;
        lastAnimTime = 0L;
        discardDraft();
        state = State.LIST;
        scroll = targetScroll = 0f;
        height = targetHeight();
    }

    /** Includes the exit animation so the caller keeps rendering it. */
    public boolean isOpen() {
        return open || anim > 0.01f;
    }

    public boolean isTyping() {
        return open && state == State.CREATE;
    }

    public boolean isInside(float mouseX, float mouseY) {
        if (!isOpen()) return false;
        clampLayout();
        return hit(mouseX, mouseY, x, renderY(), WINDOW_WIDTH, height);
    }

    private float contentHeight() {
        return state == State.CREATE ? ROW_H * 2f + 8f
                : (ThemeManager.NAMED_THEMES.size() + 1) * ROW_PITCH - 4f;
    }

    private float maxHeight() {
        return Math.max(0f, Math.min(MAX_HEIGHT, RenderUtil.getFixedScaledHeight() - 20f));
    }

    private float targetHeight() {
        return Math.min(maxHeight(), HEADER_HEIGHT + contentHeight() + FOOTER_HEIGHT);
    }

    private float viewHeight() {
        return Math.max(0f, height - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    private float maxScroll() {
        return Math.max(0f, contentHeight() - viewHeight());
    }

    private float renderY() {
        float slideY = y + (1f - Easings.OutCubic(anim)) * 6f;
        return Math.min(slideY, Math.max(0f, RenderUtil.getFixedScaledHeight() - height - 10f));
    }

    private void clampLayout() {
        height = Math.clamp(height, 0f, maxHeight());
        x = Math.clamp(x, 10f, Math.max(10f, RenderUtil.getFixedScaledWidth() - WINDOW_WIDTH - 10f));
        y = Math.clamp(y, 10f, Math.max(10f, RenderUtil.getFixedScaledHeight() - height - 10f));
        targetScroll = Math.clamp(targetScroll, 0f, maxScroll());
        scroll = Math.clamp(scroll, 0f, maxScroll());
    }

    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastAnimTime == 0L ? 0f : Math.clamp((now - lastAnimTime) / 1_000_000_000f, 0f, 0.05f);
        lastAnimTime = now;
        float step = 1f - (float) Math.exp(-dt * 18f);
        anim += ((open ? 1f : 0f) - anim) * step;
        if (!open && anim <= 0.01f) {
            reset();
            return;
        }
        if (open) height += (targetHeight() - height) * step;
        clampLayout();
        scroll += (targetScroll - scroll) * step;
        float ry = renderY();
        float opacity = anim;
        Color accent = ThemeManager.getGuiAccent();
        RenderUtil.shadow(x, ry + 2f, WINDOW_WIDTH, height, RADIUS, 12f, 0.3f * opacity, 2f, new Color(0, 0, 0, 190));
        RenderUtil.rect(x, ry, WINDOW_WIDTH, height, RADIUS, withOpacity(PANEL, opacity));
        // Clip content during height transitions and on small screens.
        Scissor.push(x, ry, WINDOW_WIDTH, height);
        try {
            RenderUtil.rect(x + 5f, ry + 4f, WINDOW_WIDTH - 10f, HEADER_HEIGHT - 5f, 7f, withOpacity(HEADER, opacity));
            RenderUtil.text(context, Msdf.SF_BOLD, x + PADDING, ry + 8f,
                    state == State.CREATE ? ez.minar.system.managers.LocalizationManager.get("Новая тема") : ez.minar.system.managers.LocalizationManager.get("Темы"), 9.5f, withOpacity(Color.WHITE, opacity));
            float closeX = x + WINDOW_WIDTH - 21f;
            boolean closeHovered = open && hit(mouseX, mouseY, closeX, ry + 6f, 16f, 16f);
            if (closeHovered) RenderUtil.rect(closeX, ry + 6f, 16f, 16f, 4f, withOpacity(FIELD, opacity));
            RenderUtil.text(context, Msdf.SF_BOLD, closeX + 4.5f, ry + 9.5f, "✕", 8f,
                    withOpacity(closeHovered ? Color.WHITE : TEXT_DIM, opacity));

            Scissor.push(x + PADDING, ry + HEADER_HEIGHT, WINDOW_WIDTH - PADDING * 2f, viewHeight());
            try {
                if (state == State.CREATE) renderCreate(context, ry, mouseX, mouseY, opacity);
                else renderList(context, ry, mouseX, mouseY, opacity, accent);
            } finally {
                Scissor.pop();
            }
            if (maxScroll() > 0f && viewHeight() > 0f) {
                float thumbH = Math.min(viewHeight(), Math.max(12f, viewHeight() * viewHeight() / contentHeight()));
                float thumbY = ry + HEADER_HEIGHT + (viewHeight() - thumbH) * scroll / maxScroll();
                RenderUtil.rect(x + WINDOW_WIDTH - 6f, thumbY, 2f, thumbH, 1f, withOpacity(TEXT_FADED, opacity));
            }
            renderFooter(context, ry, mouseX, mouseY, opacity, accent);
        } finally {
            Scissor.pop();
        }
    }

    private void renderList(DrawContext context, float ry, float mouseX, float mouseY, float opacity, Color accent) {
        float rowW = WINDOW_WIDTH - PADDING * 2f;
        for (int row = 0; row <= ThemeManager.NAMED_THEMES.size(); row++) {
            float rowY = ry + HEADER_HEIGHT + row * ROW_PITCH - scroll;
            if (rowY + ROW_H <= ry + HEADER_HEIGHT || rowY >= ry + HEADER_HEIGHT + viewHeight()) continue;
            ThemeManager.NamedTheme theme = row == 0 ? null : ThemeManager.NAMED_THEMES.get(row - 1);
            Color color = theme == null ? ThemeManager.DEFAULT_ACCENT : theme.color;
            String name = theme == null ? ez.minar.system.managers.LocalizationManager.get("По умолчанию") : theme.name;
            boolean active = ThemeManager.activeNamedTheme == row - 1;
            boolean hovered = open && inViewport(mouseX, mouseY) && hit(mouseX, mouseY, x + PADDING, rowY, rowW, ROW_H);
            RenderUtil.rect(x + PADDING, rowY, rowW, ROW_H, R_FIELD, withOpacity(hovered ? HOVER : FIELD, opacity));
            RenderUtil.rect(x + PADDING + 7f, rowY + 8.5f, 9f, 9f, 3f, withOpacity(color, opacity));
            RenderUtil.text(context, Msdf.SF_REGULAR, x + PADDING + 24f, rowY + 8f,
                    trim(name, rowW - 46f, 9.5f, Msdf.SF_REGULAR), 9.5f,
                    withOpacity(active ? Color.WHITE : TEXT_DIM, opacity));
            if (active) RenderUtil.texture(x + WINDOW_WIDTH - PADDING - 17f, rowY + 8.5f, 9f, ICON_CHECK, 1f, withOpacity(accent, opacity));
        }
    }

    private void renderCreate(DrawContext context, float ry, float mouseX, float mouseY, float opacity) {
        float fieldX = x + PADDING;
        float fieldW = WINDOW_WIDTH - PADDING * 2f;
        float nameY = ry + HEADER_HEIGHT - scroll;
        RenderUtil.rect(fieldX, nameY, fieldW, ROW_H, R_FIELD, withOpacity(FIELD, opacity));
        boolean caret = open && (System.currentTimeMillis() / 500L) % 2 == 0;
        String label = nameBuffer.isEmpty() ? ez.minar.system.managers.LocalizationManager.get("Название темы...") : nameBuffer + (caret ? "_" : "");
        RenderUtil.text(context, Msdf.SF_REGULAR, fieldX + 8f, nameY + 8f,
                trim(label, fieldW - 16f, 9.5f, Msdf.SF_REGULAR), 9.5f,
                withOpacity(nameBuffer.isEmpty() ? TEXT_FADED : Color.WHITE, opacity));
        float colorY = nameY + ROW_H + 8f;
        boolean hovered = open && inViewport(mouseX, mouseY) && hit(mouseX, mouseY, fieldX, colorY, fieldW, ROW_H);
        RenderUtil.rect(fieldX, colorY, fieldW, ROW_H, R_FIELD, withOpacity(hovered ? HOVER : FIELD, opacity));
        RenderUtil.texture(fieldX + 8f, colorY + 8.5f, 9f, ICON_PAINTBRUSH, 1f, withOpacity(TEXT_DIM, opacity));
        RenderUtil.rect(fieldX + fieldW - 18f, colorY + 8f, 10f, 10f, 3f, withOpacity(draftColor, opacity));
        RenderUtil.text(context, Msdf.SF_REGULAR, fieldX + 24f, colorY + 8f, ez.minar.system.managers.LocalizationManager.get("Выбрать цвет"), 9.5f,
                withOpacity(hovered ? Color.WHITE : TEXT_DIM, opacity));
    }

    private void renderFooter(DrawContext context, float ry, float mouseX, float mouseY, float opacity, Color accent) {
        float btnY = ry + height - FOOTER_HEIGHT + 4f;
        float width = WINDOW_WIDTH - PADDING * 2f;
        if (state == State.LIST) {
            boolean enabled = ThemeManager.NAMED_THEMES.size() < ThemeManager.MAX_NAMED_THEMES;
            boolean hovered = open && hit(mouseX, mouseY, x + PADDING, btnY, width, ROW_H);
            RenderUtil.rect(x + PADDING, btnY, width, ROW_H, R_FIELD, withOpacity(enabled && hovered ? HOVER : FIELD, opacity));
            String label = !enabled ? ez.minar.system.managers.LocalizationManager.get("Лимит тем") : ThemeManager.NAMED_THEMES.isEmpty() ? ez.minar.system.managers.LocalizationManager.get("Создать первую тему") : ez.minar.system.managers.LocalizationManager.get("+  Новая тема");
            RenderUtil.text(context, Msdf.SF_BOLD, x + WINDOW_WIDTH / 2f, btnY + 8f, label, 9.5f,
                    withOpacity(enabled ? Color.WHITE : TEXT_FADED, opacity), "center");
            return;
        }
        float btnW = (width - 8f) / 2f;
        boolean enabled = ThemeManager.canAddNamedTheme(nameBuffer);
        boolean acceptHover = open && hit(mouseX, mouseY, x + PADDING, btnY, btnW, ROW_H);
        boolean declineHover = open && hit(mouseX, mouseY, x + PADDING + btnW + 8f, btnY, btnW, ROW_H);
        RenderUtil.rect(x + PADDING, btnY, btnW, ROW_H, R_FIELD,
                withOpacity(enabled ? (acceptHover ? accent.brighter() : accent) : FIELD, opacity));
        RenderUtil.text(context, Msdf.SF_BOLD, x + PADDING + btnW / 2f, btnY + 8f, ez.minar.system.managers.LocalizationManager.get("Accept"), 9.5f,
                withOpacity(enabled ? HEADER : TEXT_FADED, opacity), "center");
        RenderUtil.rect(x + PADDING + btnW + 8f, btnY, btnW, ROW_H, R_FIELD, withOpacity(declineHover ? HOVER : FIELD, opacity));
        RenderUtil.text(context, Msdf.SF_BOLD, x + PADDING + btnW * 1.5f + 8f, btnY + 8f, ez.minar.system.managers.LocalizationManager.get("Decline"), 9.5f,
                withOpacity(declineHover ? Color.WHITE : TEXT_DIM, opacity), "center");
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isInside(mouseX, mouseY)) return false;
        if (!open || anim < 0.5f || button != 0) return true;
        float ry = renderY();
        if (hit(mouseX, mouseY, x + WINDOW_WIDTH - 21f, ry + 6f, 16f, 16f)) {
            close();
            return true;
        }
        float fieldX = x + PADDING;
        float width = WINDOW_WIDTH - PADDING * 2f;
        float btnY = ry + height - FOOTER_HEIGHT + 4f;
        if (hit(mouseX, mouseY, fieldX, btnY, width, ROW_H)) {
            if (state == State.LIST) {
                if (ThemeManager.NAMED_THEMES.size() < ThemeManager.MAX_NAMED_THEMES) {
                    discardDraft();
                    state = State.CREATE;
                    scroll = targetScroll = 0f;
                    clampLayout();
                    playSound("setting");
                }
            } else {
                float btnW = (width - 8f) / 2f;
                if (mouseX <= fieldX + btnW) acceptDraft();
                else if (mouseX >= fieldX + btnW + 8f) backToList();
            }
            return true;
        }
        if (!inViewport(mouseX, mouseY)) return true;
        if (state == State.CREATE) {
            float colorY = ry + HEADER_HEIGHT + ROW_H + 8f - scroll;
            if (colorPicker != null && hit(mouseX, mouseY, fieldX, colorY, width, ROW_H)) {
                cancelPicker();
                long session = draftSession;
                draftConsumer = color -> {
                    if (open && state == State.CREATE && draftSession == session && color != null) draftColor = color;
                };
                colorPicker.openConsumer(draftColor, draftConsumer, x + WINDOW_WIDTH, ry);
            }
        } else {
            float localY = mouseY - ry - HEADER_HEIGHT + scroll;
            int row = (int) (localY / ROW_PITCH);
            if (row >= 0 && row <= ThemeManager.NAMED_THEMES.size() && localY - row * ROW_PITCH <= ROW_H) {
                ThemeManager.applyNamedTheme(row - 1);
                playSound("smooth_on");
            }
        }
        return true;
    }

    /** Route fixed-scaled mouse coordinates and the vertical wheel amount here. */
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!isInside(mouseX, mouseY)) return false;
        if (open && Double.isFinite(amount)) targetScroll = (float) Math.clamp(targetScroll - amount * ROW_PITCH, 0d, maxScroll());
        return true;
    }

    private boolean inViewport(float mouseX, float mouseY) {
        return hit(mouseX, mouseY, x + PADDING, renderY() + HEADER_HEIGHT, WINDOW_WIDTH - PADDING * 2f, viewHeight());
    }

    /** Consume all routed characters, including rejected input, to prevent fallthrough. */
    public boolean charTyped(char c) {
        if (!isOpen()) return false;
        if (isTyping() && !Character.isISOControl(c) && !Character.isSurrogate(c)
                && nameBuffer.length() < ThemeManager.MAX_NAME_LENGTH) nameBuffer += c;
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!isOpen()) return false;
        if (!open) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (state == State.CREATE) backToList();
            else close();
        } else if (state == State.CREATE) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameBuffer.isEmpty()) {
                nameBuffer = nameBuffer.substring(0, nameBuffer.offsetByCodePoints(nameBuffer.length(), -1));
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                acceptDraft();
            }
        }
        return true;
    }

    private void acceptDraft() {
        if (!open || state != State.CREATE || !ThemeManager.canAddNamedTheme(nameBuffer)) return;
        ThemeManager.addNamedTheme(nameBuffer, draftColor);
        backToList();
        playSound("smooth_on");
    }

    public void backToList() {
        if (!open || state != State.CREATE) return;
        discardDraft();
        state = State.LIST;
        scroll = targetScroll = 0f;
        clampLayout();
    }

    private void cancelPicker() {
        draftSession++;
        if (colorPicker != null && draftConsumer != null) colorPicker.closeConsumer(draftConsumer);
        draftConsumer = null;
    }

    private void discardDraft() {
        cancelPicker();
        nameBuffer = "";
        draftColor = ThemeManager.DEFAULT_ACCENT;
    }

    private boolean hit(float mx, float my, float rx, float ry, float rw, float rh) {
        return rw > 0f && rh > 0f && mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private String trim(String text, float maxWidth, float size, MsdfFont font) {
        if (text == null || text.isEmpty() || maxWidth <= 4f) return "";
        if (Msdf.width(font, text, size) <= maxWidth) return text;
        String trimmed = text;
        while (!trimmed.isEmpty() && Msdf.width(font, trimmed + "...", size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(trimmed.length(), -1));
        }
        return trimmed + "...";
    }

    private void playSound(String sound) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("minar", sound)), 1.0f, 1.0f));
        } catch (Exception ignored) {}
    }

    private Color withOpacity(Color color, float opacity) {
        if (color == null) color = ThemeManager.DEFAULT_ACCENT;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * Math.clamp(opacity, 0f, 1f)));
    }
}
