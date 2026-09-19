package ez.minar.system.features.player;

import ez.minar.mixins.interfaces.IHandledScreen;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.InventoryCleanerManager;
import ez.minar.system.settings.impl.ModeSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

@NewFunction(name = "InventoryCleaner", desc = "Очищает инвентарь от выбранных предметов", category = Category.PLAYER)
public class InventoryCleaner extends Function {
    public static InventoryCleaner Instance;

    private static final int FIRST_INVENTORY_SLOT = 9;
    private static final int LAST_HOTBAR_SLOT = 44;
    private static final Random RANDOM = new Random();

    private final ModeSetting mode = new ModeSetting("Режим", "Легитный", "Быстрый");
    private final Queue<Integer> legitQueue = new ArrayDeque<>();
    private long nextDropTime;

    public InventoryCleaner() {
        Instance = this;
        addSettings(mode);
    }

    @Override
    public void onDisable() {
        legitQueue.clear();
        nextDropTime = 0L;
    }

    public static boolean shouldShowButton() {
        return Instance != null
                && Instance.isEnabled();
    }

    public static void requestClean() {
        if (Instance != null && Instance.isEnabled()) {
            Instance.startClean();
        }
    }

    @EventHandler
    private void onUpdate(UpdateEvent event) {
        if (legitQueue.isEmpty()) {
            return;
        }
        if (nullCheck.all()
                || mc.interactionManager == null
                || !(mc.currentScreen instanceof InventoryScreen screen)) {
            legitQueue.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextDropTime) {
            return;
        }

        while (!legitQueue.isEmpty()) {
            int slotId = legitQueue.poll();
            Slot slot = mc.player.currentScreenHandler.getSlot(slotId);
            if (slot != null && isTarget(slot)) {
                dropLegit(screen, slot);
                nextDropTime = now + 65L + RANDOM.nextInt(45);
                return;
            }
        }
    }

    private void startClean() {
        if (nullCheck.all()
                || mc.interactionManager == null
                || !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        legitQueue.clear();
        if (mode.isEnabled("Быстрый")) {
            for (int slotId : targetSlots()) {
                dropStack(slotId);
            }
            return;
        }

        legitQueue.addAll(targetSlots());
        nextDropTime = 0L;
    }

    private Queue<Integer> targetSlots() {
        Queue<Integer> slots = new ArrayDeque<>();
        for (int slotId = FIRST_INVENTORY_SLOT; slotId <= LAST_HOTBAR_SLOT; slotId++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(slotId);
            if (slot != null && isTarget(slot)) {
                slots.add(slotId);
            }
        }
        return slots;
    }

    private boolean isTarget(Slot slot) {
        if (!slot.hasStack()) {
            return false;
        }
        Set<Item> items = InventoryCleanerManager.getItems();
        return items.contains(slot.getStack().getItem());
    }

    private void dropStack(int slotId) {
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId,
                1,
                SlotActionType.THROW,
                mc.player
        );
    }

    private void dropLegit(InventoryScreen screen, Slot slot) {
        IHandledScreen handledScreen = (IHandledScreen) screen;
        Slot previousSlot = handledScreen.getFocusedSlot();
        handledScreen.setFocusedSlot(slot);
        screen.keyPressed(new KeyInput(mc.options.dropKey.getDefaultKey().getCode(), 0, 2));
        handledScreen.setFocusedSlot(previousSlot);
    }
}
