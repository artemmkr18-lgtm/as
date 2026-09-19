package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.KeyEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.utils.helpers.MoveUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

@NewFunction(name = "ElytraHelper", desc = "Помощник свапа элитры и полета", category = Category.PLAYER)
public class ElytraHelper extends Function {
    private static final int CHEST_SCREEN_SLOT = 6;

    private final KeybindSetting swapKey = new KeybindSetting("Кнопка свапа", 0);
    private final BooleanSetting equipChestplate = new BooleanSetting("Надеть когда падаешь", false);
    private final KeybindSetting fireworkKey = new KeybindSetting("Кнопка феерверка", 0);
    private final BooleanSetting autoFly = new BooleanSetting("Автофлай", false);
    private final BooleanSetting useFirework = new BooleanSetting("Юзать феерверк", false);
    private final BooleanSetting matrixBypass = new BooleanSetting("Matrix bypass", true);

    private boolean swapRequested;
    private boolean fireworkUsed;
    private boolean wasOnGround;
    private boolean wasGliding;
    private int pendingFireworkSlot = -1;
    private int fireworkStep;
    private boolean fireworkFromOffhand;
    private int tickswap;

    private SwapPhase swapPhase = SwapPhase.IDLE;
    private int pendingSwapSlot = -1;
    private int phaseWait;

    public ElytraHelper() {
        addSettings(swapKey, equipChestplate, fireworkKey, autoFly, useFirework, matrixBypass);
        equipChestplate.runnable(() -> equipChestplate.setVisible(swapKey.getKeybind() != 0));
        useFirework.runnable(() -> useFirework.setVisible(autoFly.isEnabled()));
        updateMatrixBypassVisibility();
    }

    @Override
    public void onDisable() {
        swapRequested = false;
        pendingFireworkSlot = -1;
        fireworkStep = 0;
        fireworkFromOffhand = false;
        fireworkUsed = false;
        wasOnGround = false;
        wasGliding = false;
        tickswap = 0;
        resetSlowSwap();
    }

    public boolean isWorking() {
        return swapRequested || pendingFireworkSlot != -1 || swapPhase != SwapPhase.IDLE;
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS || nullCheck.all() || mc.currentScreen != null || mc.interactionManager == null) {
            return;
        }

        if (swapKey.matchesKey(event.getInput().key())) {
            requestSwap();
        }

        if (fireworkKey.matchesKey(event.getInput().key())) {
            requestFirework();
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.currentScreen != null || mc.interactionManager == null) {
            swapRequested = false;
            resetFirework();
            resetSlowSwap();
            return;
        }

        if (swapKey.consumeMouseClick(mc.getWindow().getHandle())) {
            requestSwap();
        }

        if (fireworkKey.consumeMouseClick(mc.getWindow().getHandle())) {
            requestFirework();
        }

        if (tickswap > 0) {
            tickswap--;
        }

        if (swapPhase != SwapPhase.IDLE) {
            processSlowSwap();
        } else if (swapRequested && canRunInventoryAction()) {
            executeSwap();
            swapRequested = false;
        }

        if (pendingFireworkSlot != -1 && canRunInventoryAction()) {
            continueFirework();
        }

        if (mc.player.isGliding()) {
            wasGliding = true;
        }

        if (equipChestplate.isEnabled()) {
            handleChestplateOnLand();
        }

