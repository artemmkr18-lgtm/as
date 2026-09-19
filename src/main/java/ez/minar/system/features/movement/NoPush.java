package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;

@NewFunction(name = "NoPush", desc = "Отключает отталкивание от сущностей и воды", category = Category.MOVEMENT)
public class NoPush extends Function {
    public static NoPush Instance;
    public static BooleanSetting players = new BooleanSetting("Сущности", true);
    public static BooleanSetting water = new BooleanSetting("Вода", true);

    public NoPush() {
        Instance = this;
        addSettings(players, water);
    }
}