package ez.minar.system.features.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.MultiSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.pipeline.TexturePipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@NewFunction(name = "Watermark", desc = "Ватермарка Nexoria: три режима, перемещение в чате", category = Category.RENDER)
public class Watermark extends Function {
    private static final String CLIENT_NAME = "Nexoria";
    private static final List<String> MESSAGES = List.of(
            "Обновленная Версия!",
            "Лучший, Бесплатный, Актуальный!",
            "Всегда впереди!",
            "Гарантия качества!",
            "Мы слушаем вас.",
            "Только качество!",
            "Обходы, обходы, обходы...",
            "Мы всегда свами!",
            "MetaHvh - лучший сервер для HvH!",
            "/code upgrade - дает донат и рилики!",
            "Nexoria Upgrade"
    );
    private static final Identifier STAR = Identifier.of("minar", "icons/103_lucide_star.png");

    private final ModeSetting mode = new ModeSetting("Режим", "Новый", "Старый", "Прозрачная");
    private final TextSetting caption = new TextSetting("Своя надпись", CLIENT_NAME);
    private final MultiSetting elements = new MultiSetting("Элементы", "Фпс", "Сервер", "Юзер");
    private final ModeSetting oldStyle = new ModeSetting("Стиль", "Старый", "Новый");
    private final BooleanSetting bloomNew = new BooleanSetting("Блум", true);
    private final BooleanSetting bloomBackground = new BooleanSetting("Блум заднего фона", false);
    private final NumberSetting saturation = new NumberSetting("Насыщенность", 0.5, 0, 1, 0.1);
    private final NumberSetting glow = new NumberSetting("Свечение", 5, 0, 15, 1);
    private final BooleanSetting bloomTransparent = new BooleanSetting("Блум прозрачной", true);
    private final NumberSetting saturationTransparent = new NumberSetting("Насыщенность прозрачной", 0.5, 0, 1, 0.1);
    private final NumberSetting glowTransparent = new NumberSetting("Свечение прозрачной", 5, 0, 15, 1);
    private final NumberSetting positionX = new NumberSetting("Позиция X", 200, 0, 16384, 1);
    private final NumberSetting positionY = new NumberSetting("Позиция Y", 200, 0, 16384, 1);

    final HudDrag drag = new HudDrag(positionX, positionY);
    private boolean shown = true;
    private final TypingText animatedCaption = new TypingText(3000);
    private final TypingText animatedMessage = new TypingText(2000);
    private JsonObject fontMetrics;
    private boolean fontsChecked;
    private float displayedFps;
    private long lastFrame;

    public Watermark() {
        addSettings(mode, caption, elements, oldStyle, bloomNew, bloomBackground, saturation, glow,
                bloomTransparent, saturationTransparent, glowTransparent, positionX, positionY);
        positionX.setVisible(false);
        positionY.setVisible(false);
        mode.runnable(this::updateVisibility);
    }

    private void updateVisibility() {
        boolean old = mode.isEnabled("Старый");
        boolean transparent = mode.isEnabled("Прозрачная");
        caption.setVisible(old);
        elements.setVisible(old);
        oldStyle.setVisible(old);
        bloomNew.setVisible(!old && !transparent);
        bloomBackground.setVisible(!old && !transparent);
        saturation.setVisible(!old && !transparent);
        glow.setVisible(!old && !transparent);
        bloomTransparent.setVisible(transparent);
        saturationTransparent.setVisible(transparent);
        glowTransparent.setVisible(transparent);
        if (!shown) getSettings().forEach(setting -> setting.setVisible(false));
        resetInteraction();
    }

    @Override
    public void onEnable() {
        lastFrame = 0L;
        animatedCaption.reset();
        animatedMessage.reset();
        resetInteraction();
    }

    @Override
    public void onDisable() {
        resetInteraction();
    }

    private void resetInteraction() {
        drag.cancel();
    }

    void setShown(boolean value) {
        shown = value;
        updateVisibility();
        if (!shown) getSettings().forEach(setting -> setting.setVisible(false));
        else mode.setVisible(true);
    }

