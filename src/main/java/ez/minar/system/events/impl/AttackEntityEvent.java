package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class AttackEntityEvent extends Event {
    @Getter private final PlayerEntity player;
    @Getter private final Entity target;

    public AttackEntityEvent(PlayerEntity player, Entity target) {
        this.player = player;
        this.target = target;
    }
}
