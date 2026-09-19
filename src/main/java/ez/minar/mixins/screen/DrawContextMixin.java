package ez.minar.mixins.screen;

import ez.minar.system.menu.TitleScreenMenuRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public class DrawContextMixin {
    @Inject(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
            at = @At("HEAD"), cancellable = true)
    private void minar$hideTitleAccountString(TextRenderer textRenderer, String text, int x, int y, int color,
                                                CallbackInfo ci) {
        if (TitleScreenMenuRenderer.shouldHideTitleScreenText(text)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
            at = @At("HEAD"), cancellable = true)
    private void minar$hideTitleAccountText(TextRenderer textRenderer, Text text, int x, int y, int color,
                                              CallbackInfo ci) {
        if (TitleScreenMenuRenderer.shouldHideTitleScreenText(text.getString())) {
            ci.cancel();
        }
    }
}
