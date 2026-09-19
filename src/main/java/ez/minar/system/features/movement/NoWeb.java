package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.utils.helpers.MoveUtil;
import ez.minar.utils.helpers.PlayerInteractionHelper;
import net.minecraft.block.Blocks;

@NewFunction(
        name = "NoWeb",
        desc = "Позволяет быстро перемещаться в паутине",
        category = Category.MOVEMENT
)
public class NoWeb extends Function {

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        if (PlayerInteractionHelper.isPlayerInBlock(Blocks.COBWEB)) {
            MoveUtil.setMotion(0.64, 0.0);

            if (mc.options.jumpKey.isPressed()) {
                MoveUtil.setMotion(0.64, 0.95);
            }

            if (mc.options.sneakKey.isPressed()) {
                MoveUtil.setMotion(0.64, -0.95);
            }
        }
    }
}