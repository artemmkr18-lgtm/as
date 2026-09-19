package ez.minar.system.managers;

import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.commands.CommandFeedback;
import ez.minar.system.events.EventBus;
import ez.minar.system.events.EventManager;
import ez.minar.system.features.misc.Unhook;
import ez.minar.system.features.render.Waypoints;
import ez.minar.system.neuro.NeuroManager;
import ez.minar.utils.discord.DiscordRichPresence;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class UnhookManager {
    private static final long REHOOK_COMBO_MS = 500L;
    private static final int COMMAND_STRIP_INTERVAL_TICKS = 40;

    private static volatile boolean unhooked;
    private static final List<Function> previouslyEnabled = new ArrayList<>();
    private static boolean rehookKeyWasDown;
    private static long rehookKeyLastPress;
    private static int commandStripCounter;

    private UnhookManager() {
    }

    public static boolean isUnhooked() {
        return unhooked;
    }

    public static void unhook() {
        if (unhooked) {
            return;
        }
        unhooked = true;

        MinecraftClient mc = MinecraftClient.getInstance();

        previouslyEnabled.clear();
        for (Function function : FunctionManager.getFunctions()) {
            if (function.isEnabled() && !(function instanceof Unhook)) {
                previouslyEnabled.add(function);
            }
        }

        if (mc.currentScreen instanceof TitleScreen) {
            mc.setScreen(new TitleScreen());
        } else if (mc.currentScreen != null) {
            mc.setScreen(null);
        }

        EventBus.unregister(EventManager.getInstance());
        EventBus.unregister(Waypoints.Instance);
        NeuroManager.dropActiveSession();

        for (Function function : List.copyOf(FunctionManager.getFunctions())) {
            function.forceDisable();
        }

        if (mc.inGameHud != null) {
            ChatHud chatHud = mc.inGameHud.getChatHud();
            chatHud.clear(true);
        }

        rehookKeyWasDown = false;
        rehookKeyLastPress = 0L;
        commandStripCounter = 0;
        stripCommands(mc);

        Thread cleaner = new Thread(UnhookManager::wipeTraces, "Cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    public static void tick(MinecraftClient mc) {
        if (!unhooked) {
            return;
        }

        if (mc.getWindow() == null) {
            return;
        }

        boolean down = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (down && !rehookKeyWasDown) {
            long now = System.currentTimeMillis();
            if (now - rehookKeyLastPress <= REHOOK_COMBO_MS) {
                rehookKeyLastPress = 0L;
                rehook();
                return;
            }
            rehookKeyLastPress = now;
        }
        rehookKeyWasDown = down;

        if (++commandStripCounter >= COMMAND_STRIP_INTERVAL_TICKS) {
            commandStripCounter = 0;
            stripCommands(mc);
        }
    }

    public static synchronized void rehook() {
        if (!unhooked) {
            return;
        }
        unhooked = false;

        EventBus.register(EventManager.getInstance());
        EventBus.register(Waypoints.Instance);

        for (Function function : List.copyOf(previouslyEnabled)) {
            function.forceEnable();
        }
        previouslyEnabled.clear();

        ConfigManager.save();
        FriendManager.save();
        BlockEspManager.save();
        InventoryCleanerManager.save();
        WaypointManager.save();

        DiscordRichPresence.start();
        CommandFeedback.message("Client restored.", Formatting.GREEN);
    }

    private static void wipeTraces() {
        DiscordRichPresence.stop();

        Path gameDir = FabricLoader.getInstance().getGameDir();
        deleteRecursively(gameDir.resolve("Minar"));
        purgeCrashReports(gameDir.resolve("crash-reports"));
        scrubLog(gameDir.resolve("logs").resolve("latest.log"));
        scrubLog(gameDir.resolve("logs").resolve("debug.log"));
    }

    private static void stripCommands(MinecraftClient mc) {
        clearActiveDispatcher();

        if (mc.getNetworkHandler() != null) {
            removePrefixedNodes(mc.getNetworkHandler().getCommandDispatcher().getRoot(), ".");
        }
    }

    private static void clearActiveDispatcher() {
        try {
            Class<?> internals = Class.forName("net.fabricmc.fabric.impl.command.client.ClientCommandInternals");
            Field field = internals.getDeclaredField("activeDispatcher");
            field.setAccessible(true);

            CommandDispatcher<?> dispatcher = (CommandDispatcher<?>) field.get(null);
            if (dispatcher != null) {
                Object root = dispatcher.getRoot();
                clearNodeMaps(root);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void removePrefixedNodes(Object rootNode, String prefix) {
        try {
            Map<?, ?> children = nodeMap(rootNode, "children");
            if (children != null) {
                children.keySet().removeIf(name -> name instanceof String text && text.startsWith(prefix));
            }

            Map<?, ?> literals = nodeMap(rootNode, "literals");
            if (literals != null) {
                literals.keySet().removeIf(name -> name instanceof String text && text.startsWith(prefix));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearNodeMaps(Object rootNode) {
        try {
            Map<?, ?> children = nodeMap(rootNode, "children");
            if (children != null) {
                children.clear();
            }

            Map<?, ?> literals = nodeMap(rootNode, "literals");
            if (literals != null) {
                literals.clear();
            }

            Map<?, ?> arguments = nodeMap(rootNode, "arguments");
            if (arguments != null) {
                arguments.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static Map<?, ?> nodeMap(Object rootNode, String fieldName) throws ReflectiveOperationException {
        Field field = rootNode.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(rootNode);
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void purgeCrashReports(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    if (Files.readString(file).contains("ez.minar")) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void scrubLog(Path file) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (scrubAttempt(file)) {
                return;
            }

            try {
                Thread.sleep(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean scrubAttempt(Path file) {
        if (!Files.exists(file)) {
            return true;
        }

        try {
            List<String> lines = Files.readAllLines(file);
            List<String> cleaned = lines.stream()
                    .filter(line -> !line.toLowerCase(Locale.ROOT).contains("minar"))
                    .collect(Collectors.toList());

            if (cleaned.size() == lines.size()) {
                return true;
            }

            Files.write(file, cleaned, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
