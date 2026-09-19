package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.PacketReceiveEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

@NewFunction(
        name = "Velocity",
        category = Category.COMBAT,
        desc = "Убирает откидывание"
)
public class Velocity extends Function {

    private final ModeSetting mode = new ModeSetting("Режим", "Пакетный", "Легитный");
    private final BooleanSetting legitCounterStrafe = new BooleanSetting("Контр-стрейф", true);
    private final BooleanSetting legitJumpReset = new BooleanSetting("Прыжок при ударе", true);

    public Velocity() {
        mode.runnable(this::updateVisibility);
        updateVisibility();
        addSettings(mode, legitCounterStrafe, legitJumpReset);
    }

    private void updateVisibility() {
        boolean legit = mode.isEnabled("Легитный");
        legitCounterStrafe.setVisible(legit);
        legitJumpReset.setVisible(legit);
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (nullCheck.all() || !mode.isEnabled("Легитный") || !canUseLegitInput(event)) {
            return;
        }

        if (legitCounterStrafe.isEnabled() && event.getForward() >= 0.0F) {
            event.setForward(1.0F);
            event.setSprint(true);
        }

        if (legitJumpReset.isEnabled() && mc.player.isOnGround() && mc.player.hurtTime >= 8) {
            event.setJump(true);
        }
    }

    @EventHandler
    public void onReceive(PacketReceiveEvent event) {
        if (nullCheck.all() || !mode.isEnabled("Пакетный")) return;

        if (mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava()) {
            return;
        }

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet
                && packet.getEntityId() == mc.player.getId()) {
            event.cancel();
        }

        if (event.getPacket() instanceof ExplosionS2CPacket) {
            event.cancel();
        }
    }

    private boolean canUseLegitInput(InputEvent event) {
        return mc.player.hurtTime > 0
                && !event.isSneak()
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing()
                && !mc.player.hasVehicle()
                && !mc.player.isUsingItem();
    }
}
