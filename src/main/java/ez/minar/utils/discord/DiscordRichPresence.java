package ez.minar.utils.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class DiscordRichPresence {
    private static final String APPLICATION_ID = "1525154172440285287";
    private static final String LARGE_IMAGE_KEY = "minar";
    private static final String TELEGRAM_URL = "https://t.me/minarclient";

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private static FileChannel channel;
    private static volatile boolean running = true;
    private static Thread rpcThread;

    private DiscordRichPresence() {
    }

    public static void start() {
        if (APPLICATION_ID == null || APPLICATION_ID.isBlank()) {
            return;
        }

        running = true;
        rpcThread = new Thread(DiscordRichPresence::connectAndUpdate, "Minar Discord RPC");
        rpcThread.setDaemon(true);
        rpcThread.start();
    }

    public static void stop() {
        running = false;
        if (rpcThread != null) {
            rpcThread.interrupt();
        }
        if (channel == null) {
            return;
        }

        try {
            sendActivity(null);
            writePacket(OP_CLOSE, new JsonObject());
        } catch (Exception ignored) {
        } finally {
            closeQuietly();
        }
    }

    private static void connectAndUpdate() {
        try {
            channel = openDiscordPipe();
            if (channel == null || !running) {
                return;
            }

            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", APPLICATION_ID);
            writePacket(OP_HANDSHAKE, handshake);
            readPacket();

            sendActivity(createActivity());
            readPacket();
        } catch (Exception ignored) {
            closeQuietly();
        }
    }

    private static FileChannel openDiscordPipe() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            return null;
        }

        for (int attempt = 0; attempt < 5 && running; attempt++) {
            for (int i = 0; i < 10 && running; i++) {
                Path pipe = Path.of("\\\\.\\pipe\\discord-ipc-" + i);
                try {
                    return FileChannel.open(pipe, StandardOpenOption.READ, StandardOpenOption.WRITE);
                } catch (IOException ignored) {
                }
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static JsonObject createActivity() {
        JsonObject activity = new JsonObject();
        activity.addProperty("state", "Версия -> FREE");
        activity.addProperty("type", 0);

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", System.currentTimeMillis() / 1000L);
        activity.add("timestamps", timestamps);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", LARGE_IMAGE_KEY);
        assets.addProperty("large_text", "Minar Client");
        activity.add("assets", assets);

        JsonArray buttons = new JsonArray();
        JsonObject telegramButton = new JsonObject();
        telegramButton.addProperty("label", "Телеграм канал");
        telegramButton.addProperty("url", TELEGRAM_URL);
        buttons.add(telegramButton);
        activity.add("buttons", buttons);

        return activity;
    }

    private static void sendActivity(JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.add("args", args);
        payload.addProperty("nonce", UUID.randomUUID().toString());

        writePacket(OP_FRAME, payload);
    }

    private static void writePacket(int opCode, JsonObject payload) throws IOException {
        byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(opCode);
        packet.putInt(json.length);
        packet.put(json);
        packet.flip();

        while (packet.hasRemaining()) {
            channel.write(packet);
        }
    }

    private static String readPacket() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        readFully(header);
        header.flip();

        int opCode = header.getInt();
        int length = header.getInt();
        ByteBuffer payload = ByteBuffer.allocate(length);
        readFully(payload);
        payload.flip();

        return "op=" + opCode + ", payload=" + StandardCharsets.UTF_8.decode(payload);
    }

    private static void readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new IOException("Discord IPC closed");
            }
        }
    }

    private static void closeQuietly() {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
        } finally {
            channel = null;
        }
    }
}
