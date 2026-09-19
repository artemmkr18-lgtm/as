package ez.minar.mixins.screen;

import com.mojang.brigadier.suggestion.Suggestions;
import ez.minar.mixins.interfaces.IChatInputSuggestor;
import ez.minar.system.commands.DotCommandSuggestions;
import ez.minar.system.managers.UnhookManager;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Shadow
    public abstract void clearWindow();

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void minar$refreshDotCommands(CallbackInfo ci) {
        IChatInputSuggestor accessor = (IChatInputSuggestor) this;
        TextFieldWidget textField = accessor.minar$getTextField();
        String input = textField.getText();
        if (!input.startsWith(".") || UnhookManager.isUnhooked()) {
            return;
        }

        clearWindow();
        CompletableFuture<Suggestions> pendingSuggestions = DotCommandSuggestions.getSuggestions(input, textField.getCursor());
        accessor.minar$setPendingSuggestions(pendingSuggestions);
        if (pendingSuggestions.isDone()) {
            show(false);
        } else {
            pendingSuggestions.thenRun(() -> show(false));
        }
        ci.cancel();
    }
}
