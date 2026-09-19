package ez.minar.mixins.entity;

import ez.minar.system.events.EventBus;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.player.NoInteract;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void noInteractAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {

        NoInteract noInteract = FunctionManager.getFunction(NoInteract.class);
        if (noInteract != null && noInteract.shouldCancelEntity(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        EventBus.post(new AttackEntityEvent(player, target));
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        NoInteract noInteract = FunctionManager.getFunction(NoInteract.class);
        if (noInteract != null && noInteract.shouldCancelBlock(hitResult.getBlockPos())) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void interactEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (ez.minar.system.managers.RotationManager.isActive()) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        NoInteract noInteract = FunctionManager.getFunction(NoInteract.class);
        if (noInteract != null && noInteract.shouldCancelEntity(entity)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void interactEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (ez.minar.system.managers.RotationManager.isActive()) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        NoInteract noInteract = FunctionManager.getFunction(NoInteract.class);
        if (noInteract != null && noInteract.shouldCancelEntity(entity)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
