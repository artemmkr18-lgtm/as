package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.player.SwapSetting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.system.settings.impl.ModeSetting;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

@NewFunction(
        name = "AutoSwap",
        desc = "Авто свап предметов в оффхенд",
        category = Category.COMBAT
)
public class AutoSwap extends Function {

    private final ModeSetting itemType =
            new ModeSetting("Первый предмет", "Щит", "Геплы", "Тотем", "Шар");

    private final ModeSetting swapType =
            new ModeSetting("Второй предмет", "Щит", "Геплы", "Тотем", "Шар");

    private final KeybindSetting keyToSwap =
            new KeybindSetting("Кнопка", -1);

    private SwapPhase swapPhase = SwapPhase.READY;

    private boolean wasPressed = false;
    private int targetSlot = -1;
    private int phaseWait;

    private boolean wasForwardPressed;
    private boolean wasBackPressed;
    private boolean wasLeftPressed;
    private boolean wasRightPressed;
    private boolean wasJumpPressed;
    private boolean keysOverridden = false;

    public AutoSwap() {
        addSettings(itemType, swapType, keyToSwap);
    }

    @Override
    public void onEnable() {
        resetState();
        wasPressed = false;
    }

    @Override
    public void onDisable() {
        resetState();
        wasPressed = false;
        restoreMovementKeys();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null || mc.currentScreen != null) {
            resetState();
            return;
        }

        handleKeyPress();

        if (SwapSetting.isSlowSwapMode() && swapPhase != SwapPhase.READY) {
            processSlowSwap();
        }
    }

    private void handleKeyPress() {
        int key = keyToSwap.getKeybind();
        if (key <= 0) return;

        boolean pressed = KeybindSetting.isMouseKey(key)
                ? keyToSwap.consumeMouseClick(mc.getWindow().getHandle())
                : GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;

        if (pressed && (!wasPressed || KeybindSetting.isMouseKey(key)) && swapPhase == SwapPhase.READY) {
            targetSlot = findValidSlot();

            if (targetSlot == -1) {
                wasPressed = pressed;
                return;
            }

            if (SwapSetting.isPacketMode()) {
                executeDefaultSwap(targetSlot);
                resetState();
            } else {
                startSlowSwap(targetSlot);
            }
        }

        wasPressed = pressed;
    }

    private void startSlowSwap(int slotToSwap) {
        targetSlot = slotToSwap;

        var window = mc.getWindow();

        wasForwardPressed = InputUtil.isKeyPressed(window, mc.options.forwardKey.getDefaultKey().getCode());
        wasBackPressed = InputUtil.isKeyPressed(window, mc.options.backKey.getDefaultKey().getCode());
        wasLeftPressed = InputUtil.isKeyPressed(window, mc.options.leftKey.getDefaultKey().getCode());
        wasRightPressed = InputUtil.isKeyPressed(window, mc.options.rightKey.getDefaultKey().getCode());
        wasJumpPressed = InputUtil.isKeyPressed(window, mc.options.jumpKey.getDefaultKey().getCode());

        keysOverridden = false;
        phaseWait = 0;
        swapPhase = SwapPhase.SLOWING_DOWN;
    }

    private void processSlowSwap() {
        stopMovementKeys();
        keysOverridden = true;

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        if (phaseWait > 0) {
            phaseWait--;
            return;
        }

        switch (swapPhase) {
            case SLOWING_DOWN -> {
                if (!SwapSetting.isReadyToSwap()) {
                    return;
                }

                phaseWait = SwapSetting.nextStopDelayTicks();
                swapPhase = SwapPhase.PICKUP_ITEM;
            }

            case PICKUP_ITEM -> {
                clickSlot(targetSlot);
                phaseWait = SwapSetting.nextClickDelayTicks();
                swapPhase = SwapPhase.PICKUP_OFFHAND;
            }

            case PICKUP_OFFHAND -> {
                clickSlot(45);
                phaseWait = SwapSetting.nextClickDelayTicks();
                swapPhase = SwapPhase.RETURN_ITEM;
            }

            case RETURN_ITEM -> {
                clickSlot(targetSlot);
                phaseWait = SwapSetting.nextResumeDelayTicks();
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

    private void executeDefaultSwap(int slot) {
        clickSlot(slot);
        clickSlot(45);
        clickSlot(slot);
    }

    private void clickSlot(int slot) {
        if (slot == -1 || mc.player == null || mc.interactionManager == null) return;

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
        );
    }

    private int findValidSlot() {
        Item firstItem = getItemByType(itemType.getActiveMode());
        Item secondItem = getItemByType(swapType.getActiveMode());
        Item offhandItem = mc.player.getOffHandStack().getItem();

        int slot;

        if (offhandItem == firstItem) {
            slot = findItem(secondItem);
            if (slot != -1) return convertSlot(slot);
        }

        if (offhandItem == secondItem) {
            slot = findItem(firstItem);
            if (slot != -1) return convertSlot(slot);
        }

        if (offhandItem != firstItem && offhandItem != secondItem) {
            slot = findItem(firstItem);
            if (slot != -1) return convertSlot(slot);

            slot = findItem(secondItem);
            if (slot != -1) return convertSlot(slot);
        }

        return -1;
    }

    private int findItem(Item item) {
        if (item == Items.AIR) return -1;

        for (int i = 35; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isOf(item)) {
                return i;
            }
        }

        return -1;
    }

    private int convertSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
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
        targetSlot = -1;
        phaseWait = 0;
        keysOverridden = false;
    }

    private Item getItemByType(String type) {
        return switch (type) {
            case "Щит" -> Items.SHIELD;
            case "Тотем" -> Items.TOTEM_OF_UNDYING;
            case "Геплы" -> Items.GOLDEN_APPLE;
            case "Шар" -> Items.PLAYER_HEAD;
            default -> Items.AIR;
        };
    }

    private enum SwapPhase {
        READY,
        SLOWING_DOWN,
        PICKUP_ITEM,
        PICKUP_OFFHAND,
        RETURN_ITEM,
        SPEEDING_UP,
        FINISHED
    }
}
