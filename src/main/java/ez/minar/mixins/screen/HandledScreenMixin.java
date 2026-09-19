package ez.minar.mixins.screen;

import ez.minar.system.features.player.AhHelper;
import ez.minar.system.features.player.InventoryCleaner;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int backgroundWidth;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void minar$addInventoryCleanerButton(CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen) || !InventoryCleaner.shouldShowButton()) {
            return;
        }

        int width = 72;
        int height = 18;
        int buttonX = x + (backgroundWidth - width) / 2;
        int buttonY = Math.max(4, y - height - 4);
        this.addDrawableChild(ButtonWidget.builder(Text.literal(ez.minar.system.managers.LocalizationManager.get("Очистить")), button -> InventoryCleaner.requestClean())
                .dimensions(buttonX, buttonY, width, height)
                .build());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void minar$ahHelperHighlight(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AhHelper.renderOverlay((HandledScreen<?>) (Object) this, context, x, y);
    }
}
