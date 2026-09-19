package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;

@NewFunction(name = "Chat Helper", desc = "Helps clean chat messages", category = Category.MISC)
public class ChatHelper extends Function {
    public final BooleanSetting blurSwears = new BooleanSetting("\u0421\u043a\u0440\u044b\u0432\u0430\u0442\u044c \u043c\u0430\u0442", true);
    public final BooleanSetting removeSpam = new BooleanSetting("\u0423\u0431\u0438\u0440\u0430\u0442\u044c \u0441\u043f\u0430\u043c", true);

    public ChatHelper() {
        addSettings(blurSwears, removeSpam);
    }

    public static String protect(String text) {
        ChatHelper helper = ez.minar.system.api.FunctionManager.getFunction(ChatHelper.class);
        if (helper == null || !helper.isEnabled() || !helper.blurSwears.isEnabled() || text == null) {
            return text;
        }

        String rawText = text;
        String[] swears = {"\u0431\u043b\u044f\u0442\u044c", "\u0431\u043b\u044f", "\u0441\u0443\u043a\u0430", "\u0441\u0443\u043a", "\u0445\u0443\u0439", "\u0445\u0443\u0435", "\u043f\u0438\u0437\u0434", "\u0435\u0431\u0430", "\u0435\u0431\u043b", "\u0434\u043e\u043b\u0431", "\u043c\u0443\u0434\u0430", "\u0433\u0430\u043d\u0434", "\u043f\u0438\u0434\u043e\u0440"};
        for (String swear : swears) {
            if (rawText.toLowerCase().contains(swear)) {
                String blurred = swear.charAt(0) + "*".repeat(Math.max(1, swear.length() - 2)) + (swear.length() > 1 ? String.valueOf(swear.charAt(swear.length() - 1)) : "");
                rawText = rawText.replaceAll("(?i)" + swear, blurred);
            }
        }
        return rawText;
    }
}