        handleAutoFly();
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (isMovementBypass() && (tickswap > 0 || swapPhase != SwapPhase.IDLE)) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            event.setJump(false);
            event.setSneak(false);
            event.setSprint(false);
        }
    }

    public void requestSwap() {
        if (swapPhase != SwapPhase.IDLE || swapRequested) {
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        boolean hasElytra = findItem(inventory, Items.ELYTRA) != -1;
        boolean hasChestplate = findChestplate(inventory) != -1;
        if (!hasElytra && !hasChestplate) {
            return;
        }

        if (SwapSetting.isPacketMode()) {
            executeSwap();
            return;
        }

        if (SwapSetting.isReallyWorldMode()) {
            int targetSlot = isWearingElytra() ? findChestplate(inventory) : findItem(inventory, Items.ELYTRA);
            if (targetSlot == -1) {
                return;
            }

            pendingSwapSlot = targetSlot;
            phaseWait = 0;
            tickswap = 2;
            swapPhase = SwapPhase.SLOWING_DOWN;
            return;
        }

        tickswap = 2;
        swapRequested = true;
    }

    private void processSlowSwap() {
        tickswap = Math.max(tickswap, 2);

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
                if (pendingSwapSlot == -1 || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    resetSlowSwap();
                    return;
                }

                click(toScreenSlot(pendingSwapSlot), 0, SlotActionType.PICKUP);
                phaseWait = SwapSetting.nextClickDelayTicks();
                swapPhase = SwapPhase.PICKUP_ARMOR;
            }

            case PICKUP_ARMOR -> {
                click(CHEST_SCREEN_SLOT, 0, SlotActionType.PICKUP);
                phaseWait = SwapSetting.nextClickDelayTicks();
                swapPhase = SwapPhase.RETURN_ITEM;
            }

            case RETURN_ITEM -> {
                click(toScreenSlot(pendingSwapSlot), 0, SlotActionType.PICKUP);
                phaseWait = SwapSetting.nextResumeDelayTicks();
                swapPhase = SwapPhase.FINISHED;
            }

            case FINISHED -> resetSlowSwap();

            case IDLE -> {
            }
        }
    }

    private void resetSlowSwap() {
        swapPhase = SwapPhase.IDLE;
        pendingSwapSlot = -1;
        phaseWait = 0;
    }

    private boolean isWearingElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void useFirework() {
        requestFirework();
    }

    public void requestFirework() {
        if (swapRequested || swapPhase != SwapPhase.IDLE || pendingFireworkSlot != -1
                || mc.player.getItemCooldownManager().getCooldownProgress(Items.FIREWORK_ROCKET.getDefaultStack(), 0.0F) > 0.0F) {
            return;
        }

        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            pendingFireworkSlot = PlayerInventory.OFF_HAND_SLOT;
            fireworkStep = 0;
            fireworkFromOffhand = true;
            tickswap = 2;
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        int fireworkSlot = findItem(inventory, Items.FIREWORK_ROCKET);
        if (fireworkSlot == -1) {
            return;
        }

        pendingFireworkSlot = fireworkSlot;
        fireworkStep = fireworkSlot == inventory.getSelectedSlot() ? 1 : 0;
        fireworkFromOffhand = false;
        tickswap = 2;
    }

    private void continueFirework() {
        if (fireworkFromOffhand) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            resetFirework();
            return;
        }

        int delay = 2;

        switch (fireworkStep) {
            case 0 -> {
                click(toScreenSlot(pendingFireworkSlot), 0, SlotActionType.PICKUP);
                click(45, 0, SlotActionType.PICKUP);
                click(toScreenSlot(pendingFireworkSlot), 0, SlotActionType.PICKUP);
                fireworkStep = 1;
                tickswap = delay;
            }
            case 1 -> {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
                fireworkStep = 2;
                tickswap = delay;
            }
            case 2 -> {
                click(toScreenSlot(pendingFireworkSlot), 0, SlotActionType.PICKUP);
                click(45, 0, SlotActionType.PICKUP);
                click(toScreenSlot(pendingFireworkSlot), 0, SlotActionType.PICKUP);
                resetFirework();
            }
            default -> resetFirework();
        }
    }

    private void executeSwap() {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        int targetSlot = isWearingElytra() ? findChestplate(inventory) : findItem(inventory, Items.ELYTRA);
        if (targetSlot == -1) {
            return;
        }

        moveToArmor(targetSlot);
    }

    private void handleChestplateOnLand() {
        if (mc.player.isOnGround() && !wasOnGround && wasGliding && isWearingElytra()) {
            PlayerInventory inventory = mc.player.getInventory();
            int chestplateSlot = findChestplate(inventory);
            if (chestplateSlot != -1) {
                if (SwapSetting.isReallyWorldMode()) {
                    pendingSwapSlot = chestplateSlot;
                    phaseWait = 0;
                    tickswap = 2;
                    swapPhase = SwapPhase.SLOWING_DOWN;
                } else {
                    moveToArmor(chestplateSlot);
                }
            } else {
                int emptySlot = findEmptySlot(inventory);
                if (emptySlot != -1) {
                    moveToInventory(emptySlot);
                }
            }

            wasGliding = false;
        }

        wasOnGround = mc.player.isOnGround();
    }

    private void handleAutoFly() {
        if (!autoFly.isEnabled() || !isWearingElytra()) {
            return;
        }

        if (!mc.player.isGliding() && !mc.player.isOnGround()) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            wasGliding = true;

            if (useFirework.isEnabled() && !fireworkUsed) {
                useFirework();
                fireworkUsed = true;
            }
        } else if (mc.player.isOnGround()) {
            mc.player.jump();
            fireworkUsed = false;
        }
    }

    private int findChestplate(PlayerInventory inventory) {
        Item[] chestplates = {
                Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE,
                Items.LEATHER_CHESTPLATE
        };

        for (Item chestplate : chestplates) {
            int slot = findItem(inventory, chestplate);
            if (slot != -1) {
                return slot;
            }
        }

        return -1;
    }

    private int findItem(PlayerInventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getMainStacks().size(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    private int findEmptySlot(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getMainStacks().size(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private int toScreenSlot(int slot) {
        return slot < PlayerInventory.HOTBAR_SIZE ? slot + 36 : slot;
    }

    private void moveToArmor(int slot) {
        int sourceSlot = toScreenSlot(slot);
        click(sourceSlot, 0, SlotActionType.PICKUP);
        click(CHEST_SCREEN_SLOT, 0, SlotActionType.PICKUP);
        click(sourceSlot, 0, SlotActionType.PICKUP);
    }

    private void moveToInventory(int slot) {
        int targetSlot = toScreenSlot(slot);
        click(CHEST_SCREEN_SLOT, 0, SlotActionType.PICKUP);
        click(targetSlot, 0, SlotActionType.PICKUP);
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

    private boolean canRunInventoryAction() {
        return !isMovementBypass() || !MoveUtil.isMoving();
    }

    private boolean isMovementBypass() {
        return SwapSetting.isSlowSwapMode() && matrixBypass.isEnabled();
    }

    void updateMatrixBypassVisibility() {
        matrixBypass.setVisible(SwapSetting.isSlowSwapMode());
    }

    private void resetFirework() {
        pendingFireworkSlot = -1;
        fireworkStep = 0;
        fireworkFromOffhand = false;
    }

    private enum SwapPhase {
        IDLE,
        SLOWING_DOWN,
        PICKUP_ITEM,
        PICKUP_ARMOR,
        RETURN_ITEM,
        FINISHED
    }
}
