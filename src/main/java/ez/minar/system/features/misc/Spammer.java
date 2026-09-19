package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ButtonSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@NewFunction(name = "Spammer", desc = "Sends messages from Minar/misc/spammer.txt", category = Category.MISC)
public class Spammer extends Function {
    private final ButtonSetting openFile = new ButtonSetting("Spammer file", "Open", this::openSpammerFile);
    private final ModeSetting mode = new ModeSetting("Mode", "Chat", "Whispers");
    private final ModeSetting whisperPrefix = new ModeSetting("Prefix", "w", "msg", "tell");
    private final BooleanSetting global = new BooleanSetting("Global", true);
    private final BooleanSetting antiSpam = new BooleanSetting("AntiSpam", false);
    private final NumberSetting delay = new NumberSetting("Delay", 5, 0.5, 30, 0.5);

    private final Random random = new Random();
    private final List<String> messages = new ArrayList<>();
    private long lastMessageTime;

    public Spammer() {
        addSettings(openFile, mode, whisperPrefix, global, antiSpam, delay);
    }

    @Override
    public void onEnable() {
        loadMessages();
        lastMessageTime = 0L;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.player.networkHandler == null) return;

        if (System.currentTimeMillis() - lastMessageTime < delay.getValue() * 1000L) {
            return;
        }

        if (messages.isEmpty()) {
            loadMessages();
            if (messages.isEmpty()) {
                toggle();
                return;
            }
        }

        String message = messages.get(random.nextInt(messages.size()));
        if (antiSpam.isEnabled()) {
            message += " " + randomSuffix();
        }

        if (mode.isEnabled("Chat")) {
            sendChat(message);
        } else {
            String player = randomPlayerName();
            if (player == null || player.isBlank()) return;
            mc.player.networkHandler.sendChatCommand(whisperPrefix.getActiveMode() + " " + player + " " + message);
        }

        lastMessageTime = System.currentTimeMillis();
    }

    private void sendChat(String message) {
        if (message.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(message.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(global.isEnabled() ? "!" + message : message);
        }
    }

    private void loadMessages() {
        messages.clear();

        Path file = spammerFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                Files.createFile(file);
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            StringBuilder chunk = new StringBuilder();
            boolean hasEmptyLines = lines.stream().anyMatch(String::isBlank);

            for (String line : lines) {
                if (line.isBlank()) {
                    addChunk(chunk);
                } else if (hasEmptyLines) {
                    chunk.append(line).append(' ');
                } else {
                    messages.add(line);
                }
            }

            addChunk(chunk);
            messages.removeIf(String::isBlank);
        } catch (IOException ignored) {
        }
    }

    private void openSpammerFile() {
        Path file = spammerFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                Files.createFile(file);
            }

            Util.getOperatingSystem().open(file);
        } catch (IOException ignored) {
        }
    }

    private Path spammerFile() {
        return FabricLoader.getInstance().getGameDir().resolve("Minar").resolve("misc").resolve("spammer.txt");
    }

    private void addChunk(StringBuilder chunk) {
        if (!chunk.isEmpty()) {
            messages.add(chunk.toString().trim());
            chunk.setLength(0);
        }
    }

    private String randomPlayerName() {
        List<String> names = mc.player.networkHandler.getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(profile -> profile.name())
                .filter(name -> !name.equals(mc.player.getName().getString()))
                .toList();

        return names.isEmpty() ? null : names.get(random.nextInt(names.size()));
    }

    private String randomSuffix() {
        return "[" + (char) ('a' + random.nextInt(26)) + random.nextInt(10) + (char) ('a' + random.nextInt(26)) + "]";
    }
}
