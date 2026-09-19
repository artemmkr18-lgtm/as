package ez.minar.mixins.render;

import ez.minar.system.features.render.NoRender;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StatusEffectFogModifier.class)
public class StatusEffectFogModifierMixin {
    @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
    private void minar$noRenderBadEffectFog(CameraSubmersionType submersionType, Entity entity,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.shouldDisableBadEffects()) {
            cir.setReturnValue(false);
        }
    }
}
