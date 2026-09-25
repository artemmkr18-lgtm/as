package ez.minar.mixins.screen;

import ez.minar.utils.render.chat.ChatAnimationRenderState;
import net.minecraft.client.font.DrawnTextConsumer;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = {
    "net.minecraft.client.gui.hud.ChatHud$Hud",
    "net.minecraft.client.gui.hud.ChatHud$Interactable"
})
public abstract class ChatHudBackendMixin {
    @ModifyVariable(method = "fill(IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int minar$shiftFillLeft(int x) {
        return Math.round(x - ChatAnimationRenderState.shift());
    }

    @ModifyVariable(method = "fill(IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int minar$shiftFillRight(int x) {
        return Math.round(x - ChatAnimationRenderState.shift());
    }

    @ModifyArg(
        method = "fill(IIIII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
        index = 4
    )
    private int minar$fadeFill(int color) {
        int alpha = Math.max(0, Math.min(255, Math.round((color >>> 24) * ChatAnimationRenderState.alpha())));
        return color & 0x00FFFFFF | alpha << 24;
    }

    @ModifyArg(
        method = "text(IFLnet/minecraft/text/OrderedText;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/font/DrawnTextConsumer;text(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/client/font/DrawnTextConsumer$Transformation;Lnet/minecraft/text/OrderedText;)V"
        ),
        index = 3
    )
    private DrawnTextConsumer.Transformation minar$animateText(DrawnTextConsumer.Transformation transformation) {
        float shift = ChatAnimationRenderState.shift();
        Matrix3x2f pose = new Matrix3x2f(transformation.pose()).translate(-shift, 0.0f);
        return transformation.withPose(pose).withOpacity(transformation.opacity() * ChatAnimationRenderState.alpha());
    }

    @ModifyVariable(
        method = "indicator(IIIIFLnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int minar$shiftIndicatorLeft(int x) {
        return Math.round(x - ChatAnimationRenderState.shift());
    }

    @ModifyVariable(
        method = "indicator(IIIIFLnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 2
    )
    private int minar$shiftIndicatorRight(int x) {
        return Math.round(x - ChatAnimationRenderState.shift());
    }

    @ModifyVariable(
        method = "indicator(IIIIFLnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private float minar$fadeIndicator(float opacity) {
        return opacity * ChatAnimationRenderState.alpha();
    }

    @ModifyVariable(
        method = "indicatorIcon(IIZLnet/minecraft/client/gui/hud/MessageIndicator;Lnet/minecraft/client/gui/hud/MessageIndicator$Icon;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int minar$shiftIndicatorIcon(int x) {
        return Math.round(x - ChatAnimationRenderState.shift());
    }
}
