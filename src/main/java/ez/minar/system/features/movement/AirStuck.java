package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.settings.impl.BooleanSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

@NewFunction(name = "AirStuck", desc = "Замораживает игрока, отменяя пакеты перемещения", category = Category.MOVEMENT)
public class AirStuck extends Function {
    private final BooleanSetting stopBeforeHit =
            new BooleanSetting("Останавливаться перед ударом", false);
    private boolean criticalFreezeLatched;
    private boolean criticalPositionSynced;
    private int latchedTargetId = -1;

    public AirStuck() {
        addSettings(stopBeforeHit);
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        updateCriticalFreezeState();
        if (!shouldFreeze()) return;

        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setSprinting(false);
        mc.player.noClip = false;

        if (mc.player.getAbilities() != null) {
            mc.player.getAbilities().flying = false;
        }
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket) {
            updateCriticalFreezeState();
            if (stopBeforeHit.isEnabled() && criticalFreezeLatched && !criticalPositionSynced) {
                // This packet tells the server that the player is descending.
                // AttackAura waits for it before sending the critical attack.
                criticalPositionSynced = true;
                return;
            }
            if (shouldFreeze()) {
                event.cancel();
            }
        }
    }

    private boolean shouldFreeze() {
        if (!stopBeforeHit.isEnabled()) {
            return true;
        }

        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura == null || !aura.isEnabled() || !aura.hasTarget()) {
            return true;
        }
        return criticalFreezeLatched && criticalPositionSynced;
    }

    public boolean prepareCriticalFreeze(int targetId) {
        if (!isEnabled() || !stopBeforeHit.isEnabled()) return false;
        updateCriticalFreezeState();

        if (!criticalFreezeLatched || latchedTargetId != targetId) {
            criticalFreezeLatched = true;
            criticalPositionSynced = false;
            latchedTargetId = targetId;
        }
        return !criticalPositionSynced;
    }

    public boolean isCriticalAttackReady(int targetId) {
        if (!isEnabled() || !stopBeforeHit.isEnabled()) return true;
        updateCriticalFreezeState();
        if (!criticalFreezeLatched || !criticalPositionSynced || latchedTargetId != targetId) return false;
        if (mc.player == null || mc.player.isOnGround() || mc.player.fallDistance <= 0.0F) return false;

        // Separate strict timing for AirStuck; regular AttackAura criticals are unchanged.
        return mc.player.getAttackCooldownProgress(0.0F) >= 1.0F;
    }

    public boolean isFrozen() {
        return isEnabled() && shouldFreeze();
    }

    @EventHandler
    public void onInput(InputEvent event) {
        updateCriticalFreezeState();
        if (!shouldFreeze()) return;

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
        event.setSprint(false);
    }

    private void updateCriticalFreezeState() {
        if (!stopBeforeHit.isEnabled()) {
            resetCriticalFreeze();
            return;
        }

        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura == null || !aura.isEnabled() || !aura.isTrackingTarget(latchedTargetId)) {
            resetCriticalFreeze();
        }
    }

    private void resetCriticalFreeze() {
        criticalFreezeLatched = false;
        criticalPositionSynced = false;
        latchedTargetId = -1;
    }

    @Override
    public void onDisable() {
        resetCriticalFreeze();
        if (mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }
}