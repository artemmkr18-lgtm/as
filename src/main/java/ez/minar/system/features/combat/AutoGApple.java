package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

@NewFunction(name = "AutoGApple", desc = "Automatically eats golden apples from offhand", category = Category.COMBAT)
public class AutoGApple extends Function {

    private final NumberSetting delay = new NumberSetting("UseDelay", 0, 0, 2000, 50);
    private final NumberSetting health = new NumberSetting("Health", 15, 1, 36, 0.5);
    private final BooleanSetting absorption = new BooleanSetting("Absorption", false);
    private final BooleanSetting autoTotemIntegration = new BooleanSetting("AutoTotemIntegration", true);

    private boolean active;
    private long lastUseTime;
    private long lastSwapTime;

    public AutoGApple() {
        addSettings(delay, health, absorption, autoTotemIntegration);
    }

    @Override
    public void onDisable() {
        stopUsing();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null) {
            stopUsing();
            return;
        }

        float currentHealth = mc.player.getHealth();
        if (absorption.isEnabled()) {
            currentHealth += mc.player.getAbsorptionAmount();
        }

        boolean shouldEat = currentHealth <= health.getValue();
        if (!shouldEat) {
            stopUsing();
            return;
        }

        if (!hasGappleInOffhand()) {
            if (tryMoveGappleToOffhand()) {
                return;
            }

            stopUsing();
            return;
        }

        if (mc.player.isUsingItem()) {
            active = true;
            return;
        }

        if (System.currentTimeMillis() - lastUseTime < delay.getValue()) {
            return;
        }

        active = true;
        lastUseTime = System.currentTimeMillis();

        if (mc.currentScreen == null) {
            mc.options.useKey.setPressed(true);
        } else {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        }
    }

    private boolean hasGappleInOffhand() {
        return mc.player != null
                && (mc.player.getOffHandStack().isOf(Items.GOLDEN_APPLE)
                || mc.player.getOffHandStack().isOf(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private boolean tryMoveGappleToOffhand() {
        if (!autoTotemIntegration.isEnabled()
                || mc.currentScreen != null
                || mc.player.currentScreenHandler == null
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()
                || System.currentTimeMillis() - lastSwapTime < 200L) {
            return false;
        }

        AutoTotem autoTotem = FunctionManager.getFunction(AutoTotem.class);
        if (autoTotem == null || !autoTotem.isEnabled()) {
            return false;
        }

        int slot = findGappleSlot();
        if (slot == -1) {
            return false;
        }

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                toInventorySlot(slot),
                40,
                SlotActionType.SWAP,
                mc.player
        );
        lastSwapTime = System.currentTimeMillis();
        return true;
    }

    private int findGappleSlot() {
        for (int i = 35; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                return i;
            }
        }

        return -1;
    }

    private int toInventorySlot(int slot) {
        return slot < 9 ? slot + 36 : slot;
    }

    private void stopUsing() {
        if (active) {
            mc.options.useKey.setPressed(false);
            active = false;
        }
    }
}
