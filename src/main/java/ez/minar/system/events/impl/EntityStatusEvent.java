package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class EntityStatusEvent extends Event {
    @Getter private final EntityStatusS2CPacket packet;
    @Getter private final Entity entity;

    public EntityStatusEvent(EntityStatusS2CPacket packet, Entity entity) {
        this.packet = packet;
        this.entity = entity;
    }
}
