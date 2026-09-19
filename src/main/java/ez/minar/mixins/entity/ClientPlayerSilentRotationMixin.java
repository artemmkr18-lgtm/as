package ez.minar.mixins.entity;

import ez.minar.system.managers.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerSilentRotationMixin {
    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float getSilentYaw(ClientPlayerEntity instance) {
        return RotationManager.getYaw(instance.getYaw());
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float getSilentPitch(ClientPlayerEntity instance) {
        return RotationManager.getPitch(instance.getPitch());
    }
}
