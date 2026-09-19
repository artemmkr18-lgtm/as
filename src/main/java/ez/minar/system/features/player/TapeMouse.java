package ez.minar.system.features.player;

import ez.minar.mixins.interfaces.IMinecraftClient;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;

@NewFunction(name = "TapeMouse", desc = "Автоматические клики для фоновых ботов", category = Category.PLAYER)
public class TapeMouse extends Function {
    private final NumberSetting cps = new NumberSetting("CPS", 1.0, 0.0, 2.0, 0.05);
    private final ModeSetting button = new ModeSetting("Кнопка", "Левая", "Правая");

    private long lastClick;

    public TapeMouse() {
        addSettings(cps, button);
    }
    @EventHandler
    public void onTick(UpdateEvent event) {
        long delay = (long) (1000.0 / cps.getValue());
        long now = System.currentTimeMillis();

        if (now - lastClick < delay) {
            return;
        }

        clickForLocalPlayer();

        lastClick = now;
    }

    private void clickForLocalPlayer() {
        if (mc.player == null || mc.currentScreen != null) return;

        if (button.isEnabled("Правая")) {
            ((IMinecraftClient) mc).invokeDoItemUse();
        } else {
            ((IMinecraftClient) mc).invokeDoAttack();
        }
    }
}
