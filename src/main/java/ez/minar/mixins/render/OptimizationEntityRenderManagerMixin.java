package ez.minar.mixins.render;

import ez.minar.system.features.render.Optimization;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class OptimizationEntityRenderManagerMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void minar$cullEntities(E entity, Frustum frustum, double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player == entity) return;

        if (Optimization.shouldCullEntityStatic(entity, entity.squaredDistanceTo(cameraX, cameraY, cameraZ))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getAndUpdateRenderState", at = @At("TAIL"))
    private <E extends Entity> void minar$disableShadows(E entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
        if (Optimization.shouldDisableShadows()) {
            EntityRenderState state = cir.getReturnValue();
            if (state != null) {
                state.shadowRadius = 0.0f;
            }
        }
    }
}
