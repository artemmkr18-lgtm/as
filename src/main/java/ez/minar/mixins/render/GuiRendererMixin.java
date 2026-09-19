package ez.minar.mixins.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import ez.minar.system.menu.TitleScreenMenuRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void minar$renderTitleScreenButtons(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        TitleScreenMenuRenderer.renderButtons();
    }
}
