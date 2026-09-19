package ez.minar.mixins.render;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.misc.Cosmetics;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @ModifyVariable(
            method = "update",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack minar$replaceSwordModel(ItemStack stack) {
        Cosmetics cosmetics = FunctionManager.getFunction(Cosmetics.class);
        return cosmetics == null ? stack : cosmetics.getRenderStack(stack);
    }
}
