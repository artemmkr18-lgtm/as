package ez.minar.system.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalizationManager {
    private static final Map<String, Map<String, String>> TRANSLATIONS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static String currentLanguage = "ru_ru";
    private static volatile boolean initialized = false;

    private LocalizationManager() {}

    public static synchronized void init() {
        if (initialized) return;

        loadLanguage("ru_ru");
        loadLanguage("en_us");
        loadLanguage("pl_pl");

        String code = LanguageSelectManager.getSelectedCode();
        if (code != null && !code.isEmpty()) {
            setLanguage(code);
        } else {
            setLanguage("ru_ru");
        }
        initialized = true;
    }

    private static void loadLanguage(String langCode) {
        String normalized = normalize(langCode);
        String relPath = "assets/minar/lang/" + normalized + ".json";

        // 1. Try class resource
        try (InputStream is = LocalizationManager.class.getResourceAsStream("/" + relPath)) {
            if (is != null) {
                parseAndStore(normalized, is);
                return;
            }
        } catch (Throwable ignored) {}

        // 2. Try class loader resource
        try (InputStream is = LocalizationManager.class.getClassLoader().getResourceAsStream(relPath)) {
            if (is != null) {
                parseAndStore(normalized, is);
                return;
            }
        } catch (Throwable ignored) {}

        // 3. Try filesystem if in development
        try {
            Path devPath = Path.of("src/main/resources").resolve(relPath);
            if (Files.exists(devPath)) {
                try (InputStream is = Files.newInputStream(devPath)) {
                    parseAndStore(normalized, is);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 4. Try game directory
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir().resolve(relPath);
            if (Files.exists(gameDir)) {
                try (InputStream is = Files.newInputStream(gameDir)) {
                    parseAndStore(normalized, is);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void parseAndStore(String langCode, InputStream is) {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
            if (map != null && !map.isEmpty()) {
                TRANSLATIONS.computeIfAbsent(langCode, k -> new ConcurrentHashMap<>()).putAll(map);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static String getLanguage() {
        if (!initialized) {
            init();
        }
        return currentLanguage;
    }

    public static void setLanguage(String code) {
        currentLanguage = normalize(code);
        if (!TRANSLATIONS.containsKey(currentLanguage)) {
            loadLanguage(currentLanguage);
        }
    }

    public static String normalize(String code) {
        if (code == null || code.isEmpty()) return "ru_ru";
        String lower = code.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("ru")) return "ru_ru";
        if (lower.startsWith("en")) return "en_us";
        if (lower.startsWith("pl")) return "pl_pl";
        return lower;
    }

    public static boolean isRussian() {
        return getLanguage().startsWith("ru");
    }

    public static boolean isEnglish() {
        return getLanguage().startsWith("en");
    }

    public static boolean isPolish() {
        return getLanguage().startsWith("pl");
    }

    public static String get(String keyOrText) {
        if (keyOrText == null) return "";
        if (!initialized) {
            init();
        }

        String lang = getLanguage();
        Map<String, String> dict = TRANSLATIONS.get(lang);
        if (dict != null) {
            String val = dict.get(keyOrText);
            if (val != null && !val.isEmpty()) {
                return val;
            }
        }

        // Fallback to en_us if not in current language
        if (!lang.equals("en_us")) {
            Map<String, String> enDict = TRANSLATIONS.get("en_us");
            if (enDict != null) {
                String val = enDict.get(keyOrText);
                if (val != null && !val.isEmpty()) {
                    return val;
                }
            }
        }

        return keyOrText;
    }

    public static String get(String keyOrText, Object... args) {
        String text = get(keyOrText);
        try {
            return String.format(Locale.ROOT, text, args);
        } catch (Exception e) {
            return text;
        }
    }
}
