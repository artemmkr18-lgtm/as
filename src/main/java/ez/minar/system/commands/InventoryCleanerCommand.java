package ez.minar.system.commands;

import ez.minar.system.managers.InventoryCleanerManager;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

public final class InventoryCleanerCommand {
    private InventoryCleanerCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String line = input.trim();
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.equals(".ic") && !lower.startsWith(".ic ")) return false;

        String[] args = line.split("\\s+");
        if (args.length == 1) {
            usage();
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> change(args, true);
            case "remove" -> change(args, false);
            case "list" -> list(args);
            case "clear" -> clear(args);
            default -> usage();
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String lower = input.stripLeading().toLowerCase(Locale.ROOT);
        if (".".equals(lower) || (lower.startsWith(".") && ".ic".startsWith(lower))) {
            return List.of(".ic");
        }
        if (!lower.startsWith(".ic ")) return List.of();
        String tail = lower.substring(".ic ".length());
        String[] parts = tail.split("\\s+", -1);
        if (parts.length == 1) {
            return List.of("add", "remove", "list", "clear").stream()
                    .filter(action -> action.startsWith(parts[0]))
                    .map(action -> ".ic " + action + (action.equals("add") || action.equals("remove") ? " " : ""))
                    .toList();
        }
        if (parts.length == 2 && (parts[0].equals("add") || parts[0].equals("remove"))) {
            return ("remove".equals(parts[0]) ? InventoryCleanerManager.getIds().stream().map(Identifier::toString)
                    : InventoryCleanerManager.itemSuggestions().stream())
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(parts[1]))
                    .limit(100)
                    .map(id -> ".ic " + parts[0] + " " + id)
                    .toList();
        }
        return List.of();
    }

    private static void change(String[] args, boolean add) {
        if (args.length != 3) {
            message("Usage: .ic " + (add ? "add" : "remove") + " <item>", Formatting.YELLOW);
            return;
        }
        Identifier id = InventoryCleanerManager.resolve(args[2]);
        if (id == null) {
            message("Unknown item: " + args[2], Formatting.RED);
            return;
        }
        boolean changed = add ? InventoryCleanerManager.add(id) : InventoryCleanerManager.remove(id);
        Item item = Registries.ITEM.get(id);
        if (changed) {
            message(item.getName().getString() + (add ? " added to InventoryCleaner." : " removed from InventoryCleaner."), Formatting.GREEN);
        } else {
            message(item.getName().getString() + (add ? " is already in InventoryCleaner." : " was not found in InventoryCleaner."), Formatting.YELLOW);
        }
    }

    private static void list(String[] args) {
        if (args.length != 2) {
            message("Usage: .ic list", Formatting.YELLOW);
            return;
        }
        if (InventoryCleanerManager.getIds().isEmpty()) {
            message("InventoryCleaner list is empty.", Formatting.GRAY);
            return;
        }
        message("InventoryCleaner items (" + InventoryCleanerManager.getIds().size() + "):", Formatting.AQUA);
        for (Identifier id : InventoryCleanerManager.getIds()) {
            message("- " + Registries.ITEM.get(id).getName().getString() + " [" + id + "]", Formatting.GRAY);
        }
    }

    private static void clear(String[] args) {
        if (args.length != 2) {
            message("Usage: .ic clear", Formatting.YELLOW);
            return;
        }
        message("InventoryCleaner list cleared (" + InventoryCleanerManager.clear() + ").", Formatting.GREEN);
    }

    private static void usage() {
        message(".ic add <item> - add item", Formatting.GRAY);
        message(".ic remove <item> - remove item", Formatting.GRAY);
        message(".ic list - show selected items", Formatting.GRAY);
        message(".ic clear - clear item list", Formatting.GRAY);
    }

    private static void message(String text, Formatting color) {
        CommandFeedback.message(text, color);
    }
}
