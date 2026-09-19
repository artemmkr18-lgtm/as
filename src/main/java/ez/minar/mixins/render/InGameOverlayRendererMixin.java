package ez.minar.mixins.render;

import ez.minar.system.features.render.NoRender;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void minar$noRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                  Sprite sprite, CallbackInfo ci) {
        if (NoRender.shouldDisableFireOverlay()) {
            ci.cancel();
        }
    }
}
