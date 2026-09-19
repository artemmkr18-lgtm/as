package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import net.minecraft.util.Hand;

@NewFunction(name = "AntiAFK", desc = "Предотвращает кик за AFK", category = Category.PLAYER)
public class AntiAFK extends Function {

    private long lastActionTime = 0L;

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (System.currentTimeMillis() - lastActionTime >= 10000L) {
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.jump();
            lastActionTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastActionTime = System.currentTimeMillis();
    }
}
