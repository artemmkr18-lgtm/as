package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.features.combat.AntiBot;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.settings.impl.MultiSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.WorldToScreen;
import ez.minar.utils.render.FiguraBounds;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ez.minar.utils.render.pipeline.TexturePipeline;

import java.awt.Color;
import java.util.Locale;

@NewFunction(name = "NameTags", desc = "\u0414\u0430\u043b\u0431\u0430\u0435\u0431\u044b \u0442\u043e\u043a\u0430 \u043d\u0435 \u043f\u043e\u0439\u043c\u0443\u0442", category = Category.RENDER)
public class NameTags extends Function {
    public static NameTags Instance;

    private final MultiSetting targets = new MultiSetting("Цели", "Игроки", "Враждебные", "Животные", "На себя", "Предметы");
    private final MultiSetting elements = new MultiSetting("Показывать", "Броня", "Вещь в руке", "Хп", "Голова игрока");

    private static final float TEXT_SIZE = 7.2f;
    private static final float HEAD_SIZE = 11.5f;
    private static final float TAG_PADDING_X = 3.5f;
    private static final float TAG_PADDING_Y = 2.5f;
    private static final float TAG_RADIUS = 3.5f;
    private static final float ITEM_SIZE = 10.8f;
    private static final float ITEM_SPACING = 1.1f;
    private static final float TEXT_SEGMENT_GAP = 2.1f;
    private static final float VANILLA_RP_SCALE = 0.72f;
    private static final float VANILLA_RP_Y_OFFSET = -0.0f;
    private static final float NAME_TAG_SCALE = 0.7f;
    private static final float RP_X_OFFSET = -0.85f;
    private static final float HEALTH_X_OFFSET = 0.75f;
    private static final float ARMOR_Y_GAP = 1.6f;
    private static final Color NAME_COLOR = new Color(238, 238, 242);
    private static final Color FRIEND_BLUR = new Color(18, 18, 22, 120);
    private static final Color DEFAULT_BLUR = new Color(18, 18, 22, 120);
    private static final Color FRIEND_GRADIENT_LEFT = new Color(3, 4, 5, 65);
    private static final Color FRIEND_GRADIENT_RIGHT = new Color(3, 177, 76, 92);
    private static final Color HEALTH_GREEN = new Color(104, 255, 114);
    private static final Color HEALTH_YELLOW = new Color(255, 235, 92);
    private static final Color HEALTH_ORANGE = new Color(255, 170, 72);
    private static final Color HEALTH_RED = new Color(255, 85, 92);
    private static final Color ITEM_TEXT = new Color(230, 230, 235);

    public NameTags() {
        Instance = this;
        addSettings(targets, elements);
    }

    public boolean shouldHideVanillaLabels() {
        return targets.isEnabled("\u0418\u0433\u0440\u043e\u043a\u0438");
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        DrawContext context = event.getContext();
        if (context == null || mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;

        // Use the render-frame interpolation value for every target, including the local player.
        float tickDelta = event.getTickCounter().getTickProgress(false);

        if (targets.isEnabled("Игроки") || targets.isEnabled("Враждебные")
                || targets.isEnabled("Животные") || targets.isEnabled("На себя")) {
            renderLivingEntities(context, tickDelta);
        }
        if (targets.isEnabled("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b")) {
            renderItems(context, tickDelta);
        }
    }

    private void renderLivingEntities(DrawContext context, float tickDelta) {
        int screenW = RenderUtil.getFixedScaledWidth();
        int screenH = RenderUtil.getFixedScaledHeight();
        AntiBot antiBot = FunctionManager.getFunction(AntiBot.class);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !shouldRenderLiving(living)) continue;
            if (living == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            if (living instanceof PlayerEntity player
                    && antiBot != null && antiBot.isEnabled() && antiBot.isBot(player)) continue;

            Vec3d interp = WorldToScreen.getInterpolatedPos(living, tickDelta);
            float dist = (float) cameraPos.distanceTo(interp);
            if (dist > 150f) continue;

            net.minecraft.util.math.Box modelBounds = living instanceof PlayerEntity player
                    ? FiguraBounds.get(player)
                    : living.getBoundingBox();
            double modelTopOffset = Math.max(
                    living.getHeight(),
                    modelBounds.maxY - living.getBoundingBox().minY
            );
            Vec3d headTop = interp.add(0, modelTopOffset, 0);

            float[] screen = WorldToScreen.project(headTop);
            if (screen == null) continue;
            if (screen[0] < -50 || screen[0] > screenW + 50 || screen[1] < -50 || screen[1] > screenH + 50) continue;

            renderLivingTag(context, living, screen[0], screen[1], dist);
        }
    }

