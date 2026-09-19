package ez.minar.system.commands;

import ez.minar.system.managers.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class WayCommand {
    private WayCommand() {
    }

    public static boolean executeIfCommand(String input) {
        String line = input.trim();
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.equals(".way") && !lower.startsWith(".way ")) return false;

        List<String> tokens = tokenize(line);
        if (tokens.size() == 1) {
            usage();
            return true;
        }
        switch (tokens.get(1).toLowerCase(Locale.ROOT)) {
            case "add" -> add(tokens);
            case "remove" -> remove(tokens);
            case "list" -> list();
            case "clear" -> clear();
            default -> usage();
        }
        return true;
    }

    public static List<String> suggestionsFor(String input) {
        String lower = input.stripLeading().toLowerCase(Locale.ROOT);
        if (".".equals(lower) || (lower.startsWith(".") && ".way".startsWith(lower))) {
            return List.of(".way");
        }
        if (!lower.startsWith(".way ")) return List.of();
        String tail = lower.substring(".way ".length());
        String[] parts = tail.split("\\s+", -1);
        if (parts.length == 1) {
            return Stream.of("add", "remove", "list", "clear")
                    .filter(action -> action.startsWith(parts[0]))
                    .map(action -> ".way " + action + (action.equals("add") || action.equals("remove") ? " " : ""))
                    .toList();
        }
        return List.of();
    }

    private static void add(List<String> tokens) {
        if (tokens.size() != 6) {
            message("Формат: .way add \"Название\" <x> <y> <z>", Formatting.YELLOW);
            return;
        }
        String name = tokens.get(2).trim();
        if (name.isEmpty()) {
            message("Название метки не может быть пустым.", Formatting.RED);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        double baseX = mc.player == null ? 0 : mc.player.getX();
        double baseY = mc.player == null ? 0 : mc.player.getY();
        double baseZ = mc.player == null ? 0 : mc.player.getZ();

        Double x = parseCoord(tokens.get(3), baseX);
        Double y = parseCoord(tokens.get(4), baseY);
        Double z = parseCoord(tokens.get(5), baseZ);
        if (x == null || y == null || z == null) {
            message("Неверные координаты. Пример: .way add \"Дом\" 100 64 -200", Formatting.RED);
            return;
        }

        WaypointManager.add(name, x, y, z);
        message("Метка \"" + name + "\" добавлена ["
                + WaypointManager.fmt(x) + ", " + WaypointManager.fmt(y) + ", " + WaypointManager.fmt(z) + "].", Formatting.GREEN);
    }

    private static void remove(List<String> tokens) {
        if (tokens.size() != 3) {
            message("Формат: .way remove \"Название\"", Formatting.YELLOW);
            return;
        }
        String name = tokens.get(2).trim();
        if (WaypointManager.remove(name)) {
            message("Метка \"" + name + "\" удалена.", Formatting.GREEN);
        } else {
            message("Метка \"" + name + "\" не найдена.", Formatting.YELLOW);
        }
    }

    private static void list() {
        List<WaypointManager.Waypoint> waypoints = WaypointManager.getWaypoints();
        if (waypoints.isEmpty()) {
            message("Список меток пуст.", Formatting.GRAY);
            return;
        }
        message("Метки (" + waypoints.size() + "):", Formatting.AQUA);
        for (WaypointManager.Waypoint waypoint : waypoints) {
            message("- " + waypoint.name() + " [" + WaypointManager.fmt(waypoint.x()) + ", "
                    + WaypointManager.fmt(waypoint.y()) + ", " + WaypointManager.fmt(waypoint.z()) + "]", Formatting.GRAY);
        }
    }

    private static void clear() {
        message("Список меток очищен (" + WaypointManager.clear() + ").", Formatting.GREEN);
    }

    private static void usage() {
        message(".way add \"Название\" <x> <y> <z> - добавить метку", Formatting.GRAY);
        message(".way list - список меток", Formatting.GRAY);
        message(".way remove \"Название\" - удалить метку", Formatting.GRAY);
        message(".way clear - очистить список", Formatting.GRAY);
    }

    private static Double parseCoord(String token, double base) {
        try {
            if (token.startsWith("~")) {
                String rest = token.substring(1).trim();
                return base + (rest.isEmpty() ? 0 : Double.parseDouble(rest));
            }
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static void message(String text, Formatting color) {
        CommandFeedback.message(text, color);
    }
}
