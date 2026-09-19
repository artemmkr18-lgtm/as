package ez.minar.mixins.figura;

import org.figuramc.figura.gui.ActionWheel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ActionWheel.class)
public class FiguraActionWheelDisabler {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableRender(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "setEnabled(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableEnable(boolean enabled, CallbackInfo ci) {
        if (enabled) {
            org.figuramc.figura.gui.ActionWheel.setEnabled(false);
            ci.cancel();
        }
    }
}
