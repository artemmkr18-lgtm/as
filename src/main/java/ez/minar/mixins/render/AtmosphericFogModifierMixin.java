package ez.minar.mixins.render;

import ez.minar.system.features.render.Fog;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogModifier.class)
public class AtmosphericFogModifierMixin {
    @Inject(method = "applyStartEndModifier", at = @At("RETURN"))
    private void applyFogSettings(FogData data, Camera camera, ClientWorld world, float viewDistance,
                                  RenderTickCounter renderTickCounter, CallbackInfo ci) {
        Fog.applyAtmosphericFog(data);
    }
}
