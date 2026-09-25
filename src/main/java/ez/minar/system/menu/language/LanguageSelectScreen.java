package ez.minar.system.menu.language;

import ez.minar.system.managers.LanguageSelectManager;
import ez.minar.system.menu.ThemeManager;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfManager;
import ez.minar.utils.render.pipeline.TitleBackgroundPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class LanguageSelectScreen extends Screen {

    private static final Identifier ICON_GLOBE = Identifier.of("minar", "icons/175_lucide_globe.png");
    private static final Identifier ICON_CHECK = Identifier.of("minar", "icons/004_lucide_check.png");

    public static final Color GLASS_BG = new Color(24, 28, 38, 175);
    public static final Color GLASS_OUTLINE = new Color(255, 255, 255, 18);
    public static final float GLASS_BLUR = 14.0f;
    public static final float CARD_RADIUS = 10.0f;

    private static final float ANIMATION_DURATION = 420f;

    private final Screen parent;
    private long startTime;
    private long lastFrameTime;

    private float closeBtnHover = 0f;

    public static class LanguageCard {
        public final String code;
        public final String nativeName;
        public final String englishName;
        public final String tag;
        public final String buttonText;
        public final Identifier flagTexture;
        public float hoverProgress = 0f;
        public float clickScale = 0f;

        public LanguageCard(String code, String nativeName, String englishName, String tag, String buttonText, String flagFile) {
            this.code = code;
            this.nativeName = nativeName;
            this.englishName = englishName;
            this.tag = tag;
            this.buttonText = buttonText;
            this.flagTexture = Identifier.of("minar", "icons/flags/" + flagFile);
        }
    }

    private final List<LanguageCard> cards = new ArrayList<>();

    public LanguageSelectScreen(Screen parent) {
        super(Text.literal("Language Select"));
        this.parent = parent;

        // Box 1: Русский
        cards.add(new LanguageCard("ru_ru", "Русский", "Russian", "RU", "Выбрать", "ru.png"));
        // Box 2: English
        cards.add(new LanguageCard("en_us", "English", "English (US)", "EN", "Select", "en.png"));
        // Box 3: Polski
        cards.add(new LanguageCard("pl_pl", "Polski", "Polish", "PL", "Wybierz", "pl.png"));
    }

    @Override
    protected void init() {
        super.init();
        startTime = System.currentTimeMillis();
        lastFrameTime = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        TitleBackgroundPipeline.draw();
        float sw = RenderUtil.getFixedScaledWidth();
        float sh = RenderUtil.getFixedScaledHeight();
        RenderUtil.rect(0, 0, sw, sh, 0, new Color(0, 0, 0, 140));
    }

    private float calculateBaseY(float centerY, float entryAnim) {
        float naturalY = centerY - 50f;
        return Math.max(90f, naturalY) + (1f - entryAnim) * 30f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        MsdfManager.init();
        RenderUtil.beginBlurFrame();

        int scaledWidth = RenderUtil.getFixedScaledWidth();
        int scaledHeight = RenderUtil.getFixedScaledHeight();
        float mx = RenderUtil.convertX(mouseX);
        float my = RenderUtil.convertY(mouseY);

        long now = System.currentTimeMillis();
        float deltaMs = lastFrameTime == 0 ? 16f : Math.min(50f, now - lastFrameTime);
        lastFrameTime = now;

        float timePassed = now - startTime;
        float entryProgress = Math.min(1f, timePassed / ANIMATION_DURATION);
        float entryAnim = Easings.OutBack(entryProgress);
        float alpha = Math.min(1f, entryProgress * 1.5f);

        Color themeColor = ThemeManager.getThemeColor();
        String currentLang = "";
        if (client != null && client.getLanguageManager() != null && client.getLanguageManager().getLanguage() != null) {
            currentLang = client.getLanguageManager().getLanguage();
        }
        if (currentLang.isEmpty() && client != null && client.options != null && client.options.language != null) {
            currentLang = client.options.language;
        }
        if (currentLang.isEmpty()) {
            currentLang = LanguageSelectManager.getSelectedCode();
        }

        float centerX = scaledWidth / 2f;
        float centerY = scaledHeight / 2f;
        float baseY = calculateBaseY(centerY, entryAnim);

        // Header Title
        float headerY = baseY - 55f;
        float globeSize = 22f;
        float globeX = centerX - globeSize / 2f;
        float globeY = headerY - 30f;

        RenderUtil.glowCircle(centerX, globeY + globeSize / 2f, 38f, withAlpha(themeColor, 0.28f * alpha));
        try {
            RenderUtil.texture(globeX, globeY, globeSize, ICON_GLOBE, 0f, withAlpha(themeColor, alpha));
        } catch (Throwable ignored) {
            RenderUtil.circleOutline(centerX, globeY + globeSize / 2f, globeSize / 2f, 1.5f, withAlpha(themeColor, alpha));
        }

        RenderUtil.text(context, Msdf.SF_BOLD, centerX, headerY, "ВЫБОР ЯЗЫКА / CHOOSE LANGUAGE", 17f,
                withAlpha(Color.WHITE, alpha), "center");
        RenderUtil.text(context, Msdf.SF_REGULAR, centerX, headerY + 20f,
                "Выберите язык для продолжения • Choose language • Wybierz język", 11f,
                withAlpha(new Color(165, 172, 185), alpha), "center");

        // 3 Cards Layout
        float cardW = 190f;
        float cardH = 245f;
        float gap = 20f;
        float totalW = cards.size() * cardW + (cards.size() - 1) * gap;
        float startX = centerX - totalW / 2f;

        for (int i = 0; i < cards.size(); i++) {
            LanguageCard card = cards.get(i);

            // Update click feedback animation
            if (card.clickScale > 0f) {
                card.clickScale = Math.max(0f, card.clickScale - deltaMs * 0.008f);
            }

            float cardX = startX + i * (cardW + gap);
            boolean hovered = mx >= cardX && mx <= cardX + cardW && my >= baseY - 10f && my <= baseY + cardH;

            float targetHover = hovered ? 1f : 0f;
            card.hoverProgress += (targetHover - card.hoverProgress) * Math.min(1f, deltaMs * 0.014f);

            float lift = card.hoverProgress * 6f;
            float currentCardY = baseY - lift;

            renderCard(context, card, cardX, currentCardY, cardW, cardH, alpha, card.hoverProgress, card.clickScale,
                    currentLang.equalsIgnoreCase(card.code), themeColor, mx, my);
        }

        // Close / Back button (if parent screen is available or a language is already chosen)
        if (parent != null || LanguageSelectManager.hasSelectedLanguage()) {
            float closeW = 90f;
            float closeH = 28f;
            float closeX = centerX - closeW / 2f;
            float closeY = baseY + cardH + 18f;

            boolean closeHover = mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH;
            float targetCloseHover = closeHover ? 1f : 0f;
            closeBtnHover += (targetCloseHover - closeBtnHover) * Math.min(1f, deltaMs * 0.015f);

            RenderUtil.blur(closeX, closeY, closeW, closeH, 6f, 10f);
            RenderUtil.rect(closeX, closeY, closeW, closeH, 6f, withAlpha(new Color(255, 255, 255, 12), alpha));
            Color closeOutline = closeBtnHover > 0.01f
                    ? blendColors(new Color(255, 255, 255, 25), themeColor, closeBtnHover)
                    : new Color(255, 255, 255, 25);
            RenderUtil.outline(closeX, closeY, closeW, closeH, 6f, 0.5f, withAlpha(closeOutline, alpha));

            Color closeTextCol = closeBtnHover > 0.01f
                    ? blendColors(new Color(200, 205, 215), Color.WHITE, closeBtnHover)
                    : new Color(180, 185, 195);
            RenderUtil.text(context, Msdf.SF_REGULAR, centerX, closeY + 8f, ez.minar.system.managers.LocalizationManager.get("Назад / Back"), 10.5f,
                    withAlpha(closeTextCol, alpha), "center");
        }
    }

    private void renderCard(DrawContext context, LanguageCard card, float x, float y, float width, float height,
                            float screenAlpha, float hoverProgress, float clickScale, boolean isActive,
                            Color themeColor, float mx, float my) {
        float scaleOffset = clickScale * 2f;
        float actualX = x + scaleOffset;
        float actualY = y + scaleOffset;
        float actualW = width - scaleOffset * 2f;
        float actualH = height - scaleOffset * 2f;

        // Frosted glass blur underneath card
        RenderUtil.blur(actualX, actualY, actualW, actualH, CARD_RADIUS, GLASS_BLUR);

        // Card shadow / glow
        if (hoverProgress > 0.01f) {
            RenderUtil.shadow(actualX, actualY + 2f, actualW, actualH, CARD_RADIUS, 18f,
                    0.38f * hoverProgress * screenAlpha, 2.2f, themeColor);
        } else {
            RenderUtil.shadow(actualX, actualY + 2f, actualW, actualH, CARD_RADIUS, 10f,
                    0.20f * screenAlpha, 2.0f, new Color(0, 0, 0, 180));
        }

        // Frosted glass body
        Color bgColor = blendColors(GLASS_BG, new Color(30, 36, 50, 190), hoverProgress);
        RenderUtil.rect(actualX, actualY, actualW, actualH, CARD_RADIUS, withAlpha(bgColor, screenAlpha));

        // Glass outline rim
        Color rimColor = blendColors(GLASS_OUTLINE, withAlpha(themeColor, 0.85f), hoverProgress);
        RenderUtil.outline(actualX, actualY, actualW, actualH, CARD_RADIUS, 0.75f, withAlpha(rimColor, screenAlpha));

        // Active checkmark badge at top-right
        if (isActive) {
            float badgeX = actualX + actualW - 24f;
            float badgeY = actualY + 9f;
            RenderUtil.rect(badgeX, badgeY, 15f, 15f, 7.5f, withAlpha(new Color(34, 197, 94, 230), screenAlpha));
            try {
                RenderUtil.texture(badgeX + 1.5f, badgeY + 1.5f, 12f, ICON_CHECK, 0f, withAlpha(Color.WHITE, screenAlpha));
            } catch (Throwable ignored) {
                RenderUtil.rect(badgeX + 4.5f, badgeY + 4.5f, 6f, 6f, 3f, withAlpha(Color.WHITE, screenAlpha));
            }
        }

        // Flag badge container
        float flagW = 96f;
        float flagH = 64f;
        float flagX = actualX + (actualW - flagW) / 2f;
        float flagY = actualY + 22f;
        float flagRadius = 7f;

        // Shadow behind flag
        RenderUtil.shadow(flagX, flagY + 1.5f, flagW, flagH, flagRadius, 9f,
                0.32f * screenAlpha, 2.0f, new Color(0, 0, 0, 190));

        // Draw Flag Texture (with procedural fallback)
        renderFlag(card, flagX, flagY, flagW, flagH, flagRadius, screenAlpha);

        // Subtle flag glass rim outline
        RenderUtil.outline(flagX, flagY, flagW, flagH, flagRadius, 0.5f,
                withAlpha(new Color(255, 255, 255, 40), screenAlpha));

        // Language Title
        float textY = actualY + 104f;
        Color titleColor = blendColors(new Color(245, 247, 250), Color.WHITE, hoverProgress);
        RenderUtil.text(context, Msdf.SF_BOLD, actualX + actualW / 2f, textY, card.nativeName, 16f,
                withAlpha(titleColor, screenAlpha), "center");

        // Subtitle / English Name
        RenderUtil.text(context, Msdf.SF_REGULAR, actualX + actualW / 2f, textY + 22f, card.englishName, 10.5f,
                withAlpha(new Color(155, 162, 175), screenAlpha), "center");

        // Language Code Tag Badge
        float pillW = 32f;
        float pillH = 15f;
        float pillX = actualX + (actualW - pillW) / 2f;
        float pillY = textY + 41f;
        RenderUtil.rect(pillX, pillY, pillW, pillH, 4f, withAlpha(new Color(255, 255, 255, 16), screenAlpha));
        RenderUtil.outline(pillX, pillY, pillW, pillH, 4f, 0.5f, withAlpha(new Color(255, 255, 255, 26), screenAlpha));
        RenderUtil.text(context, Msdf.SF_BOLD, actualX + actualW / 2f, pillY + 3f, card.tag, 8.5f,
                withAlpha(new Color(205, 210, 220), screenAlpha), "center");

        // Action Button
        float btnW = actualW - 28f;
        float btnH = 30f;
        float btnX = actualX + 14f;
        float btnY = actualY + actualH - btnH - 16f;
        float btnRadius = 7f;

        boolean btnHovered = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
        float btnProgress = Math.max(hoverProgress, btnHovered ? 1f : 0f);

        Color btnBg = blendColors(new Color(255, 255, 255, 14), withAlpha(themeColor, 0.85f), btnProgress);
        RenderUtil.rect(btnX, btnY, btnW, btnH, btnRadius, withAlpha(btnBg, screenAlpha));

        Color btnRim = blendColors(new Color(255, 255, 255, 28), Color.WHITE, btnProgress);
        RenderUtil.outline(btnX, btnY, btnW, btnH, btnRadius, 0.6f, withAlpha(btnRim, screenAlpha));

        if (btnProgress > 0.01f) {
            RenderUtil.shadow(btnX, btnY + 1f, btnW, btnH, btnRadius, 12f,
                    0.26f * btnProgress * screenAlpha, 1.8f, themeColor);
        }

        Color btnTextCol = blendColors(new Color(225, 230, 240), Color.WHITE, btnProgress);
        RenderUtil.text(context, Msdf.SF_BOLD, actualX + actualW / 2f, btnY + 9f, card.buttonText, 11.5f,
                withAlpha(btnTextCol, screenAlpha), "center");
    }

    private void renderFlag(LanguageCard card, float x, float y, float width, float height, float radius, float alpha) {
        boolean textureRendered = false;
        try {
            var client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                var tex = client.getTextureManager().getTexture(card.flagTexture);
                if (tex != null && tex.getGlTextureView() != null) {
                    RenderUtil.texture(x, y, width, height, card.flagTexture, radius, withAlpha(Color.WHITE, alpha));
                    textureRendered = true;
                }
            }
        } catch (Throwable ignored) {
        }

        if (!textureRendered) {
            renderProceduralFlag(card.code, x, y, width, height, radius, alpha);
        }
    }

    private void renderProceduralFlag(String code, float x, float y, float w, float h, float r, float alpha) {
        if ("ru_ru".equalsIgnoreCase(code)) {
            // White, Blue, Red
            float sh = h / 3f;
            RenderUtil.rect(x, y, w, sh, r, r, 0f, 0f, withAlpha(Color.WHITE, alpha));
            RenderUtil.rect(x, y + sh, w, sh, 0f, withAlpha(new Color(0, 57, 166), alpha));
            RenderUtil.rect(x, y + sh * 2f, w, h - sh * 2f, 0f, 0f, r, r, withAlpha(new Color(213, 43, 30), alpha));
        } else if ("pl_pl".equalsIgnoreCase(code)) {
            // White, Red
            float sh = h / 2f;
            RenderUtil.rect(x, y, w, sh, r, r, 0f, 0f, withAlpha(Color.WHITE, alpha));
            RenderUtil.rect(x, y + sh, w, h - sh, 0f, 0f, r, r, withAlpha(new Color(220, 20, 60), alpha));
        } else {
            // English flag representation
            RenderUtil.rect(x, y, w, h, r, withAlpha(new Color(1, 33, 105), alpha));
            float crossW = 10f;
            RenderUtil.rect(x + (w - crossW) / 2f, y, crossW, h, 0f, withAlpha(Color.WHITE, alpha));
            RenderUtil.rect(x, y + (h - crossW) / 2f, w, crossW, 0f, withAlpha(Color.WHITE, alpha));
            float innerW = 6f;
            RenderUtil.rect(x + (w - innerW) / 2f, y, innerW, h, 0f, withAlpha(new Color(200, 16, 46), alpha));
            RenderUtil.rect(x, y + (h - innerW) / 2f, w, innerW, 0f, withAlpha(new Color(200, 16, 46), alpha));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.button();
        if (button != 0) {
            return super.mouseClicked(click, doubled);
        }

        float scaledWidth = RenderUtil.getFixedScaledWidth();
        float scaledHeight = RenderUtil.getFixedScaledHeight();
        float mx = RenderUtil.convertX((float) click.x());
        float my = RenderUtil.convertY((float) click.y());

        float centerX = scaledWidth / 2f;
        float centerY = scaledHeight / 2f;

        float entryProgress = Math.min(1f, (System.currentTimeMillis() - startTime) / ANIMATION_DURATION);
        float entryAnim = Easings.OutBack(entryProgress);
        float baseY = calculateBaseY(centerY, entryAnim);

        float cardW = 190f;
        float cardH = 245f;
        float gap = 20f;
        float totalW = cards.size() * cardW + (cards.size() - 1) * gap;
        float startX = centerX - totalW / 2f;

        for (int i = 0; i < cards.size(); i++) {
            LanguageCard card = cards.get(i);
            float cardX = startX + i * (cardW + gap);
            float currentY = baseY - card.hoverProgress * 6f;

            if (mx >= cardX && mx <= cardX + cardW && my >= currentY && my <= currentY + cardH) {
                card.clickScale = 1.0f;
                chooseLanguage(card.code);
                return true;
            }
        }

        // Close button check
        if (parent != null || LanguageSelectManager.hasSelectedLanguage()) {
            float closeW = 90f;
            float closeH = 28f;
            float closeX = centerX - closeW / 2f;
            float closeY = baseY + cardH + 18f;
            if (mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH) {
                close();
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void playClickSound() {
        if (client != null && client.getSoundManager() != null) {
            client.getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f)
            );
        }
    }

    private void chooseLanguage(String code) {
        playClickSound();
        LanguageSelectManager.selectLanguage(code);
        if (client != null) {
            client.setScreen(parent != null ? parent : new TitleScreen());
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        playClickSound();
        if (client != null) {
            client.setScreen(parent != null ? parent : new TitleScreen());
        }
    }

    private static Color withAlpha(Color color, float a) {
        int alpha = Math.clamp(Math.round(color.getAlpha() * Math.clamp(a, 0f, 1f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color blendColors(Color c1, Color c2, float ratio) {
        float f = Math.clamp(ratio, 0f, 1f);
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * f);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * f);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * f);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * f);
        return new Color(r, g, b, a);
    }
}
