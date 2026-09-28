package ez.minar.system.menu;

import ez.minar.system.managers.UnhookManager;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ThemeManager {
    public static final Color DEFAULT_ACCENT = new Color(10, 245, 175);
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_NAMED_THEMES = 128;
    private static final int MAX_CUSTOM_THEMES = 128;
    public static Color Theme_Color = DEFAULT_ACCENT;
    public static Color Theme_Color2 = DEFAULT_ACCENT;
    public static boolean twoColors = false;

    public static final Color PASTEL_RED = new Color(168, 85, 247);
    public static final Color PASTEL_BLUE = new Color(192, 132, 252);
    public static final List<Color> CUSTOM_THEMES = new ArrayList<>();

    /** Named theme created in the GUI: name + accent color, persisted in themes.properties. */
    public static class NamedTheme {
        public final String name;
        public final Color color;

        public NamedTheme(String name, Color color) {
            this.name = name == null ? "" : name;
            this.color = withoutAlpha(color);
        }
    }

    public static final List<NamedTheme> NAMED_THEMES = new ArrayList<>();
    public static int activeNamedTheme = -1; // -1 = default pink accent
    private static Path themeFile;

    private ThemeManager() {
    }

    public static void init() {
        Path minarDir = FabricLoader.getInstance().getGameDir().resolve("Minar");
        themeFile = minarDir.resolve("themes.properties");

        try {
            Files.createDirectories(minarDir);
            load();
        } catch (IOException ignored) {
        }
    }

    public static void setThemeColor(Color color) {
        Theme_Color = withoutAlpha(color);
        save();
    }

    public static void setThemeColor2(Color color) {
        Theme_Color2 = withoutAlpha(color);
        save();
    }

    public static Color getThemeColor() {
        if (!twoColors) {
            return Theme_Color;
        }
        float time = (System.currentTimeMillis() % 2000L) / 2000f;
        float t = (float) (Math.sin(time * Math.PI * 2) * 0.5f + 0.5f);
        int r = (int) (Theme_Color.getRed() + (Theme_Color2.getRed() - Theme_Color.getRed()) * t);
        int g = (int) (Theme_Color.getGreen() + (Theme_Color2.getGreen() - Theme_Color.getGreen()) * t);
        int b = (int) (Theme_Color.getBlue() + (Theme_Color2.getBlue() - Theme_Color.getBlue()) * t);
        return new Color(r, g, b);
    }

    /** Accent the ClickGUI should show: the active named theme, or the Nexoria pink default. */
    public static Color getGuiAccent() {
        if (activeNamedTheme >= 0 && activeNamedTheme < NAMED_THEMES.size()) {
            return NAMED_THEMES.get(activeNamedTheme).color;
        }
        return DEFAULT_ACCENT;
    }

    /** True when a valid non-duplicate theme can be added with this name. */
    public static boolean canAddNamedTheme(String name) {
        if (name == null) return false;
        String trimmed = name.strip();
        return !trimmed.isEmpty() && trimmed.length() <= MAX_NAME_LENGTH
                && trimmed.codePoints().noneMatch(Character::isISOControl)
                && NAMED_THEMES.size() < MAX_NAMED_THEMES
                && NAMED_THEMES.stream().noneMatch(t -> t.name.equalsIgnoreCase(trimmed));
    }

    public static void addNamedTheme(String name, Color color) {
        if (!canAddNamedTheme(name)) return;
        NAMED_THEMES.add(new NamedTheme(name.strip(), color));
        applyNamedTheme(NAMED_THEMES.size() - 1);
    }

    public static void applyNamedTheme(int index) {
        if (index < -1 || index >= NAMED_THEMES.size()) return;
        activeNamedTheme = index;
        if (index >= 0) {
            Theme_Color = withoutAlpha(NAMED_THEMES.get(index).color);
            save();
        } else {
            applyDefault();
        }
    }

    /** Resets the accent to the built-in default and turns two-color cycling off. */
    private static void applyDefault() {
        twoColors = false;
        Theme_Color = DEFAULT_ACCENT;
        save();
    }

    public static void removeNamedTheme(int index) {
        if (index < 0 || index >= NAMED_THEMES.size()) return;
        NAMED_THEMES.remove(index);
        if (activeNamedTheme == index) {
            activeNamedTheme = -1;
            Theme_Color = DEFAULT_ACCENT;
            twoColors = false;
        } else if (activeNamedTheme > index) {
            activeNamedTheme--;
        }
        save();
    }

    public static int addCustomTheme(Color color) {
        if (CUSTOM_THEMES.size() >= MAX_CUSTOM_THEMES) return -1;
        CUSTOM_THEMES.add(withoutAlpha(color));
        save();
        return CUSTOM_THEMES.size() - 1;
    }

    public static void setCustomTheme(int index, Color color) {
        if (index < 0 || index >= CUSTOM_THEMES.size()) {
            return;
        }

        CUSTOM_THEMES.set(index, withoutAlpha(color));
        save();
    }

    public static void removeCustomTheme(int index) {
        if (index < 0 || index >= CUSTOM_THEMES.size()) {
            return;
        }

        Color removed = CUSTOM_THEMES.remove(index);
        if (sameColor(removed, Theme_Color)) {
            Theme_Color = CUSTOM_THEMES.isEmpty() ? PASTEL_RED : CUSTOM_THEMES.get(Math.min(index, CUSTOM_THEMES.size() - 1));
        }
        save();
    }

    public static void save() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        if (themeFile == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("selected", colorToHex(Theme_Color));
        properties.setProperty("selected2", colorToHex(Theme_Color2));
        properties.setProperty("twoColors", String.valueOf(twoColors));
        properties.setProperty("custom.count", String.valueOf(CUSTOM_THEMES.size()));
        for (int i = 0; i < CUSTOM_THEMES.size(); i++) {
            properties.setProperty("custom." + i, colorToHex(CUSTOM_THEMES.get(i)));
        }
        properties.setProperty("named.count", String.valueOf(NAMED_THEMES.size()));
        for (int i = 0; i < NAMED_THEMES.size(); i++) {
            NamedTheme t = NAMED_THEMES.get(i);
            properties.setProperty("named." + i + ".name", t.name == null ? "" : t.name);
            properties.setProperty("named." + i + ".color", colorToHex(t.color));
        }
        properties.setProperty("named.active", String.valueOf(activeNamedTheme));

        try (var writer = Files.newBufferedWriter(themeFile)) {
            properties.store(writer, "Minar themes");
        } catch (IOException ignored) {
        }
    }

    private static void load() throws IOException {
        if (!Files.exists(themeFile)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(themeFile)) {
            properties.load(reader);
        }

        Theme_Color = parseColor(properties.getProperty("selected"), DEFAULT_ACCENT);
        Theme_Color2 = parseColor(properties.getProperty("selected2"), DEFAULT_ACCENT);
        twoColors = Boolean.parseBoolean(properties.getProperty("twoColors", "false"));
        CUSTOM_THEMES.clear();
        int count = Math.clamp(parseInt(properties.getProperty("custom.count"), 0), 0, 4096);
        for (int i = 0; i < count; i++) {
            CUSTOM_THEMES.add(parseColor(properties.getProperty("custom." + i), PASTEL_RED));
        }

        NAMED_THEMES.clear();
        // Keep legacy collections above the creation limit; cap corrupt file counts.
        int namedCount = Math.clamp(parseInt(properties.getProperty("named.count"), 0), 0, 4096);
        for (int i = 0; i < namedCount; i++) {
            String name = properties.getProperty("named." + i + ".name", "");
            Color color = parseColor(properties.getProperty("named." + i + ".color"), DEFAULT_ACCENT);
            NAMED_THEMES.add(new NamedTheme(name, color));
        }
        activeNamedTheme = parseInt(properties.getProperty("named.active"), -1);
        if (activeNamedTheme < -1 || activeNamedTheme >= NAMED_THEMES.size()) activeNamedTheme = -1;
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

    private static String colorToHex(Color color) {
        if (color == null) color = DEFAULT_ACCENT;
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color withoutAlpha(Color color) {
        if (color == null) color = DEFAULT_ACCENT;
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static boolean sameColor(Color first, Color second) {
        return first.getRed() == second.getRed() && first.getGreen() == second.getGreen() && first.getBlue() == second.getBlue();
    }
}
