package ez.minar.system.managers;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlockEspManager {
    private static final String FILE_NAME = "blockesp.txt";
    private static final Set<Identifier> BLOCKS = new LinkedHashSet<>();
    private static Path file;

    private BlockEspManager() {
    }

    public static void init() {
        file = FabricLoader.getInstance().getGameDir().resolve("Minar").resolve(FILE_NAME);
        load();
    }

    public static Identifier resolve(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        return id != null && Registries.BLOCK.containsId(id) ? id : null;
    }

    public static boolean add(Identifier id) {
        boolean changed = BLOCKS.add(id);
        if (changed) save();
        return changed;
    }

    public static boolean remove(Identifier id) {
        boolean changed = BLOCKS.remove(id);
        if (changed) save();
        return changed;
    }

    public static int clear() {
        int count = BLOCKS.size();
        BLOCKS.clear();
        save();
        return count;
    }

    public static Set<Identifier> getIds() {
        return Set.copyOf(BLOCKS);
    }

    public static Set<Block> getBlocks() {
        Set<Block> result = new LinkedHashSet<>();
        for (Identifier id : BLOCKS) {
            result.add(Registries.BLOCK.get(id));
        }
        return result;
    }

    public static List<String> blockSuggestions() {
        return Registries.BLOCK.getIds().stream()
                .map(id -> "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString())
                .sorted()
                .toList();
    }

    public static void save() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        if (file == null) {
            file = FabricLoader.getInstance().getGameDir().resolve("Minar").resolve(FILE_NAME);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, BLOCKS.stream().map(Identifier::toString).toList());
        } catch (IOException ignored) {
        }
    }

    private static void load() {
        BLOCKS.clear();
        if (!Files.exists(file)) {
            save();
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                Identifier id = resolve(line.trim());
                if (id != null) BLOCKS.add(id);
            }
        } catch (IOException ignored) {
        }
    }
}
