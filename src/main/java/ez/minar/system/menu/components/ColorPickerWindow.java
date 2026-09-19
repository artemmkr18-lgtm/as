package ez.minar.system.menu.components;

import ez.minar.system.menu.GuiTheme;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.awt.Color;

public class ColorPickerWindow {
    private static final float WINDOW_WIDTH = 180f;
    private static final float WINDOW_HEIGHT = 188f;
    private static final float RADIUS = 10f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float PADDING = 10f;

    private static final float FIELD_WIDTH = 160f;
    private static final float FIELD_HEIGHT = 92f;
    private static final float FIELD_RADIUS = 6f;

    private static final float HUE_HEIGHT = 10f;
    private static final float HUE_RADIUS = 5f;
    private static final int HUE_SEGMENTS = 40;

    private static final Color[] PRESETS = {
            new Color(255, 255, 255), // White
            new Color(255, 59, 48),   // Red
            new Color(255, 149, 0),   // Orange
            new Color(255, 204, 0),   // Yellow
            new Color(52, 199, 89),   // Green
            new Color(50, 173, 230),  // Cyan
            new Color(0, 122, 255),   // Blue
            new Color(175, 82, 222),  // Purple
            new Color(255, 45, 85),   // Pink
    };

    private ColorSetting setting;
    private java.util.function.Consumer<Color> consumer; // external mode (theme menu): no setting bound
    private boolean open;
    private float anim;
    private long lastAnimTime;

    private float x = 100f;
    private float y = 100f;

    private boolean draggingHeader;
    private float dragOffsetX;
    private float dragOffsetY;

    private boolean draggingField;
    private boolean draggingHue;

    private float hue;
    private float saturation;
    private float brightness = 1.0f;
    private int lastRgb;
    private String title = "Выбор цвета";

    public void open(ColorSetting setting, float targetX, float targetY) {
        if (setting == null) return;
        if (this.open && this.setting == setting) {
            close();
            return;
        }

        this.setting = setting;
        this.consumer = null;
        Color col = setting.getColor();
        if (col == null) col = Color.WHITE;
        readHsb(col);

        positionAt(targetX, targetY);
        this.title = setting.getDisplayName();
        this.open = true;
        playSound("setting");
    }

    /** Opens the picker without a ColorSetting: every change is streamed to `live`. */
    public void openConsumer(Color initial, java.util.function.Consumer<Color> live, float targetX, float targetY) {
        this.setting = null;
        this.consumer = live;
        Color col = initial != null ? initial : Color.WHITE;
        readHsb(col);

        positionAt(targetX, targetY);
        this.title = ez.minar.system.managers.LocalizationManager.get("Цвет темы");
        this.open = true;
        playSound("setting");
    }

