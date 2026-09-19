package ez.minar.mixins.screen;

import ez.minar.system.menu.ClickGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(CallbackInfoReturnable<Integer> cir) {
        if (this.client != null && this.client.currentScreen instanceof ClickGui) {
            int optionMax = this.client.options.getMaxFps().getValue();
            cir.setReturnValue(Math.max(120, optionMax));
        }
    }
}
