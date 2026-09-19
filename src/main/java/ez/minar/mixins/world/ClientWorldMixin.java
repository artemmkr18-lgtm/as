package ez.minar.mixins.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("HEAD"), cancellable = true)
    private void onScheduleBlockRerenderIfNeeded(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState old, net.minecraft.block.BlockState updated, CallbackInfo ci) {
        if (MinecraftClient.getInstance().world != (Object) this) ci.cancel();
    }

    @Inject(method = "updateListeners", at = @At("HEAD"), cancellable = true)
    private void onUpdateListeners(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState oldState, net.minecraft.block.BlockState newState, int flags, CallbackInfo ci) {
        if (MinecraftClient.getInstance().world != (Object) this) ci.cancel();
    }
}
