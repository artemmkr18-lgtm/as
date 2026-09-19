package ez.minar.system.commands;

import ez.minar.system.neuro.NeuroManager;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NeuroCommand {
    private NeuroCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String stripped = input.strip();
        if (!stripped.toLowerCase(Locale.ROOT).startsWith(".neuro")) {
            return false;
        }

        String[] args = stripped.split("\\s+");
        if (!args[0].equalsIgnoreCase(".neuro")) {
            return false;
        }
        if (args.length == 1) {
            help();
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "record" -> {
                String targetName = args.length >= 3 ? args[2] : "default";
                NeuroManager.startRecording(targetName);
            }
            case "play" -> {
                String targetName = args.length >= 3 ? args[2] : (NeuroManager.getActiveModelName() != null ? NeuroManager.getActiveModelName() : "default");
                NeuroManager.play(targetName);
            }
            case "stop" -> {
                NeuroManager.stopRecording();
            }
            case "cancel" -> {
                NeuroManager.cancelActiveWork();
            }
            default -> help();
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String lower = input.stripLeading().toLowerCase(Locale.ROOT);
        if (!lower.startsWith(".neuro")) {
            return ".neuro".startsWith(lower) ? List.of(".neuro") : List.of();
        }

        String[] args = lower.split("\\s+", -1);
        if (args.length == 1) {
            return List.of(".neuro ");
        }
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (String action : List.of("record", "play", "stop", "cancel")) {
                if (action.startsWith(args[1])) {
                    suggestions.add(".neuro " + action);
                }
            }
            return suggestions;
        }
        if (args.length == 3 && "play".equals(args[1])) {
            return NeuroManager.getNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2]))
                    .map(name -> ".neuro play " + name)
                    .toList();
        }
        return List.of();
    }

    private static void help() {
        CommandFeedback.message(".neuro record [название] — начать запись камеры (по умолч. 'default')", Formatting.GRAY);
        CommandFeedback.message(".neuro stop — сохранить датасет и подготовить модель", Formatting.GRAY);
        CommandFeedback.message(".neuro play [название] — обучить или загрузить модель", Formatting.GRAY);
        CommandFeedback.message(".neuro cancel — отменить текущую запись или обучение", Formatting.GRAY);
    }
}