    private void readHsb(Color col) {
        if (col == null) col = Color.WHITE;
        float[] hsb = Color.RGBtoHSB(col.getRed(), col.getGreen(), col.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        lastRgb = col.getRGB();
    }

    private void positionAt(float targetX, float targetY) {
        mouseReleased();
        lastAnimTime = System.currentTimeMillis();
        float screenW = RenderUtil.getFixedScaledWidth();
        float screenH = RenderUtil.getFixedScaledHeight();

        float preferredX = targetX + 15f;
        float preferredY = targetY - 20f;

        if (preferredX + WINDOW_WIDTH > screenW - 10f) {
            preferredX = targetX - WINDOW_WIDTH - 15f;
        }
        this.x = Math.clamp(preferredX, 10f, Math.max(10f, screenW - WINDOW_WIDTH - 10f));
        this.y = Math.clamp(preferredY, 10f, Math.max(10f, screenH - WINDOW_HEIGHT - 10f));
    }

    /** Detach only the caller's picker session, never a subsequently opened setting. */
    public void closeConsumer(java.util.function.Consumer<Color> owner) {
        if (owner != null && consumer == owner) close();
    }

    public void close() {
        consumer = null;
        mouseReleased();
        if (!open) return;
        open = false;
        playSound("settingclose");
    }

    public void reset() {
        this.open = false;
        this.anim = 0f;
        this.lastAnimTime = 0L;
        this.setting = null;
        this.consumer = null;
        this.draggingHeader = false;
        this.draggingField = false;
        this.draggingHue = false;
    }

    public boolean isOpen() {
        return open || anim > 0.01f;
    }

    public boolean isInside(float mouseX, float mouseY) {
        if (!open) return false;
        return mouseX >= x && mouseX <= x + WINDOW_WIDTH && mouseY >= y && mouseY <= y + WINDOW_HEIGHT;
    }

    public boolean isDragging() {
        return draggingHeader || draggingField || draggingHue;
    }

    public ColorSetting getSetting() {
        return setting;
    }

    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (lastAnimTime == 0L) lastAnimTime = now;
        float dt = Math.clamp(now - lastAnimTime, 0L, 50L) / 1000f;
        lastAnimTime = now;

        float targetAnim = open ? 1f : 0f;
        anim = anim + (targetAnim - anim) * (1f - (float) Math.exp(-dt * 20.0f));
        if (!open && anim <= 0.01f) {
            reset();
            return;
        }

        if (open && setting != null) {
            Color col = setting.getColor();
            if (col == null) col = Color.WHITE;
            if (col.getRGB() != lastRgb) readHsb(col);
        }

        float ease = Easings.OutCubic(anim);
        float opacity = anim;
        float renderY = y + (1f - ease) * 6f;

        // 1. Shadow
        RenderUtil.shadow(x, renderY + 2f, WINDOW_WIDTH, WINDOW_HEIGHT, RADIUS, 12f, 0.35f * opacity, 2f, new Color(0, 0, 0, 190));

        // Flat, opaque at rest; no blur or glass behind the color controls.
        RenderUtil.rect(x, renderY, WINDOW_WIDTH, WINDOW_HEIGHT, RADIUS, withOpacity(new Color(28, 28, 28), opacity));
        RenderUtil.rect(x + 5f, renderY + 4f, WINDOW_WIDTH - 10f, HEADER_HEIGHT - 5f, 6f,
                withOpacity(new Color(23, 23, 23), opacity));
        RenderUtil.outline(x, renderY, WINDOW_WIDTH, WINDOW_HEIGHT, RADIUS, 0.85f,
                withOpacity(new Color(255, 255, 255, 14), opacity));

        // Header text remains stable during closing.
        RenderUtil.text(context, Msdf.SF_BOLD, x + PADDING, renderY + 6.5f, ez.minar.system.managers.LocalizationManager.get(title), 9.5f, withOpacity(Color.WHITE, opacity));

        // Close button
        float closeX = x + WINDOW_WIDTH - 21f;
        float closeY = renderY + 4f;
        float closeSize = 16f;
        boolean closeHovered = open && mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize;
        if (closeHovered) {
            RenderUtil.rect(closeX, closeY, closeSize, closeSize, 4f, withOpacity(new Color(255, 255, 255, 25), opacity));
        }
        RenderUtil.text(context, Msdf.SF_BOLD, closeX + 4.5f, closeY + 3.5f, "✕", 8f, withOpacity(closeHovered ? Color.WHITE : GuiTheme.TEXT_DIM, opacity));

        // Header divider
        RenderUtil.rect(x + 8f, renderY + HEADER_HEIGHT, WINDOW_WIDTH - 16f, 0.8f, 0.4f, withOpacity(GuiTheme.DIVIDER, opacity * 0.85f));

        // 7. SV Field
        float fieldX = x + PADDING;
        float fieldY = renderY + 28f;
        Color topLeft = Color.WHITE;
        Color topMid = mix(Color.WHITE, Color.getHSBColor(hue, 1f, 1f), 0.5f);
        Color topRight = Color.getHSBColor(hue, 1f, 1f);
        Color midLeft = new Color(128, 128, 128);
        Color midMid = Color.getHSBColor(hue, 0.5f, 0.5f);
        Color midRight = Color.getHSBColor(hue, 1f, 0.5f);
        Color black = Color.BLACK;

        RenderUtil.rect(fieldX, fieldY, FIELD_WIDTH, FIELD_HEIGHT, FIELD_RADIUS,
                withOpacity(topLeft, opacity),
                withOpacity(topMid, opacity),
                withOpacity(topRight, opacity),
                withOpacity(midLeft, opacity),
                withOpacity(midMid, opacity),
                withOpacity(midRight, opacity),
                withOpacity(black, opacity),
                withOpacity(black, opacity),
                withOpacity(black, opacity));
        RenderUtil.outline(fieldX, fieldY, FIELD_WIDTH, FIELD_HEIGHT, FIELD_RADIUS, 0.8f,
                withOpacity(new Color(255, 255, 255, 40), opacity));

        // Marker ring in SV field
        float markerX = fieldX + saturation * FIELD_WIDTH;
        float markerY = fieldY + (1f - brightness) * FIELD_HEIGHT;
        RenderUtil.shadow(markerX - 4.5f, markerY - 4.5f, 9f, 9f, 4.5f, 3f, 0.4f * opacity, 0.8f, Color.BLACK);
        RenderUtil.circleOutline(markerX, markerY, 4.5f, 1.5f, withOpacity(Color.WHITE, opacity));

        // 8. Hue Slider
        float hueX = x + PADDING;
        float hueY = renderY + 124f;
        float dotW = HUE_HEIGHT;
        float step = (FIELD_WIDTH - dotW) / (float) (HUE_SEGMENTS - 1);
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            float progress = (float) i / (float) (HUE_SEGMENTS - 1);
            Color hueCol = Color.getHSBColor(progress, 1f, 1f);
            RenderUtil.rect(hueX + i * step, hueY, dotW, HUE_HEIGHT, HUE_RADIUS, withOpacity(hueCol, opacity));
        }
        RenderUtil.outline(hueX, hueY, FIELD_WIDTH, HUE_HEIGHT, HUE_RADIUS, 0.8f, withOpacity(new Color(255, 255, 255, 50), opacity));

