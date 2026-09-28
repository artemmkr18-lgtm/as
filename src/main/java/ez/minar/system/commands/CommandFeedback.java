package ez.minar.system.commands;

import ez.minar.system.menu.ThemeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.awt.Color;

public final class CommandFeedback {
    private static final String PREFIX = "Minar";

    private CommandFeedback() {
    }

    public static void message(String message, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }

        if (!client.isOnThread()) {
            client.execute(() -> message(message, color));
            return;
        }

        client.inGameHud.getChatHud().addMessage(
                Text.literal("[")
                        .formatted(Formatting.DARK_GRAY)
                        .append(gradientPrefix())
                        .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(message).formatted(color))
        );
    }

    private static MutableText gradientPrefix() {
        MutableText text = Text.empty();
        Color start = ThemeManager.getThemeColor();
        Color end = Color.WHITE;

        for (int i = 0; i < PREFIX.length(); i++) {
            float progress = PREFIX.length() == 1 ? 1f : (float) i / (PREFIX.length() - 1);
            int red = lerp(start.getRed(), end.getRed(), progress);
            int green = lerp(start.getGreen(), end.getGreen(), progress);
            int blue = lerp(start.getBlue(), end.getBlue(), progress);
            text.append(Text.literal(String.valueOf(PREFIX.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((red << 16) | (green << 8) | blue))));
        }

        return text;
    }

    private static int lerp(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }
}
