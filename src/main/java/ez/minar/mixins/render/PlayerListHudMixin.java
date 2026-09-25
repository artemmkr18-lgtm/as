package ez.minar.mixins.render;

import ez.minar.system.features.misc.StreamerMode;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void cleanStreamerTabName(PlayerListEntry playerListEntry, CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        if (original != null) {
            String protectedText = StreamerMode.protect(original.getString());
            if (!protectedText.equals(original.getString())) {
                cir.setReturnValue(Text.literal(protectedText));
            }
        }
    }
}
