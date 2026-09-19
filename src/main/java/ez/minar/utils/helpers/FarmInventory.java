package ez.minar.utils.helpers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class FarmInventory {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private FarmInventory() {
    }

    public static void click(int syncId, int slot, int button, SlotActionType type) {
        if (MC.player == null || MC.interactionManager == null) return;
        MC.interactionManager.clickSlot(syncId, slot, button, type, MC.player);
    }

    public static void quickMove(int syncId, int slot) {
        click(syncId, slot, 0, SlotActionType.QUICK_MOVE);
    }

    public static void pickup(int syncId, int slot) {
        click(syncId, slot, 0, SlotActionType.PICKUP);
    }

    public static int findHotbar(Item item) {
        if (MC.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (MC.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    public static int findInv(Item item) {
        if (MC.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (MC.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    public static boolean selectHotbar(Item item) {
        int slot = findHotbar(item);
        if (slot < 0 || MC.player == null) return false;
        MC.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    public static int count(Item item) {
        if (MC.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = MC.player.getInventory().getStack(i);
            if (stack.isOf(item)) total += stack.getCount();
        }
        return total;
    }

    public static int freeSlots() {
        if (MC.player == null) return 0;
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (MC.player.getInventory().getStack(i).isEmpty()) free++;
        }
        return free;
    }

    public static void sendCommand(String command) {
        if (MC.player == null || MC.getNetworkHandler() == null || command == null || command.isBlank()) return;
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        MC.getNetworkHandler().sendChatCommand(cmd);
    }

    public static void closeScreen() {
        if (MC.player != null) MC.player.closeHandledScreen();
    }
}
