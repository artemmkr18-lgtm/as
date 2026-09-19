package ez.minar.system.commands;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

public final class ParseCommand {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private ParseCommand() {
    }

    public static void parsePlayers() {
        if (mc.player == null) {
            CommandFeedback.message("Вы не подключены к серверу.", Formatting.RED);
            return;
        }

        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler == null) {
            CommandFeedback.message("Список игроков недоступен.", Formatting.RED);
            return;
        }

        File directory = new File(mc.runDirectory, "files/parser");
        if (!directory.exists() && !directory.mkdirs()) {
            CommandFeedback.message("Не удалось создать папку parser.", Formatting.RED);
            return;
        }

        Collection<PlayerListEntry> entries = networkHandler.getListedPlayerListEntries();
        File file = new File(directory, fileName());

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            for (PlayerListEntry entry : entries) {
                Team team = entry.getScoreboardTeam();
                Text prefix = team == null ? Text.empty() : team.getPrefix();
                Text suffix = team == null ? Text.empty() : team.getSuffix();

                writer.write(prefix.getString());
                writer.write(entry.getProfile().name());
                writer.write(suffix.getString());
                writer.write(System.lineSeparator());
            }

            CommandFeedback.message("Сохранено игроков: " + entries.size() + " -> " + file.getName(), Formatting.GREEN);
        } catch (Exception exception) {
            CommandFeedback.message("Ошибка сохранения parse: " + exception.getMessage(), Formatting.RED);
        }
    }

    private static String fileName() {
        String server = "local";
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            server = mc.getCurrentServerEntry().address;
        }

        server = server.replaceAll("[^A-Za-z0-9._-]", "_");
        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        return server + "_" + time + ".txt";
    }
}
