package ez.minar.system.dummy;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;

import java.util.UUID;

public class FakeDummyPlayer extends OtherClientPlayerEntity {
    public FakeDummyPlayer(ClientWorld world, String name, UUID uuid) {
        super(world, new GameProfile(uuid, name));
    }

    @Override
    public boolean clientDamage(DamageSource source) {
        return false;
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        // Dummy stays stable for consistent rotation training and testing
    }

    public void spawn() {
        this.unsetRemoved();
        ((ClientWorld) this.getEntityWorld()).addEntity(this);
    }

    public void remove() {
        ((ClientWorld) this.getEntityWorld()).removeEntity(this.getId(), Entity.RemovalReason.DISCARDED);
        this.onRemoved();
    }
}
