package ez.minar.system.commands;

import ez.minar.system.autobuy.config.AutoBuyConfigFile;
import ez.minar.system.autobuy.manager.AutoBuyManager;
import ez.minar.system.autobuy.ui.AutoBuyScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public final class AutoBuyCommand {
    public static final String COMMAND = "autobuy";
    public static final String SHORT_COMMAND = "ab";

    private AutoBuyCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String commandLine = input.trim();
        if (!isAutoBuyCommand(commandLine)) {
            return false;
        }

        String[] arguments = commandLine.split("\\s+");
        if (arguments.length == 1 || (arguments.length == 2 && arguments[1].equalsIgnoreCase("gui"))) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.send(() -> client.setScreen(new ez.minar.system.autobuy.ui.AutoBuyMenuScreen()));
            return true;
        }

        switch (arguments[1].toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                boolean newState = !ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().isEnabled();
                ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().setEnabled(newState);
                AutoBuyManager.getInstance().setEnabled(newState);
                CommandFeedback.message("AutoBuy " + (newState ? "включен." : "выключен."), newState ? Formatting.GREEN : Formatting.RED);
            }
            case "on" -> {
                ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().setEnabled(true);
                AutoBuyManager.getInstance().setEnabled(true);
                CommandFeedback.message("AutoBuy включен.", Formatting.GREEN);
            }
            case "off" -> {
                ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine.getInstance().setEnabled(false);
                AutoBuyManager.getInstance().setEnabled(false);
                CommandFeedback.message("AutoBuy выключен.", Formatting.RED);
            }
            case "save" -> {
                AutoBuyConfigFile.save();
                CommandFeedback.message("Конфиг AutoBuy сохранен.", Formatting.GREEN);
            }
            case "load" -> {
                AutoBuyConfigFile.load();
                CommandFeedback.message("Конфиг AutoBuy перезагружен.", Formatting.GREEN);
            }
            default -> showUsage();
        }
        return true;
    }

    private static boolean isAutoBuyCommand(String commandLine) {
        String lower = commandLine.toLowerCase(Locale.ROOT);
        return lower.equals("." + COMMAND) || lower.startsWith("." + COMMAND + " ")
                || lower.equals("." + SHORT_COMMAND) || lower.startsWith("." + SHORT_COMMAND + " ");
    }

    private static void showUsage() {
        CommandFeedback.message(".autobuy / .ab — открыть меню AutoBuy", Formatting.GRAY);
        CommandFeedback.message(".autobuy toggle — переключить работу AutoBuy", Formatting.GRAY);
        CommandFeedback.message(".autobuy on/off — включить или выключить", Formatting.GRAY);
        CommandFeedback.message(".autobuy save/load — сохранить или перезагрузить конфиг", Formatting.GRAY);
    }

    public static List<String> suggestionsFor(String input) {
        String commandLine = input.stripLeading().toLowerCase(Locale.ROOT);
        if (".".equals(commandLine) || (commandLine.startsWith(".") && ("." + COMMAND).startsWith(commandLine))) {
            return List.of(".autobuy", ".ab");
        }
        if (commandLine.startsWith(".ab") && !commandLine.startsWith(".ab ")) {
            return List.of(".ab");
        }
        if (!commandLine.startsWith(".autobuy ") && !commandLine.startsWith(".ab ")) {
            return List.of();
        }

        String prefix = commandLine.startsWith(".autobuy ") ? ".autobuy " : ".ab ";
        String attribute = commandLine.substring(prefix.length());
        if (attribute.contains(" ")) {
            return List.of();
        }

        return List.of("gui", "toggle", "on", "off", "save", "load").stream()
                .filter(action -> action.startsWith(attribute))
                .map(action -> prefix + action)
                .toList();
    }
}
