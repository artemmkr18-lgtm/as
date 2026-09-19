package ez.minar.system.commands;

import ez.minar.system.managers.FriendManager;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public final class FriendCommand {
    public static final String PREFIX = ".";
    public static final String COMMAND = "friend";

    private FriendCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String commandLine = input.trim();
        if (!isFriendCommand(commandLine)) {
            return false;
        }

        String[] arguments = commandLine.split("\\s+");
        if (arguments.length == 1) {
            showUsage();
            return true;
        }

        switch (arguments[1].toLowerCase(Locale.ROOT)) {
            case "add" -> add(arguments);
            case "remove" -> remove(arguments);
            case "clear" -> clear(arguments);
            default -> showUsage();
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String commandLine = input.stripLeading().toLowerCase(Locale.ROOT);
        if (PREFIX.equals(commandLine) || (commandLine.startsWith(PREFIX) && (PREFIX + COMMAND).startsWith(commandLine))) {
            return List.of(".friend");
        }
        if (!commandLine.startsWith(".friend ")) {
            return List.of();
        }

        String attribute = commandLine.substring(".friend ".length());
        if (attribute.contains(" ")) {
            return List.of();
        }

        return List.of(".friend add <nick>", ".friend remove <nick>", ".friend clear").stream()
                .filter(suggestion -> suggestion.substring(".friend ".length())
                        .toLowerCase(Locale.ROOT).startsWith(attribute))
                .toList();
    }

    private static boolean isFriendCommand(String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return lowerInput.equals(".friend") || lowerInput.startsWith(".friend ");
    }

    private static void add(String[] arguments) {
        if (arguments.length != 3 || !validName(arguments[2])) {
            message("Usage: .friend add <nick>", Formatting.YELLOW);
            return;
        }

        if (FriendManager.add(arguments[2])) {
            message(arguments[2] + " added to friends.", Formatting.GREEN);
        } else {
            message(arguments[2] + " is already in friends.", Formatting.YELLOW);
        }
    }

    private static void remove(String[] arguments) {
        if (arguments.length != 3 || !validName(arguments[2])) {
            message("Usage: .friend remove <nick>", Formatting.YELLOW);
            return;
        }

        if (FriendManager.remove(arguments[2])) {
            message(arguments[2] + " removed from friends.", Formatting.GREEN);
        } else {
            message(arguments[2] + " was not found in friends.", Formatting.YELLOW);
        }
    }

    private static void clear(String[] arguments) {
        if (arguments.length != 2) {
            message("Usage: .friend clear", Formatting.YELLOW);
            return;
        }

        int cleared = FriendManager.clear();
        message("Friends list cleared (" + cleared + ").", Formatting.GREEN);
    }

    private static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static void showUsage() {
        message(".friend add <nick> - add friend", Formatting.GRAY);
        message(".friend remove <nick> - remove friend", Formatting.GRAY);
        message(".friend clear - clear friends list", Formatting.GRAY);
    }

    private static void message(String message, Formatting color) {
        CommandFeedback.message(message, color);
    }
}
