package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.events.EventManager;
import lombok.Getter;

@NewFunction(name = "ClickGUI", desc = "Стиль интерфейса: Дефолт / Дроп", category = Category.RENDER)
public class ClickGuiSettings extends Function {
    @Getter private final ModeSetting style = new ModeSetting("Стиль ClickGUI", "Дефолт", "Дроп");

    public ClickGuiSettings() {
        addSettings(style);
        style.runnable(EventManager::applyClickGuiStyle);
    }
}
