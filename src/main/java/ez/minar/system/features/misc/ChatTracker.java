package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.EntityStatusEvent;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NewFunction(name = "ChatTracker", desc = "Shows formatted chat notices for totems and buffs", category = Category.MISC)
public class ChatTracker extends Function {
    private static final byte TOTEM_POP_STATUS = 35;
    private static final Pattern QUOTED_NAME = Pattern.compile("[\"'](.*?)[\"']");

    private final BooleanSetting totems = new BooleanSetting("Totems", true);
    private final BooleanSetting buffs = new BooleanSetting("Buffs", true);

    public ChatTracker() {
        addSettings(totems, buffs);
    }

    @EventHandler
    public void onEntityStatus(EntityStatusEvent event) {
        if (!totems.isEnabled()) return;
        if (event.getPacket().getStatus() != TOTEM_POP_STATUS) return;
        if (!(event.getEntity() instanceof PlayerEntity player)) return;
        if (player == mc.player) return;

        showLostMessage(player.getName().getString(), "Totem of Undying", 0xFFD966);
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        if (!buffs.isEnabled() || mc.player == null) return;

        String message = event.getPacket().content().getString();
        String lower = message.toLowerCase(Locale.ROOT);

        if (!isBuffMessage(lower)) return;

        String player = findPlayerInMessage(message);
        if (isSelf(player)) return;
        if (player == null) {
            player = "%player%";
        }

        showLostMessage(player, extractName(message, "Buff"), 0x79D7FF);
    }

    private boolean isBuffMessage(String lower) {
        boolean hasBuffWord = lower.contains("buff")
                || lower.contains("effect")
                || lower.contains("potion")
                || lower.contains("баф")
                || lower.contains("эффект")
                || lower.contains("зель");

        boolean hasLostWord = lower.contains("lost")
                || lower.contains("expired")
                || lower.contains("потер")
                || lower.contains("законч");

        return hasBuffWord && hasLostWord;
    }

    private String findPlayerInMessage(String message) {
        if (mc.player.networkHandler == null) return null;

        String lower = message.toLowerCase(Locale.ROOT);
        return mc.player.networkHandler.getPlayerList().stream()
                .map(entry -> entry.getProfile().name())
                .filter(name -> lower.contains(name.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private boolean isSelf(String playerName) {
        return mc.player != null && playerName.equalsIgnoreCase(mc.player.getName().getString());
    }

    private String extractName(String message, String fallback) {
        Matcher matcher = QUOTED_NAME.matcher(message);
        if (matcher.find() && !matcher.group(1).isBlank()) {
            return matcher.group(1).trim();
        }

        return fallback;
    }

    private void showLostMessage(String player, String itemName, int itemColor) {
        if (mc == null) return;

        MutableText text = Text.literal("[")
                .formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Minar").formatted(Formatting.AQUA))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(player).formatted(Formatting.WHITE))
                .append(Text.literal(" lost ").formatted(Formatting.GRAY))
                .append(Text.literal("\"").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(itemName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(itemColor))))
                .append(Text.literal("\"").formatted(Formatting.DARK_GRAY));

        mc.execute(() -> {
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(text);
            }
        });
    }
}
