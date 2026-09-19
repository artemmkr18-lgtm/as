package ez.minar.mixins.figura;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the vanilla first-person arm when the avatar has no textured model
 * geometry attached to that arm.
 */
@Mixin(value = PlayerEntityRenderer.class, priority = 500)
public class FiguraFirstPersonArmFallback {

    @Inject(
            method = "renderArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"
            )
    )
    private void minar$keepVanillaArm(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            Identifier skinTexture,
            ModelPart arm,
            boolean sleeve,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        Avatar avatar = AvatarManager.getAvatarForPlayer(client.player.getUuid());
        if (avatar == null || avatar.renderer == null || avatar.renderer.root == null) {
            return;
        }

        PlayerEntityModel playerModel =
                (PlayerEntityModel) ((PlayerEntityRenderer) (Object) this).getModel();
        ParentType armType = arm == playerModel.leftArm ? ParentType.LeftArm : ParentType.RightArm;
        if (hasTexturedArmPart(avatar.renderer.root, armType, false)) {
            return;
        }

        // Figura's callback may hide this part. Ours is appended afterwards and
        // restores it immediately before Minecraft submits the vanilla arm.
        ((FiguraSubmitCallBackExtension) (Object) arm).figura$addPreRenderingCallback(
                (vertexConsumers, pose) -> {
                    arm.visible = true;
                    return true;
                }
        );
    }

    private static boolean hasTexturedArmPart(FiguraModelPart part, ParentType wantedArm, boolean insideArm) {
        boolean inWantedArm = part.parentType == wantedArm || (insideArm && !part.parentType.isSeparate);

        if (inWantedArm && hasFacesAndTexture(part)) {
            return true;
        }

        for (FiguraModelPart child : part.children) {
            if (hasTexturedArmPart(child, wantedArm, inWantedArm)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFacesAndTexture(FiguraModelPart part) {
        if (part.facesByTexture == null || part.textures == null) {
            return false;
        }

        int count = Math.min(part.facesByTexture.size(), part.textures.size());
        for (int i = 0; i < count; i++) {
            if (part.facesByTexture.get(i) <= 0) {
                continue;
            }

            FiguraTextureSet set = part.textures.get(i);
            if (set == null) {
                continue;
            }

            for (FiguraTexture texture : set.textures) {
                if (texture != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
