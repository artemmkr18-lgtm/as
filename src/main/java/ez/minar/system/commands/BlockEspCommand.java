package ez.minar.system.commands;

import ez.minar.system.managers.BlockEspManager;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

public final class BlockEspCommand {
    private BlockEspCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String line = input.trim();
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.equals(".blockesp") && !lower.startsWith(".blockesp ")) return false;

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
        if (".".equals(lower) || (lower.startsWith(".") && ".blockesp".startsWith(lower))) {
            return List.of(".blockesp");
        }
        if (!lower.startsWith(".blockesp ")) return List.of();
        String tail = lower.substring(".blockesp ".length());
        String[] parts = tail.split("\\s+", -1);
        if (parts.length == 1) {
            return List.of("add", "remove", "list", "clear").stream()
                    .filter(action -> action.startsWith(parts[0]))
                    .map(action -> ".blockesp " + action + (action.equals("add") || action.equals("remove") ? " " : ""))
                    .toList();
        }
        if (parts.length == 2 && (parts[0].equals("add") || parts[0].equals("remove"))) {
            return ("remove".equals(parts[0]) ? BlockEspManager.getIds().stream().map(Identifier::toString)
                    : BlockEspManager.blockSuggestions().stream())
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(parts[1]))
                    .limit(100)
                    .map(id -> ".blockesp " + parts[0] + " " + id)
                    .toList();
        }
        return List.of();
    }

    private static void change(String[] args, boolean add) {
        if (args.length != 3) {
            message("Usage: .blockesp " + (add ? "add" : "remove") + " <block>", Formatting.YELLOW);
            return;
        }
        Identifier id = BlockEspManager.resolve(args[2]);
        if (id == null) {
            message("Unknown block: " + args[2], Formatting.RED);
            return;
        }
        boolean changed = add ? BlockEspManager.add(id) : BlockEspManager.remove(id);
        Block block = Registries.BLOCK.get(id);
        if (changed) {
            message(block.getName().getString() + (add ? " added to BlockESP." : " removed from BlockESP."), Formatting.GREEN);
        } else {
            message(block.getName().getString() + (add ? " is already in BlockESP." : " was not found in BlockESP."), Formatting.YELLOW);
        }
    }

    private static void list(String[] args) {
        if (args.length != 2) {
            message("Usage: .blockesp list", Formatting.YELLOW);
            return;
        }
        if (BlockEspManager.getIds().isEmpty()) {
            message("BlockESP list is empty.", Formatting.GRAY);
            return;
        }
        message("BlockESP blocks (" + BlockEspManager.getIds().size() + "):", Formatting.AQUA);
        for (Identifier id : BlockEspManager.getIds()) {
            message("- " + Registries.BLOCK.get(id).getName().getString() + " [" + id + "]", Formatting.GRAY);
        }
    }

    private static void clear(String[] args) {
        if (args.length != 2) {
            message("Usage: .blockesp clear", Formatting.YELLOW);
            return;
        }
        message("BlockESP list cleared (" + BlockEspManager.clear() + ").", Formatting.GREEN);
    }

    private static void usage() {
        message(".blockesp add <block> - add block", Formatting.GRAY);
        message(".blockesp remove <block> - remove block", Formatting.GRAY);
        message(".blockesp list - show selected blocks", Formatting.GRAY);
        message(".blockesp clear - clear block list", Formatting.GRAY);
    }

    private static void message(String text, Formatting color) {
        CommandFeedback.message(text, color);
    }
}
