package ez.minar.mixins.screen;

import ez.minar.system.managers.UnhookManager;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(PressableWidget.class)
public class PressableWidgetMixin {
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void minar$drawLiquidGlassButton(DrawContext context, CallbackInfo ci) {
        if (UnhookManager.isUnhooked() || !(MinecraftClient.getInstance().currentScreen instanceof TitleScreen)) {
            return;
        }

        ClickableWidget widget = (ClickableWidget) (Object) this;
        float alpha = widget.getAlpha();
        minar$drawGlassButton(widget, alpha);

        ci.cancel();
    }

    private void minar$drawGlassButton(ClickableWidget widget, float alpha) {
        float guiScale = RenderUtil.getScaleFactor();
        float x = widget.getX() * guiScale;
        float y = widget.getY() * guiScale;
        float width = widget.getWidth() * guiScale;
        float height = widget.getHeight() * guiScale;
        boolean selected = widget.isSelected();
        float radius = 4f;
        Color tint = selected
                ? new Color(255, 255, 255, Math.round(130f * alpha))
                : new Color(210, 218, 225, Math.round(92f * alpha));

        RenderUtil.shadow(x + 0.5f, y + 1.2f, width - 1f, height - 0.4f, radius,
                9f, 0.28f * alpha, 2.2f, new Color(0, 0, 0, 170));
        RenderUtil.rect(x, y, width, height, radius,
                new Color(18, 22, 28, Math.round((selected ? 128f : 96f) * alpha)));
        RenderUtil.fluidGlass(x, y, width, height, radius, selected ? 1.0f : 0.9f,
                widget.active ? 0.94f * alpha : 0.52f * alpha, tint);
        RenderUtil.outline(x + 0.45f, y + 0.45f, width - 0.9f, height - 0.9f, radius,
                0.85f, new Color(255, 255, 255, Math.round((selected ? 128f : 76f) * alpha)),
                new Color(255, 255, 255, Math.round(32f * alpha)));
        RenderUtil.rect(x + 6f, y + 2f, width - 12f, 1.05f, 0.5f,
                new Color(255, 255, 255, Math.round((selected ? 74f : 48f) * alpha)),
                new Color(255, 255, 255, 0));
    }
}
