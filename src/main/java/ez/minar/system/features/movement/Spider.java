package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ListSetting;

@NewFunction(name = "Spider", desc = "Позволяет ползать по стенам", category = Category.MOVEMENT)
public class Spider extends Function {

    private final ListSetting mode = new ListSetting("Режим", "Vanilla");

    public Spider() {
        addSettings(mode);
    }

    @EventHandler
    public void onTick(UpdateEvent event) {
        if (nullCheck.all()) return;

        if (mc.player.horizontalCollision && mode.isEnabled("Vanilla")) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.2, mc.player.getVelocity().z);
        }
    }
}
