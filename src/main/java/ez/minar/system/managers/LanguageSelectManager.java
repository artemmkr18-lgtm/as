package ez.minar.system.managers;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class LanguageSelectManager {
    private static final String FILE_NAME = "language.properties";
    private static final String KEY_SELECTED = "language_selected";
    private static final String KEY_CODE = "selected_code";

    private static Path configDirectory;
    private static Path configFile;
    private static boolean languageSelected = false;
    private static String selectedCode = "ru_ru";
    private static boolean promptedThisSession = false;

    private LanguageSelectManager() {
    }

    public static void init() {
        configDirectory = FabricLoader.getInstance().getGameDir().resolve("Minar");
        configFile = configDirectory.resolve(FILE_NAME);
        load();
        LocalizationManager.init();
        LocalizationManager.setLanguage(selectedCode);
    }

    public static boolean hasSelectedLanguage() {
        return languageSelected;
    }

    public static String getSelectedCode() {
        return selectedCode;
    }

    public static boolean hasPromptedThisSession() {
        return promptedThisSession;
    }

    public static void setPromptedThisSession(boolean prompted) {
        promptedThisSession = prompted;
    }

    public static void selectLanguage(String code) {
        languageSelected = true;
        selectedCode = code;
        save();
        LocalizationManager.setLanguage(code);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            String currentLang = mc.getLanguageManager() != null ? mc.getLanguageManager().getLanguage() : "";
            boolean langChanged = !code.equalsIgnoreCase(currentLang);

            mc.options.language = code;
            if (mc.getLanguageManager() != null) {
                mc.getLanguageManager().setLanguage(code);
            }
            mc.options.write();

            if (langChanged) {
                var reloadFuture = mc.reloadResources();
                if (reloadFuture != null) {
                    reloadFuture.thenRun(() -> mc.execute(() -> {
                        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) {
                            mc.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
                        }
                    }));
                }
            }
        }
    }

    public static boolean save() {
        try {
            if (configDirectory == null) {
                configDirectory = FabricLoader.getInstance().getGameDir().resolve("Minar");
                configFile = configDirectory.resolve(FILE_NAME);
            }
            Files.createDirectories(configDirectory);
            Properties props = new Properties();
            props.setProperty(KEY_SELECTED, String.valueOf(languageSelected));
            props.setProperty(KEY_CODE, selectedCode);
            try (var writer = Files.newBufferedWriter(configFile)) {
                props.store(writer, "Minar Language Selection Configuration");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean load() {
        try {
            if (configFile == null || !Files.exists(configFile)) {
                return false;
            }
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(configFile)) {
                props.load(reader);
            }
            String selectedStr = props.getProperty(KEY_SELECTED, "false");
            languageSelected = Boolean.parseBoolean(selectedStr);
            selectedCode = props.getProperty(KEY_CODE, "ru_ru");
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
