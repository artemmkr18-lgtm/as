package ez.minar.system.features.combat;

import ez.minar.mixins.interfaces.IPlayerInteractEntityC2SPacket;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@NewFunction(name = "MaceKiller", desc = "Simulates fall packets before attack", category = Category.COMBAT)
public final class MaceKiller extends Function {
    private final NumberSetting height = new NumberSetting("Height", 1.0, 1.0, 512.0, 0.1);
    public static boolean cancelCrit;
    private static Vec3d syncedBasePos;
    private static int syncedAttacks;
    private static long syncedUntil;

    public MaceKiller() {
        addSettings(height);
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (nullCheck.all()) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) || !isAttack(packet)) return;

        Entity entity = mc.world.getEntityById(((IPlayerInteractEntityC2SPacket) packet).getEntityId());
        if (entity == null || entity instanceof EndCrystalEntity || cancelCrit) return;

        if (syncedBasePos != null && syncedAttacks > 0 && System.currentTimeMillis() <= syncedUntil) {
            doCrit(syncedBasePos, true);
            syncedAttacks--;
            if (syncedAttacks <= 0) clearSync();
            return;
        }
        clearSync();

        doCrit();
    }

    public void doCrit() {
        if (nullCheck.all() || mc.player.networkHandler == null) return;
        if ((mc.player.isOnGround() || mc.player.getAbilities().flying)
                && !mc.player.isInLava()
                && !mc.player.isSubmergedInWater()) {
            doCrit(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), false);
        }
    }

    private void doCrit(Vec3d basePos, boolean force) {
        if (nullCheck.all() || mc.player.networkHandler == null || basePos == null) return;
        if (!force && (!mc.player.isOnGround() && !mc.player.getAbilities().flying)) return;
        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return;

        simulateFallPacket(basePos, height.getValue());
        simulateFallPacket(basePos, 0.0);
    }

    public static void syncNextAttack(Vec3d basePos) {
        syncedBasePos = basePos;
        syncedAttacks = 1;
        syncedUntil = System.currentTimeMillis() + 250L;
    }

    public static void clearSync() {
        syncedBasePos = null;
        syncedAttacks = 0;
        syncedUntil = 0L;
    }

    private void simulateFallPacket(Vec3d basePos, double yDelta) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                basePos.x,
                basePos.y + yDelta,
                basePos.z,
                false,
                mc.player.horizontalCollision
        ));
    }

    private boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        AttackHandler handler = new AttackHandler();
        packet.handle(handler);
        return handler.attack;
    }

    private static final class AttackHandler implements PlayerInteractEntityC2SPacket.Handler {
        private boolean attack;

        @Override
        public void interact(Hand hand) {
        }

        @Override
        public void interactAt(Hand hand, Vec3d pos) {
        }

        @Override
        public void attack() {
            attack = true;
        }
    }
}
