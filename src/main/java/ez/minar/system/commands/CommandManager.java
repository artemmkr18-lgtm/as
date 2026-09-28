package ez.minar.system.commands;

import ez.minar.system.managers.UnhookManager;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class CommandManager {
    private CommandManager() {
    }

    public static boolean isHiddenWhileUnhooked(String input) {
        return UnhookManager.isUnhooked() && input.stripLeading().toLowerCase(Locale.ROOT).startsWith(UnhookCommand.PREFIX);
    }

    public static boolean executeIfCommand(String input) {
        if (UnhookManager.isUnhooked()) {
            return false;
        }

        return UnhookCommand.executeIfCommand(input) || FriendCommand.executeIfCommand(input) || ConfigCommand.executeIfCommand(input)
                || BindCommand.executeIfCommand(input)
                || BlockEspCommand.executeIfCommand(input)
                || InventoryCleanerCommand.executeIfCommand(input)
                || WayCommand.executeIfCommand(input)
                || NeuroCommand.executeIfCommand(input)
                || FakePlayerCommand.executeIfCommand(input)
                || CalcCommand.executeIfCommand(input)
                || AutoBuyCommand.executeIfCommand(input)
                || executeParse(input);
    }

    public static List<String> suggestionsFor(String input) {
        if (UnhookManager.isUnhooked()) {
            return List.of();
        }

        String commandLine = input.stripLeading().toLowerCase(Locale.ROOT);
        if (".".equals(commandLine)) {
            return Stream.of(".friend", ".cfg", ".bind", ".blockesp", ".ic", ".way", ".neuro", ".calc", ".autobuy", ".ab", ".fk", ".parse", ".unhook").sorted().toList();
        }

        if (commandLine.startsWith(".p") && ".parse".startsWith(commandLine)) {
            return List.of(".parse");
        }

        return Stream.of(
                        UnhookCommand.suggestionsFor(input).stream(),
                        FriendCommand.suggestionsFor(input).stream(),
                        ConfigCommand.suggestionsFor(input).stream(),
                        BindCommand.suggestionsFor(input).stream(),
                        BlockEspCommand.suggestionsFor(input).stream(),
                        InventoryCleanerCommand.suggestionsFor(input).stream(),
                        WayCommand.suggestionsFor(input).stream(),
                        NeuroCommand.suggestionsFor(input).stream(),
                        FakePlayerCommand.suggestionsFor(input).stream(),
                        CalcCommand.suggestionsFor(input).stream(),
                        AutoBuyCommand.suggestionsFor(input).stream()
                )
                .flatMap(stream -> stream)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static boolean executeParse(String input) {
        if (!".parse".equalsIgnoreCase(input.strip())) {
            return false;
        }

        ParseCommand.parsePlayers();
        return true;
    }

    public static String tabCompletionFor(String input) {
        List<String> suggestions = suggestionsFor(input);
        if (suggestions.isEmpty()) {
            return null;
        }

        String suggestion = suggestions.getFirst();
        if (".friend".equals(suggestion) || ".cfg".equals(suggestion)
                || ".bind".equals(suggestion) || ".blockesp".equals(suggestion) || ".ic".equals(suggestion)
                || ".way".equals(suggestion) || ".neuro".equals(suggestion) || ".calc".equals(suggestion) || ".fk".equals(suggestion)
                || ".unhook".equals(suggestion)) {
            return suggestion + " ";
        }
        if (suggestion.endsWith("<nick>")) {
            return suggestion.substring(0, suggestion.length() - "<nick>".length());
        }
        return suggestion;
    }
}
