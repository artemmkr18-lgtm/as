package ez.minar.system.autobuy.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class CommandSender {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void sendCommand(ClientPlayerEntity player, String command) {
        if (player != null && player.networkHandler != null && command != null && !command.isBlank()) {
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            player.networkHandler.sendChatCommand(cmd);
        }
    }

    public static void handleServerSwitch(String cmd) {
        if (mc.player != null && mc.player.networkHandler != null) {
            String command = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            mc.player.networkHandler.sendChatCommand(command);
            ServerSwitchHandler.setWaitingForServerLoad(true);
            ServerSwitchHandler.setServerSwitchTime(System.currentTimeMillis());
        }
    }

    public static void openAuction() {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand("ah");
        }
    }
}
