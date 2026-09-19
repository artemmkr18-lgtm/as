package ez.minar.mixins.figura;

import net.minecraft.client.gui.DrawContext;
import org.figuramc.figura.gui.PaperDoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PaperDoll.class, remap = false)
public class FiguraPaperDollDisabler {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void disableRender(DrawContext gui, boolean force, CallbackInfo ci) {
        ci.cancel();
    }
}
