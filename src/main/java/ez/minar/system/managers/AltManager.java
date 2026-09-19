package ez.minar.system.managers;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AltManager {
    public static class AltAccount {
        public String name;
        public boolean isFavorite;

        public AltAccount(String name, boolean isFavorite) {
            this.name = name;
            this.isFavorite = isFavorite;
        }
    }

    private static final List<AltAccount> alts = new ArrayList<>();
    private static Path configDirectory;
    private static Path altsFile;

    public static void init() {
        configDirectory = FabricLoader.getInstance().getGameDir().resolve("Minar");
        altsFile = configDirectory.resolve("alts.txt");
        load();
    }

    public static List<AltAccount> getAlts() {
        return new ArrayList<>(alts);
    }

    public static void addAlt(String name) {
        if (alts.stream().noneMatch(a -> a.name.equalsIgnoreCase(name))) {
            alts.add(new AltAccount(name, false));
            sortAndSave();
        }
    }

    public static void removeAlt(String name) {
        if (alts.removeIf(a -> a.name.equalsIgnoreCase(name))) {
            sortAndSave();
        }
    }

    public static void toggleFavorite(String name) {
        for (AltAccount alt : alts) {
            if (alt.name.equalsIgnoreCase(name)) {
                alt.isFavorite = !alt.isFavorite;
                sortAndSave();
                break;
            }
        }
    }
    
    public static void renameAlt(String oldName, String newName) {
        for (AltAccount alt : alts) {
            if (alt.name.equalsIgnoreCase(oldName)) {
                alt.name = newName;
                sortAndSave();
                break;
            }
        }
    }

    private static void sortAndSave() {
        alts.sort((a, b) -> {
            if (a.isFavorite && !b.isFavorite) return -1;
            if (!a.isFavorite && b.isFavorite) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        save();
    }

    public static void load() {
        if (!Files.exists(altsFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(altsFile);
            alts.clear();
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    if (line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        alts.add(new AltAccount(parts[0], Boolean.parseBoolean(parts[1])));
                    } else {
                        alts.add(new AltAccount(line, false));
                    }
                }
            }
            alts.sort((a, b) -> {
                if (a.isFavorite && !b.isFavorite) return -1;
                if (!a.isFavorite && b.isFavorite) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        try {
            Files.createDirectories(configDirectory);
            List<String> lines = new ArrayList<>();
            for (AltAccount alt : alts) {
                lines.add(alt.name + ":" + alt.isFavorite);
            }
            Files.write(altsFile, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
