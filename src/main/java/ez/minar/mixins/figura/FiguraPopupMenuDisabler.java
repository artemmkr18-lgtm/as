package ez.minar.mixins.figura;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import org.figuramc.figura.gui.PopupMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PopupMenu.class)
public class FiguraPopupMenuDisabler {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableRender(DrawContext gui, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "setEnabled(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableEnable(boolean enabled, CallbackInfo ci) {
        if (enabled) {
            ci.cancel();
        }
    }

    @Inject(method = "setEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void disableEntity(Entity entity, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void alwaysDisabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
