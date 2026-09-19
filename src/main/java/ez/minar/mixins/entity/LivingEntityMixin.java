package ez.minar.mixins.entity;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.features.movement.NoPush;
import ez.minar.system.features.render.BeautifulHands;
import ez.minar.system.features.render.SwingAnimations;
import ez.minar.system.managers.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    public void isPushable(CallbackInfoReturnable<Boolean> cir) {
        if (NoPush.Instance.isEnabled() && NoPush.players.isEnabled()) cir.setReturnValue(false);
    }

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void preventAuraSprintReset(boolean sprinting, CallbackInfo ci) {
        if (!sprinting || !((Object) this instanceof ClientPlayerEntity)) return;

        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura != null && aura.isDroppingSprint()) {
            ci.cancel();
        }
    }

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void modifyHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this != client.player) return;

        SwingAnimations animations = FunctionManager.getFunction(SwingAnimations.class);
        LivingEntity player = (LivingEntity) (Object) this;
        if (animations != null && animations.shouldOverrideVanilla()) {
            cir.setReturnValue(animations.getSwingDuration(cir.getReturnValue(), player.handSwinging, player.handSwingTicks));
            return;
        }

        BeautifulHands beautifulHands = FunctionManager.getFunction(BeautifulHands.class);
        if (beautifulHands != null && beautifulHands.isEnabled()) {
            cir.setReturnValue(beautifulHands.getSwingDuration(cir.getReturnValue()));
        }
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float correctJumpYaw(LivingEntity instance) {
        return (Object) this == MinecraftClient.getInstance().player && RotationManager.shouldCorrectMovement()
                ? RotationManager.getYaw(instance.getYaw())
                : instance.getYaw();
    }

    private boolean minar_modifiedElytraRotations = false;
    private float minar_prevElytraYaw;
    private float minar_prevElytraPitch;

    @Inject(method = "travel", at = @At("HEAD"))
    private void onTravelHead(net.minecraft.util.math.Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity player && RotationManager.shouldCorrectMovement() && player.isGliding()) {
            minar_prevElytraYaw = player.getYaw();
            minar_prevElytraPitch = player.getPitch();
            player.setYaw(RotationManager.getYaw(minar_prevElytraYaw));
            player.setPitch(RotationManager.getPitch(minar_prevElytraPitch));
            minar_modifiedElytraRotations = true;
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelReturn(net.minecraft.util.math.Vec3d movementInput, CallbackInfo ci) {
        if (minar_modifiedElytraRotations && (Object) this instanceof ClientPlayerEntity player) {
            player.setYaw(minar_prevElytraYaw);
            player.setPitch(minar_prevElytraPitch);
            minar_modifiedElytraRotations = false;
        }
    }
}