    private boolean shouldRenderLiving(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        if (entity == mc.player) return targets.isEnabled("На себя");
        if (entity instanceof PlayerEntity) return targets.isEnabled("Игроки");
        if (entity instanceof HostileEntity) return targets.isEnabled("Враждебные");
        return entity instanceof AnimalEntity && targets.isEnabled("Животные");
    }

    private void renderLivingTag(DrawContext context, LivingEntity living, float x, float y, float dist) {
        float scaleFactor = Math.max(1.0f, 9.0f / Math.max(dist, 3.0f));
        float sc = NAME_TAG_SCALE * scaleFactor;
        PlayerEntity player = living instanceof PlayerEntity p ? p : null;
        boolean isFriend = player != null && FriendManager.isFriend(player.getName().getString());
        Text rpPrefix = player == null ? Text.empty() : getRpPrefix(player);
        Text rpSuffix = player == null ? Text.empty() : getRpSuffix(player);
        boolean hasRpPrefix = hasText(rpPrefix);
        boolean hasRpSuffix = hasText(rpSuffix);
        String displayName = player == null ? living.getDisplayName().getString() : getDisplayName(player, hasRpPrefix || hasRpSuffix);
        float health = living.getHealth() + living.getAbsorptionAmount();
        float maxHealth = Math.max(1f, living.getMaxHealth());

        float displayHealth = health;

        Text rpPrefixText = hasRpPrefix ? normalizeRpText(rpPrefix) : Text.empty();
        Text rpSuffixText = hasRpSuffix ? normalizeRpText(rpSuffix) : Text.empty();
        float textSize = TEXT_SIZE * sc;
        float textGap = TEXT_SEGMENT_GAP * sc;
        float vanillaScale = VANILLA_RP_SCALE * sc;
        boolean showHealth = elements.isEnabled("Хп");
        boolean showHead = player != null && elements.isEnabled("Голова игрока");
        String healthText = showHealth ? formatHealth(displayHealth) : "";
        float nameWidth = Msdf.width(Msdf.SF_BOLD, displayName, textSize);
        float healthWidth = Msdf.width(Msdf.SF_BOLD, healthText, textSize);
        float prefixWidth = hasRpPrefix ? getVanillaWidth(rpPrefixText, vanillaScale) : 0f;
        float suffixWidth = hasRpSuffix ? getVanillaWidth(rpSuffixText, vanillaScale) : 0f;
        float textWidth = nameWidth + prefixWidth + suffixWidth;
        if (hasRpPrefix) textWidth += textGap;
        if (hasRpSuffix) textWidth += textGap;
        if (showHealth) textWidth += textGap + healthWidth;
        float msdfHeight = Msdf.height(Msdf.SF_BOLD, textSize);
        float vanillaHeight = mc.textRenderer.fontHeight * vanillaScale;
        float textHeight = Math.max(msdfHeight, vanillaHeight);
        float headW = showHead ? HEAD_SIZE * sc + textGap : 0f;
        float paddingX = TAG_PADDING_X * sc;
        float paddingY = TAG_PADDING_Y * sc;
        float totalW = headW + textWidth + paddingX * 2f;
        float totalH = Math.max(HEAD_SIZE * sc, textHeight) + paddingY * 2f;

        float tagX = x - totalW / 2f;
        float tagY = y - totalH - 10 * sc;

        if (HUD.Instance != null) {
            HUD.Instance.renderElementPanel(null, tagX, tagY, totalW, totalH, TAG_RADIUS * sc, 1f);
        } else {
            RenderUtil.hudBlur(tagX, tagY, totalW, totalH, TAG_RADIUS * sc, 25f, 1f, DEFAULT_BLUR);
        }

        if (isFriend) {
            RenderUtil.rect(tagX, tagY, totalW, totalH, TAG_RADIUS * sc,
                    FRIEND_GRADIENT_LEFT,
                    FRIEND_GRADIENT_RIGHT,
                    FRIEND_GRADIENT_RIGHT,
                    FRIEND_GRADIENT_LEFT);
        }

        float contentX = tagX + paddingX;
        float msdfY = tagY + (totalH - msdfHeight) / 2f - 0.45f * sc;
        float vanillaY = tagY + (totalH - vanillaHeight) / 2f + VANILLA_RP_Y_OFFSET * sc;

        if (showHead) {
            float headSize = HEAD_SIZE * sc;
            renderPlayerHead(player, contentX, tagY + (totalH - headSize) / 2f, headSize);
            contentX += headSize + textGap;
        }

        float drawX = contentX;
        if (hasRpPrefix) {
            renderVanillaText(context, rpPrefixText, drawX + RP_X_OFFSET * sc, vanillaY, vanillaScale);
            drawX += prefixWidth + textGap;
        }
        RenderUtil.text(context, Msdf.SF_BOLD, drawX, msdfY, displayName, textSize, NAME_COLOR);
        drawX += nameWidth;
        if (hasRpSuffix) {
            drawX += textGap;
            renderVanillaText(context, rpSuffixText, drawX, vanillaY, vanillaScale);
            drawX += suffixWidth;
        }
        if (showHealth) {
            drawX += textGap;
            RenderUtil.text(context, Msdf.SF_BOLD, drawX + HEALTH_X_OFFSET * sc, msdfY, healthText, textSize, getHealthColor(displayHealth, maxHealth));
        }

        renderLivingItems(context, living, x, tagY, sc);
    }

