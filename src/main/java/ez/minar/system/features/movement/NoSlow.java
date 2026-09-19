package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.NoSlowEvent;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.utils.helpers.NetworkUtils;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

@NewFunction(name = "NoSlow", desc = "Убирает замедления от использования предметов и инвентаря", category = Category.MOVEMENT)
public class NoSlow extends Function {

    @Getter private static NoSlow instance;
    public static final ModeSetting mode = new ModeSetting(
            "Режим",
            "Vanilla",
            "Grim",
            "ReallyWorld"
    );

    private int ticks = 0;
    private int cycleCounter = 0;
    private boolean cancel = false;
    private boolean forcedSprintDuringEat;
    private boolean sprintWasPressedBeforeEat;

    private static final long KEY_RESTORE_DELAY_MS = 15L;
    private static final long SYNC_CLOSE_DELAY_MS = 90L;
    private static final long DEFAULT_CLOSE_DELAY_MS = 40L;
    private static final long PACKET_REPLAY_DELAY_MS = 10L;
    private static final float SYNC_MOVEMENT_STOP_SECONDS = 0.09F;
    private static final float DEFAULT_MOVEMENT_STOP_SECONDS = 0.04F;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float MILLIS_PER_SECOND = 1000.0F;

    private final List<Packet<?>> pendingPackets = new ArrayList<>();
    private int stopTicks;
    private long stopUntilMs;
    private long waitTimeMs;
    public boolean slow;

    public NoSlow() {
        addSettings(mode);
        instance = this;
    }

