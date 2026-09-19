package ez.minar.mixins.render;


import ez.minar.system.managers.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class SilentRotationRenderMixin {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void updateSilentRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if ((Object) entity != mc.player) return;



        if (!RotationManager.isActive()) return;

        float yaw = RotationManager.getRenderYaw(state.bodyYaw);
        state.bodyYaw = yaw;
        state.relativeHeadYaw = 0.0F;
        state.pitch = RotationManager.getRenderPitch(state.pitch);
    }
}
