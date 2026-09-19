package ez.minar.system.managers;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FriendManager {
    private static final String FILE_NAME = "friends.txt";
    private static final Map<String, String> FRIENDS = new LinkedHashMap<>();
    private static Path friendsFile;
    private static boolean legacyMigrationNeeded;

    private FriendManager() {
    }

    public static void init() {
        friendsFile = FabricLoader.getInstance().getGameDir().resolve("Minar").resolve(FILE_NAME);
        legacyMigrationNeeded = !Files.exists(friendsFile);
        load();
    }

    public static boolean add(String name) {
        boolean added = FRIENDS.putIfAbsent(normalize(name), name) == null;
        if (added) {
            legacyMigrationNeeded = false;
            save();
        }
        return added;
    }

    public static boolean remove(String name) {
        boolean removed = FRIENDS.remove(normalize(name)) != null;
        if (removed) {
            legacyMigrationNeeded = false;
            save();
        }
        return removed;
    }

    public static int clear() {
        int size = FRIENDS.size();
        FRIENDS.clear();
        legacyMigrationNeeded = false;
        save();
        return size;
    }

    public static Map<String, String> getFriends() {
        return new LinkedHashMap<>(FRIENDS);
    }

    public static void setFriends(Map<String, String> friends) {
        FRIENDS.clear();
        FRIENDS.putAll(friends);
    }

    public static void migrateLegacyFriends(Map<String, String> friends) {
        if (!legacyMigrationNeeded) {
            return;
        }
        setFriends(friends);
        save();
        legacyMigrationNeeded = false;
    }

    public static boolean isFriend(String name) {
        return name != null && FRIENDS.containsKey(normalize(name));
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void load() {
        FRIENDS.clear();
        if (!Files.exists(friendsFile)) {
            save();
            return;
        }

        try {
            for (String line : Files.readAllLines(friendsFile)) {
                String name = line.trim();
                if (name.matches("[A-Za-z0-9_]{1,16}")) {
                    FRIENDS.putIfAbsent(normalize(name), name);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean save() {
        if (UnhookManager.isUnhooked()) {
            return false;
        }

        if (friendsFile == null) {
            friendsFile = FabricLoader.getInstance().getGameDir().resolve("Minar").resolve(FILE_NAME);
        }
        try {
            Files.createDirectories(friendsFile.getParent());
            List<String> names = List.copyOf(FRIENDS.values());
            Files.write(friendsFile, names);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
