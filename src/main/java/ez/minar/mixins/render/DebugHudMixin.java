package ez.minar.mixins.render;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.misc.StreamerMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hideDebugHud(DrawContext drawContext, CallbackInfo callbackInfo) {
        StreamerMode streamerMode = FunctionManager.getFunction(StreamerMode.class);
        if (streamerMode != null && streamerMode.isEnabled()) {
            callbackInfo.cancel();
        }
    }
}
