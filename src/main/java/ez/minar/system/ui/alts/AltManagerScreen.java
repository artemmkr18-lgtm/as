package ez.minar.system.ui.alts;

import ez.minar.mixins.interfaces.IMinecraftClient;
import ez.minar.system.managers.AltManager;
import ez.minar.system.managers.AltManager.AltAccount;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.SkinUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.pipeline.TexturePipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.blaze3d.systems.RenderSystem;

public class AltManagerScreen extends Screen {

    private final Screen parent;
    private String nameInput = "";
    private boolean nameFocused = false;
    private float scrollY = 0;
    
    private long startTime;
    private long lastFrameTime;
    
    private final Map<String, Float> itemHoverStates = new HashMap<>();
    private String editingAlt = null;
    
    private float hoverSelectorY = 0f;
    private float hoverSelectorAlpha = 0f;
    
    private final Map<String, Float> clickScales = new HashMap<>();
    private final Map<String, Float> hoverScales = new HashMap<>();
    
    private String displayedCurrentName = "";
    private String prevCurrentName = "";
    private long currentNameChangeStart = 0L;
    private final Map<String, Long> accountAddTimes = new HashMap<>();
    private final float[] charOpacities = new float[16];

    private static final float ANIMATION_DURATION = 400f;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        startTime = System.currentTimeMillis();
        lastFrameTime = System.currentTimeMillis();
    }

    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("minar", "images/background.png");

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        ez.minar.utils.render.pipeline.TitleBackgroundPipeline.draw();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int scaledWidth = RenderUtil.getFixedScaledWidth();
        int scaledHeight = RenderUtil.getFixedScaledHeight();
        float mx = RenderUtil.convertX(mouseX);
        float my = RenderUtil.convertY(mouseY);

        long currentTime = System.currentTimeMillis();
        float deltaMs = (float) (currentTime - lastFrameTime);
        lastFrameTime = currentTime;

        for (Map.Entry<String, Float> entry : clickScales.entrySet()) {
            float val = entry.getValue();
            if (val > 0) {
                entry.setValue(Math.max(0f, val - deltaMs / 100f));
            }
        }

        float timePassed = currentTime - startTime;
        float progress = Math.min(1f, timePassed / ANIMATION_DURATION);
        float anim = Easings.OutBack(progress);
        
        float panelW = 320f;
        float panelH = 240f;
        float centerX = scaledWidth / 2f;
        float centerY = scaledHeight / 2f;
        float panelX = centerX - panelW / 2;
        float panelY = centerY - panelH / 2 + (1f - anim) * 50f;
        
        float alpha = Math.min(1f, progress * 2f);
        int alphaInt = (int) (255 * alpha);
        
        float radius = 10f;
        
        RenderUtil.shadow(panelX, panelY + 1.5f, panelW, panelH, radius, 8f, 0.22f * alpha, 2.0f, new Color(0, 0, 0, 170));
        RenderUtil.hudBlur(panelX, panelY, panelW, panelH, radius, 10f, alpha, new Color(100, 100, 100, 10));
        
        RenderUtil.text(context, Msdf.SF_BOLD, centerX, panelY + 15, ez.minar.system.managers.LocalizationManager.get("Alt Manager"), 16f, new Color(245, 247, 250, alphaInt), "center");
        
        String currentName = client.getSession().getUsername();
        if (displayedCurrentName.isEmpty()) {
            displayedCurrentName = currentName;
            prevCurrentName = currentName;
        } else if (!currentName.equals(displayedCurrentName)) {
            prevCurrentName = displayedCurrentName;
            displayedCurrentName = currentName;
            currentNameChangeStart = System.currentTimeMillis();
        }
        
        long now = System.currentTimeMillis();
        float animProgress = 1f;
        if (currentNameChangeStart > 0) {
            animProgress = Math.min(1f, (now - currentNameChangeStart) / 400f);
        }
        
        ez.minar.utils.render.msdf.MsdfFont headerFont = ez.minar.utils.render.msdf.MsdfManager.getDefault();
        String prefix = ez.minar.system.managers.LocalizationManager.get("Current: ");
        float prefixWidth = ez.minar.utils.render.msdf.Msdf.width(headerFont, prefix, 12f);
        
        if (animProgress >= 1f) {
            float nameWidth = ez.minar.utils.render.msdf.Msdf.width(headerFont, displayedCurrentName, 12f);
            float startX = centerX - (prefixWidth + nameWidth) / 2f;
            RenderUtil.text(context, startX, panelY + 35, prefix, 12f, new Color(185, 190, 196, alphaInt));
            RenderUtil.text(context, startX + prefixWidth, panelY + 35, displayedCurrentName, 12f, new Color(185, 190, 196, alphaInt));
        } else {
            float inAnim = ez.minar.utils.math.Easings.OutBack(animProgress);
            float outAnim = ez.minar.utils.math.Easings.OutCubic(animProgress);
            
            float newNameWidth = ez.minar.utils.render.msdf.Msdf.width(headerFont, displayedCurrentName, 12f);
            float oldNameWidth = ez.minar.utils.render.msdf.Msdf.width(headerFont, prevCurrentName, 12f);
            
            float currentTotalWidth = prefixWidth + oldNameWidth + (newNameWidth - oldNameWidth) * inAnim;
            float startX = centerX - currentTotalWidth / 2f;
            
            RenderUtil.text(context, startX, panelY + 35, prefix, 12f, new Color(185, 190, 196, alphaInt));
            
            float clipPad = 6f;
            ez.minar.utils.render.scissor.Scissor.push(startX + prefixWidth - clipPad, panelY + 35 - clipPad, Math.max(newNameWidth, oldNameWidth) + clipPad * 2f, 12f + clipPad * 2f);
            
            float outYOffset = -15f * outAnim;
            int outAlpha = (int) (alphaInt * (1f - animProgress));
            if (outAlpha > 0) {
                RenderUtil.text(context, startX + prefixWidth, panelY + 35 + outYOffset, prevCurrentName, 12f, new Color(185, 190, 196, outAlpha));
            }
            
            float inYOffset = 15f * (1f - inAnim);
            int inAlpha = (int) (alphaInt * Math.min(1f, animProgress * 2f));
            if (inAlpha > 0) {
                RenderUtil.text(context, startX + prefixWidth, panelY + 35 + inYOffset, displayedCurrentName, 12f, new Color(185, 190, 196, inAlpha));
            }
            
            ez.minar.utils.render.scissor.Scissor.pop();
        }
        
        float listX = panelX + 10f;
        float listY = panelY + 55f;
        float listW = panelW - 130f;
        float listH = panelH - 70f;

        RenderUtil.hudBlur(listX, listY, listW, listH, radius, 8f, alpha,  new Color(150, 150, 150, 20));
        
        List<AltAccount> alts = AltManager.getAlts();
        
        ez.minar.utils.render.scissor.Scissor.push(listX, listY, listW, listH);
        
        float currentY = listY + 5f + scrollY;
        for (int i = 0; i < alts.size(); i++) {
            AltAccount alt = alts.get(i);
            
            if (!accountAddTimes.containsKey(alt.name)) {
                long stagger = (currentTime - startTime < 100) ? (i * 40L) : 0L;
                accountAddTimes.put(alt.name, currentTime + stagger);
            }
            
            long addTime = accountAddTimes.get(alt.name);
            float itemProgress = Math.max(0f, Math.min(1f, (currentTime - addTime) / 400f));
            
            if (itemProgress == 0f) {
                continue;
            }
            
            float itemBounce = Easings.OutBack(itemProgress);
            float itemH = 26f;
            
            float actualItemH = itemH * Math.max(0f, Math.min(1f, itemProgress));
            
            float itemXOffset = (1f - itemBounce) * 20f;
            float itemAlphaMult = Math.max(0f, Math.min(1f, itemProgress * 2f));
            int itemAlphaInt = (int) (alphaInt * itemAlphaMult);
            float itemAlpha = alpha * itemAlphaMult;

            boolean hoverItem = mx >= listX + 5 && mx <= listX + listW - 5 && my >= currentY && my <= currentY + itemH;
            
            float targetHover = hoverItem ? 1f : 0f;
            float currentHover = itemHoverStates.getOrDefault(alt.name, 0f);
            currentHover += (targetHover - currentHover) * (deltaMs / 80f);
            currentHover = Math.max(0f, Math.min(1f, currentHover));
            itemHoverStates.put(alt.name, currentHover);
            
            float headSize = 16f;
            float headScale = 1f - currentHover;
            float actualHeadSize = headSize * headScale;
            float headX = listX + 10f + itemXOffset + (headSize - actualHeadSize) / 2f;
            float headY = currentY + 5f + (headSize - actualHeadSize) / 2f;
            
            if (actualHeadSize > 0.5f) {
                Identifier avatar = SkinUtil.getAvatar(alt.name);
                int colorRGB = new Color(255, 255, 255, Math.round(255f * itemAlpha * headScale)).getRGB();
                
                if (avatar != null) {
                    var view = client.getTextureManager().getTexture(avatar).getGlTextureView();
                    TexturePipeline.draw(RenderUtil.createProjection(), headX, headY, actualHeadSize, view, colorRGB, 2f, 0f, 0f, 0f, 1f, 1f, false);
                } else {
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + alt.name).getBytes());
                    Identifier defaultSkin = net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(offlineUuid).body().texturePath();
                    var view = client.getTextureManager().getTexture(defaultSkin).getGlTextureView();
                    TexturePipeline.draw(RenderUtil.createProjection(), headX, headY, actualHeadSize, view, colorRGB, 2f, 0f, 8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, false);
                    TexturePipeline.draw(RenderUtil.createProjection(), headX, headY, actualHeadSize, view, colorRGB, 2f, 0f, 40f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, false);
                }
            }
            
            float nameX = listX + 10f + itemXOffset + headSize + 6f - (currentHover * (headSize + 6f));
            Color nameColor = currentName.equals(alt.name) ? new Color(255, 255, 255, itemAlphaInt) : new Color(100, 100, 100, itemAlphaInt);
            if (alt.isFavorite) {
                nameColor = new Color(255, 200, 50, itemAlphaInt);
            }
            RenderUtil.text(context, nameX, currentY + 9f, alt.name, 10f, nameColor);
            
            if (currentHover > 0.01f) {
                float iconAnim = Easings.OutBack(currentHover);
                float btnSize = 14f;
                float btnStartX = listX + listW - 5f - (btnSize * 3f) - 10f + itemXOffset;
                
                drawAnimIconButton(context, "R", btnStartX, currentY + 6f, btnSize, mx, my, itemAlpha, alt.isFavorite ? new Color(255, 200, 50) : new Color(200, 200, 200), iconAnim);
                drawAnimIconButton(context, "T", btnStartX + btnSize + 5f, currentY + 6f, btnSize, mx, my, itemAlpha, new Color(200, 200, 200), iconAnim);
                drawAnimIconButton(context, "S", btnStartX + (btnSize + 5f) * 2f, currentY + 6f, btnSize, mx, my, itemAlpha, new Color(255, 80, 80), iconAnim);
            }
            
            currentY += actualItemH + 2f;
        }
        ez.minar.utils.render.scissor.Scissor.pop();
        
        float controlsX = listX + listW + 10f;
        float controlsY = listY;
        float controlsW = panelW - listW - 30f;
        
        float fieldH = 20f;
        
        float fieldClick = Easings.OutBack(clickScales.getOrDefault("field", 0f));
        float fieldScale = 1f - (fieldClick * 0.05f);
        float actualFieldW = controlsW * fieldScale;
        float actualFieldH = fieldH * fieldScale;
        float fX = controlsX + (controlsW - actualFieldW) / 2f;
        float fY = controlsY + (fieldH - actualFieldH) / 2f;

        RenderUtil.hudBlur(fX, fY, actualFieldW, actualFieldH, 7, 5f, alpha,  new Color(150, 150, 150, 20));
        
        if (nameInput.isEmpty() && !nameFocused) {
            RenderUtil.text(context, fX + 5, fY + 5, ez.minar.system.managers.LocalizationManager.get("Nickname"), 10f, new Color(185, 190, 196, alphaInt));
        } else {
            float currentX = fX + 5;
            ez.minar.utils.render.msdf.MsdfFont font = ez.minar.utils.render.msdf.MsdfManager.getDefault();
            for (int i = 0; i < nameInput.length(); i++) {
                char c = nameInput.charAt(i);
                if (charOpacities[i] < 1f) {
                    charOpacities[i] += (1f - charOpacities[i]) * (deltaMs / 60f);
                    if (charOpacities[i] > 1f) charOpacities[i] = 1f;
                }
                
                float charAnim = Easings.OutBack(charOpacities[i]);
                if (charAnim > 0.05f) {
                    int charAlpha = (int) (255 * alpha * Math.max(0, Math.min(1f, charOpacities[i])));
                    float fullWidth = ez.minar.utils.render.msdf.Msdf.width(font, String.valueOf(c), 10f);
                    float scaledSize = 10f * charAnim;
                    float charScaledWidth = ez.minar.utils.render.msdf.Msdf.width(font, String.valueOf(c), scaledSize);
                    
                    float charX = currentX + (fullWidth - charScaledWidth) / 2f;
                    float charY = fY + 5 + (10f - scaledSize) / 2f;
                    
                    RenderUtil.text(context, charX, charY, String.valueOf(c), scaledSize, new Color(245, 247, 250, charAlpha));
                }
                currentX += ez.minar.utils.render.msdf.Msdf.width(font, String.valueOf(c), 10f) + 0.5f;
            }
            if (nameFocused && (System.currentTimeMillis() / 500 % 2 == 0)) {
                RenderUtil.text(context, currentX, fY + 5, "_", 10f, new Color(245, 247, 250, alphaInt));
            }
        }
        
        float btnY = controlsY + 30f;
        String actionBtnText = editingAlt != null ? ez.minar.system.managers.LocalizationManager.get("Save") : ez.minar.system.managers.LocalizationManager.get("Add");
        drawButton(context, actionBtnText, controlsX, btnY, controlsW, 20f, mx, my, alpha, "actionBtn", deltaMs);
        
        btnY += 25f;
        drawButton(context, ez.minar.system.managers.LocalizationManager.get("Randomize"), controlsX, btnY, controlsW, 20f, mx, my, alpha, "randBtn", deltaMs);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void drawAnimIconButton(DrawContext context, String symbol, float x, float y, float size, float mx, float my, float alpha, Color baseColor, float iconAnim) {
        boolean hover = mx >= x && mx <= x + size && my >= y && my <= y + size;
        Color c = hover ? baseColor : new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), Math.round(150f));
        
        float flyAlpha = Math.max(0f, Math.min(1f, iconAnim));
        c = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * alpha * flyAlpha));
        
        if (flyAlpha > 0.05f) {
            float drawX = x + (1f - iconAnim) * 20f;
            RenderUtil.text(context, ez.minar.utils.render.msdf.Msdf.WTMICO, drawX + size / 2f, y + 2f, symbol, 10f, c, "center");
        }
    }

    private void drawButton(DrawContext context, String text, float x, float y, float w, float h, float mx, float my, float alpha, String id, float deltaMs) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float click = Easings.OutBack(clickScales.getOrDefault(id, 0f));
        
        float targetHover = hover ? 1f : 0f;
        float currentHover = hoverScales.getOrDefault(id, 0f);
        currentHover += (targetHover - currentHover) * (deltaMs / 60f);
        hoverScales.put(id, currentHover);
        
        float hoverScale = 1f + (Easings.OutBack(currentHover) * 0.05f);
        float scale = hoverScale - (click * 0.05f);
        
        float actualW = w * scale;
        float actualH = h * scale;
        float drawX = x + (w - actualW) / 2f;
        float drawY = y + (h - actualH) / 2f;
        
        float radius = 4f;
        RenderUtil.hudBlur(drawX, drawY, actualW, actualH, radius+3, 5f, alpha, new Color(150, 150, 150, 20));
        
        Color textColor = hover ? new Color(248, 250, 252, Math.round(255f * alpha)) : new Color(185, 190, 196, Math.round(210f * alpha));
        
        RenderUtil.text(context, drawX + actualW / 2, drawY + 5 * scale, text, 10f * scale, textColor, "center");
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float scaledWidth = RenderUtil.getFixedScaledWidth();
        float scaledHeight = RenderUtil.getFixedScaledHeight();
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        float mx = RenderUtil.convertX((float) mouseX);
        float my = RenderUtil.convertY((float) mouseY);

        float panelW = 320f;
        float panelH = 240f;
        float centerX = scaledWidth / 2f;
        float centerY = scaledHeight / 2f;
        float panelX = centerX - panelW / 2;
        float panelY = centerY - panelH / 2;
        
        float listX = panelX + 10f;
        float listY = panelY + 55f;
        float listW = panelW - 130f;
        float listH = panelH - 70f;
        
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            List<AltAccount> alts = AltManager.getAlts();
            float currentY = listY + 5f + scrollY;
            for (int i = 0; i < alts.size(); i++) {
                float itemH = 26f;
                if (mx >= listX + 5 && mx <= listX + listW - 5 && my >= currentY && my <= currentY + itemH) {
                    float currentHover = itemHoverStates.getOrDefault(alts.get(i).name, 0f);
                    
                    if (currentHover > 0.5f) {
                        float btnSize = 14f;
                        float btnStartX = listX + listW - 5f - (btnSize * 3f) - 10f;
                        
                        if (mx >= btnStartX && mx <= btnStartX + btnSize) {
                            AltManager.toggleFavorite(alts.get(i).name);
                            return true;
                        }
                        if (mx >= btnStartX + btnSize + 5f && mx <= btnStartX + btnSize * 2f + 5f) {
                            editingAlt = alts.get(i).name;
                            nameInput = editingAlt;
                            nameFocused = true;
                            for (int j = 0; j < 16; j++) charOpacities[j] = (j < nameInput.length()) ? 1f : 0f;
                            return true;
                        }
                        if (mx >= btnStartX + (btnSize + 5f) * 2f && mx <= btnStartX + btnSize * 3f + 10f) {
                            AltManager.removeAlt(alts.get(i).name);
                            return true;
                        }
                    }
                    
                    loginTo(alts.get(i).name);
                    return true;
                }
                currentY += itemH + 2f;
            }
        }
        
        float controlsX = listX + listW + 10f;
        float controlsY = listY;
        float controlsW = panelW - listW - 30f;
        
        nameFocused = (mx >= controlsX && mx <= controlsX + controlsW && my >= controlsY && my <= controlsY + 20f);
        if (nameFocused && button == 0) {
            clickScales.put("field", 1f);
        }
        
        if (!nameFocused && button == 0 && mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
            if (editingAlt != null && isHover(mx, my, controlsX, controlsY + 30f, controlsW, 20f)) {
                
            } else {
                editingAlt = null;
            }
        }
        
        float btnY = controlsY + 30f;
        if (isHover(mx, my, controlsX, btnY, controlsW, 20f)) { 
            clickScales.put("actionBtn", 1f);
            if (!nameInput.isEmpty()) {
                if (editingAlt != null) {
                    AltManager.renameAlt(editingAlt, nameInput);
                    editingAlt = null;
                } else {
                    AltManager.addAlt(nameInput);
                }
                nameInput = "";
                for (int j = 0; j < 16; j++) charOpacities[j] = 0f;
            }
            return true;
        }
        btnY += 25f;
        if (isHover(mx, my, controlsX, btnY, controlsW, 20f)) { 
            clickScales.put("randBtn", 1f);
            String generated = generateRandomNickname();
            AltManager.addAlt(generated);
            return true;
        }
        
        return super.mouseClicked(click, doubled);
    }
    
    private boolean isHover(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollY += (float) (verticalAmount * 15f);
        if (scrollY > 0) scrollY = 0;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void loginTo(String name) {
        ((IMinecraftClient) client).setSession(createSessionWithName(client.getSession(), name));
    }

    private Session createSessionWithName(Session current, String name) {
        try {
            java.lang.reflect.Constructor<Session> c = Session.class.getDeclaredConstructor(String.class, UUID.class, String.class, Optional.class, Optional.class);
            c.setAccessible(true);
            return c.newInstance(name, UUID.randomUUID(), current.getAccessToken(), Optional.empty(), Optional.empty());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String generateRandomNickname() {
        String[] prefixes = {"sacrificed", "angel", "dark", "shadow", "blood", "crimson", "silent", "dead", "cursed", "broken", "lost", "fallen", "hollow", "pale", "grim", "frost", "night", "doom", "void", "ghost", "weeping", "black", "ruined", "bleeding", "forsaken", "mystic", "cruel", "cold", "bitter", "fatal", "toxic", "venom", "death", "fear", "pain", "agony", "grief", "sorrow", "tragic", "gothic", "vamp", "witch", "demon", "devil", "evil", "sin", "vile", "pure", "divine", "holy", "sacred", "abyss", "phantom", "specter", "spirit", "wraith", "ghoul", "vampire", "werewolf", "dragon", "beast", "monster", "astral", "lunar", "solar", "cosmic", "stellar", "twilight", "midnight", "dusk", "eclipse", "storm", "thunder", "rain", "wind", "fire", "flame", "ash", "ember", "smoke", "dust", "iron", "steel", "silver", "gold", "ruby", "onyx", "opal", "jade", "pearl", "diamond", "crystal", "glass", "mirror", "dream", "nightmare", "vision", "starlight", "moonlight", "sad", "lonely", "hidden", "secret", "blind", "freezing", "burning", "glowing", "fading", "dying", "crying", "sighing", "sleeping", "waking", "dreaming", "falling", "flying", "drowning", "sinking", "floating", "rising", "hiding", "seeking", "losing", "taking", "breaking", "destroying", "killing", "saving", "healing", "hurting", "loving", "hating", "kissing", "biting", "scratching", "feeling", "surviving", "escaping", "fleeing", "crawling", "creeping", "sneaking", "waiting", "watching", "listening", "somber", "morbid", "ghastly", "macabre", "eerie", "spooky", "creepy", "sinister", "diabolical", "fiendish", "hellish", "infernal", "wicked", "corrupt", "twisted", "warped", "sick", "deranged", "insane", "mad", "psycho", "manic", "frantic", "wild", "savage", "feral", "rabid", "vicious", "brutal", "ruthless", "merciless", "pitiless", "heartless", "soulless", "mindless", "faceless", "nameless", "formless", "endless", "timeless", "deathless", "immortal", "eternal", "infinite", "grave", "tomb", "crypt", "vault", "cage", "trap", "snare", "net", "web"};
        String[] suffixes = {"heart", "knife", "blade", "soul", "tears", "blood", "moon", "star", "scythe", "shade", "thorn", "crown", "reaper", "raven", "rose", "dust", "ash", "bone", "skull", "ghost", "flame", "bane", "beast", "demon", "angel", "wing", "sword", "dagger", "fang", "claw", "horn", "eye", "gaze", "kiss", "bite", "mark", "scar", "vein", "pulse", "breath", "song", "cry", "scream", "whisper", "echo", "shadow", "night", "mist", "fog", "cloud", "vapor", "poison", "cure", "life", "death", "rebirth", "flesh", "skin", "hair", "tail", "feather", "silk", "thread", "chain", "wire", "cord", "path", "way", "gate", "door", "wall", "room", "base", "fort", "castle", "tower", "city", "town", "kingdom", "empire", "world", "planet", "galaxy", "universe", "dimension", "realm", "domain", "space", "heaven", "hell", "purgatory", "limbo", "nexus", "core", "edge", "end", "start", "origin", "source", "seed", "tree", "flower", "leaf", "branch", "root", "grass", "weed", "bush", "forest", "wood", "jungle", "swamp", "desert", "sand", "dirt", "mud", "rock", "stone", "mountain", "hill", "valley", "cliff", "cave", "mine", "pit", "hole", "volcano", "ocean", "sea", "lake", "river", "stream", "drop", "tear", "sweat", "nectar", "honey", "wax", "amber", "coral", "shell", "ivory", "hoof", "slayer", "walker", "hunter", "bringer", "seeker", "keeper", "master", "lord", "king", "queen", "prince", "princess", "child", "boy", "girl", "man", "woman", "god", "goddess", "deity", "idol", "hero", "villain", "friend", "foe", "enemy", "ally", "lover", "partner", "mate", "companion", "pet", "slave", "servant", "ruler", "leader", "follower", "guide", "teacher", "student", "learner", "speaker", "singer", "dancer", "player", "maker", "creator", "destroyer", "killer", "savior", "healer", "hurter", "pain", "agony", "fear", "terror", "horror", "dread", "panic", "fright", "shock", "awe", "wonder", "magic", "spell", "charm", "hex", "curse", "jinx", "trick", "illusion", "dream", "nightmare", "vision", "memory", "thought", "mind", "brain", "spirit", "phantom", "specter", "wraith", "ghoul", "zombie", "mummy", "vampire", "werewolf", "dragon", "serpent", "snake", "viper", "cobra", "spider", "scorpion", "wasp", "bee", "ant", "fly", "bug", "worm", "slug", "snail", "leech"};
        java.util.Random rand = new java.util.Random();
        String result = "";
        while (true) {
            String p = prefixes[rand.nextInt(prefixes.length)];
            String s = suffixes[rand.nextInt(suffixes.length)];
            result = p + s;
            if (result.length() <= 16) {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        if (nameFocused) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE && nameInput.length() > 0) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
                charOpacities[nameInput.length()] = 0f;
                return true;
            } else if (input.key() == GLFW.GLFW_KEY_ENTER) {
                if (!nameInput.isEmpty()) {
                    if (editingAlt != null) {
                        AltManager.renameAlt(editingAlt, nameInput);
                        editingAlt = null;
                    } else {
                        AltManager.addAlt(nameInput);
                    }
                    nameInput = "";
                    for (int i = 0; i < 16; i++) charOpacities[i] = 0f;
                }
                return true;
            } else if (input.key() == GLFW.GLFW_KEY_V && (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) {
                String clipboard = client.keyboard.getClipboard();
                if (clipboard != null) {
                    nameInput += clipboard.replaceAll("[^a-zA-Z0-9_]", "");
                    if (nameInput.length() > 16) nameInput = nameInput.substring(0, 16);
                }
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (nameFocused && input.isValidChar()) {
            if (nameInput.length() < 16) {
                nameInput += input.asString();
                return true;
            }
        }
        return super.charTyped(input);
    }
}
