package ez.minar.system.commands;

import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.utils.helpers.KeyHelper;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BindCommand {
    private static final List<String> KEY_SUGGESTIONS = List.of(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "SPACE", "TAB", "CAPS", "LSHIFT", "RSHIFT", "LCTRL", "RCTRL",
            "LALT", "RALT", "UP", "DOWN", "LEFT", "RIGHT",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "MOUSE3", "MOUSE4", "MOUSE5"
    );

    private BindCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String commandLine = input.trim();
        if (!commandLine.equalsIgnoreCase(".bind")
                && !commandLine.toLowerCase(Locale.ROOT).startsWith(".bind ")) {
            return false;
        }

        String[] arguments = commandLine.split("\\s+");
        if (arguments.length < 2) {
            showUsage();
            return true;
        }

        switch (arguments[1].toLowerCase(Locale.ROOT)) {
            case "add" -> add(arguments);
            case "remove" -> remove(arguments);
            case "list" -> list(arguments);
            default -> showUsage();
        }
        return true;
    }

    public static List<String> moduleNames() {
        return FunctionManager.getFunctions().stream()
                .map(Function::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> keySuggestions() {
        return KEY_SUGGESTIONS;
    }

    public static List<String> suggestionsFor(String input) {
        String commandLine = input.stripLeading();
        String lower = commandLine.toLowerCase(Locale.ROOT);
        if (".".equals(lower) || (lower.startsWith(".") && ".bind".startsWith(lower))) {
            return List.of(".bind");
        }
        if (!lower.startsWith(".bind ")) return List.of();

        String tail = commandLine.substring(".bind ".length());
        String[] parts = tail.split("\\s+", -1);
        if (parts.length == 1) {
            return List.of(".bind add", ".bind remove", ".bind list").stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                    .toList();
        }

        String action = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length == 2 && ("add".equals(action) || "remove".equals(action))) {
            String modulePrefix = parts[1].toLowerCase(Locale.ROOT);
            return moduleNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(modulePrefix))
                    .map(name -> ".bind " + action + " " + name + ("add".equals(action) ? " " : ""))
                    .toList();
        }
        if (parts.length == 3 && "add".equals(action)) {
            String keyPrefix = parts[2].toLowerCase(Locale.ROOT);
            return KEY_SUGGESTIONS.stream()
                    .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(keyPrefix))
                    .map(key -> ".bind add " + parts[1] + " " + key)
                    .toList();
        }
        return List.of();
    }

    private static void add(String[] arguments) {
        if (arguments.length != 4) {
            message("Usage: .bind add <module> <key>", Formatting.YELLOW);
            return;
        }
        Function function = findModule(arguments[2]);
        if (function == null) {
            message("Module '" + arguments[2] + "' not found.", Formatting.RED);
            return;
        }
        int key = parseKey(arguments[3]);
        if (key <= 0) {
            message("Unknown key '" + arguments[3] + "'.", Formatting.RED);
            return;
        }
        function.setKeybind(key);
        message(function.getName() + " bound to " + KeyHelper.getKeyName(key) + ".", Formatting.GREEN);
    }

    private static void remove(String[] arguments) {
        if (arguments.length != 3) {
            message("Usage: .bind remove <module>", Formatting.YELLOW);
            return;
        }
        Function function = findModule(arguments[2]);
        if (function == null) {
            message("Module '" + arguments[2] + "' not found.", Formatting.RED);
            return;
        }
        function.setKeybind(0);
        message("Bind for " + function.getName() + " removed.", Formatting.GREEN);
    }

    private static void list(String[] arguments) {
        if (arguments.length != 2) {
            message("Usage: .bind list", Formatting.YELLOW);
            return;
        }
        List<Function> bound = new ArrayList<>(FunctionManager.getFunctions().stream()
                .filter(function -> function.getKeybind() > 0)
                .toList());
        bound.sort(Comparator.comparing(Function::getName, String.CASE_INSENSITIVE_ORDER));
        if (bound.isEmpty()) {
            message("No active binds.", Formatting.GRAY);
            return;
        }
        message("Binds (" + bound.size() + "):", Formatting.GRAY);
        for (Function function : bound) {
            message(function.getName() + " -> " + KeyHelper.getKeyName(function.getKeybind()), Formatting.WHITE);
        }
    }

    private static Function findModule(String name) {
        return FunctionManager.getFunctions().stream()
                .filter(function -> function.getName().equalsIgnoreCase(name)
                        || function.getDisplayName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static int parseKey(String value) {
        String key = value.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "").replace(".", "");
        if (key.length() == 1) {
            char character = key.charAt(0);
            if (character >= 'A' && character <= 'Z') return GLFW.GLFW_KEY_A + character - 'A';
            if (character >= '0' && character <= '9') return GLFW.GLFW_KEY_0 + character - '0';
        }
        if (key.matches("F([1-9]|1[0-2])")) {
            return GLFW.GLFW_KEY_F1 + Integer.parseInt(key.substring(1)) - 1;
        }
        return switch (key) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "CAPS", "CAPSLOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "SHIFT", "LSHIFT", "LEFTSHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RSHIFT", "RIGHTSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "CTRL", "CONTROL", "LCTRL", "LEFTCTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RCTRL", "RIGHTCTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "ALT", "LALT", "LEFTALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "RALT", "RIGHTALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "MOUSE3", "MMOUSE", "MIDDLE" -> KeybindSetting.toMouseKey(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            case "MOUSE4" -> KeybindSetting.toMouseKey(GLFW.GLFW_MOUSE_BUTTON_4);
            case "MOUSE5" -> KeybindSetting.toMouseKey(GLFW.GLFW_MOUSE_BUTTON_5);
            default -> 0;
        };
    }

    private static void showUsage() {
        message(".bind add <module> <key> - add bind", Formatting.GRAY);
        message(".bind remove <module> - remove bind", Formatting.GRAY);
        message(".bind list - show active binds", Formatting.GRAY);
    }

    private static void message(String text, Formatting color) {
        CommandFeedback.message(text, color);
    }
}