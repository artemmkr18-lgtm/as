package ez.minar.system.commands;

import ez.minar.system.dummy.FakePlayerManager;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FakePlayerCommand {
    private FakePlayerCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String stripped = input.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(".fk") && !lower.startsWith(".fakeplayer")) {
            return false;
        }

        String[] args = stripped.split("\\s+");
        if (!args[0].equalsIgnoreCase(".fk") && !args[0].equalsIgnoreCase(".fakeplayer")) {
            return false;
        }

        if (args.length == 1) {
            help();
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add", "spawn", "create" -> {
                String name = args.length >= 3 ? args[2] : "NeuroDummy";
                FakePlayerManager.addDummy(name);
            }
            case "remove", "del", "delete" -> {
                String name = args.length >= 3 ? args[2] : "";
                FakePlayerManager.removeDummy(name);
            }
            case "clear" -> FakePlayerManager.clearAll();
            case "list" -> {
                List<String> list = FakePlayerManager.getNames();
                if (list.isEmpty()) {
                    CommandFeedback.message("Активных нейро-дамми нет.", Formatting.GRAY);
                } else {
                    CommandFeedback.message("Активные нейро-дамми (" + list.size() + "):", Formatting.AQUA);
                    for (String name : list) {
                        CommandFeedback.message(" - " + name, Formatting.GRAY);
                    }
                }
            }
            default -> help();
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String lower = input.stripLeading().toLowerCase(Locale.ROOT);
        if (!lower.startsWith(".fk") && !lower.startsWith(".fakeplayer")) {
            if (".fk".startsWith(lower)) return List.of(".fk");
            if (".fakeplayer".startsWith(lower)) return List.of(".fakeplayer");
            return List.of();
        }

        String[] args = lower.split("\\s+", -1);
        if (args.length == 1) {
            return List.of(".fk ");
        }
        if (args.length == 2) {
            List<String> subcommands = List.of("add", "remove", "clear", "list");
            List<String> suggestions = new ArrayList<>();
            for (String sub : subcommands) {
                if (sub.startsWith(args[1])) {
                    suggestions.add(args[0] + " " + sub);
                }
            }
            return suggestions;
        }
        if (args.length == 3 && ("remove".equals(args[1]) || "del".equals(args[1]))) {
            return FakePlayerManager.getNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2]))
                    .map(name -> args[0] + " " + args[1] + " " + name)
                    .toList();
        }
        return List.of();
    }

    private static void help() {
        CommandFeedback.message("Команда .fk (.fakeplayer):", Formatting.AQUA);
        CommandFeedback.message(" .fk add [name] — заспавнить нейро-дамми на своей позиции", Formatting.GRAY);
        CommandFeedback.message(" .fk remove [name] — удалить дамми", Formatting.GRAY);
        CommandFeedback.message(" .fk clear — удалить всех дамми", Formatting.GRAY);
        CommandFeedback.message(" .fk list — список активных дамми", Formatting.GRAY);
    }
}