    public void renderHud(Render2DEvent event) {
        DrawContext context = event.getContext();
        if (context == null || mc.player == null || mc.world == null || mc.options.hudHidden
                || mc.getDebugHud().shouldShowDebugHud()) {
            resetInteraction();
            return;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) {
            resetInteraction();
            return;
        }
        loadFonts();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 1f / 60f : Math.clamp((now - lastFrame) / 1000f, 0f, 0.1f);
        lastFrame = now;
        displayedFps += (mc.getCurrentFps() - displayedFps) * (1f - (float) Math.exp(-1.52f * dt));

        if (mode.isEnabled("Старый")) renderOld(context, now);
        else renderPixel(context, now, mode.isEnabled("Прозрачная"));
    }

    private void renderOld(DrawContext context, long now) {
        String custom = caption.getValue();
        animatedCaption.update(List.of(CLIENT_NAME, custom), now);
        List<String> parts = new ArrayList<>();
        parts.add(custom.equals(CLIENT_NAME) ? CLIENT_NAME : animatedCaption.value());
        if (elements.isEnabled("Фпс")) parts.add((int) displayedFps + "fps");
        if (elements.isEnabled("Сервер")) {
            var server = mc.getCurrentServerEntry();
            parts.add(server == null || "45.93.200.8:25610".equals(server.address) ? "local" : server.address);
        }
        if (elements.isEnabled("Юзер")) parts.add(mc.player.getName().getString());
        String text = String.join(" - ", parts);
        float width = textWidth("inter", text) + 20f;
        float height = 15f;
        float[] position = updatePosition(width, height);
        float x = position[0], y = position[1];
        if (oldStyle.isEnabled("Новый")) {
            RenderUtil.rect(x, y, width, height, 3f, new Color(0, 0, 0, 200));
        } else {
            Color color = themeColor(0, now);
            RenderUtil.shadow(x, y, width, height, 2f, 8f, 0.25f, 2f, alpha(color, 64));
            RenderUtil.rect(x, y, width, height, 2f, alpha(mix(Color.BLACK, color, 0.35f), 128));
            RenderUtil.outline(x, y, width, height, 2f, 0.5f, alpha(color, 100));
        }
        RenderUtil.texture(x + 3f, y + 2.5f, 6f, STAR, 0f, Color.WHITE);
        RenderUtil.texture(x + 8f, y + 5f, 5f, STAR, 0f, Color.WHITE);
        RenderUtil.texture(x + 4.5f, y + 8.5f, 4f, STAR, 0f, Color.WHITE);
        drawText(context, "inter", text, x + 16f, y + (height - fontHeight("inter")) / 2f, Color.WHITE);
    }

    private void renderPixel(DrawContext context, long now, boolean transparent) {
        animatedMessage.update(MESSAGES, now);
        String title = CLIENT_NAME + " " + (int) displayedFps + (transparent ? "ФПС" : " ФПС");
        String subtitle = animatedMessage.value();
        float width = Math.max(textWidth("pixel", title), textWidth("pixel_small", subtitle)) + 7.5f;
        float height = 22f;
        float[] position = updatePosition(width, height);
        float x = position[0], y = position[1];
        float amount = (float) (transparent ? saturationTransparent.getValue() : saturation.getValue());
        float shadow = (float) (transparent ? glowTransparent.getValue() : glow.getValue());
        Color[] background = new Color[4];
        Color[] face = new Color[4];
        for (int i = 0; i < 4; i++) {
            background[i] = themeColor(i * 90, now);
            face[i] = mix(new Color(255, 255, 255, transparent ? 50 : 255), background[i], amount);
        }
        if (!transparent) {
            panel(x, y + 2f, width - 2f, height - 2f, 3f, shadow, bloomBackground.isEnabled(), background);
        }
        panel(x + 2f, y, width - 2f, height - 2f, 2f, shadow,
                transparent ? bloomTransparent.isEnabled() : bloomNew.isEnabled(), face);
        Color textShadow = alpha(mix(Color.BLACK, background[0], transparent ? 0.4f : 0.5f), 128);
        drawText(context, "pixel", title, x + 5.5f, y + 0.5f, textShadow);
        drawText(context, "pixel", title, x + 5f, y, Color.BLACK);
        float subtitleY = y + height - fontHeight("pixel_small") - 2f;
        drawText(context, "pixel_small", subtitle, x + 5.25f, subtitleY + 0.25f, textShadow);
        drawText(context, "pixel_small", subtitle, x + 5f, subtitleY, new Color(20, 20, 20));
    }

