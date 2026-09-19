package ez.minar.mixins.render;

import ez.minar.system.features.render.Fog;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void applyFogColor(Camera camera, float tickProgress, ClientWorld world, int viewDistance,
                               float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        cir.setReturnValue(Fog.applyAtmosphericColor(camera, cir.getReturnValue()));
    }
}
