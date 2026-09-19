package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.player.SwapSetting;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

@NewFunction(name = "AutoArmor", desc = "Автоматически надевает лучшую броню", category = Category.COMBAT)
public class AutoArmor extends Function {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private SwapPhase swapPhase = SwapPhase.READY;
    private int pendingInventorySlot = -1;
    private EquipmentSlot pendingArmorSlot;
    private int phaseWait;
    private int pieceCooldown;

    private boolean wasForwardPressed;
    private boolean wasBackPressed;
    private boolean wasLeftPressed;
    private boolean wasRightPressed;
    private boolean wasJumpPressed;
    private boolean keysOverridden;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        pieceCooldown = 0;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null || mc.currentScreen != null) {
            resetState();
            pieceCooldown = 0;
            return;
        }

        if (swapPhase == SwapPhase.READY && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            return;
        }

        if (SwapSetting.isPacketMode()) {
            equipBestPacket();
            return;
        }

        if (pieceCooldown > 0) {
            pieceCooldown--;
            return;
        }

        if (swapPhase == SwapPhase.READY) {
            ArmorMove move = findBestMove();
            if (move == null) {
                return;
            }

            pendingInventorySlot = move.inventorySlot();
            pendingArmorSlot = move.armorSlot();
            saveMovementKeys();
            phaseWait = 0;
            swapPhase = SwapPhase.SLOWING_DOWN;
        }

        processSlowSwap();
    }

    private void equipBestPacket() {
        ArmorMove[] moves = new ArmorMove[ARMOR_SLOTS.length];
        int count = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int inventorySlot = findBestArmorSlot(slot);
            if (inventorySlot == -1) {
                continue;
            }

            ItemStack inventoryStack = mc.player.getInventory().getStack(inventorySlot);
            ItemStack equippedStack = mc.player.getEquippedStack(slot);
            if (getArmorScore(inventoryStack, slot) > getArmorScore(equippedStack, slot)) {
                moves[count++] = new ArmorMove(inventorySlot, slot);
            }
        }

        for (int i = 0; i < count; i++) {
            moveToArmor(moves[i].inventorySlot(), moves[i].armorSlot());
        }
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
                if (hasFullArmorEquipped() && findBestMove() == null) {
                    resetState();
                    return;
                }

                if (!SwapSetting.isReadyToSwap()) {
                    return;
                }

                phaseWait = SwapSetting.nextStopDelayTicks();
                swapPhase = SwapPhase.PICKUP_ITEM;
            }

            case PICKUP_ITEM -> {
                if (!isPendingMoveValid()) {
                    resetState();
                    return;
                }

                click(toScreenSlot(pendingInventorySlot));
                phaseWait = SwapSetting.nextClickDelayTicks();
                swapPhase = SwapPhase.PICKUP_ARMOR;
            }

            case PICKUP_ARMOR -> {
                click(toArmorScreenSlot(pendingArmorSlot));
                phaseWait = SwapSetting.nextClickDelayTicks();
                swapPhase = SwapPhase.RETURN_ITEM;
            }

            case RETURN_ITEM -> {
                click(toScreenSlot(pendingInventorySlot));
                phaseWait = SwapSetting.nextResumeDelayTicks();
                swapPhase = SwapPhase.SPEEDING_UP;
            }

            case SPEEDING_UP -> {
                restoreMovementKeys();
                swapPhase = SwapPhase.FINISHED;
            }

            case FINISHED -> {
                int delay = SwapSetting.nextPieceDelayTicks();
                resetState();
                pieceCooldown = delay;
            }

            case READY -> {
            }
        }
    }

    private ArmorMove findBestMove() {
        ArmorMove bestMove = null;
        double bestDifference = 0.0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int inventorySlot = findBestArmorSlot(slot);
            if (inventorySlot == -1) {
                continue;
            }

            ItemStack inventoryStack = mc.player.getInventory().getStack(inventorySlot);
            ItemStack equippedStack = mc.player.getEquippedStack(slot);
            double difference = getArmorScore(inventoryStack, slot) - getArmorScore(equippedStack, slot);

            if (difference > bestDifference) {
                bestDifference = difference;
                bestMove = new ArmorMove(inventorySlot, slot);
            }
        }

        return bestMove;
    }

    private int findBestArmorSlot(EquipmentSlot armorSlot) {
        int bestSlot = -1;
        double bestScore = 0.0;
        PlayerInventory inventory = mc.player.getInventory();

        for (int slot = 0; slot < inventory.getMainStacks().size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isArmorForSlot(stack, armorSlot)) {
                continue;
            }

            double score = getArmorScore(stack, armorSlot);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private boolean hasFullArmorEquipped() {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!isArmorForSlot(mc.player.getEquippedStack(slot), slot)) {
                return false;
            }
        }

        return true;
    }

    private boolean isPendingMoveValid() {
        if (pendingInventorySlot < 0 || pendingArmorSlot == null) {
            return false;
        }

        ItemStack inventoryStack = mc.player.getInventory().getStack(pendingInventorySlot);
        ItemStack equippedStack = mc.player.getEquippedStack(pendingArmorSlot);
        return isArmorForSlot(inventoryStack, pendingArmorSlot)
                && getArmorScore(inventoryStack, pendingArmorSlot) > getArmorScore(equippedStack, pendingArmorSlot);
    }

    private boolean isArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return false;
        }

        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null
                && equippable.slot() == slot
                && getBaseArmorScore(stack, slot) > 0.0;
    }

    private double getArmorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        double baseScore = getBaseArmorScore(stack, slot);
        if (baseScore <= 0.0) {
            return 0.0;
        }

        return baseScore + getEnchantmentScore(stack) + getDurabilityScore(stack);
    }

    private double getBaseArmorScore(ItemStack stack, EquipmentSlot slot) {
        AttributeModifiersComponent modifiers = stack.getOrDefault(
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                AttributeModifiersComponent.DEFAULT
        );

        double armor = modifiers.applyOperations(EntityAttributes.ARMOR, 0.0, slot);
        double toughness = modifiers.applyOperations(EntityAttributes.ARMOR_TOUGHNESS, 0.0, slot);
        double knockbackResistance = modifiers.applyOperations(EntityAttributes.KNOCKBACK_RESISTANCE, 0.0, slot);

        return armor * 1000.0 + toughness * 100.0 + knockbackResistance * 100.0;
    }

    private double getEnchantmentScore(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        return enchantments.getEnchantmentEntries()
                .stream()
                .mapToDouble(entry -> entry.getIntValue() * 10.0)
                .sum();
    }

    private double getDurabilityScore(ItemStack stack) {
        if (!stack.isDamageable()) {
            return 1.0;
        }

        return (double) (stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage();
    }

    private void moveToArmor(int inventorySlot, EquipmentSlot armorSlot) {
        int sourceSlot = toScreenSlot(inventorySlot);
        int targetSlot = toArmorScreenSlot(armorSlot);

        click(sourceSlot);
        click(targetSlot);
        click(sourceSlot);
    }

    private void click(int slot) {
        if (slot == -1 || mc.interactionManager == null || mc.player == null) {
            return;
        }

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
        );
    }

    private int toScreenSlot(int slot) {
        return slot < PlayerInventory.HOTBAR_SIZE ? slot + 36 : slot;
    }

    private int toArmorScreenSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
    }

    private void saveMovementKeys() {
        if (mc.getWindow() == null) {
            return;
        }

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

    private void resetState() {
        if (keysOverridden) {
            restoreMovementKeys();
        }

        swapPhase = SwapPhase.READY;
        pendingInventorySlot = -1;
        pendingArmorSlot = null;
        phaseWait = 0;
        keysOverridden = false;
    }

    private record ArmorMove(int inventorySlot, EquipmentSlot armorSlot) {
    }

    private enum SwapPhase {
        READY,
        SLOWING_DOWN,
        PICKUP_ITEM,
        PICKUP_ARMOR,
        RETURN_ITEM,
        SPEEDING_UP,
        FINISHED
    }
}
