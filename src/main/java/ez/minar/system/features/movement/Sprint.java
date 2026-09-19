package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.events.impl.UpdateEvent;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

@NewFunction(name = "Sprint", desc = "Автоматически удерживает состояние бега", category = Category.MOVEMENT)
public class Sprint extends Function {
    private boolean serverSprinting;

    @Override
    public void onEnable() {
        serverSprinting = mc.player != null && mc.player.isSprinting();
    }

    @Override
    public void onDisable() {
        serverSprinting = false;
    }

    @EventHandler
    public void update(UpdateEvent event) {
        if (nullCheck.player()) return;

        ez.minar.system.features.combat.AttackAura aura = ez.minar.system.api.FunctionManager.getFunction(ez.minar.system.features.combat.AttackAura.class);
        ez.minar.system.features.combat.TriggerBot bot = ez.minar.system.api.FunctionManager.getFunction(ez.minar.system.features.combat.TriggerBot.class);

        if ((aura != null && aura.isDroppingSprint()) || (bot != null && bot.isDroppingSprint())) {
            return;
        }

        if (mc.player.isSprinting()) {
            if (!canSprint()) {
                mc.player.setSprinting(false);
            }
        } else if (canSprint()) {
            mc.player.setSprinting(true);
        }
    }

    private boolean canSprint() {
        return !mc.player.hasBlindnessEffect() && (!mc.player.hasVehicle() ||
                mc.player.canSprintAsVehicle()) && mc.player.input.hasForwardMovement() && mc.player.getHungerManager().canSprint();
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPacket() instanceof ClientCommandC2SPacket packet)) return;
        if (packet.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
            if (serverSprinting) {
                event.cancel();
                return;
            }
            serverSprinting = true;
        } else if (packet.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
            if (!serverSprinting) {
                event.cancel();
                return;
            }
            serverSprinting = false;
        }
    }
}
