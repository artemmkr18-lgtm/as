package ez.minar.system.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import ez.minar.system.managers.ConfigManager;
import ez.minar.system.managers.BlockEspManager;
import ez.minar.system.managers.InventoryCleanerManager;
import ez.minar.system.managers.WaypointManager;
import ez.minar.system.neuro.NeuroManager;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public final class DotCommandSuggestions {
    private static final CommandDispatcher<Object> DISPATCHER = new CommandDispatcher<>();

    static {
        DISPATCHER.register(literal(".cfg")
                .then(literal("dir"))
                .then(literal("save")
                        .then(argument("name", StringArgumentType.word())))
                .then(literal("load")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(ConfigManager.getConfigNames(), builder))))
                .then(literal("reset")));

        DISPATCHER.register(literal(".friend")
                .then(literal("add")
                        .then(argument("nick", StringArgumentType.word())))
                .then(literal("remove")
                        .then(argument("nick", StringArgumentType.word())))
                .then(literal("clear")));

        DISPATCHER.register(literal(".parse"));
        DISPATCHER.register(literal(".bind")
                .then(literal("add")
                        .then(argument("module", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(BindCommand.moduleNames(), builder))
                                .then(argument("key", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(BindCommand.keySuggestions(), builder)))))
                .then(literal("remove")
                        .then(argument("module", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(BindCommand.moduleNames(), builder))))
                .then(literal("list")));
        DISPATCHER.register(literal(".blockesp")
                .then(literal("add")
                        .then(argument("block", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(BlockEspManager.blockSuggestions(), builder))))
                .then(literal("remove")
                        .then(argument("block", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        BlockEspManager.getIds().stream().map(Object::toString), builder))))
                .then(literal("list"))
                .then(literal("clear")));
        DISPATCHER.register(literal(".ic")
                .then(literal("add")
                        .then(argument("item", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(InventoryCleanerManager.itemSuggestions(), builder))))
                .then(literal("remove")
                        .then(argument("item", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        InventoryCleanerManager.getIds().stream().map(Object::toString), builder))))
                .then(literal("list"))
                .then(literal("clear")));
        DISPATCHER.register(literal(".way")
                .then(literal("add")
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("x", StringArgumentType.word())
                                        .then(argument("y", StringArgumentType.word())
                                                .then(argument("z", StringArgumentType.word()))))))
                .then(literal("remove")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(WaypointManager.names(), builder))))
                .then(literal("list"))
                .then(literal("clear")));
        DISPATCHER.register(literal(".neuro")
                .then(literal("status"))
                .then(literal("list"))
                .then(literal("load")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        CommandSource.suggestMatching(ez.minar.system.neuro.NeuroModel.listModels(), builder))))
                .then(literal("play")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        CommandSource.suggestMatching(ez.minar.system.neuro.NeuroModel.listModels(), builder))))
                .then(literal("record")
                        .then(argument("name", StringArgumentType.word())))
                .then(literal("stop"))
                .then(literal("train")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        CommandSource.suggestMatching(ez.minar.system.neuro.NeuroModel.listDatasets(), builder))
                                .then(argument("epochs", StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                CommandSource.suggestMatching(List.of("200", "400", "800"), builder)))))
                .then(literal("dir"))
                .then(literal("data"))
                .then(literal("cancel"))
                .then(literal("why")));
                DISPATCHER.register(literal(".fk")
                .then(literal("add")
                        .then(argument("name", StringArgumentType.word())))
                .then(literal("remove")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        CommandSource.suggestMatching(ez.minar.system.dummy.FakePlayerManager.getNames(), builder))))
                .then(literal("clear"))
                .then(literal("list")));
        DISPATCHER.register(literal(".calc"));
    }

    private DotCommandSuggestions() {
    }

    public static CompletableFuture<Suggestions> getSuggestions(String input, int cursor) {
        return DISPATCHER.getCompletionSuggestions(DISPATCHER.parse(input, new Object()), cursor);
    }
}
