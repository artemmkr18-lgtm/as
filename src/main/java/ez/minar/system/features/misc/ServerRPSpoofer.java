package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketReceiveEvent;
import ez.minar.system.events.impl.UpdateEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;

@NewFunction(name = "ServerRPSpoof", desc = "Отправляет серверу статус успешной загрузки ресурс-пака", category = Category.MISC)
public class ServerRPSpoofer extends Function {
    private ResourcePackSendS2CPacket pendingPack;
    private long acceptedAt;

    @EventHandler
    public void onPacket(PacketReceiveEvent event) {
        if (event.getPacket() instanceof ResourcePackSendS2CPacket packet) {
            pendingPack = packet;
            acceptedAt = 0L;
            event.cancel();
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (pendingPack == null || mc.player == null) {
            return;
        }

        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler == null) {
            return;
        }

        if (acceptedAt == 0L) {
            networkHandler.sendPacket(new ResourcePackStatusC2SPacket(pendingPack.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            acceptedAt = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - acceptedAt >= 300L) {
            networkHandler.sendPacket(new ResourcePackStatusC2SPacket(pendingPack.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            pendingPack = null;
            acceptedAt = 0L;
        }
    }

    @Override
    public void onDisable() {
        pendingPack = null;
        acceptedAt = 0L;
    }
}
