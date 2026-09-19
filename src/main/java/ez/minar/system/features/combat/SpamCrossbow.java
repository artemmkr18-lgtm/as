package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

@NewFunction(name = "SpamCrossbow", desc = "Спамит стрелами из арбалета", category = Category.COMBAT)
public class SpamCrossbow extends Function {

    @EventHandler
    public void onTick(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        boolean inMainHand = mc.player.getMainHandStack().getItem() == Items.CROSSBOW;
        boolean inOffHand = mc.player.getOffHandStack().getItem() == Items.CROSSBOW;

        if (inMainHand || inOffHand) {
            Hand hand = inMainHand ? Hand.MAIN_HAND : Hand.OFF_HAND;
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(hand, 0, mc.player.getYaw(), mc.player.getPitch()));
        }
    }
}
