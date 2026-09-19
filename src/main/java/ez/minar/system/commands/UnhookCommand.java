package ez.minar.system.commands;

import ez.minar.system.managers.UnhookManager;

import java.util.List;
import java.util.Locale;

public final class UnhookCommand {
    public static final String PREFIX = ".";
    public static final String COMMAND = "unhook";

    private UnhookCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String commandLine = input.trim().toLowerCase(Locale.ROOT);
        if (!commandLine.equals(PREFIX + COMMAND)) {
            return false;
        }

        UnhookManager.unhook();
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String commandLine = input.stripLeading().toLowerCase(Locale.ROOT);
        if (PREFIX.equals(commandLine) || (commandLine.startsWith(PREFIX) && (PREFIX + COMMAND).startsWith(commandLine))) {
            return List.of(".unhook");
        }
        return List.of();
    }
}
