package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.settings.impl.TextSetting;

import java.util.Locale;
import java.util.UUID;

@NewFunction(name = "Auto Auth", desc = "Automatically logs in and registers on servers", category = Category.MISC)
public class AutoAuth extends Function {

    private final TextSetting password = new TextSetting("Password", UUID.randomUUID().toString().replace("-", "").substring(0, 8));

    public AutoAuth() {
        addSettings(password);
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        String message = event.getPacket().content().getString().toLowerCase(Locale.ROOT);

        if (message.contains("/login")) {
            mc.player.networkHandler.sendChatCommand("login " + password.getValue());
        } else if (message.contains("/reg") || message.contains("/register")) {
            mc.player.networkHandler.sendChatCommand("register " + password.getValue() + " " + password.getValue());
        }
    }
}
