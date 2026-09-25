package ez.minar.system.managers;

import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.features.render.HUD;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ButtonSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.MultiSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ConfigManager {
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final Properties DEFAULTS = new Properties();
    private static Path configDirectory;
    private static Path configFile;
    private static int autoSaveTicks;

    private ConfigManager() {
    }

    public static void init() {
        configDirectory = FabricLoader.getInstance().getGameDir().resolve("Minar");
        configFile = configDirectory.resolve(CONFIG_FILE_NAME);
        snapshotDefaults();
        load();
    }

    public static boolean save() {
        return save(null);
    }

    public static boolean save(String name) {
        if (UnhookManager.isUnhooked()) {
            return false;
        }

        ensureInitialized();
        Properties properties = new Properties();
        writeCurrentState(properties);

        try {
            Files.createDirectories(configDirectory);
            try (var writer = Files.newBufferedWriter(configPath(name))) {
                properties.store(writer, "Minar config");
            }
            ThemeManager.save();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean load() {
        return load(null);
    }

    public static boolean load(String name) {
        if (UnhookManager.isUnhooked()) {
            return false;
        }

        ensureInitialized();
        Path path = configPath(name);
        if (!Files.exists(path)) {
            if (name == null) {
                save();
            }
            return false;
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            apply(properties);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void tickAutoSave() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        if (++autoSaveTicks >= 100) {
            autoSaveTicks = 0;
            save();
        }
    }

    public static void reset() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        ensureInitialized();
        apply(DEFAULTS);
        save();
    }

    public static boolean openDirectory() {
        ensureInitialized();
        try {
            Files.createDirectories(configDirectory);
            Util.getOperatingSystem().open(configDirectory);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isValidConfigName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public static List<String> getConfigNames() {
        ensureInitialized();
        try {
            Files.createDirectories(configDirectory);
            try (var stream = Files.list(configDirectory)) {
                return stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".properties"))
                        .map(path -> path.getFileName().toString())
                        .filter(fileName -> !"themes.properties".equalsIgnoreCase(fileName))
                        .map(fileName -> fileName.substring(0, fileName.length() - ".properties".length()))
                        .filter(ConfigManager::isValidConfigName)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static void snapshotDefaults() {
        DEFAULTS.clear();
        writeCurrentState(DEFAULTS);
        DEFAULTS.setProperty("theme.selected", colorToHex(ThemeManager.PASTEL_RED));
        DEFAULTS.setProperty("theme.selected2", colorToHex(ThemeManager.PASTEL_BLUE));
        DEFAULTS.setProperty("theme.twoColors", "false");
        DEFAULTS.setProperty("theme.custom.count", "0");
        DEFAULTS.setProperty("friends.count", "0");
    }

    private static void writeCurrentState(Properties properties) {
        properties.setProperty("hud.schema", "2");
        properties.setProperty("theme.selected", colorToHex(ThemeManager.Theme_Color));
        properties.setProperty("theme.selected2", colorToHex(ThemeManager.Theme_Color2));
        properties.setProperty("theme.twoColors", String.valueOf(ThemeManager.twoColors));
        properties.setProperty("theme.custom.count", String.valueOf(ThemeManager.CUSTOM_THEMES.size()));
        for (int i = 0; i < ThemeManager.CUSTOM_THEMES.size(); i++) {
            properties.setProperty("theme.custom." + i, colorToHex(ThemeManager.CUSTOM_THEMES.get(i)));
        }

        HUD hud = FunctionManager.getFunction(HUD.class);
        if (hud != null) {
            Map<String, Float> positions = hud.getElementPositions();
            positions.forEach((key, value) ->
                    properties.setProperty("hud.position." + key, String.valueOf(value)));
        }

        for (Function function : FunctionManager.getFunctions()) {
            String functionKey = functionKey(function);
            properties.setProperty(functionKey + ".enabled", String.valueOf(function.isEnabled()));
            properties.setProperty(functionKey + ".keybind", String.valueOf(function.getKeybind()));

            for (Setting setting : function.getSettings()) {
                String settingKey = functionKey + ".setting." + settingKey(setting);
                writeSetting(properties, settingKey, setting);
            }
        }
    }

    private static void apply(Properties properties) {
        migrateHudSettings(properties);
        ThemeManager.setThemeColor(parseColor(properties.getProperty("theme.selected"), ThemeManager.PASTEL_RED));
        ThemeManager.setThemeColor2(parseColor(properties.getProperty("theme.selected2"), ThemeManager.PASTEL_BLUE));
        ThemeManager.twoColors = Boolean.parseBoolean(properties.getProperty("theme.twoColors", "false"));
        ThemeManager.CUSTOM_THEMES.clear();
        int themeCount = parseInt(properties.getProperty("theme.custom.count"), 0);
        for (int i = 0; i < themeCount; i++) {
            ThemeManager.CUSTOM_THEMES.add(parseColor(properties.getProperty("theme.custom." + i), ThemeManager.PASTEL_RED));
        }
        ThemeManager.save();

        int friendCount = parseInt(properties.getProperty("friends.count"), 0);
        Map<String, String> legacyFriends = new LinkedHashMap<>();
        for (int i = 0; i < friendCount; i++) {
            String friend = properties.getProperty("friends." + i);
            if (friend != null && friend.matches("[A-Za-z0-9_]{1,16}")) {
                legacyFriends.put(friend.toLowerCase(), friend);
            }
        }
        FriendManager.migrateLegacyFriends(legacyFriends);

        HUD hud = FunctionManager.getFunction(HUD.class);
        if (hud != null) {
            hud.applyElementPositions(properties, ConfigManager::parseFloat);
        }

        for (Function function : FunctionManager.getFunctions()) {
            String functionKey = functionKey(function);
            function.setKeybind(parseInt(properties.getProperty(functionKey + ".keybind"), 0));

            for (Setting setting : function.getSettings()) {
                String settingKey = functionKey + ".setting." + settingKey(setting);
                readSetting(properties, settingKey, setting);
            }

            boolean enabled = Boolean.parseBoolean(properties.getProperty(functionKey + ".enabled", "false"));
            if (function.isEnabled() != enabled) {
                function.toggle();
            }
        }
    }

    private static void migrateHudSettings(Properties properties) {
        if (properties.containsKey("hud.schema")) return;
        String oldPrefix = "function.Watermark.setting.";
        String newPrefix = "function.HUD.setting.Ватермарка:_";
        for (String key : Set.copyOf(properties.stringPropertyNames())) {
            if (key.startsWith(oldPrefix)) {
                properties.putIfAbsent(newPrefix + key.substring(oldPrefix.length()), properties.getProperty(key));
            }
        }
        if (Boolean.parseBoolean(properties.getProperty("function.Watermark.enabled", "false"))) {
            properties.setProperty("function.HUD.enabled", "true");
        }
        properties.setProperty("hud.schema", "2");
    }

    private static void writeSetting(Properties properties, String key, Setting setting) {
        if (setting instanceof BooleanSetting booleanSetting) {
            properties.setProperty(key, String.valueOf(booleanSetting.isEnabled()));
        } else if (setting instanceof ButtonSetting buttonSetting) {
            properties.setProperty(key, buttonSetting.getStoredValue());
        } else if (setting instanceof NumberSetting numberSetting) {
            properties.setProperty(key, String.valueOf(numberSetting.getValue()));
        } else if (setting instanceof ModeSetting modeSetting) {
            properties.setProperty(key, modeSetting.getActiveMode());
        } else if (setting instanceof MultiSetting multiSetting) {
            properties.setProperty(key, String.join(",", multiSetting.getEnabled()));
        } else if (setting instanceof KeybindSetting keybindSetting) {
            properties.setProperty(key, String.valueOf(keybindSetting.getKeybind()));
        } else if (setting instanceof ColorSetting colorSetting) {
            properties.setProperty(key, colorToHex(colorSetting.getColor()));
        } else if (setting instanceof TextSetting textSetting) {
            properties.setProperty(key, textSetting.getValue());
        }
    }

    private static void readSetting(Properties properties, String key, Setting setting) {
        String value = properties.getProperty(key);
        if (value == null && setting instanceof ModeSetting && key.endsWith(".setting.\u0421\u0442\u0438\u043b\u044c_HUD")) {
            value = legacyHudStyle(properties, key);
        }
        if (value == null) {
            return;
        }

        if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.setEnabled(Boolean.parseBoolean(value));
        } else if (setting instanceof ButtonSetting buttonSetting) {
            buttonSetting.setStoredValue(value);
        } else if (setting instanceof NumberSetting numberSetting) {
            numberSetting.setValue(parseDouble(value, numberSetting.getValue()));
        } else if (setting instanceof ModeSetting modeSetting) {
            modeSetting.setMode(value);
        } else if (setting instanceof MultiSetting multiSetting) {
            multiSetting.setEnabledOptions(value.isEmpty() ? Set.of() : java.util.Arrays.stream(value.split(",")).map(String::trim).collect(java.util.stream.Collectors.toSet()));
        } else if (setting instanceof KeybindSetting keybindSetting) {
            keybindSetting.setKeybind(parseInt(value, keybindSetting.getKeybind()));
        } else if (setting instanceof ColorSetting colorSetting) {
            colorSetting.setColor(parseColor(value, colorSetting.getColor()));
        } else if (setting instanceof TextSetting textSetting) {
            textSetting.setValue(value);
        }
    }

    private static void ensureInitialized() {
        if (configDirectory == null || configFile == null) {
            configDirectory = FabricLoader.getInstance().getGameDir().resolve("Minar");
            configFile = configDirectory.resolve(CONFIG_FILE_NAME);
        }
    }

    private static Path configPath(String name) {
        return name == null ? configFile : configDirectory.resolve(name + ".properties");
    }

    private static String functionKey(Function function) {
        return "function." + safeKey(function.getName());
    }

    private static String settingKey(Setting setting) {
        return safeKey(setting.getName());
    }

    private static String safeKey(String value) {
        return value.replace(".", "_").replace(" ", "_");
    }

    private static String legacyHudStyle(Properties properties, String key) {
        String prefix = key.substring(0, key.length() - ".setting.\u0421\u0442\u0438\u043b\u044c_HUD".length());
        String settingPrefix = prefix + ".setting.";
        if (Boolean.parseBoolean(properties.getProperty(settingPrefix + "Watermark_\u0416\u0438\u0434\u043a\u043e\u0435_\u0441\u0442\u0435\u043a\u043b\u043e"))
                || Boolean.parseBoolean(properties.getProperty(settingPrefix + "Keybinds_\u0416\u0438\u0434\u043a\u043e\u0435_\u0441\u0442\u0435\u043a\u043b\u043e"))
                || Boolean.parseBoolean(properties.getProperty(settingPrefix + "Potions_\u0416\u0438\u0434\u043a\u043e\u0435_\u0441\u0442\u0435\u043a\u043b\u043e"))
                || Boolean.parseBoolean(properties.getProperty(settingPrefix + "TargetHUD_\u0416\u0438\u0434\u043a\u043e\u0435_\u0441\u0442\u0435\u043a\u043b\u043e"))
                || Boolean.parseBoolean(properties.getProperty(settingPrefix + "Hotbar_\u0416\u0438\u0434\u043a\u043e\u0435_\u0441\u0442\u0435\u043a\u043b\u043e"))) {
            return "\u0416\u0438\u0434\u043a\u043e\u0435 \u0441\u0442\u0435\u043a\u043b\u043e";
        }
        return null;
    }

    private static String colorToHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color parseColor(String value, Color fallback) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') {
            return fallback;
        }

        try {
            return new Color(Integer.parseInt(value.substring(1), 16));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }
}
