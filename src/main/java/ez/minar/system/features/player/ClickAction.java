package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.KeyEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.system.settings.impl.ModeSetting;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

@NewFunction(name = "ClickAction", desc = "Использование эндер перла по бинду", category = Category.PLAYER)
public class ClickAction extends Function {

    private final ModeSetting mode = new ModeSetting("Тип", "Обычный", "Легитный");
    private final KeybindSetting pearlKey = new KeybindSetting("Кнопка эндер-жемчуга", 0);

    private int pendingPearlSlot = -1;
    private int oldSlot = -1;

    private ActionType actionType = ActionType.START;

    private long actionStartedAt;
    private long useStartedAt;

    private boolean pendingInventorySwap;

    private boolean suppressMovement;
    private boolean keysOverridden;

    private boolean wasForwardPressed;
    private boolean wasBackPressed;
    private boolean wasLeftPressed;
    private boolean wasRightPressed;
    private boolean wasJumpPressed;

    public ClickAction() {
        addSettings(mode, pearlKey);
    }

    @Override
    public void onDisable() {
        resetPearl();
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS
                || nullCheck.all()
                || mc.currentScreen != null
                || mc.interactionManager == null) {
            return;
        }

        if (pearlKey.matchesKey(event.getInput().key())) {
            requestPearl();
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.currentScreen != null || mc.interactionManager == null) {
            resetPearl();
            return;
        }

        if (pearlKey.consumeMouseClick(mc.getWindow().getHandle())) {
            requestPearl();
        }

        if (pendingPearlSlot != -1) {
            continuePearl();
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (!suppressMovement) {
            return;
        }

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
        event.setSprint(false);
    }

    private void requestPearl() {
        if (pendingPearlSlot != -1
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()
                || mc.player.getItemCooldownManager().getCooldownProgress(Items.ENDER_PEARL.getDefaultStack(), 0.0F) > 0.0F) {
            return;
        }

        sendRotatePacket();

        if (mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
            if (mode.isEnabled("Обычный")) {
                usePearl(Hand.OFF_HAND);
            } else {
                startLegitPearl(PlayerInventory.OFF_HAND_SLOT);
            }
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        int pearlSlot = findPearl(inventory);

        if (pearlSlot == -1) {
            return;
        }

        if (mode.isEnabled("Обычный")) {
            useDefaultPearl(pearlSlot);
        } else {
            startLegitPearl(pearlSlot);
        }
    }

    private void useDefaultPearl(int pearlSlot) {
        PlayerInventory inventory = mc.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();

        if (pearlSlot != selectedSlot) {
            if (pearlSlot < PlayerInventory.HOTBAR_SIZE) {
                switchSlot(pearlSlot);
            } else {
                click(toScreenSlot(pearlSlot), selectedSlot, SlotActionType.SWAP);
            }
        }

        sendRotatePacket();
        usePearl(Hand.MAIN_HAND);

        if (pearlSlot != selectedSlot) {
            if (pearlSlot < PlayerInventory.HOTBAR_SIZE) {
                switchSlot(selectedSlot);
            } else {
                click(toScreenSlot(pearlSlot), selectedSlot, SlotActionType.SWAP);
            }
        }
    }

    private void startLegitPearl(int pearlSlot) {
        pendingPearlSlot = pearlSlot;
        oldSlot = mc.player.getInventory().getSelectedSlot();

        actionType = ActionType.SLOWING_DOWN;
        actionStartedAt = System.currentTimeMillis();
        useStartedAt = actionStartedAt;

        pendingInventorySwap = false;

        saveMovementKeys();
        stopMovementKeys();

        suppressMovement = true;
        keysOverridden = true;
    }

    private void continuePearl() {
        Hand hand = pendingPearlSlot == PlayerInventory.OFF_HAND_SLOT ? Hand.OFF_HAND : Hand.MAIN_HAND;

        switch (actionType) {
            case SLOWING_DOWN -> {
                stopMovementKeys();

                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }

                if (System.currentTimeMillis() - actionStartedAt >= 50L) {
                    actionType = ActionType.START;
                    actionStartedAt = System.currentTimeMillis();
                }
            }

            case START -> {
                if (hand != Hand.OFF_HAND && pendingPearlSlot != oldSlot) {
                    if (pendingPearlSlot < PlayerInventory.HOTBAR_SIZE) {
                        switchSlot(pendingPearlSlot);
                    } else {
                        click(toScreenSlot(pendingPearlSlot), oldSlot, SlotActionType.SWAP);
                        pendingInventorySwap = true;
                    }
                }

                actionType = ActionType.WAIT;
                actionStartedAt = System.currentTimeMillis();
            }

            case WAIT -> {
                if (System.currentTimeMillis() - actionStartedAt >= 50L) {
                    actionType = ActionType.USE_ITEM;
                }
            }

            case USE_ITEM -> {
                sendRotatePacket();
                usePearl(hand);

                actionType = ActionType.SWAP_BACK;
                useStartedAt = System.currentTimeMillis();
            }

            case SWAP_BACK -> {
                if (System.currentTimeMillis() - useStartedAt < 300L) {
                    return;
                }

                if (hand != Hand.OFF_HAND && pendingPearlSlot != oldSlot) {
                    if (pendingInventorySwap) {
                        click(toScreenSlot(pendingPearlSlot), oldSlot, SlotActionType.SWAP);
                    } else {
                        switchSlot(oldSlot);
                    }
                }

                actionType = ActionType.SPEEDING_UP;
                actionStartedAt = System.currentTimeMillis();
            }

            case SPEEDING_UP -> {
                restoreMovementKeys();
                resetPearl();
            }

            default -> resetPearl();
        }
    }

