package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.managers.UnhookManager;

@NewFunction(name = "Unhook", desc = "\u041f\u043e\u043b\u043d\u043e\u0441\u0442\u044c\u044e \u043e\u0442\u043a\u043b\u044e\u0447\u0430\u0435\u0442 \u0447\u0438\u0442 \u0438 \u0441\u043a\u0440\u044b\u0432\u0430\u0435\u0442 \u0432\u0441\u0435 \u0441\u043b\u0435\u0434\u044b", category = Category.MISC)
public class Unhook extends Function {
    @Override
    public void toggle() {
        if (isEnabled()) {
            setEnabled(false);
            return;
        }

        setEnabled(true);
        UnhookManager.unhook();
    }
}
