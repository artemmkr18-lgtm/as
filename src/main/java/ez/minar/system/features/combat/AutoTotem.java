package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Box;

import java.util.List;

@NewFunction(
        name = "AutoTotem",
        desc = "Moves a totem to offhand when dangerous",
        category = Category.COMBAT
)
public class AutoTotem extends Function {

    private final NumberSetting health =
            new NumberSetting("Порог здоровья", 4.0, 2.0, 8.0, 0.1);

    private final BooleanSetting crystalCheck =
            new BooleanSetting("Кристаллы", true);

    private final BooleanSetting tntCheck =
            new BooleanSetting("TNT", true);

    private final NumberSetting tntDistance =
            new NumberSetting("Дистанция до TNT", 8.0, 0.0, 50.0, 0.1);

    private long lastSwapTime;

    private SwapPhase swapPhase = SwapPhase.READY;

    private boolean wasForwardPressed;
    private boolean wasBackPressed;
    private boolean wasLeftPressed;
    private boolean wasRightPressed;
    private boolean wasJumpPressed;
    private boolean keysOverridden = false;
    private int pendingTotemSlot = -1;

    public AutoTotem() {
        addSettings(
                health,
                crystalCheck,
                tntCheck,
                tntDistance
        );

        tntCheck.runnable(this::updateVisibility);
        updateVisibility();
    }

    @Override
    public void onEnable() {
        resetState();
        lastSwapTime = 0L;
    }

    @Override
    public void onDisable() {
        resetState();
        lastSwapTime = 0L;
        restoreMovementKeys();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        updateVisibility();

        if (nullCheck.all()
                || mc.interactionManager == null
                || mc.world == null) {
            resetState();
            return;
        }

        if (mc.currentScreen != null) {
            resetState();
            return;
        }

        if (swapPhase == SwapPhase.READY) {
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                return;
            }

            if (!trigger()) return;

            ItemStack offhand = mc.player.getOffHandStack();
            int totemSlot = findBestTotemSlot();

            if (totemSlot == -1) return;

            ItemStack totemStack = mc.player.getInventory().getStack(totemSlot);

            boolean needSwap =
                    !offhand.isOf(Items.TOTEM_OF_UNDYING)
                            || offhand.hasEnchantments() && !totemStack.hasEnchantments();

            if (needSwap && canSwap()) {
                saveMovementKeys();
                pendingTotemSlot = totemSlot;
                swapPhase = SwapPhase.SLOWING_DOWN;
            }
        } else {
            processSwap();
        }
    }

    private void updateVisibility() {
        tntDistance.setVisible(tntCheck.isEnabled());
    }

    private boolean trigger() {
        if (mc.player.getItemCooldownManager()
                .isCoolingDown(Items.TOTEM_OF_UNDYING.getDefaultStack())) {
            return false;
        }

        float elytraBonus =
                mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                        ? 4.0F
                        : 0.0F;

        float currentHealth =
                mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (currentHealth < health.getValue() + elytraBonus) {
            return true;
        }

        if (crystalCheck.isEnabled() && hasDangerousCrystal()) {
            return true;
        }

        return tntCheck.isEnabled() && hasDangerousTnt();
    }

    private boolean hasDangerousCrystal() {
        Box box = mc.player.getBoundingBox().expand(5.0);

        List<EndCrystalEntity> crystals =
                mc.world.getEntitiesByClass(
                        EndCrystalEntity.class,
                        box,
                        crystal -> mc.player.distanceTo(crystal) < 5.0F
                );

        return !crystals.isEmpty();
    }

    private boolean hasDangerousTnt() {
        Box box = mc.player.getBoundingBox().expand(tntDistance.getValue());

        List<TntEntity> tnts =
                mc.world.getEntitiesByClass(
                        TntEntity.class,
                        box,
                        tnt -> mc.player.distanceTo(tnt) < tntDistance.getValue()
                );

        return !tnts.isEmpty();
    }

    private int findBestTotemSlot() {
        int bestSlot = -1;
        boolean bestEnchanted = true;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.TOTEM_OF_UNDYING)) continue;

            boolean enchanted = stack.hasEnchantments();
            if (bestSlot == -1 || (bestEnchanted && !enchanted)) {
                bestSlot = i;
                bestEnchanted = enchanted;
                if (!enchanted) break;
            }
        }

        return bestSlot;
    }

    private void swapWithOffhand(int inventorySlot) {
        int slotIndex = toInventorySlot(inventorySlot);

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotIndex,
                40,
                SlotActionType.SWAP,
                mc.player
        );
    }

    private int toInventorySlot(int slot) {
        return slot < 9 ? slot + 36 : slot;
    }

    private boolean canSwap() {
        return System.currentTimeMillis() - lastSwapTime >= 200L;
    }

    private void processSwap() {
        switch (swapPhase) {
            case SLOWING_DOWN -> {
                stopMovementKeys();
                keysOverridden = true;

                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }

                swapPhase = SwapPhase.SWAPPING;
            }

            case SWAPPING -> {
                swapWithOffhand(pendingTotemSlot);
                lastSwapTime = System.currentTimeMillis();
                swapPhase = SwapPhase.SPEEDING_UP;
            }

            case SPEEDING_UP -> {
                restoreMovementKeys();
                swapPhase = SwapPhase.FINISHED;
            }

            case FINISHED -> resetState();

            case READY -> {
            }
        }
    }

    private void saveMovementKeys() {
        var window = mc.getWindow();

        wasForwardPressed = InputUtil.isKeyPressed(window, mc.options.forwardKey.getDefaultKey().getCode());
        wasBackPressed = InputUtil.isKeyPressed(window, mc.options.backKey.getDefaultKey().getCode());
        wasLeftPressed = InputUtil.isKeyPressed(window, mc.options.leftKey.getDefaultKey().getCode());
        wasRightPressed = InputUtil.isKeyPressed(window, mc.options.rightKey.getDefaultKey().getCode());
        wasJumpPressed = InputUtil.isKeyPressed(window, mc.options.jumpKey.getDefaultKey().getCode());

        keysOverridden = false;
    }

    private void stopMovementKeys() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    private void restoreMovementKeys() {
        if (!keysOverridden || mc.getWindow() == null) return;

        var window = mc.getWindow();

        boolean currentForward = InputUtil.isKeyPressed(window, mc.options.forwardKey.getDefaultKey().getCode());
        boolean currentBack = InputUtil.isKeyPressed(window, mc.options.backKey.getDefaultKey().getCode());
        boolean currentLeft = InputUtil.isKeyPressed(window, mc.options.leftKey.getDefaultKey().getCode());
        boolean currentRight = InputUtil.isKeyPressed(window, mc.options.rightKey.getDefaultKey().getCode());
        boolean currentJump = InputUtil.isKeyPressed(window, mc.options.jumpKey.getDefaultKey().getCode());

        mc.options.forwardKey.setPressed(wasForwardPressed && currentForward);
        mc.options.backKey.setPressed(wasBackPressed && currentBack);
        mc.options.leftKey.setPressed(wasLeftPressed && currentLeft);
        mc.options.rightKey.setPressed(wasRightPressed && currentRight);
        mc.options.jumpKey.setPressed(wasJumpPressed && currentJump);

        keysOverridden = false;
    }

    private void resetState() {
        if (keysOverridden) {
            restoreMovementKeys();
        }

        swapPhase = SwapPhase.READY;
        pendingTotemSlot = -1;
        keysOverridden = false;
    }

    private enum SwapPhase {
        READY,
        SLOWING_DOWN,
        SWAPPING,
        SPEEDING_UP,
        FINISHED
    }
}