    private KeyBinding[] movementKeys() {
        if (mc.options == null) return new KeyBinding[0];
        return new KeyBinding[] {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
        };
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        // Inventory NoSlow logic
        KeyBinding[] keys = movementKeys();
        
        if (isMovementStopActive()) {
            clearMovementState(keys);
            if (stopTicks > 0) {
                stopTicks--;
            }
        } else if (System.currentTimeMillis() < waitTimeMs) {
            clearMovementState(keys);
        } else if (mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof SignEditScreen || mc.currentScreen instanceof AnvilScreen) {
            clearMovementState(keys);
        } else if (mode.isEnabled("Grim") && mc.currentScreen instanceof GenericContainerScreen) {
            clearMovementState(keys);
        } else {
            if (mc.currentScreen instanceof InventoryScreen && shouldBufferInventoryClicks()) {
                updateKeyBindingState(keys);
            }
        }

        // Item Usage NoSlow logic
        if (mode.isEnabled("Grim") || mode.isEnabled("ReallyWorld")) {
            if (!mc.player.isUsingRiptide()) {
                if (mc.player.isUsingItem()) {
                    ticks++;
                } else {
                    ticks = 0;
                    cycleCounter = 0;
                }
            }
            updateEatSprintBoost();
        } else {
            if (mc.player.getActiveHand() == Hand.MAIN_HAND || mc.player.getActiveHand() == Hand.OFF_HAND) {
                if (mc.player.isUsingItem()) {
                    ticks++;
                } else {
                    ticks = 0;
                }
            } else {
                ticks = 0;
            }
            resetEatSprintBoostIfNeeded();
        }
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (nullCheck.all()) return;
        if (NetworkUtils.silentSending) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClickSlotC2SPacket && hasPlayerMovement() && mc.currentScreen instanceof InventoryScreen && shouldBufferInventoryClicks()) {
            pendingPackets.add(packet);
            event.cancel();
        } else if (packet instanceof CloseHandledScreenC2SPacket && mc.currentScreen instanceof InventoryScreen && hasPlayerMovement() && !pendingPackets.isEmpty() && shouldBufferInventoryClicks()) {
            slow = true;
            new Thread(this::replayInventoryPackets, "NoSlow packet replay").start();
            event.cancel();
        }
    }

    @EventHandler
    public void onNoSlow(NoSlowEvent event) {
        if (nullCheck.all()) return;

        if (mode.isEnabled("Vanilla")) {
            event.setCancelled(true);
        } else if (mode.isEnabled("Grim") || mode.isEnabled("ReallyWorld")) {
            int[] thresholds = new int[]{2, 2, 3};
            int threshold = thresholds[cycleCounter % 2];
            if (ticks >= threshold) {
                event.setCancelled(true);
                ticks = 0;
                cycleCounter++;
            }
        }
    }

    private boolean shouldBufferInventoryClicks() {
        return mode.isEnabled("Grim") || mode.isEnabled("ReallyWorld");
    }

    private boolean hasPlayerMovement() {
        return mc.player != null && mc.player.input != null
                && mc.player.input.getMovementInput().lengthSquared() > 1.0E-7f;
    }

    private void replayInventoryPackets() {
        waitTimeMs = System.currentTimeMillis() + KEY_RESTORE_DELAY_MS;
        stopMovementTemporarily(mode.isEnabled("ReallyWorld") ? SYNC_MOVEMENT_STOP_SECONDS : DEFAULT_MOVEMENT_STOP_SECONDS);

        try {
            Thread.sleep(mode.isEnabled("ReallyWorld") ? SYNC_CLOSE_DELAY_MS : DEFAULT_CLOSE_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        for (Packet<?> packet : pendingPackets) {
            if (mc.player == null) {
                continue;
            }

            NetworkUtils.sendSilentPacket(packet);

            try {
                Thread.sleep(PACKET_REPLAY_DELAY_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        pendingPackets.clear();
        slow = false;

        if (mc.player != null && mc.player.currentScreenHandler != null) {
            NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }
    }

    private void updateKeyBindingState(KeyBinding[] keys) {
        if (mc.getWindow() == null) return;

        for (KeyBinding key : keys) {
            int keyCode = key.getDefaultKey().getCode();
            boolean pressed = keyCode != InputUtil.UNKNOWN_KEY.getCode()
                    && InputUtil.isKeyPressed(mc.getWindow(), keyCode);

            key.setPressed(pressed);
        }
    }

    private void clearMovementState(KeyBinding[] keys) {
        for (KeyBinding key : keys) {
            key.setPressed(false);
        }
    }

    private boolean isMovementStopActive() {
        return stopTicks > 0 || System.currentTimeMillis() < stopUntilMs;
    }

    public static void stopMovementTemporarily(float seconds) {
        NoSlow noSlow = instance;
        if (noSlow == null || !noSlow.isEnabled()) {
            clearMovementNow();
            return;
        }

        int ticks = Math.max(1, (int) Math.ceil(seconds * TICKS_PER_SECOND));
        noSlow.stopTicks = Math.max(noSlow.stopTicks, ticks);
        noSlow.stopUntilMs = Math.max(
                noSlow.stopUntilMs,
                System.currentTimeMillis() + (long) (seconds * MILLIS_PER_SECOND)
        );
        noSlow.clearMovementState(noSlow.movementKeys());
    }

    public static void clearMovementNow() {
        NoSlow noSlow = instance;
        if (noSlow == null || noSlow.mc == null || noSlow.mc.options == null) {
            return;
        }

        KeyBinding[] keys = {
                noSlow.mc.options.forwardKey,
                noSlow.mc.options.backKey,
                noSlow.mc.options.leftKey,
                noSlow.mc.options.rightKey,
                noSlow.mc.options.jumpKey,
                noSlow.mc.options.sprintKey,
                noSlow.mc.options.sneakKey
        };

        for (KeyBinding key : keys) {
            key.setPressed(false);
        }
    }

    private boolean isOnSnowOrCarpet() {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        BlockPos playerPos = mc.player.getBlockPos();
        Block block = mc.world.getBlockState(playerPos).getBlock();
        return block == Blocks.SNOW
                || block == Blocks.WHITE_CARPET
                || block == Blocks.ORANGE_CARPET
                || block == Blocks.MAGENTA_CARPET
                || block == Blocks.LIGHT_BLUE_CARPET
                || block == Blocks.YELLOW_CARPET
                || block == Blocks.LIME_CARPET
                || block == Blocks.PINK_CARPET
                || block == Blocks.GRAY_CARPET
                || block == Blocks.LIGHT_GRAY_CARPET
                || block == Blocks.CYAN_CARPET
                || block == Blocks.PURPLE_CARPET
                || block == Blocks.BLUE_CARPET
                || block == Blocks.BROWN_CARPET
                || block == Blocks.GREEN_CARPET
                || block == Blocks.RED_CARPET
                || block == Blocks.BLACK_CARPET;
    }

    private void updateEatSprintBoost() {
        if (mc.player == null || mc.options == null) {
            return;
        }
        boolean eating = mc.player.isUsingItem() && mc.player.getActiveItem().getUseAction() == UseAction.EAT;
        if (eating) {
            if (!forcedSprintDuringEat) {
                sprintWasPressedBeforeEat = mc.options.sprintKey.isPressed();
                forcedSprintDuringEat = true;
            }
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);
            return;
        }
        resetEatSprintBoostIfNeeded();
    }

    private void resetEatSprintBoostIfNeeded() {
        if (!forcedSprintDuringEat || mc.options == null) {
            return;
        }
        if (!sprintWasPressedBeforeEat) {
            mc.options.sprintKey.setPressed(false);
        }
        forcedSprintDuringEat = false;
        sprintWasPressedBeforeEat = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetEatSprintBoostIfNeeded();
        ticks = 0;
        cycleCounter = 0;
        cancel = false;

        pendingPackets.clear();
        slow = false;
        stopTicks = 0;
        stopUntilMs = 0L;
        waitTimeMs = 0L;

        if (mc.options != null) {
            clearMovementState(movementKeys());
        }
    }
}