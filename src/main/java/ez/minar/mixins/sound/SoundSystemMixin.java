package ez.minar.mixins.sound;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @ModifyVariable(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private SoundInstance onPlaySound(SoundInstance sound) {
        if (sound != null && sound.getId() != null) {
            Identifier id = sound.getId();
            if ("minecraft".equals(id.getNamespace()) && id.getPath().contains("totem")) {
                net.minecraft.sound.SoundCategory category = sound.getCategory() != null ? sound.getCategory() : net.minecraft.sound.SoundCategory.PLAYERS;
                return new PositionedSoundInstance(
                        SoundEvent.of(Identifier.of("minar", "totem_pop")),
                        category,
                        1.0f,
                        1.0f,
                        SoundInstance.createRandom(),
                        sound.getX(),
                        sound.getY(),
                        sound.getZ()
                );
            }
        }
        return sound;
    }
}
