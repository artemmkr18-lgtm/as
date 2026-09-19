package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketReceiveEvent;

@NewFunction(name = "NoSlotChange", desc = "Запрещает серверу менять слот хотбара", category = Category.PLAYER)
public class NoSlotChange extends Function {
    @EventHandler
    public void onPacket(PacketReceiveEvent event) {
        String packetName = event.getPacket().getClass().getSimpleName();
        if (packetName.contains("UpdateSelectedSlot") || packetName.contains("HeldItemChange") || packetName.contains("SetCarriedItem")) {
            event.cancel();
        }
    }

}
