package ez.minar.mixins.entity;

import ez.minar.system.events.EventBus;
import ez.minar.system.events.impl.NoSlowEvent;
import ez.minar.system.events.impl.PostMotionEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void update(CallbackInfo ci) {
        RotationManager.update();
        EventBus.post(new UpdateEvent());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postMotion(CallbackInfo ci) {
        EventBus.post(new PostMotionEvent());
    }

    @Redirect(method = "applyMovementSpeedFactors", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean applyMovementSpeedFactors(ClientPlayerEntity instance) {
        NoSlowEvent event = new NoSlowEvent();
        EventBus.post(event);

        if (event.isCancelled()) {
            return false;
        }

        return instance.isUsingItem();
    }
}
