package ez.minar.mixins.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void minar$hideVanillaPlayerNameTags(PlayerEntityRenderState state, MatrixStack matrices,
                                                 OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState,
                                                 CallbackInfo ci) {
        if (ez.minar.system.features.render.NameTags.Instance != null && ez.minar.system.features.render.NameTags.Instance.isEnabled() && ez.minar.system.features.render.NameTags.Instance.shouldHideVanillaLabels()) {
            ci.cancel();
        }
    }
}
