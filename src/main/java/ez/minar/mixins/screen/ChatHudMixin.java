package ez.minar.mixins.screen;

import ez.minar.system.features.misc.ChatHelper;
import ez.minar.Minar;
import ez.minar.system.menu.ThemeManager;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.awt.Color;
import java.util.List;
import ez.minar.system.api.FunctionManager;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow @Final private List<ChatHudLine> messages;

    @Shadow public abstract void refresh();

    private String minar$lastText = "";
    private int minar$spamCount = 1;

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true)
    private Text minar$modifyMessage(Text text) {
        ChatHelper helper = FunctionManager.getFunction(ChatHelper.class);
        if (helper != null && helper.isEnabled()) {
            String rawText = text.getString();



            if (helper.removeSpam.isEnabled()) {
                if (rawText.equals(minar$lastText)) {
                    minar$spamCount++;

                    MutableText spamText = Text.empty();
                    String suffix = " [x" + minar$spamCount + "]";
                    Color start = ThemeManager.getThemeColor();
                    Color end = Color.WHITE;

                    for (int i = 0; i < suffix.length(); i++) {
                        float progress = suffix.length() == 1 ? 1f : (float) i / (suffix.length() - 1);
                        int red = Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress);
                        int green = Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
                        int blue = Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress);
                        spamText.append(Text.literal(String.valueOf(suffix.charAt(i)))
                                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((red << 16) | (green << 8) | blue))));
                    }

                    text = text.copy().append(spamText);

                    if (!messages.isEmpty()) {
                        messages.remove(0);
                        refresh();
                    }
                } else {
                    minar$lastText = rawText;
                    minar$spamCount = 1;
                }
            }
        }
        return text;
    }
}