    private Text getRpPrefix(PlayerEntity player) {
        if (player.getScoreboardTeam() == null) return Text.literal("");
        Text prefix = player.getScoreboardTeam().getPrefix();
        return prefix == null ? Text.literal("") : prefix;
    }

    private Text getRpSuffix(PlayerEntity player) {
        if (player.getScoreboardTeam() == null) return Text.literal("");
        Text suffix = player.getScoreboardTeam().getSuffix();
        return suffix == null ? Text.literal("") : suffix;
    }

    private String getDisplayName(PlayerEntity player, boolean hasRpText) {
        String realName = player.getGameProfile().name();
        if (hasRpText) return realName;

        Text customName = player.getCustomName();
        if (customName != null) {
            String custom = customName.getString().trim();
            if (!custom.isEmpty() && !custom.equals(realName)) {
                return custom;
            }
        }

        String displayStr = player.getDisplayName().getString().trim();
        if (!displayStr.isEmpty() && !displayStr.equals(realName)) {
            return displayStr;
        }

        return realName;
    }

    private boolean hasText(Text text) {
        return text != null && !text.getString().trim().isEmpty();
    }

    private Text normalizeRpText(Text text) {
        MutableText result = Text.empty();
        text.visit((style, string) -> {
            appendLegacyText(result, string, style);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private String cleanSegmentText(String text) {
        return text.trim().replace("[", "").replace("]", "");
    }

    private void appendLegacyText(MutableText result, String text, Style baseStyle) {
        String value = cleanSegmentText(text);
        if (value.isEmpty()) return;

        Style style = baseStyle;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u00a7' && i + 1 < value.length()) {
                appendTextPart(result, builder, style);
                Formatting formatting = Formatting.byCode(value.charAt(i + 1));
                if (formatting != null) {
                    style = formatting == Formatting.RESET ? Style.EMPTY : style.withFormatting(formatting);
                }
                i++;
            } else {
                builder.append(c);
            }
        }
        appendTextPart(result, builder, style);
    }

    private void appendTextPart(MutableText result, StringBuilder builder, Style style) {
        if (builder.isEmpty()) return;
        result.append(Text.literal(builder.toString()).setStyle(style));
        builder.setLength(0);
    }

    private float getVanillaWidth(Text text, float scale) {
        return mc.textRenderer.getWidth(text) * scale;
    }

    private void renderVanillaText(DrawContext context, Text text, float x, float y, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawText(mc.textRenderer, text, 0, 0, -1, false);
        context.getMatrices().popMatrix();
    }

    private String formatHealth(float health) {
        return String.format(Locale.US, "%.1f", health);
    }

    private Color getHealthColor(float health, float maxHealth) {
        float ratio = health / maxHealth;
        if (ratio > 0.75f) return HEALTH_GREEN;
        if (ratio > 0.5f) return HEALTH_YELLOW;
        if (ratio > 0.25f) return HEALTH_ORANGE;
        return HEALTH_RED;
    }

    private float coloredTextWidth(String text, float size) {
        return Msdf.width(Msdf.SF_BOLD, stripColorCodes(text), size);
    }

    private String stripColorCodes(String text) {
        return text.replaceAll("\u00a7.", "");
    }

    private float renderColoredText(DrawContext context, String text, float x, float y, float size) {
        float drawX = x;
        StringBuilder current = new StringBuilder();
        Color currentColor = ITEM_TEXT;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                if (!current.isEmpty()) {
                    RenderUtil.text(context, Msdf.SF_BOLD, drawX, y, current.toString(), size, currentColor);
                    drawX += Msdf.width(Msdf.SF_BOLD, current.toString(), size);
                    current.setLength(0);
                }
                char code = text.charAt(i + 1);
                currentColor = getColorFromCode(code);
                i++;
            } else {
                current.append(text.charAt(i));
            }
        }
        if (!current.isEmpty()) {
            RenderUtil.text(context, Msdf.SF_BOLD, drawX, y, current.toString(), size, currentColor);
            drawX += Msdf.width(Msdf.SF_BOLD, current.toString(), size);
        }
        return drawX;
    }

    private Color getColorFromCode(char code) {
        return switch (code) {
            case '0' -> new Color(0, 0, 0);
            case '1' -> new Color(0, 0, 170);
            case '2' -> new Color(0, 170, 0);
            case '3' -> new Color(0, 170, 170);
            case '4' -> new Color(170, 0, 0);
            case '5' -> new Color(170, 0, 170);
            case '6' -> new Color(255, 170, 0);
            case '7' -> new Color(170, 170, 170);
            case '8' -> new Color(85, 85, 85);
            case '9' -> new Color(85, 85, 255);
            case 'a' -> new Color(85, 255, 85);
            case 'b' -> new Color(85, 255, 255);
            case 'c' -> new Color(255, 85, 85);
            case 'd' -> new Color(255, 85, 255);
            case 'e' -> new Color(255, 255, 85);
            case 'f' -> new Color(255, 255, 255);
            case 'r' -> new Color(230, 230, 235);
            default -> new Color(230, 230, 235);
        };
    }

    private void renderPlayerHead(PlayerEntity player, float x, float y, float size) {
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            SkinTextures skin = clientPlayer.getSkin();
            Identifier texture = skin.body().texturePath();
            renderSkinHead(texture, x, y, size);
        } else {
            Identifier texture = DefaultSkinHelper.getSkinTextures(player.getUuid()).body().texturePath();
            renderSkinHead(texture, x, y, size);
        }
    }

    private void renderSkinHead(Identifier texture, float x, float y, float size) {
        var view = mc.getTextureManager().getTexture(texture).getGlTextureView();
        float radius = 2.5f;
        TexturePipeline.draw(RenderUtil.createProjection(), x, y, size, view,
                0xFFFFFFFF, radius, 0f,
                8f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, false);
        TexturePipeline.draw(RenderUtil.createProjection(), x, y, size, view,
                0xFFFFFFFF, radius, 0f,
                40f / 64f, 8f / 64f, 8f / 64f, 8f / 64f, false);
    }

    private void renderLivingItems(DrawContext context, LivingEntity living, float x, float y, float sc) {
        java.util.List<ItemStack> selected = new java.util.ArrayList<>();
        if (elements.isEnabled("Вещь в руке")) {
            selected.add(living.getMainHandStack());
            selected.add(living.getOffHandStack());
        }
        if (elements.isEnabled("Броня")) {
            selected.add(living.getEquippedStack(EquipmentSlot.FEET));
            selected.add(living.getEquippedStack(EquipmentSlot.LEGS));
            selected.add(living.getEquippedStack(EquipmentSlot.CHEST));
            selected.add(living.getEquippedStack(EquipmentSlot.HEAD));
        }
        ItemStack[] stacks = selected.toArray(ItemStack[]::new);

        int visible = 0;
        for (ItemStack stack : stacks) {
            if (!shouldSkipItem(stack)) visible++;
        }
        if (visible == 0) return;

        float itemSize = ITEM_SIZE * sc;
        float spacing = ITEM_SPACING * sc;
        float totalItemWidth = visible * itemSize + (visible - 1) * spacing;

        float startX = x - totalItemWidth / 2f;
        float itemY = y - itemSize - ARMOR_Y_GAP * sc;
        int visibleIndex = 0;

        for (ItemStack stack : stacks) {
            if (shouldSkipItem(stack)) continue;
            float ix = startX + visibleIndex * (itemSize + spacing);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(ix, itemY);
            float itemScale = itemSize / 16f;
            context.getMatrices().scale(itemScale, itemScale);
            context.drawItem(stack, 0, 0);
            context.drawStackOverlay(mc.textRenderer, stack, 0, 0);
            context.getMatrices().popMatrix();
            visibleIndex++;
        }
    }

    private boolean shouldSkipItem(ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof AirBlockItem;
    }

    private void renderItems(DrawContext context, float tickDelta) {
        int screenW = RenderUtil.getFixedScaledWidth();
        int screenH = RenderUtil.getFixedScaledHeight();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            Vec3d interp = WorldToScreen.getInterpolatedPos(entity, tickDelta);
            float dist = (float) cameraPos.distanceTo(interp);
            if (dist > 150f) continue;

            Vec3d top = interp.add(0, 0.5, 0);
            float[] screen = WorldToScreen.project(top);
            if (screen == null) continue;
            if (screen[0] < -50 || screen[0] > screenW + 50 || screen[1] < -50 || screen[1] > screenH + 50) continue;

            ItemStack stack = itemEntity.getStack();
            String name = itemEntity.getName().getString();
            int count = stack.getCount();
            if (count > 1) name += " \u00a77x\u00a7c" + count;

            float scaleFactor = Math.max(1.0f, 9.0f / Math.max(dist, 3.0f));
            float sc = NAME_TAG_SCALE * scaleFactor;

            float textW = coloredTextWidth(name, TEXT_SIZE * sc);
            float itemSize = ITEM_SIZE * sc;
            float itemSpacing = ITEM_SPACING * sc;

            float paddingX = TAG_PADDING_X * sc;
            float paddingY = TAG_PADDING_Y * sc;

            float contentW = itemSize + itemSpacing + textW;
            float totalW = contentW + paddingX * 2f;

            float contentH = Math.max(itemSize, TEXT_SIZE * sc);
            float totalH = contentH + paddingY * 2f;

            float tagX = screen[0] - totalW / 2f;
            float tagY = screen[1] - totalH;

            if (HUD.Instance != null) {
                HUD.Instance.renderElementPanel(null, tagX, tagY, totalW, totalH, TAG_RADIUS * sc, 1f);
            } else {
                RenderUtil.hudBlur(tagX, tagY, totalW, totalH, TAG_RADIUS * sc, 25f, 1f, DEFAULT_BLUR);
            }

            float contentX = tagX + paddingX;
            float itemDrawY = tagY + (totalH - itemSize) / 2f;
            float textDrawY = tagY + (totalH - TEXT_SIZE * sc) / 2f - 0.5f * sc;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(contentX, itemDrawY);
            float itemScale = itemSize / 16f;
            context.getMatrices().scale(itemScale, itemScale);
            context.drawItem(stack, 0, 0);
            context.getMatrices().popMatrix();

            renderColoredText(context, name, contentX + itemSize + itemSpacing, textDrawY, TEXT_SIZE * sc);
        }
    }
}