        // Hue thumb
        float thumbX = hueX + hue * (FIELD_WIDTH - 6f);
        RenderUtil.shadow(thumbX, hueY - 1.5f, 6f, HUE_HEIGHT + 3f, 3f, 3f, 0.35f * opacity, 0.6f, Color.BLACK);
        RenderUtil.rect(thumbX, hueY - 1.5f, 6f, HUE_HEIGHT + 3f, 3f, withOpacity(Color.WHITE, opacity));
        RenderUtil.outline(thumbX, hueY - 1.5f, 6f, HUE_HEIGHT + 3f, 3f, 0.7f, withOpacity(new Color(0, 0, 0, 70), opacity));

        // 9. Preview & Info Row
        float infoY = renderY + 138f;
        Color currentColor = Color.getHSBColor(hue, saturation, brightness);
        float previewW = 28f;
        float previewH = 17f;
        RenderUtil.shadow(x + PADDING, infoY + 0.5f, previewW, previewH, 4f, 2.5f, 0.2f * opacity, 0.8f, Color.BLACK);
        RenderUtil.rect(x + PADDING, infoY, previewW, previewH, 4f, withOpacity(currentColor, opacity));
        RenderUtil.outline(x + PADDING, infoY, previewW, previewH, 4f, 0.8f, withOpacity(new Color(255, 255, 255, 80), opacity));

        String hex = String.format("#%02X%02X%02X", currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue());
        RenderUtil.text(context, Msdf.SF_BOLD, x + PADDING + previewW + 8f, infoY + 0.5f, hex, 8.5f, withOpacity(Color.WHITE, opacity));

        String rgb = currentColor.getRed() + ", " + currentColor.getGreen() + ", " + currentColor.getBlue();
        RenderUtil.text(context, x + PADDING + previewW + 8f, infoY + 9.5f, rgb, 7.5f, withOpacity(GuiTheme.TEXT_DIM, opacity));

        // 10. Presets Row
        float palY = renderY + 160f;
        RenderUtil.rect(x + 8f, palY, WINDOW_WIDTH - 16f, 0.6f, 0.3f, withOpacity(GuiTheme.DIVIDER, opacity * 0.7f));

