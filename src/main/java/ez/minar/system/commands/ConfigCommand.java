package ez.minar.system.commands;

import ez.minar.system.managers.ConfigManager;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public final class ConfigCommand {
    public static final String COMMAND = "cfg";

    private ConfigCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String commandLine = input.trim();
        if (!isConfigCommand(commandLine)) {
            return false;
        }

        String[] arguments = commandLine.split("\\s+");
        if (arguments.length < 2 || arguments.length > 3) {
            showUsage();
            return true;
        }

        switch (arguments[1].toLowerCase(Locale.ROOT)) {
            case "dir" -> {
                if (arguments.length != 2) {
                    showUsage();
                } else {
                    openDirectory();
                }
            }
            case "load" -> load(arguments);
            case "save" -> save(arguments);
            case "reset" -> {
                if (arguments.length != 2) {
                    showUsage();
                } else {
                    reset();
                }
            }
            default -> showUsage();
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String commandLine = input.stripLeading().toLowerCase(Locale.ROOT);
        if (".".equals(commandLine) || (commandLine.startsWith(".") && ("." + COMMAND).startsWith(commandLine))) {
            return List.of(".cfg");
        }
        if (!commandLine.startsWith(".cfg ")) {
            return List.of();
        }

        String attribute = commandLine.substring(".cfg ".length());
        if (attribute.contains(" ")) {
            return List.of();
        }

        return List.of(".cfg dir", ".cfg load", ".cfg save", ".cfg reset").stream()
                .filter(suggestion -> suggestion.substring(".cfg ".length())
                        .toLowerCase(Locale.ROOT).startsWith(attribute))
                .toList();
    }

    private static boolean isConfigCommand(String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return lowerInput.equals(".cfg") || lowerInput.startsWith(".cfg ");
    }

    private static void openDirectory() {
        if (ConfigManager.openDirectory()) {
            CommandFeedback.message("Config folder opened.", Formatting.GREEN);
        } else {
            CommandFeedback.message("Could not open config folder.", Formatting.RED);
        }
    }

    private static void load(String[] arguments) {
        if (arguments.length == 3 && !ConfigManager.isValidConfigName(arguments[2])) {
            CommandFeedback.message("Invalid config name. Use letters, numbers, _ or -.", Formatting.YELLOW);
            return;
        }

        String name = arguments.length == 3 ? arguments[2] : null;
        if (ConfigManager.load(name)) {
            CommandFeedback.message(configLabel(name) + " loaded.", Formatting.GREEN);
        } else {
            CommandFeedback.message(configLabel(name) + " was not found or could not be loaded.", Formatting.RED);
        }
    }

    private static void save(String[] arguments) {
        if (arguments.length == 3 && !ConfigManager.isValidConfigName(arguments[2])) {
            CommandFeedback.message("Invalid config name. Use letters, numbers, _ or -.", Formatting.YELLOW);
            return;
        }

        String name = arguments.length == 3 ? arguments[2] : null;
        if (ConfigManager.save(name)) {
            CommandFeedback.message(configLabel(name) + " saved.", Formatting.GREEN);
        } else {
            CommandFeedback.message("Could not save " + configLabel(name).toLowerCase(Locale.ROOT) + ".", Formatting.RED);
        }
    }

    private static void reset() {
        ConfigManager.reset();
        CommandFeedback.message("Settings reset.", Formatting.GREEN);
    }

    private static void showUsage() {
        CommandFeedback.message(".cfg dir - open config folder", Formatting.GRAY);
        CommandFeedback.message(".cfg load [name] - load config", Formatting.GRAY);
        CommandFeedback.message(".cfg save [name] - save config", Formatting.GRAY);
        CommandFeedback.message(".cfg reset - reset all settings", Formatting.GRAY);
    }

    private static String configLabel(String name) {
        return name == null ? "Config" : "Config '" + name + "'";
    }
}
