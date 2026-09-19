package ez.minar.mixins.figura;

import ez.minar.system.features.misc.FiguraRuntimeGate;
import net.minecraft.nbt.NbtCompound;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.UserData;
import org.figuramc.figura.backend2.NetworkStuff;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class FiguraRuntimeGateMixin {

    @Mixin(value = FiguraMod.class, remap = false)
    public static class Tick {
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private static void minar$skipTickWhileCosmeticsDisabled(CallbackInfo ci) {
            if (!FiguraRuntimeGate.isEnabled()) {
                ci.cancel();
            }
        }
    }

    @Mixin(value = NetworkStuff.class, remap = false)
    public static class Network {
        @Inject(method = {"auth", "reAuth"}, at = @At("HEAD"), cancellable = true)
        private static void minar$skipAuthWhileCosmeticsDisabled(CallbackInfo ci) {
            if (!FiguraRuntimeGate.isEnabled()) {
                ci.cancel();
            }
        }

        @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
        private static void minar$skipDelayedConnectWhileCosmeticsDisabled(String token, CallbackInfo ci) {
            if (!FiguraRuntimeGate.isEnabled()) {
                ci.cancel();
            }
        }
    }

    @Mixin(value = UserData.class, remap = false)
    public static class AvatarLoading {
        @Inject(method = "loadAvatar", at = @At("HEAD"), cancellable = true)
        private void minar$discardCompletedLoadWhileCosmeticsDisabled(NbtCompound nbt, CallbackInfo ci) {
            if (!FiguraRuntimeGate.isEnabled()) {
                ci.cancel();
            }
        }
    }
}
