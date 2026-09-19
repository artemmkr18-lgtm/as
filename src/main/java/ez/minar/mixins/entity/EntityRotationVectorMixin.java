package ez.minar.mixins.entity;

import ez.minar.system.managers.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityRotationVectorMixin {

    @Inject(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void redirectRotationVector(CallbackInfoReturnable<Vec3d> cir) {
        if ((Object) this instanceof ClientPlayerEntity player && RotationManager.shouldCorrectMovement() && player.isGliding()) {
            float pitch = RotationManager.getPitch(player.getPitch());
            float yaw = RotationManager.getYaw(player.getYaw());
            float f = pitch * ((float)Math.PI / 180F);
            float g = -yaw * ((float)Math.PI / 180F);
            float h = MathHelper.cos(g);
            float i = MathHelper.sin(g);
            float j = MathHelper.cos(f);
            float k = MathHelper.sin(f);
            cir.setReturnValue(new Vec3d((double)(i * j), (double)(-k), (double)(h * j)));
        }
    }
}