        float presetY = renderY + 167f;
        float presetSize = 12f;
        float presetGap = (FIELD_WIDTH - PRESETS.length * presetSize) / (float) (PRESETS.length - 1);
        for (int i = 0; i < PRESETS.length; i++) {
            float px = fieldX + i * (presetSize + presetGap);
            boolean hovered = open && mouseX >= px && mouseX <= px + presetSize && mouseY >= presetY && mouseY <= presetY + presetSize;
            if (hovered) {
                RenderUtil.shadow(px - 1f, presetY - 1f, presetSize + 2f, presetSize + 2f, 4f, 2f, 0.25f * opacity, 0.5f, PRESETS[i]);
            }
            RenderUtil.rect(px, presetY, presetSize, presetSize, 3f, withOpacity(PRESETS[i], opacity));
            RenderUtil.outline(px, presetY, presetSize, presetSize, 3f, 0.7f, withOpacity(hovered ? Color.WHITE : new Color(255, 255, 255, 60), opacity));
        }
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!open || button != 0) return false;

        // 1. Close button
        float closeX = x + WINDOW_WIDTH - 21f;
        float closeY = y + 4f;
        float closeSize = 16f;
        if (mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize) {
            close();
            return true;
        }

        // 2. Header drag
        if (mouseX >= x && mouseX <= x + WINDOW_WIDTH - 22f && mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
            draggingHeader = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return true;
        }

        // 3. SV Field
        float fieldX = x + PADDING;
        float fieldY = y + 28f;
        if (mouseX >= fieldX && mouseX <= fieldX + FIELD_WIDTH && mouseY >= fieldY && mouseY <= fieldY + FIELD_HEIGHT) {
            draggingField = true;
            updateSV(mouseX, mouseY);
            return true;
        }

        // 4. Hue Slider
        float hueX = x + PADDING;
        float hueY = y + 124f;
        if (mouseX >= hueX && mouseX <= hueX + FIELD_WIDTH && mouseY >= hueY - 3f && mouseY <= hueY + HUE_HEIGHT + 3f) {
            draggingHue = true;
            updateHue(mouseX);
            return true;
        }

        // 5. Presets
        float presetY = y + 167f;
        float presetSize = 12f;
        float presetGap = (FIELD_WIDTH - PRESETS.length * presetSize) / (float) (PRESETS.length - 1);
        for (int i = 0; i < PRESETS.length; i++) {
            float px = fieldX + i * (presetSize + presetGap);
            if (mouseX >= px && mouseX <= px + presetSize && mouseY >= presetY && mouseY <= presetY + presetSize) {
                applyPreset(PRESETS[i]);
                playSound("setting");
                return true;
            }
        }

        // 6. Click inside window consumes the click
        if (isInside(mouseX, mouseY)) {
            return true;
        }

        return false;
    }

    public boolean mouseDragged(float mouseX, float mouseY, int button) {
        if (!open || button != 0) return false;

        float screenW = RenderUtil.getFixedScaledWidth();
        float screenH = RenderUtil.getFixedScaledHeight();

        if (draggingHeader) {
            this.x = Math.clamp(mouseX - dragOffsetX, 4f, Math.max(4f, screenW - WINDOW_WIDTH - 4f));
            this.y = Math.clamp(mouseY - dragOffsetY, 4f, Math.max(4f, screenH - WINDOW_HEIGHT - 4f));
            return true;
        }

        if (draggingField) {
            updateSV(mouseX, mouseY);
            return true;
        }

        if (draggingHue) {
            updateHue(mouseX);
            return true;
        }

        return false;
    }

    public void mouseReleased() {
        draggingHeader = false;
        draggingField = false;
        draggingHue = false;
    }

    private void updateSV(float mouseX, float mouseY) {
        float fieldX = x + PADDING;
        float fieldY = y + 28f;
        this.saturation = Math.clamp((mouseX - fieldX) / FIELD_WIDTH, 0f, 1f);
        this.brightness = 1f - Math.clamp((mouseY - fieldY) / FIELD_HEIGHT, 0f, 1f);
        applyColor();
    }

    private void updateHue(float mouseX) {
        float hueX = x + PADDING;
        this.hue = Math.clamp((mouseX - hueX) / FIELD_WIDTH, 0f, 1f);
        applyColor();
    }

    private void applyColor() {
        if (!open) return;
        Color color = Color.getHSBColor(hue, saturation, brightness);
        this.lastRgb = color.getRGB();
        if (consumer != null) {
            consumer.accept(color);
        } else if (setting != null) {
            setting.setColor(color);
        }
    }

    private void applyPreset(Color color) {
        if (!open || color == null) return;
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.lastRgb = color.getRGB();
        if (consumer != null) {
            consumer.accept(color);
        } else if (setting != null) {
            setting.setColor(color);
        }
    }

    private void playSound(String sound) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("minar", sound)), 1.0f, 1.0f)
            );
        } catch (Exception ignored) {}
    }

    private Color mix(Color first, Color second, float progress) {
        float clamped = Math.clamp(progress, 0f, 1f);
        int red = (int) (first.getRed() + (second.getRed() - first.getRed()) * clamped);
        int green = (int) (first.getGreen() + (second.getGreen() - first.getGreen()) * clamped);
        int blue = (int) (first.getBlue() + (second.getBlue() - first.getBlue()) * clamped);
        int alpha = (int) (first.getAlpha() + (second.getAlpha() - first.getAlpha()) * clamped);
        return new Color(red, green, blue, alpha);
    }

    private Color withOpacity(Color color, float opacity) {
        int alpha = (int) (color.getAlpha() * Math.clamp(opacity, 0f, 1f));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

}
