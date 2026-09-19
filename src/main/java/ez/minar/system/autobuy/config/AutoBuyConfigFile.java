package ez.minar.system.autobuy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.originalitems.ItemRegistry;
import ez.minar.system.autobuy.settings.AutoBuySettingsManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class AutoBuyConfigFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File getConfigFile() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        File autoBuyDir = gameDir.resolve("Minar").resolve("AutoBuy").toFile();
        if (!autoBuyDir.exists()) {
            autoBuyDir.mkdirs();
        }
        return new File(autoBuyDir, "AutoBuyConfig.json");
    }

    public static void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("enabled_items")) {
                    JsonObject enabledItems = json.getAsJsonObject("enabled_items");
                    for (AutoBuyableItem item : ItemRegistry.getAllItems()) {
                        if (enabledItems.has(item.getDisplayName())) {
                            item.setEnabled(enabledItems.get(item.getDisplayName()).getAsBoolean());
                        }
                    }
                }

                if (json.has("settings")) {
                    JsonObject settings = json.getAsJsonObject("settings");
                    AutoBuySettingsManager.getInstance().loadFromJson(settings);
                    ItemRegistry.reloadSettings();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        File file = getConfigFile();
        JsonObject json = new JsonObject();

        JsonObject enabledItems = new JsonObject();
        for (AutoBuyableItem item : ItemRegistry.getAllItems()) {
            enabledItems.addProperty(item.getDisplayName(), item.isEnabled());
        }
        json.add("enabled_items", enabledItems);

        JsonObject settings = AutoBuySettingsManager.getInstance().saveToJson();
        json.add("settings", settings);

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
