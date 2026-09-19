package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.settings.impl.BooleanSetting;

import java.util.Locale;

@NewFunction(name = "AutoTpAccept", desc = "Автоматически принимает запросы телепортации", category = Category.MISC)
public class AutoTpAccept extends Function {
    private static final String[] TELEPORT_MESSAGES = {
            "has requested teleport",
            "просит телепортироваться",
            "хочет телепортироваться к вам",
            "просит к вам телепортироваться"
    };

    private final BooleanSetting friendsOnly = new BooleanSetting("Принимать только друзей", true);
    private boolean canAccept;

    public AutoTpAccept() {
        addSettings(friendsOnly);
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        String message = event.getPacket().content().getString();
        String lower = message.toLowerCase(Locale.ROOT);
        if (!isTeleportMessage(lower)) {
            return;
        }

        canAccept = !friendsOnly.isEnabled()
                || FriendManager.getFriends().values().stream()
                .anyMatch(friend -> lower.contains(friend.toLowerCase(Locale.ROOT)));
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (!canAccept || mc.player == null || mc.player.networkHandler == null) {
            return;
        }

        mc.player.networkHandler.sendChatCommand("tpaccept");
        canAccept = false;
    }

    @Override
    public void onDisable() {
        canAccept = false;
    }

    private boolean isTeleportMessage(String lowerMessage) {
        for (String marker : TELEPORT_MESSAGES) {
            if (lowerMessage.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