    private int findPearl(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getMainStacks().size(); slot++) {
            if (inventory.getStack(slot).isOf(Items.ENDER_PEARL)) {
                return slot;
            }
        }

        return -1;
    }

    private int toScreenSlot(int slot) {
        return slot < PlayerInventory.HOTBAR_SIZE ? slot + 36 : slot;
    }

    private void switchSlot(int slot) {
        if (slot < 0
                || slot >= PlayerInventory.HOTBAR_SIZE
                || slot == mc.player.getInventory().getSelectedSlot()) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void usePearl(Hand hand) {
        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);
    }

    private void sendRotatePacket() {
        AttackAura attackAura = FunctionManager.getFunction(AttackAura.class);

        if (attackAura != null && attackAura.getTarget() != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    mc.player.getYaw(),
                    mc.player.getPitch(),
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision
            ));
        }
    }

    private void click(int slot, int button, SlotActionType actionType) {
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot,
                button,
                actionType,
                mc.player
        );
    }

    private void saveMovementKeys() {
        var window = mc.getWindow();

        wasForwardPressed = InputUtil.isKeyPressed(window, mc.options.forwardKey.getDefaultKey().getCode());
        wasBackPressed = InputUtil.isKeyPressed(window, mc.options.backKey.getDefaultKey().getCode());
        wasLeftPressed = InputUtil.isKeyPressed(window, mc.options.leftKey.getDefaultKey().getCode());
        wasRightPressed = InputUtil.isKeyPressed(window, mc.options.rightKey.getDefaultKey().getCode());
        wasJumpPressed = InputUtil.isKeyPressed(window, mc.options.jumpKey.getDefaultKey().getCode());
    }

    private void stopMovementKeys() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    private void restoreMovementKeys() {
        if (!keysOverridden || mc.getWindow() == null) {
            return;
        }

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

    private void resetPearl() {
        if (keysOverridden) {
            restoreMovementKeys();
        }

        pendingPearlSlot = -1;
        oldSlot = -1;

        actionType = ActionType.START;

        actionStartedAt = 0L;
        useStartedAt = 0L;

        pendingInventorySwap = false;
        suppressMovement = false;
        keysOverridden = false;
    }

    private enum ActionType {
        SLOWING_DOWN,
        START,
        WAIT,
        USE_ITEM,
        SWAP_BACK,
        SPEEDING_UP
    }
}