package ez.minar.mixins.render;

import ez.minar.system.events.EventBus;
import ez.minar.system.events.EventManager;
import ez.minar.system.events.impl.HudRenderEvent;
import ez.minar.system.events.impl.PostHudRenderEvent;
import ez.minar.system.events.impl.PreHudRenderEvent;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.features.render.Crosshair;
import ez.minar.system.features.render.HUD;
import ez.minar.system.features.render.NoRender;
import ez.minar.system.menu.cosmetics.CosmeticsScreen;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private PlayerListHud playerListHud;

    @Shadow
    protected abstract void renderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter,
                                             net.minecraft.entity.player.PlayerEntity player,
                                             net.minecraft.item.ItemStack stack, int seed);

    @Unique
    private float minar$tabAnimValue = 0.0f;
    @Unique
    private long minar$lastTabUpdate = System.currentTimeMillis();
    @Unique
    private boolean minar$mainHudShifted;

    @Inject(method = "render", at = @At("HEAD"))
    private void triggerPreHudRenderEvent(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        EventBus.post(new PreHudRenderEvent(context, tickCounter.getTickProgress(false)));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        EventBus.post(new PostHudRenderEvent(context, tickCounter.getTickProgress(false)));
        context.getMatrices().pushMatrix();
        float scale = RenderUtil.getDrawContextScale();
        context.getMatrices().scale(scale, scale);
        try {
            EventBus.post(new Render2DEvent(tickCounter, context));
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void rockstar$animatePlayerList(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        if (this.client.world == null || this.client.player == null || this.client.player.networkHandler == null) {
            return;
        }
        Scoreboard scoreboard = this.client.world.getScoreboard();
        ScoreboardObjective scoreboardObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        boolean bl = this.client.options.playerListKey.isPressed() && (!this.client.isInSingleplayer() || this.client.player.networkHandler.getListedPlayerListEntries().size() > 1 || scoreboardObjective != null);

        long now = System.currentTimeMillis();
        float delta = (now - minar$lastTabUpdate) / 200.0f;
        minar$lastTabUpdate = now;
        if (bl) {
            minar$tabAnimValue = Math.min(1.0f, minar$tabAnimValue + delta);
        } else {
            minar$tabAnimValue = Math.max(0.0f, minar$tabAnimValue - delta);
        }

        this.playerListHud.setVisible(bl);
        if (minar$tabAnimValue <= 0.005f) {
            if (!bl) callbackInfo.cancel();
            return;
        }

        callbackInfo.cancel();
        float f = minar$tabAnimValue;
        float f2 = MathHelper.clamp(f, 0.0f, 1.0f);
        float f3 = drawContext.getScaledWindowWidth();
        float f4 = 0.96f + 0.04f * f2;
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(f3 / 2.0f, 0.0f);
        drawContext.getMatrices().scale(f4, f4);
        drawContext.getMatrices().translate(-f3 / 2.0f, (f2 - 1.0f) * 10.0f);
        this.playerListHud.render(drawContext, drawContext.getScaledWindowWidth(), scoreboard, scoreboardObjective);
        drawContext.getMatrices().popMatrix();
    }

    @Inject(method = "renderMainHud", at = @At("HEAD"))
    private void rockstar$shiftVanillaMainHud(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        if (HUD.Instance != null && HUD.Instance.isEnabled() && HUD.Instance.hotbar.isEnabled()) {
            float shift = HUD.getHotbarShift();
            if (shift > 0.0f) {
                drawContext.getMatrices().pushMatrix();
                drawContext.getMatrices().translate(0.0f, -shift);
                this.minar$mainHudShifted = true;
            }
        }
    }

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void rockstar$popVanillaMainHud(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        if (this.minar$mainHudShifted) {
            this.minar$mainHudShifted = false;
            drawContext.getMatrices().popMatrix();
        }
        EventBus.post(new HudRenderEvent(drawContext, renderTickCounter.getTickProgress(false)));
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (this.client.currentScreen == EventManager.clickGui
                || this.client.currentScreen instanceof CosmeticsScreen
                || Crosshair.shouldReplaceVanilla()
                || NoRender.shouldDisableCrosshair()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void minar$noRenderVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NoRender.shouldDisableVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void minar$noRenderBadEffects(DrawContext context, float distortionStrength, CallbackInfo ci) {
        if (NoRender.shouldDisableBadEffects()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void minar$cancelDefaultHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HUD.shouldDisableDefaultHotbar() && HUD.Instance != null && this.client.player != null) {
            HUD.Instance.renderHotbarXyeta(context, tickCounter,
                    (x, y, tick, player, stack, seed) ->
                            renderHotbarItem(context, x, y, tick, player, stack, seed));
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void minar$cancelDefaultStatusBars(DrawContext context, CallbackInfo ci) {
        if (HUD.shouldHideStatusBars()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void minar$hideDefaultStatusEffectIcons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HUD.shouldHideDefaultStatusEffectIcons()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void minar$cancelDefaultHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (HUD.shouldHideStatusBars()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldShowExperienceBar", at = @At("HEAD"), cancellable = true)
    private void minar$hideDefaultExperienceBar(CallbackInfoReturnable<Boolean> cir) {
        if (HUD.shouldHideStatusBars()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "getCurrentBarType",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z"))
    private boolean minar$disableExperienceBarType(ClientPlayerInteractionManager interactionManager) {
        return !HUD.shouldHideStatusBars() && interactionManager != null && interactionManager.hasExperienceBar();
    }

    @Redirect(method = "renderMainHud",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z"))
    private boolean minar$disableExperienceLevelText(ClientPlayerInteractionManager interactionManager) {
        return !HUD.shouldHideStatusBars() && interactionManager != null && interactionManager.hasExperienceBar();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void minar$cancelScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HUD.shouldDisableDefaultScoreboard()) {
            ci.cancel();
        }
    }
}
