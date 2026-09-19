package ez.minar.system.menu.cosmetics;

import ez.minar.system.features.misc.Cosmetics;
import ez.minar.system.menu.ThemeManager;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.render.OverlayTexture;
import org.figuramc.figura.utils.ui.UIHelper;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class CosmeticsScreen extends Screen {
    private static final float PANEL_WIDTH = 540f;
    private static final float PANEL_HEIGHT = 350f;
    private static final float HEADER_HEIGHT = 48f;
    private static final float PADDING = 12f;
    private static final float CARD_WIDTH = 94f;
    private static final float CARD_HEIGHT = 76f;
    private static final float CARD_GAP = 6f;
    private static final int COLUMNS = 5;

    private final Screen parent;
    private final Cosmetics cosmetics;
    private int currentTab = 0;
    private float scroll;
    private float targetScroll;
    private long openedAt;
    private long lastFrame;
    private float frameDelta;
    private final Map<String, Float> selectionAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final Map<String, Float> previewRotations = new HashMap<>();
    private final Map<String, Float> targetPreviewRotations = new HashMap<>();
    private String hoveredPreviewModel;
    private float categoryTransition = 1f;

    public CosmeticsScreen(Screen parent, Cosmetics cosmetics) {
        super(Text.literal("Cosmetics"));
        this.parent = parent;
        this.cosmetics = cosmetics;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        lastFrame = openedAt;
        selectionAnimations.clear();
        selectionAnimations.put(currentTab == 0 ? cosmetics.getSelectedSword() : cosmetics.getSelectedModel(), 1f);
        hoverAnimations.clear();
        previewRotations.clear();
        targetPreviewRotations.clear();
        cosmetics.loadAllPreviewModels();
        super.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        long now = System.currentTimeMillis();
        float delta = Math.min(50f, now - lastFrame);
        lastFrame = now;
        frameDelta = delta;
        scroll += (targetScroll - scroll) * Math.min(1f, delta * 0.018f);
        categoryTransition = animate(categoryTransition, 1f, 0.018f, delta);

        float progress = Math.min(1f, (now - openedAt) / 190f);
        float animation = Easings.OutCubic(progress);
        float opacity = animation;

        float fixedWidth = RenderUtil.getFixedScaledWidth();
        float fixedHeight = RenderUtil.getFixedScaledHeight();
        float centerX = fixedWidth / 2f;
        float centerY = fixedHeight / 2f;
        float panelX = centerX - PANEL_WIDTH / 2f;
        float panelY = centerY - PANEL_HEIGHT / 2f;
        float fixedMouseX = RenderUtil.convertX(mouseX);
        float fixedMouseY = RenderUtil.convertY(mouseY);

        RenderUtil.rect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 10f,
                withOpacity(new Color(18, 18, 22, 245), opacity));
        RenderUtil.rect(panelX + 1f, panelY + 1f, PANEL_WIDTH - 2f, HEADER_HEIGHT - 1f,
                0f, 0f, 9f, 9f, withOpacity(new Color(255, 255, 255, 8), opacity));

        float tabX = panelX + PANEL_WIDTH - 120f;
        float tabY = panelY + 18f;
        String[] tabs = {ez.minar.system.managers.LocalizationManager.get("Swords"), ez.minar.system.managers.LocalizationManager.get("Models")};
        for (int i = 0; i < tabs.length; i++) {
            boolean active = currentTab == i;
            float tw = Msdf.width(tabs[i], 8f);
            RenderUtil.text(context, tabX, tabY, tabs[i], 8f, withOpacity(active ? Color.WHITE : new Color(132, 132, 140), opacity));
            if (active) {
                RenderUtil.rect(tabX, tabY + 12f, tw, 1f, 0f, withOpacity(ThemeManager.getThemeColor(), opacity));
            }
            tabX += tw + 16f;
        }

        RenderUtil.text(context, panelX + PADDING, panelY + 11f, ez.minar.system.managers.LocalizationManager.get("Cosmetics"), 13f,
                withOpacity(new Color(240, 240, 244), opacity));
        RenderUtil.text(context, panelX + PADDING, panelY + 27f, currentTab == 0 ? cosmetics.getSelectedSword() : cosmetics.getSelectedModel(),
                7.4f, withOpacity(new Color(132, 132, 140), opacity));

        float contentX = panelX + 18f;
        float contentY = panelY + HEADER_HEIGHT + 9f;
        float contentWidth = PANEL_WIDTH - 36f;
        float contentHeight = PANEL_HEIGHT - HEADER_HEIGHT - 21f;

        float categoryEase = Easings.OutCubic(categoryTransition);
        renderGrid(context, contentX, contentY + (1f - categoryEase) * 9f,
                contentWidth, contentHeight, fixedMouseX, fixedMouseY, opacity * categoryEase);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderGrid(DrawContext context, float x, float y, float width, float height,
                                 float mouseX, float mouseY, float opacity) {
        String[] models = currentTab == 0 ? Cosmetics.SWORD_MODELS : Cosmetics.PLAYER_MODELS;
        hoveredPreviewModel = null;
        float contextScale = RenderUtil.getDrawContextScale();
        context.enableScissor(
                (int) Math.floor(x * contextScale),
                (int) Math.floor(y * contextScale),
                (int) Math.ceil((x + width) * contextScale),
                (int) Math.ceil((y + height) * contextScale)
        );
        Scissor.push(x, y, width, height);
        for (int i = 0; i < models.length; i++) {
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            float cardX = x + column * (CARD_WIDTH + CARD_GAP);
            float cardY = y + row * (CARD_HEIGHT + CARD_GAP) - scroll;
            if (cardY + CARD_HEIGHT < y || cardY > y + height) {
                continue;
            }
            renderCard(context, models[i], cardX, cardY, mouseX, mouseY, opacity);
        }
        Scissor.pop();
        context.disableScissor();

        renderScrollbar(x, y, width, height, opacity, getMaxScroll(height, models.length));
    }

    private void renderCard(DrawContext context, String model, float x, float y,
                                 float mouseX, float mouseY, float opacity) {
        boolean selected = currentTab == 0 ? model.equals(cosmetics.getSelectedSword()) : model.equals(cosmetics.getSelectedModel());
        boolean hovered = inside(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
        if (currentTab == 1 && hovered) {
            hoveredPreviewModel = model;
        }
        float selection = animate(selectionAnimations.getOrDefault(model, 0f),
                selected ? 1f : 0f, 0.022f, frameDelta);
        float hover = animate(hoverAnimations.getOrDefault(model, 0f),
                hovered ? 1f : 0f, 0.028f, frameDelta);
        selectionAnimations.put(model, selection);
        hoverAnimations.put(model, hover);

        // Premium background styling
        Color accent = ThemeManager.getThemeColor();
        Color baseBg = new Color(20, 20, 24, 200);
        Color hoverBg = mix(baseBg, new Color(255, 255, 255, 20), hover * 0.4f);
        Color finalBg = mix(hoverBg, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60), selection);
        
        // Render card background with glowing border if selected
        if (selection > 0.01f) {
            RenderUtil.rect(x - 1f, y - 1f, CARD_WIDTH + 2f, CARD_HEIGHT + 2f, 7f, withOpacity(accent, opacity * selection * 0.6f));
        }
        RenderUtil.rect(x, y, CARD_WIDTH, CARD_HEIGHT, 6f, withOpacity(finalBg, opacity));

        if (currentTab == 0) {
            ItemStack preview = cosmetics.createPreviewStack(model);
            context.getMatrices().pushMatrix();
            float contextScale = RenderUtil.getDrawContextScale();
            float iconScale = 2f * (1f + hover * 0.12f + selection * 0.04f);
            context.getMatrices().scale(contextScale, contextScale);
            context.getMatrices().translate(
                    x + CARD_WIDTH / 2f - 8f * iconScale,
                    y + 23f - 8f * iconScale
            );
            context.getMatrices().scale(iconScale, iconScale);
            cosmetics.drawPreview(context, preview, 0, 0);
            context.getMatrices().popMatrix();
        } else {
            org.figuramc.figura.avatar.Avatar previewAvatar = cosmetics.getPreviewAvatar(model);
            if (previewAvatar == null) {
                RenderUtil.text(context, x + CARD_WIDTH / 2f, y + CARD_HEIGHT / 2f - 8f,
                        "...", 9f, withOpacity(new Color(150, 150, 160), opacity), "center");
            } else {
            float cx = x + CARD_WIDTH / 2f;
            float cy = y + CARD_HEIGHT - 12f;

            // UIHelper renders the real client player as the preview entity.
            // Hide its vanilla body on the isolated preview avatar so only the
            // cosmetic model is visible. The selected in-world avatar is separate.
            if (previewAvatar.luaRuntime != null) {
                previewAvatar.luaRuntime.vanilla_model.PLAYER.setVisible(false);
            }
            
            float targetYaw = targetPreviewRotations.getOrDefault(model, 0f);
            float yaw = animate(previewRotations.getOrDefault(model, 0f),
                    targetYaw, 0.014f, frameDelta);
            previewRotations.put(model, yaw);
            float pitch = -8f;
            
            float scale = 22f + (hover * 3f) + (selection * 2f); // Pop out effect on hover
            
            if (selected) {
                RenderUtil.rect(cx - 15f, cy - 35f, 30f, 35f, 15f, withOpacity(accent, opacity * 0.2f * selection));
            }

            try {
                FiguraPreviewAvatarContext.begin(previewAvatar);
                UIHelper.drawEntity(
                        cx, cy,
                        scale,
                        pitch, yaw,
                        client.player,
                        context,
                        new Vector3f(),
                        EntityRenderMode.FIGURA_GUI,
                        (int) x, (int) y, (int) (x + CARD_WIDTH), (int) (y + CARD_HEIGHT)
                );
            } catch (Exception e) {
                RenderUtil.text(context, x + CARD_WIDTH / 2f, y + CARD_HEIGHT / 2f - 8f, "?", 16f, withOpacity(new Color(200, 200, 210), opacity), "center");
            } finally {
                FiguraPreviewAvatarContext.end();
            }
            }
        }

        String name = trim(model, CARD_WIDTH - 12f, 7.1f);
        RenderUtil.text(context, x + CARD_WIDTH / 2f, y + CARD_HEIGHT - 14f, name, 7.1f,
                withOpacity(selected ? Color.WHITE : new Color(184, 184, 190), opacity), "center");
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseXRaw = (float) click.x();
        float mouseYRaw = (float) click.y();
        int button = click.button();
        float mouseX = RenderUtil.convertX((float) mouseXRaw);
        float mouseY = RenderUtil.convertY((float) mouseYRaw);
        Layout layout = layout();
        
        float tabX = layout.panelX + PANEL_WIDTH - 120f;
        float tabY = layout.panelY + 18f;
        String[] tabs = {"Swords", "Models"};
        for (int i = 0; i < tabs.length; i++) {
            float tw = Msdf.width(tabs[i], 8f);
            if (inside(mouseX, mouseY, tabX, tabY - 2f, tw, 14f)) {
                if (currentTab != i) {
                    currentTab = i;
                    scroll = 0f;
                    targetScroll = 0f;
                    categoryTransition = 0f;
                }
                return true;
            }
            tabX += tw + 16f;
        }

        float contentX = layout.panelX + 18f;
        float contentY = layout.panelY + HEADER_HEIGHT + 9f;
        float contentHeight = PANEL_HEIGHT - HEADER_HEIGHT - 21f;
        if (button == 0 && inside(mouseX, mouseY, contentX, contentY, PANEL_WIDTH - 36f, contentHeight)) {
            int index = resolveGridIndex(mouseX, mouseY, contentX, contentY);
            String[] models = currentTab == 0 ? Cosmetics.SWORD_MODELS : Cosmetics.PLAYER_MODELS;
            if (index >= 0 && index < models.length) {
                if (currentTab == 0) {
                    cosmetics.setSelectedSword(models[index]);
                } else {
                    cosmetics.setSelectedModel(models[index]);
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == 1 && hoveredPreviewModel != null) {
            float currentTarget = targetPreviewRotations.getOrDefault(hoveredPreviewModel, 0f);
            targetPreviewRotations.put(hoveredPreviewModel,
                    currentTarget + (float) verticalAmount * 32f);
            return true;
        }

        float contentHeight = PANEL_HEIGHT - HEADER_HEIGHT - 21f;
        int count = currentTab == 0 ? Cosmetics.SWORD_MODELS.length : Cosmetics.PLAYER_MODELS.length;
        targetScroll = Math.clamp(targetScroll - (float) verticalAmount * 34f, 0f,
                getMaxScroll(contentHeight, count));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        cosmetics.restoreSelectedModelAfterPreview();
        cosmetics.unloadAllPreviewModels();
        FiguraPreviewAvatarContext.clear();
        previewRotations.clear();
        targetPreviewRotations.clear();
        super.removed();
    }

    private void renderScrollbar(float x, float y, float width, float height, float opacity, float maxScroll) {
        if (maxScroll <= 0f) {
            return;
        }
        float trackHeight = height;
        float thumbHeight = Math.max(24f, trackHeight * height / (height + maxScroll));
        float thumbY = y + (trackHeight - thumbHeight) * (scroll / maxScroll);
        RenderUtil.rect(x + width + 5f, y, 2f, trackHeight, 1f,
                withOpacity(new Color(255, 255, 255, 10), opacity));
        RenderUtil.rect(x + width + 5f, thumbY, 2f, thumbHeight, 1f,
                withOpacity(ThemeManager.getThemeColor(), opacity * 0.75f));
    }

    private int resolveGridIndex(float mouseX, float mouseY, float contentX, float contentY) {
        int column = (int) ((mouseX - contentX) / (CARD_WIDTH + CARD_GAP));
        int row = (int) ((mouseY - contentY + scroll) / (CARD_HEIGHT + CARD_GAP));
        float localX = mouseX - contentX - column * (CARD_WIDTH + CARD_GAP);
        float localY = mouseY - contentY + scroll - row * (CARD_HEIGHT + CARD_GAP);
        if (column < 0 || column >= COLUMNS || localX > CARD_WIDTH || localY > CARD_HEIGHT) {
            return -1;
        }
        return row * COLUMNS + column;
    }

    private float getMaxScroll(float viewportHeight, int modelCount) {
        int rows = (modelCount + COLUMNS - 1) / COLUMNS;
        float contentHeight = rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
        return Math.max(0f, contentHeight - viewportHeight);
    }

    private Layout layout() {
        return new Layout(
                RenderUtil.getFixedScaledWidth() / 2f - PANEL_WIDTH / 2f,
                RenderUtil.getFixedScaledHeight() / 2f - PANEL_HEIGHT / 2f
        );
    }

    private String trim(String text, float width, float size) {
        if (Msdf.width(text, size) <= width) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && Msdf.width(result + "...", size) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private Color withOpacity(Color color, float opacity) {
        int alpha = (int) (color.getAlpha() * Math.clamp(opacity, 0f, 1f));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private float animate(float current, float target, float speed, float delta) {
        float factor = 1f - (float) Math.exp(-speed * delta);
        float next = current + (target - current) * Math.clamp(factor, 0f, 1f);
        return Math.abs(next - target) < 0.001f ? target : next;
    }

    private Color mix(Color first, Color second, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        int red = (int) (first.getRed() + (second.getRed() - first.getRed()) * value);
        int green = (int) (first.getGreen() + (second.getGreen() - first.getGreen()) * value);
        int blue = (int) (first.getBlue() + (second.getBlue() - first.getBlue()) * value);
        int alpha = (int) (first.getAlpha() + (second.getAlpha() - first.getAlpha()) * value);
        return new Color(red, green, blue, alpha);
    }

    private record Layout(float panelX, float panelY) {
    }
}
