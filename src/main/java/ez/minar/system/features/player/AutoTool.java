package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;

import ez.minar.utils.helpers.InventoryUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

@NewFunction(name = "AutoTool", desc = "Выбирает лучший инструмент для добычи блоков", category = Category.PLAYER)
public class AutoTool extends Function {

    private int itemIndex = -1, oldSlot = -1;
    private boolean status;
    private SwapPhase swapPhase = SwapPhase.READY;

    private boolean wasForwardPressed;
    private boolean wasBackPressed;
    private boolean wasLeftPressed;
    private boolean wasRightPressed;
    private boolean wasJumpPressed;
    private boolean keysOverridden = false;

    public AutoTool() {
    }

    @EventHandler
    public void onTick(UpdateEvent e) {
        if (nullCheck.player() || mc.player.isCreative()) {
            itemIndex = -1;
            resetState();
            return;
        }

        if (mc.player.isUsingItem()) return;

        if (isMousePressed()) {
            if (oldSlot == -1 && swapPhase == SwapPhase.READY) {
                itemIndex = findBestToolSlot();

                if (itemIndex != -1) {
                    if (itemIndex >= 0 && itemIndex <= 8) {
                        oldSlot = mc.player.getInventory().getSelectedSlot();
                        mc.player.getInventory().setSelectedSlot(itemIndex);
                        status = true;
                    } else {
                        startSwap(itemIndex);
                    }
                }
            }
        } else if (oldSlot != -1 && swapPhase == SwapPhase.READY) {
            if (oldSlot >= 0 && oldSlot <= 8) {
                mc.player.getInventory().setSelectedSlot(oldSlot);
                oldSlot = -1;
                status = false;
            } else {
                startSwapBack();
            }
        }

        if (swapPhase != SwapPhase.READY) {
            processSwap();
        }
    }

    private void processSwap() {
        switch (swapPhase) {
            case SLOWING_DOWN -> {
                stopMovementKeys();
                keysOverridden = true;

                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }

                swapPhase = SwapPhase.SWAP;
            }
            case SWAP -> {
                InventoryUtil.swapWithBypassGrim(this::swap);
                oldSlot = itemIndex;
                swapPhase = SwapPhase.RESTORE;
            }
            case RESTORE -> {
                restoreMovementKeys();
                swapPhase = SwapPhase.READY;
            }
            case SWAP_BACK_SLOWING_DOWN -> {
                stopMovementKeys();
                keysOverridden = true;

                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }

                swapPhase = SwapPhase.SWAP_BACK;
            }
            case SWAP_BACK -> {
                InventoryUtil.swapWithBypassGrim(this::swapBack);
                swapPhase = SwapPhase.SWAP_BACK_RESTORE;
            }
            case SWAP_BACK_RESTORE -> {
                restoreMovementKeys();
                swapPhase = SwapPhase.READY;
            }
            case READY -> {
            }
        }
    }

    private void startSwap(int index) {
        itemIndex = index;
        saveMovementKeys();
        swapPhase = SwapPhase.SLOWING_DOWN;
    }

    private void startSwapBack() {
        saveMovementKeys();
        swapPhase = SwapPhase.SWAP_BACK_SLOWING_DOWN;
    }

    private void saveMovementKeys() {
        if (mc.getWindow() == null) return;
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
        oldSlot = -1;
        status = false;
        keysOverridden = false;
    }

    private void swap() {
        if (itemIndex == -1) return;
        status = true;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, itemIndex, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
    }

    private void swapBack() {
        if (oldSlot == -1 || !status) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, oldSlot, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
        oldSlot = -1;
        status = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
        itemIndex = -1;
    }

    private int findBestToolSlot() {
        if (mc.crosshairTarget instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);

            int bestSlot = -1;
            float bestSpeed = 1.0f;

            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                float speed = stack.getMiningSpeedMultiplier(state);

                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = slot;
                }
            }
            return bestSlot;
        }
        return -1;
    }

    private boolean isMousePressed() {
        return mc.crosshairTarget != null && mc.options.attackKey.isPressed();
    }

    private enum SwapPhase {
        READY,
        SLOWING_DOWN,
        SWAP,
        RESTORE,
        SWAP_BACK_SLOWING_DOWN,
        SWAP_BACK,
        SWAP_BACK_RESTORE
    }
}
