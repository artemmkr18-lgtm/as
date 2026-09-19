package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.world.ClientWorld;

@NewFunction(name = "Ambience", desc = "Изменяет локальное время мира", category = Category.RENDER)
public class Ambience extends Function {
    private final NumberSetting time = new NumberSetting("Time", 6000.0, 0.0, 24000.0, 100.0);

    private boolean hasSavedTime;
    private long savedTimeOfDay;

    public Ambience() {
        addSettings(time);
        time.runnable(() -> {
            if (isEnabled()) {
                applyTime();
            }
        });
    }

    @Override
    public void onEnable() {
        saveTime();
        applyTime();
    }

    @Override
    public void onDisable() {
        restoreTime();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        applyTime();
    }

    @EventHandler
    public void onRender(Render2DEvent event) {
        applyTime();
    }

    private void saveTime() {
        ClientWorld world = mc.world;
        if (world == null) {
            hasSavedTime = false;
            return;
        }

        savedTimeOfDay = world.getTimeOfDay();
        hasSavedTime = true;
    }

    private void restoreTime() {
        ClientWorld world = mc.world;
        if (world == null || !hasSavedTime) {
            return;
        }

        world.setTime(world.getLevelProperties().getTime(), savedTimeOfDay, true);
        hasSavedTime = false;
    }

    private void applyTime() {
        ClientWorld world = mc.world;
        if (world == null) {
            return;
        }

        world.setTime(world.getLevelProperties().getTime(), (long) time.getValue(), false);
    }
}
