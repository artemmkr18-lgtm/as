package ez.minar.system.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import ez.minar.system.managers.ConfigManager;
import ez.minar.system.managers.BlockEspManager;
import ez.minar.system.managers.InventoryCleanerManager;
import ez.minar.system.neuro.NeuroManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class MinarClientCommands {
    private MinarClientCommands() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".cfg")
                        .then(literal("dir").executes(context -> {
                            openDirectory();
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(literal("save")
                                .executes(context -> {
                                    save(null);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            save(StringArgumentType.getString(context, "name"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(literal("load")
                                .executes(context -> {
                                    load(null);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(ConfigManager.getConfigNames(), builder))
                                        .executes(context -> {
                                            load(StringArgumentType.getString(context, "name"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(literal("reset").executes(context -> {
                            ConfigManager.reset();
                            CommandFeedback.message("Settings reset.", Formatting.GREEN);
                            return Command.SINGLE_SUCCESS;
                        }))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".friend")
                        .then(literal("add")
                                .then(argument("nick", StringArgumentType.word())
                                        .executes(context -> executeFriend("add", StringArgumentType.getString(context, "nick")))))
                        .then(literal("remove")
                                .then(argument("nick", StringArgumentType.word())
                                        .executes(context -> executeFriend("remove", StringArgumentType.getString(context, "nick")))))
                        .then(literal("clear").executes(context -> {
                            FriendCommand.executeIfCommand(".friend clear");
                            return Command.SINGLE_SUCCESS;
                        }))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".parse").executes(context -> {
                    ParseCommand.parsePlayers();
                    return Command.SINGLE_SUCCESS;
                })
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".bind")
                        .then(literal("add")
                                .then(argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(BindCommand.moduleNames(), builder))
                                        .then(argument("key", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(BindCommand.keySuggestions(), builder))
                                                .executes(context -> executeBind(".bind add "
                                                        + StringArgumentType.getString(context, "module") + " "
                                                        + StringArgumentType.getString(context, "key"))))))
                        .then(literal("remove")
                                .then(argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(BindCommand.moduleNames(), builder))
                                        .executes(context -> executeBind(".bind remove "
                                                + StringArgumentType.getString(context, "module")))))
                        .then(literal("list").executes(context -> executeBind(".bind list")))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".blockesp")
                        .then(literal("add")
                                .then(argument("block", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                BlockEspManager.blockSuggestions(), builder))
                                        .executes(context -> executeBlockEsp(".blockesp add "
                                                + StringArgumentType.getString(context, "block")))))
                        .then(literal("remove")
                                .then(argument("block", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                BlockEspManager.getIds().stream().map(Object::toString), builder))
                                        .executes(context -> executeBlockEsp(".blockesp remove "
                                                + StringArgumentType.getString(context, "block")))))
                        .then(literal("list").executes(context -> executeBlockEsp(".blockesp list")))
                        .then(literal("clear").executes(context -> executeBlockEsp(".blockesp clear")))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".ic")
                        .then(literal("add")
                                .then(argument("item", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                InventoryCleanerManager.itemSuggestions(), builder))
                                        .executes(context -> executeInventoryCleaner(".ic add "
                                                + StringArgumentType.getString(context, "item")))))
                        .then(literal("remove")
                                .then(argument("item", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                InventoryCleanerManager.getIds().stream().map(Object::toString), builder))
                                        .executes(context -> executeInventoryCleaner(".ic remove "
                                                + StringArgumentType.getString(context, "item")))))
                        .then(literal("list").executes(context -> executeInventoryCleaner(".ic list")))
                        .then(literal("clear").executes(context -> executeInventoryCleaner(".ic clear")))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".way")
                        .executes(context -> executeWay(".way"))
                        .then(argument("args", StringArgumentType.greedyString())
                                .executes(context -> executeWay(".way " + StringArgumentType.getString(context, "args"))))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal(".neuro")
                        .executes(context -> {
                            NeuroCommand.executeIfCommand(".neuro");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(literal("record")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            NeuroManager.startRecording(StringArgumentType.getString(context, "name"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(literal("play")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                CommandSource.suggestMatching(NeuroManager.getNames(), builder))
                                        .executes(context -> {
                                            NeuroManager.play(StringArgumentType.getString(context, "name"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(literal("stop").executes(context -> {
                            NeuroManager.stopRecording();
                            return Command.SINGLE_SUCCESS;
                        }))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal(".calc")
                        .executes(context -> executeCalc(".calc"))
                        .then(argument("args", StringArgumentType.greedyString())
                                .executes(context -> executeCalc(".calc " + StringArgumentType.getString(context, "args"))))
                )
        );

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal(".autobuy")
                    .executes(context -> executeAutoBuy(".autobuy"))
                    .then(argument("args", StringArgumentType.greedyString())
                            .suggests((context, builder) -> CommandSource.suggestMatching(AutoBuyCommand.suggestionsFor(".autobuy "), builder))
                            .executes(context -> executeAutoBuy(".autobuy " + StringArgumentType.getString(context, "args"))))
            );
            dispatcher.register(literal(".ab")
                    .executes(context -> executeAutoBuy(".ab"))
                    .then(argument("args", StringArgumentType.greedyString())
                            .suggests((context, builder) -> CommandSource.suggestMatching(AutoBuyCommand.suggestionsFor(".ab "), builder))
                            .executes(context -> executeAutoBuy(".ab " + StringArgumentType.getString(context, "args"))))
            );
        });
    }

    private static int executeAutoBuy(String command) {
        AutoBuyCommand.executeIfCommand(command);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCalc(String command) {
        CalcCommand.executeIfCommand(command);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeWay(String command) {
        WayCommand.executeIfCommand(command);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeBind(String command) {
        BindCommand.executeIfCommand(command);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeBlockEsp(String command) {
        BlockEspCommand.executeIfCommand(command);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInventoryCleaner(String command) {
        InventoryCleanerCommand.executeIfCommand(command);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeFriend(String action, String name) {
        FriendCommand.executeIfCommand(".friend " + action + " " + name);
        return Command.SINGLE_SUCCESS;
    }

    private static void openDirectory() {
        if (ConfigManager.openDirectory()) {
            CommandFeedback.message("Config folder opened.", Formatting.GREEN);
        } else {
            CommandFeedback.message("Could not open config folder.", Formatting.RED);
        }
    }

    private static void load(String name) {
        if (name != null && !ConfigManager.isValidConfigName(name)) {
            CommandFeedback.message("Invalid config name. Use letters, numbers, _ or -.", Formatting.YELLOW);
            return;
        }

        if (ConfigManager.load(name)) {
            CommandFeedback.message(configLabel(name) + " loaded.", Formatting.GREEN);
        } else {
            CommandFeedback.message(configLabel(name) + " was not found or could not be loaded.", Formatting.RED);
        }
    }

    private static void save(String name) {
        if (name != null && !ConfigManager.isValidConfigName(name)) {
            CommandFeedback.message("Invalid config name. Use letters, numbers, _ or -.", Formatting.YELLOW);
            return;
        }

        if (ConfigManager.save(name)) {
            CommandFeedback.message(configLabel(name) + " saved.", Formatting.GREEN);
        } else {
            CommandFeedback.message("Could not save " + configLabel(name).toLowerCase(Locale.ROOT) + ".", Formatting.RED);
        }
    }

    private static String configLabel(String name) {
        return name == null ? "Config" : "Config '" + name + "'";
    }
}