    private void panel(float x, float y, float width, float height, float radius, float shadow, boolean bloom, Color[] c) {
        if (bloom && shadow > 0f) {
            RenderUtil.shadow(x, y, width, height, radius, shadow, 0.45f, 1f, alpha(c[0], 140));
        }
        Color top = mix(c[0], c[1], 0.5f);
        Color bottom = mix(c[3], c[2], 0.5f);
        RenderUtil.rect(x, y, width, height, radius,
                c[0], top, c[1], mix(c[0], c[3], 0.5f), mix(top, bottom, 0.5f), mix(c[1], c[2], 0.5f),
                c[3], bottom, c[2]);
    }

    private Color themeColor(int phase, long now) {
        Color first = ThemeManager.getGuiAccent();
        Color second = ThemeManager.twoColors ? ThemeManager.Theme_Color2 : mix(first, Color.WHITE, 0.25f);
        float t = (float) (0.5 + 0.5 * Math.sin(now / 1000.0 * Math.PI + Math.toRadians(phase)));
        return mix(first, second, t);
    }

    private float[] updatePosition(float width, float height) {
        return drag.place(width, height);
    }

    private void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;
        try (var stream = Watermark.class.getResourceAsStream("/assets/minar/textures/watermark/metrics.json")) {
            if (stream != null) {
                fontMetrics = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
            fontMetrics = null;
        }
    }

    private float fontHeight(String name) {
        if (fontMetrics == null) return name.equals("pixel") ? 12f : 9f;
        return fontMetrics.getAsJsonObject(name).get("height").getAsFloat();
    }

    private float textWidth(String font, String text) {
        if (fontMetrics == null) return Msdf.width(Msdf.SF_BOLD, text, fontHeight(font));
        JsonObject glyphs = fontMetrics.getAsJsonObject(font).getAsJsonObject("glyphs");
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            JsonObject glyph = glyphs.getAsJsonObject(String.valueOf(text.charAt(i)));
            if (glyph == null) glyph = glyphs.getAsJsonObject("?");
            width += glyph.get("advance").getAsFloat();
        }
        return width;
    }

    private void drawText(DrawContext context, String font, String text, float x, float y, Color color) {
        if (fontMetrics == null) {
            RenderUtil.text(context, Msdf.SF_BOLD, x, y, text, fontHeight(font), color);
            return;
        }
        JsonObject glyphs = fontMetrics.getAsJsonObject(font).getAsJsonObject("glyphs");
        var texture = mc.getTextureManager().getTexture(Identifier.of("minar", "textures/watermark/" + font + ".png"));
        var projection = RenderUtil.createProjection();
        for (int i = 0; i < text.length(); i++) {
            JsonObject g = glyphs.getAsJsonObject(String.valueOf(text.charAt(i)));
            if (g == null) g = glyphs.getAsJsonObject("?");
            float w = g.get("w").getAsFloat(), h = g.get("h").getAsFloat();
            TexturePipeline.draw(projection, x - 1f, y - 1f, w / 2f, h / 2f, texture.getGlTextureView(),
                    color.getRGB(), 0f, 0f, g.get("x").getAsFloat() / 1024f,
                    g.get("y").getAsFloat() / 1024f, w / 1024f, h / 1024f, true);
            x += g.get("advance").getAsFloat();
        }
    }

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static class TypingText {
        private final long delay;
        private List<String> texts = List.of();
        private int textIndex;
        private int charIndex;
        private boolean forward = true;
        private long lastUpdate;

        private TypingText(long delay) {
            this.delay = delay;
        }

        private void reset() {
            textIndex = charIndex = 0;
            forward = true;
            lastUpdate = System.currentTimeMillis();
        }

        private void update(List<String> values, long now) {
            if (!texts.equals(values)) {
                texts = List.copyOf(values);
                reset();
            }
            if (texts.isEmpty() || now - lastUpdate < 100) return;
            lastUpdate = now;
            if (forward) {
                if (charIndex < texts.get(textIndex).length()) charIndex++;
                else {
                    forward = false;
                    lastUpdate = now + delay;
                }
            } else if (charIndex > 0) charIndex--;
            else {
                forward = true;
                textIndex = (textIndex + 1) % texts.size();
            }
        }

        private String value() {
            return texts.isEmpty() ? "" : texts.get(textIndex).substring(0, charIndex);
        }
    }
}
