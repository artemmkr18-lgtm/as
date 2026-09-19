package ez.minar.mixins.screen;

import ez.minar.system.autobuy.manager.AutoBuyManager;
import ez.minar.system.autobuy.ui.AutoBuyScreen;
import ez.minar.system.managers.UnhookManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> {
    @Unique
    private ButtonWidget takeAllButton;
    @Unique
    private ButtonWidget dropAllButton;
    @Unique
    private ButtonWidget storeAllButton;
    @Unique
    private ButtonWidget autoBuyButton;
    @Unique
    private ButtonWidget autoBuySettingsButton;
    @Unique
    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();

    public GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Unique
    private boolean buttonsAdded = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void minar$onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (UnhookManager.isUnhooked()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        String title = this.getTitle().getString();

        if (!buttonsAdded) {
            addButtons(mc, title);
            buttonsAdded = true;
        }

        if (autoBuyButton != null) {
            boolean active = ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().isEnabled() || autoBuyManager.isEnabled();
            autoBuyButton.setMessage(Text.literal("AutoBuy: " + (active ? "§aON" : "§cOFF")));
        }
    }

    @Unique
    private void addButtons(MinecraftClient mc, String title) {
        int baseX = (this.width + this.backgroundWidth) / 2 + 4;
        int baseY = (this.height - this.backgroundHeight) / 2;

        this.dropAllButton = ButtonWidget.builder(
                Text.literal(ez.minar.system.managers.LocalizationManager.get("Выбросить")),
                button -> dropAll(mc)
        ).dimensions(baseX, baseY, 80, 20).build();

        this.takeAllButton = ButtonWidget.builder(
                Text.literal(ez.minar.system.managers.LocalizationManager.get("Взять всё")),
                button -> takeAll(mc)
        ).dimensions(baseX, baseY + 22, 80, 20).build();

        this.storeAllButton = ButtonWidget.builder(
                Text.literal(ez.minar.system.managers.LocalizationManager.get("Сложить всё")),
                button -> storeAll(mc)
        ).dimensions(baseX, baseY + 44, 80, 20).build();

        this.addDrawableChild(dropAllButton);
        this.addDrawableChild(takeAllButton);
        this.addDrawableChild(storeAllButton);

        if (title.contains("Аукцион") || title.contains("Аукционы") || title.contains("Auction")) {
            int autoBuyX = (this.width - this.backgroundWidth) / 2 + this.backgroundWidth / 2 - 105;
            int autoBuyY = Math.max(2, (this.height - this.backgroundHeight) / 2 - 25);

            this.autoBuyButton = ButtonWidget.builder(
                    Text.literal("AutoBuy: " + (ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().isEnabled() ? "§aON" : "§cOFF")),
                    button -> {
                        boolean newState = !ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().isEnabled();
                        ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().setEnabled(newState);
                        autoBuyManager.setEnabled(newState);
                        button.setMessage(Text.literal("AutoBuy: " + (newState ? "§aON" : "§cOFF")));
                    }
            ).dimensions(autoBuyX, autoBuyY, 100, 20).build();
            this.addDrawableChild(autoBuyButton);

            this.autoBuySettingsButton = ButtonWidget.builder(
                    Text.literal(ez.minar.system.managers.LocalizationManager.get("Настройки")),
                    button -> mc.setScreen(new ez.minar.system.autobuy.ui.AutoBuyMenuScreen())
            ).dimensions(autoBuyX + 105, autoBuyY, 95, 20).build();
            this.addDrawableChild(autoBuySettingsButton);
        }
    }

    @Unique
    private void takeAll(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null || mc.interactionManager == null) return;
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory != player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        player.currentScreenHandler.syncId,
                        slot.id,
                        0,
                        SlotActionType.QUICK_MOVE,
                        player
                );
            }
        }
    }

    @Unique
    private void dropAll(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null || mc.interactionManager == null) return;
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory != player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        player.currentScreenHandler.syncId,
                        slot.id,
                        1,
                        SlotActionType.THROW,
                        player
                );
            }
        }
    }

    @Unique
    private void storeAll(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null || mc.interactionManager == null) return;
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory == player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        player.currentScreenHandler.syncId,
                        slot.id,
                        0,
                        SlotActionType.QUICK_MOVE,
                        player
                );
            }
        }
    }
}
