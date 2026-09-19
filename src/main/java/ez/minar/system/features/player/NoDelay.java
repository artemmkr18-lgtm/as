package ez.minar.system.features.player;

import ez.minar.mixins.interfaces.IClientPlayerInteractionManager;
import ez.minar.mixins.interfaces.ILivingEntity;
import ez.minar.mixins.interfaces.IMinecraftClient;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.MultiSetting;

@NewFunction(name = "NoDelay", desc = "Убирает задержку у выбранных элементов", category = Category.PLAYER)
public class NoDelay extends Function {
    final MultiSetting delay = new MultiSetting("Убрать задержку", "Прыжка", "Ломания", "ПКМ");

    public NoDelay() {
        addSettings(delay);
    }

    @EventHandler
    public void onTick(UpdateEvent e) {
        if (nullCheck.player()) return;

        if (delay.isEnabled("Ломания")) ((IClientPlayerInteractionManager) mc.interactionManager).setBlockBreakingCooldown(0);
        if (delay.isEnabled("Прыжка")) ((ILivingEntity) mc.player).setJumpingCooldown(0);
        if (delay.isEnabled("ПКМ")) ((IMinecraftClient) mc).setItemUseCooldown(0);
    }
}
