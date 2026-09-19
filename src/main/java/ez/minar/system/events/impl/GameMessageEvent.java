package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.Getter;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

public class GameMessageEvent extends Event {
    @Getter private final GameMessageS2CPacket packet;

    public GameMessageEvent(GameMessageS2CPacket packet) {
        this.packet = packet;
    }
}
