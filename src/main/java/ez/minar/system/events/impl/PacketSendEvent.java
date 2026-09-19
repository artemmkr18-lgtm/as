package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import net.minecraft.network.packet.Packet;

public class PacketSendEvent extends Event {
    private final Packet<?> packet;
    private boolean cancelled;

    public PacketSendEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}