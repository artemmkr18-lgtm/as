package ez.minar.utils.helpers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public class NetworkUtils {
    public static boolean silentSending;

    public static void sendSilentPacket(Packet<?> packet) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            silentSending = true;
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(packet);
            silentSending = false;
        }
    }
}
