package ez.minar.mixins.entity;

import ez.minar.system.events.EventBus;
import ez.minar.system.events.impl.EventOnMovePost;
import ez.minar.system.features.movement.NoPush;
import ez.minar.system.managers.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Redirect(method = "updateMovementInFluid",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/fluid/FluidState;getVelocity(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d stopWaterVelocity(FluidState instance, BlockView world, BlockPos pos) {
        if (NoPush.Instance.isEnabled() && NoPush.water.isEnabled()) {
            return Vec3d.ZERO;
        }
        return instance.getVelocity(world, pos);
    }

    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float correctMovementYaw(Entity instance) {
        return RotationManager.shouldCorrectMovement() && instance instanceof ClientPlayerEntity
                ? RotationManager.getYaw(instance.getYaw())
                : instance.getYaw();
    }

    @Inject(method = "updateVelocity", at = @At("TAIL"))
    private void onUpdateVelocityPost(float speed, Vec3d movementInput, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player != null && self.getId() == player.getId()) {
            EventBus.post(new EventOnMovePost(speed, movementInput));
        }
    }

}
