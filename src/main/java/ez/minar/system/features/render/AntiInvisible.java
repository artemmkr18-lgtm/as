package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.NumberSetting;

@NewFunction(
        name = "AntiInvisible",
        desc = "Показывает всех сущностей с эффектом невидимости",
        category = Category.RENDER
)
public class AntiInvisible extends Function {
    private final NumberSetting opacity = new NumberSetting("Прозрачность", 35.0, 0.0, 100.0, 1.0);

    public AntiInvisible() {
        addSettings(opacity);
    }

    public int getAlpha() {
        return (int) Math.round(opacity.getValue() * 255.0 / 100.0);
    }
}
