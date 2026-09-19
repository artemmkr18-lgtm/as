package ez.minar.mixins.render;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.render.WorldRecolor;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @ModifyArg(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 1
            ),
            index = 0
    )
    private Vector3fc minar$applyWorldLightColor(Vector3fc vanillaColor) {
        WorldRecolor worldRecolor = FunctionManager.getFunction(WorldRecolor.class);
        return worldRecolor == null ? vanillaColor : worldRecolor.encodeLightColor(vanillaColor);
    }
}
