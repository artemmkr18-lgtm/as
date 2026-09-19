package ez.minar.mixins.screen;

import ez.minar.system.commands.CommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void minar$handleCommand(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (CommandManager.isHiddenWhileUnhooked(chatText)) {
            ci.cancel();
            return;
        }

        if (!CommandManager.executeIfCommand(chatText)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (addToHistory) {
            client.inGameHud.getChatHud().addToMessageHistory(chatText.trim());
        }
        ci.cancel();
    }
}
