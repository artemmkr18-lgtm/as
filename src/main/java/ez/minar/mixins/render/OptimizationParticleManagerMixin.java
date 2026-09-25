package ez.minar.mixins.render;

import ez.minar.system.features.render.Optimization;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class OptimizationParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void minar$dropParticleInstance(Particle particle, CallbackInfo ci) {
        if (Optimization.shouldDropParticleStatic()) {
            ci.cancel();
        }
    }
}
