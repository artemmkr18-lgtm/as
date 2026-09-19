package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.utils.helpers.NetworkUtils;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

@NewFunction(name = "GuiMove", desc = "Позволяет двигаться с открытым инвентарём", category = Category.MOVEMENT)
public class GuiMove extends Function {

    private final ModeSetting mode = new ModeSetting("Mode", "Packet", "Grim full", "Legit");

    private final List<Packet<?>> delayedPackets = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> taskQueue = new LinkedList<>();
    private boolean processingPackets = false;
    private boolean lockMovement = false;
    private int legitStopTicks = 0;

    public GuiMove() {
        addSettings(mode);
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (mc.player == null) {
            cleanup();
            return;
        }

        if (!taskQueue.isEmpty()) {
            Runnable task = taskQueue.poll();
            if (task != null) {
                task.run();
            }
        }

        if (lockMovement) {
            stopMovementKeys();
            return;
        }

        if (mc.currentScreen == null
                || mc.currentScreen instanceof ChatScreen
                || mc.currentScreen instanceof SignEditScreen
                || mc.currentScreen instanceof AnvilScreen) {
            legitStopTicks = 0;
            return;
        }

        if (mode.isEnabled("Legit") && shouldStopForLegit()) {
            stopMovementKeys();
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            if (legitStopTicks > 0) {
                legitStopTicks--;
            }
            return;
        }

        for (KeyBinding binding : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.rightKey, mc.options.leftKey, mc.options.jumpKey, mc.options.sprintKey}) {
            if (InputUtil.isKeyPressed(mc.getWindow(), binding.getDefaultKey().getCode())) {
                binding.setPressed(true);
            }
        }
    }

    @EventHandler
    public void onPacket(PacketSendEvent e) {
        if (mc.player == null) return;

        if (mode.isEnabled("Legit") && e.getPacket() instanceof ClickSlotC2SPacket) {
            legitStopTicks = 3;
            return;
        }

        if (mode.isEnabled("Packet") || NetworkUtils.silentSending) return;

        if (e.getPacket() instanceof ClickSlotC2SPacket clickPacket
                && mc.currentScreen instanceof InventoryScreen
                && hasPlayerMovement() && shouldAllowMovement()) {
            delayedPackets.add(clickPacket);
            e.cancel();
        } else if (e.getPacket() instanceof CloseHandledScreenC2SPacket closePacket
                && closePacket.getSyncId() == 0 && !delayedPackets.isEmpty() && !processingPackets) {
            delayedPackets.add(closePacket);
            e.cancel();
            processDelayedPackets();
        }
    }

    private boolean shouldStopForLegit() {
        if (legitStopTicks > 0) return true;
        return mc.player.currentScreenHandler != null
                && !mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private void stopMovementKeys() {
        for (KeyBinding binding : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.rightKey, mc.options.leftKey, mc.options.jumpKey, mc.options.sprintKey}) {
            binding.setPressed(false);
        }
    }

    private void processDelayedPackets() {
        processingPackets = true;

        if (mode.isEnabled("Grim full")) {
            taskQueue.add(() -> lockMovement = true);
            taskQueue.add(() -> {
                for (Packet<?> p : delayedPackets) NetworkUtils.sendSilentPacket(p);
                delayedPackets.clear();
                processingPackets = false;
                lockMovement = false;
                restoreKeys();
            });
        }
    }

    private void restoreKeys() {
        if (mc.getWindow() == null) return;
        for (KeyBinding binding : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.rightKey, mc.options.leftKey, mc.options.jumpKey, mc.options.sprintKey}) {
            binding.setPressed(InputUtil.isKeyPressed(mc.getWindow(), binding.getDefaultKey().getCode()));
        }
    }

    private boolean hasPlayerMovement() {
        return mc.player != null && mc.player.input != null
                && mc.player.input.getMovementInput().lengthSquared() > 1.0E-7f;
    }

    private boolean shouldAllowMovement() {
        return mc.player != null && mc.player.currentScreenHandler != null
                && mc.player.currentScreenHandler.slots.size() >= 27;
    }

    private void cleanup() {
        delayedPackets.clear();
        taskQueue.clear();
        processingPackets = false;
        lockMovement = false;
        legitStopTicks = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        cleanup();
    }
}
