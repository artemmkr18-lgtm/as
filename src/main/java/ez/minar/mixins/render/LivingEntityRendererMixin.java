package ez.minar.mixins.render;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.render.AntiInvisible;
import ez.minar.system.features.render.EntityESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Unique
    private static VertexConsumerProvider.Immediate minar$outlineConsumers;

    @Unique
    private int minar$antiInvisibleAlpha = -1;

    @Shadow
    protected M model;

    @Shadow
    public abstract Identifier getTexture(S state);

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void minar$updateExtraRenderState(LivingEntity entity, S state, float tickDelta, CallbackInfo ci) {
        EntityESP entityESP = FunctionManager.getFunction(EntityESP.class);
        if (entityESP != null) {
            entityESP.trackRenderedEntity(entity, state);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.player && client.options.getPerspective().isFirstPerson()) {
            state.outlineColor = EntityRenderState.NO_OUTLINE;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void minar$showInvisibleEntity(S state, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                           CameraRenderState cameraRenderState, CallbackInfo ci) {
        minar$antiInvisibleAlpha = -1;

        AntiInvisible antiInvisible = FunctionManager.getFunction(AntiInvisible.class);
        if (antiInvisible == null || !antiInvisible.isEnabled() || !state.invisible) {
            return;
        }

        state.invisibleToPlayer = false;
        minar$antiInvisibleAlpha = antiInvisible.getAlpha();
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 0x26FFFFFF))
    private int minar$applyInvisiblePlayerAlpha(int originalColor) {
        if (minar$antiInvisibleAlpha < 0) {
            return originalColor;
        }
        return (minar$antiInvisibleAlpha << 24) | 0x00FFFFFF;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void minar$renderEntityEsp(S state, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                       CameraRenderState cameraRenderState, CallbackInfo ci) {
        EntityESP esp = FunctionManager.getFunction(EntityESP.class);
        if (esp == null || !esp.shouldRenderLiving(state)) return;

        if (!esp.hasOutlineEffect()) return;

        esp.renderEntityMask(() -> {
            model.setAngles(state);
            if (minar$outlineConsumers == null) {
                minar$outlineConsumers = VertexConsumerProvider.immediate(new BufferAllocator(786432));
            }
            matrices.push();
            float scale = 1.01f;
            matrices.scale(scale, scale, scale);
            matrices.translate(0.0f, -0.01f, 0.0f);
            VertexConsumer buffer = minar$outlineConsumers.getBuffer(model.getLayer(getTexture(state)));
            model.render(matrices, buffer, state.light, LivingEntityRenderer.getOverlay(state, 0.0f), 0xFFFFFFFF);
            minar$outlineConsumers.draw();
            matrices.pop();
        }, state);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void minar$renderChinaHat(S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        ez.minar.system.features.render.ChinaHat chinaHat = FunctionManager.getFunction(ez.minar.system.features.render.ChinaHat.class);
        if (chinaHat != null && chinaHat.isEnabled() && state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState playerState) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null && playerState.id == mc.player.getId()) {
                if (!mc.options.getPerspective().isFirstPerson() && model instanceof net.minecraft.client.render.entity.model.BipedEntityModel<?> bipedModel) {
                     chinaHat.render(matrices, bipedModel);
                }
            }
        }
    }
}
