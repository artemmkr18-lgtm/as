package ez.minar.system.commands;

import ez.minar.system.neuro.NeuroManager;
import ez.minar.system.neuro.NeuroModel;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
            NeuroManager.printStatus();
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> NeuroManager.printStatus();
            case "list" -> {
                List<String> models = NeuroModel.listModels();
                CommandFeedback.message("Доступные нейро-модели (" + models.size() + "):", Formatting.AQUA);
                String active = NeuroModel.getActiveModelName();
                for (String model : models) {
                    boolean isActive = model.equalsIgnoreCase(active);
                    CommandFeedback.message(" - " + model + (isActive ? " (активна)" : ""), isActive ? Formatting.GREEN : Formatting.GRAY);
                }
            }
            case "load", "play" -> {
                String targetName = args.length >= 3 ? args[2] : NeuroModel.DEFAULT_MODEL;
                NeuroManager.loadModel(targetName);
            }
            case "record", "rec" -> {
                String targetName = args.length >= 3 ? args[2] : "session";
                NeuroManager.startRecording(targetName);
            }
            case "stop" -> {
                NeuroManager.stopRecording();
            }
            case "cancel" -> {
                NeuroManager.cancelActiveWork();
            }
            case "train" -> {
                String name = args.length >= 3 ? args[2] : "custom";
                int epochs = 400;
                if (args.length >= 4) {
                    try {
                        epochs = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                NeuroManager.train(name, epochs);
            }
            case "dir" -> {
                Path dir = NeuroModel.getNeuroDir();
                Util.getOperatingSystem().open(dir.toFile());
                CommandFeedback.message("Папка открыта: " + dir.toAbsolutePath(), Formatting.GRAY);
            }
            case "data" -> {
                Path dataDir = NeuroModel.getDataDir();
                try (Stream<Path> stream = Files.list(dataDir)) {
                    List<Path> files = stream.filter(p -> p.getFileName().toString().endsWith(".csv")).toList();
                    CommandFeedback.message("Записанные датасеты (" + files.size() + "):", Formatting.AQUA);
                    for (Path file : files) {
                        long size = Files.size(file) / 1024;
                        CommandFeedback.message(" - " + file.getFileName() + " (" + size + " KB)", Formatting.GRAY);
                    }
                } catch (Exception e) {
                    CommandFeedback.message("Ошибка чтения папки датасетов: " + e.getMessage(), Formatting.RED);
                }
            }
            case "why" -> {
                CommandFeedback.message("Neuro Aura использует рекуррентную сеть GRU с Mixture Density Network (MDN) из клиента Rockstar.", Formatting.AQUA);
                CommandFeedback.message("Камера ведётся естественными движениями с микро-тремором и автоматической привязкой к чувствительности мыши (GCD).", Formatting.GRAY);
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
            List<String> subcommands = List.of("status", "list", "load", "record", "stop", "train", "dir", "data", "cancel", "why");
            List<String> suggestions = new ArrayList<>();
            for (String action : subcommands) {
                if (action.startsWith(args[1])) {
                    suggestions.add(".neuro " + action);
                }
            }
            return suggestions;
        }
        if (args.length == 3 && ("load".equals(args[1]) || "play".equals(args[1]))) {
            return NeuroModel.listModels().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2]))
                    .map(name -> ".neuro " + args[1] + " " + name)
                    .toList();
        }
        if (args.length == 4 && "train".equals(args[1])) {
            return List.of("200", "400", "800").stream()
                    .filter(e -> e.startsWith(args[3]))
                    .map(e -> ".neuro train " + args[2] + " " + e)
                    .toList();
        }
        return List.of();
    }

    private static void help() {
        CommandFeedback.message("Использование .neuro:", Formatting.AQUA);
        CommandFeedback.message(" .neuro status — статус модели и датасетов", Formatting.GRAY);
        CommandFeedback.message(" .neuro list — список доступных моделей", Formatting.GRAY);
        CommandFeedback.message(" .neuro load <имя> — загрузить модель (по умолч. 'default')", Formatting.GRAY);
        CommandFeedback.message(" .neuro record [имя] — начать запись датасета боевки", Formatting.GRAY);
        CommandFeedback.message(" .neuro stop — остановить запись", Formatting.GRAY);
        CommandFeedback.message(" .neuro train <имя> [эпохи] — обучить модель через PyTorch", Formatting.GRAY);
        CommandFeedback.message(" .neuro dir — открыть папку с моделями и данными", Formatting.GRAY);
        CommandFeedback.message(" .neuro data — список записанных датасетов", Formatting.GRAY);
    }
}
