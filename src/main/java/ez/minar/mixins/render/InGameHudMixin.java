package ez.minar.mixins.render;

import ez.minar.system.events.EventBus;
import ez.minar.system.events.EventManager;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.features.render.Crosshair;
import ez.minar.system.features.render.HUD;
import ez.minar.system.features.render.NoRender;
import ez.minar.system.menu.cosmetics.CosmeticsScreen;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    MinecraftClient mc = MinecraftClient.getInstance();

    @Shadow
    protected abstract void renderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter,
                                             net.minecraft.entity.player.PlayerEntity player,
                                             net.minecraft.item.ItemStack stack, int seed);

    @Inject(method = "render", at = @At("RETURN"))
    private void renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        context.getMatrices().pushMatrix();
        float scale = RenderUtil.getDrawContextScale();
        context.getMatrices().scale(scale, scale);
        try {
            EventBus.post(new Render2DEvent(tickCounter, context));
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (mc.currentScreen == EventManager.clickGui
                || mc.currentScreen instanceof CosmeticsScreen
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
        if (HUD.shouldDisableDefaultHotbar() && HUD.Instance != null && mc.player != null) {
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
        return !HUD.shouldHideStatusBars() && interactionManager.hasExperienceBar();
    }

    @Redirect(method = "renderMainHud",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z"))
    private boolean minar$disableExperienceLevelText(ClientPlayerInteractionManager interactionManager) {
        return !HUD.shouldHideStatusBars() && interactionManager.hasExperienceBar();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void minar$cancelScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HUD.shouldDisableDefaultScoreboard()) {
            ci.cancel();
        }
    }

}
