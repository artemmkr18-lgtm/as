package ez.minar.mixins.interfaces;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldownManager.class)
public interface IItemCooldownManager {
    @Accessor("entries")
    Map<Identifier, ?> minar$getEntries();

    @Accessor("tick")
    int minar$getTick();
}
