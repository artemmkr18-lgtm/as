package ez.minar.mixins.render;

import ez.minar.system.features.render.Optimization;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public class OptimizationLightmapMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void minar$throttleLightmap(float delta, CallbackInfo ci) {
        if (!Optimization.shouldUpdateLightmapStatic()) {
            ci.cancel();
        }
    }
}
