package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.TextSetting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NewFunction(name = "StreamerMode", desc = "Скрывает ник", category = Category.MISC)
public class StreamerMode extends Function {

    private final ModeSetting mode = new ModeSetting("Режим", "Простой");
    private final BooleanSetting simpleNick = new BooleanSetting("Ник", true);
    private final TextSetting customNick = new TextSetting("Ник для замены", "Protected", 32);

    public StreamerMode() {
        addSettings(mode, simpleNick, customNick);
        simpleNick.runnable(this::updateSettingVisibility);
        updateSettingVisibility();
    }

    public static String protect(String text) {
        StreamerMode streamerMode = FunctionManager.getFunction(StreamerMode.class);
        if (streamerMode == null || !streamerMode.isEnabled()) {
            return text;
        }

        return streamerMode.replace(text);
    }

    private String replace(String text) {
        if (text == null || mc.getSession() == null) {
            return text;
        }

        if (!simpleNick.isEnabled()) {
            return text;
        }

        return replaceName(text, mc.getSession().getUsername(), customNick.getValue());
    }

    private String replaceName(String text, String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            return text;
        }

        return Pattern.compile(Pattern.quote(from), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(to));
    }

    private void updateSettingVisibility() {
        customNick.setVisible(simpleNick.isEnabled());
    }
}
