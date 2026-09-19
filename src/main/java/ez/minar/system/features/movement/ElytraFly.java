package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.helpers.MoveUtil;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

@NewFunction(name = "ElytraFly", desc = "Автоматический полет на элитрах", category = Category.MOVEMENT)
public class ElytraFly extends Function {
    private static final int CHEST_SCREEN_SLOT = 6;

    private final BooleanSetting smartSwap = new BooleanSetting("Умный свап", false);
    private final NumberSetting horizontalSpeed = new NumberSetting("Скорость", 1.0, 0.1, 3.0, 0.1);
    private final NumberSetting verticalSpeed = new NumberSetting("Вертикальная", 0.5, 0.1, 2.0, 0.1);

    public ElytraFly() {
        addSettings(smartSwap, horizontalSpeed, verticalSpeed);
    }

    @Override
    public void onEnable() {
        if (nullCheck.all()) return;
        if (smartSwap.isEnabled()) {
            swapToElytra();
        }
    }

    @Override
    public void onDisable() {
        if (nullCheck.all()) return;
        if (smartSwap.isEnabled() && isWearingElytra()) {
            swapToChestplate();
        }
        restoreJumpKey();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null) return;

        if (!mc.player.getAbilities().flying
                && mc.player.isOnGround()
                && !isInLiquid()
                && isWearingElytra()
                && !mc.options.jumpKey.isPressed()) {
            mc.player.jump();
        }

        if (!mc.player.getAbilities().flying
                && !mc.player.isOnGround()
                && !isInLiquid()
                && !mc.player.isGliding()
                && isWearingElytra()) {
            startFallFlying();
        }

        if (mc.player.hurtTime > 0 && smartSwap.isEnabled() && isWearingElytra()) {
            swapToChestplate();
            return;
        }

        if (isWearingElytra()) {
            if (mc.player.isGliding()) {
                controlFlight();
                rotateToAuraTarget();
            }
        } else if (smartSwap.isEnabled()) {
            swapToElytra();
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (nullCheck.player()) return;
        if (isWearingElytra() && !mc.player.isGliding() && event.isJump()) {
            event.setJump(false);
        }
    }

    private void startFallFlying() {
        mc.player.startGliding();
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private void controlFlight() {
        double motionX = 0.0D;
        double motionZ = 0.0D;

        if (MoveUtil.isMoving()) {
            double[] direction = MoveUtil.calculateDirection(horizontalSpeed.getValue());
            motionX = direction[0];
            motionZ = direction[1];
        }

        double motionY = 0.0D;
        if (mc.options.jumpKey.isPressed()) {
            motionY += verticalSpeed.getValue();
        }
        if (mc.options.sneakKey.isPressed()) {
            motionY -= verticalSpeed.getValue();
        }

        mc.player.setVelocity(motionX, motionY, motionZ);
    }

    private void rotateToAuraTarget() {
        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        LivingEntity target = aura != null ? aura.getTarget() : null;
        float yaw = target != null ? target.getYaw() : mc.player.getYaw();

        RotationManager.rotate(
                new RotationManager.Rotation(yaw, 0.0F),
                RotationManager.MoveCorrection.NONE,
                180.0F,
                180.0F,
                180.0F,
                true,
                180.0F
        );

        mc.player.setYaw(yaw);
        mc.player.setPitch(0.0F);
        mc.player.setHeadYaw(yaw);
        mc.player.setBodyYaw(yaw);
    }

    private boolean isWearingElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean isInLiquid() {
        return mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava();
    }

    private void swapToElytra() {
        int elytraSlot = findItem(Items.ELYTRA);
        if (elytraSlot != -1) {
            moveToArmor(elytraSlot);
        }
    }

    private void swapToChestplate() {
        int chestplateSlot = findChestplate();
        if (chestplateSlot != -1) {
            moveToArmor(chestplateSlot);
        }
    }

    private int findChestplate() {
        Item[] chestplates = {
                Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.LEATHER_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE
        };

        for (Item chestplate : chestplates) {
            int slot = findItem(chestplate);
            if (slot != -1) {
                return slot;
            }
        }

        return -1;
    }

    private int findItem(Item item) {
        PlayerInventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getMainStacks().size(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    private void moveToArmor(int slot) {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            return;
        }

        int sourceSlot = toScreenSlot(slot);
        click(sourceSlot);
        click(CHEST_SCREEN_SLOT);
        click(sourceSlot);
    }

    private int toScreenSlot(int slot) {
        return slot < PlayerInventory.HOTBAR_SIZE ? slot + 36 : slot;
    }

    private void click(int slot) {
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
        );
    }

    private void restoreJumpKey() {
        boolean pressed = InputUtil.isKeyPressed(mc.getWindow(), mc.options.jumpKey.getDefaultKey().getCode());
        mc.options.jumpKey.setPressed(pressed);
    }
}
