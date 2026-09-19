package ez.minar.mixins.figura;

import ez.minar.system.menu.cosmetics.FiguraPreviewAvatarContext;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class FiguraPreviewAvatarMixin {

    @Mixin(PlayerEntityRenderer.class)
    public static class CaptureRenderState {
        @Inject(method = "updateRenderState", at = @At("RETURN"))
        private void minar$capturePreviewAvatar(
                PlayerLikeEntity player,
                PlayerEntityRenderState state,
                float tickDelta,
                CallbackInfo ci
        ) {
            FiguraPreviewAvatarContext.capture(state);
        }
    }

    @Mixin(value = AvatarManager.class, remap = false)
    public static class ResolveRenderState {
        @Inject(method = "getAvatar(Lnet/minecraft/client/render/entity/state/EntityRenderState;)Lorg/figuramc/figura/avatar/Avatar;", at = @At("HEAD"), cancellable = true)
        private static void minar$resolvePreviewAvatar(
                EntityRenderState state,
                CallbackInfoReturnable<Avatar> cir
        ) {
            Avatar avatar = FiguraPreviewAvatarContext.get(state);
            if (avatar != null) {
                cir.setReturnValue(avatar);
            }
        }
    }
}
