package ez.minar.system.managers;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WaypointManager {

    public record Waypoint(String name, double x, double y, double z) {
    }

    private static final String FILE_NAME = "waypoints.txt";
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static Path file;

    private WaypointManager() {
    }

    public static void init() {
        file = FabricLoader.getInstance().getGameDir().resolve("Minar").resolve(FILE_NAME);
        load();
    }

    public static boolean add(String name, double x, double y, double z) {
        WAYPOINTS.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name));
        WAYPOINTS.add(new Waypoint(name, x, y, z));
        save();
        return true;
    }

    public static boolean remove(String name) {
        boolean changed = WAYPOINTS.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name));
        if (changed) save();
        return changed;
    }

    public static int clear() {
        int count = WAYPOINTS.size();
        WAYPOINTS.clear();
        save();
        return count;
    }

    public static List<Waypoint> getWaypoints() {
        return List.copyOf(WAYPOINTS);
    }

    public static List<String> names() {
        return WAYPOINTS.stream().map(Waypoint::name).toList();
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
            List<String> lines = new ArrayList<>();
            for (Waypoint waypoint : WAYPOINTS) {
                lines.add(waypoint.x() + ";" + waypoint.y() + ";" + waypoint.z() + ";" + waypoint.name());
            }
            Files.write(file, lines);
        } catch (IOException ignored) {
        }
    }

    private static void load() {
        WAYPOINTS.clear();
        if (!Files.exists(file)) {
            save();
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                String[] parts = line.split(";", 4);
                if (parts.length < 4) continue;
                try {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    double z = Double.parseDouble(parts[2].trim());
                    WAYPOINTS.add(new Waypoint(parts[3], x, y, z));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static String fmt(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
