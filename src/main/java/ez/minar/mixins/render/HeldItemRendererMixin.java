package ez.minar.mixins.render;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.render.BeautifulHands;
import ez.minar.system.features.render.HandChams;
import ez.minar.system.features.render.HandShader;
import ez.minar.system.features.render.SwingAnimations;
import ez.minar.system.features.render.ViewModel;
import ez.minar.utils.render.pipeline.ChamsPipeline;
import ez.minar.utils.render.pipeline.HandShaderPipeline;
import net.minecraft.block.Block;
import net.minecraft.item.FishingRodItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.SwingAnimationType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaternionfc;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    private float equipProgressMainHand;

    @Shadow
    private float lastEquipProgressMainHand;

    @Shadow
    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
    }

    @Shadow
    private void swingArm(float swingProgress, MatrixStack matrixStack, int i, Arm arm) {
    }

    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                      float equipProgress, float swingProgress, Arm arm) {
    }

    @Shadow
    public abstract void renderItem(net.minecraft.entity.LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode,
                                    MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light);

    @Unique
    private boolean minar$overrideSwing;

    @Unique
    private float minar$tickProgress;

    @Unique
    private float minar$equipProgress;

    @Redirect(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V",
                    ordinal = 0))
    private void minar$skipViewBobPitch(MatrixStack matrices, Quaternionfc rotation) {
        ViewModel viewModel = FunctionManager.getFunction(ViewModel.class);
        if (viewModel != null && viewModel.isStaticHandsEnabled()) {
            return;
        }

        matrices.multiply(rotation);
    }

    @Redirect(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V",
                    ordinal = 1))
    private void minar$skipViewBobYaw(MatrixStack matrices, Quaternionfc rotation) {
        ViewModel viewModel = FunctionManager.getFunction(ViewModel.class);
        if (viewModel != null && viewModel.isStaticHandsEnabled()) {
            return;
        }

        matrices.multiply(rotation);
    }

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void minar$applyViewModel(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                      float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                      OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        ViewModel viewModel = FunctionManager.getFunction(ViewModel.class);
        if (viewModel == null) return;

        boolean mainHand = hand == Hand.MAIN_HAND;
        Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        viewModel.apply(matrices, arm);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void minar$captureSwingState(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                         float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                         OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        HandChams chams = FunctionManager.getFunction(HandChams.class);
        if (chams != null && chams.shouldHideHandsTexture() && !ChamsPipeline.isRenderingMask()) {
            minar$overrideSwing = false;
            ci.cancel();
            return;
        }

        HandShader handShader = FunctionManager.getFunction(HandShader.class);
        if (handShader != null && handShader.shouldHideHands() && !HandShaderPipeline.isRenderingMask()) {
            minar$overrideSwing = false;
            ci.cancel();
            return;
        }

        if (minar$renderBeautifulHandsCustom(player, tickProgress, hand, swingProgress, item,
                equipProgress, matrices, orderedRenderCommandQueue, light)) {
            minar$overrideSwing = false;
            ci.cancel();
            return;
        }

        minar$tickProgress = tickProgress;
        minar$equipProgress = equipProgress;
        minar$overrideSwing = minar$shouldOverride(player, hand, item);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void minar$clearSwingState(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                       float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                       OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        minar$overrideSwing = false;
    }

    @Redirect(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))
    private void minar$redirectEquipOffset(HeldItemRenderer renderer, MatrixStack matrices, Arm arm, float equipProgress) {
        if (!minar$overrideSwing) {
            applyEquipOffset(matrices, arm, equipProgress);
        }
    }

    @Redirect(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V"))
    private void minar$redirectSwingArm(HeldItemRenderer renderer, float swingProgress, MatrixStack matrices, int side, Arm arm) {
        SwingAnimations swingAnimations = FunctionManager.getFunction(SwingAnimations.class);
        if (minar$overrideSwing && swingAnimations != null) {
            swingAnimations.renderSwordAnimation(matrices, swingProgress, minar$equipProgress, arm,
                    minar$tickProgress, lastEquipProgressMainHand, equipProgressMainHand);
            return;
        }

        swingArm(swingProgress, matrices, side, arm);
    }

    @Redirect(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void minar$redirectRenderItem(HeldItemRenderer renderer, net.minecraft.entity.LivingEntity entity,
                                          ItemStack stack, ItemDisplayContext renderMode, MatrixStack matrices,
                                          OrderedRenderCommandQueue orderedRenderCommandQueue, int light) {
        minar$applyViewModelRotation(matrices, minar$armFromRenderMode(renderMode));
        renderItem(entity, stack, renderMode, matrices, orderedRenderCommandQueue, light);
    }

    @Unique
    private boolean minar$shouldOverride(AbstractClientPlayerEntity player, Hand hand, ItemStack item) {
        SwingAnimations swingAnimations = FunctionManager.getFunction(SwingAnimations.class);
        return swingAnimations != null
                && swingAnimations.shouldOverrideVanilla()
                && hand == Hand.MAIN_HAND
                && !item.isEmpty()
                && !item.contains(DataComponentTypes.MAP_ID)
                && !item.isOf(Items.CROSSBOW)
                && !player.isUsingSpyglass()
                && !player.isUsingRiptide()
                && !(player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand)
                && item.getSwingAnimation().type() == SwingAnimationType.WHACK;
    }

    @Unique
    private boolean minar$renderBeautifulHandsCustom(AbstractClientPlayerEntity player, float tickProgress, Hand hand,
                                                     float swingProgress, ItemStack item, float equipProgress,
                                                     MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        BeautifulHands beautifulHands = FunctionManager.getFunction(BeautifulHands.class);
        if (beautifulHands == null
                || !beautifulHands.isEnabled()
                || item.contains(DataComponentTypes.MAP_ID)
                || player.isUsingSpyglass()
                || player.isUsingRiptide()) {
            return false;
        }

        if (item.isEmpty() && !beautifulHands.shouldRenderEmptyHand()) {
            return false;
        }

        if (minar$usesVanillaFirstPersonFallback(item)) {
            return false;
        }

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == Arm.RIGHT ? 1 : -1;
        boolean right = arm == Arm.RIGHT;
        double frameStep = beautifulHands.frameStep();
        Vec3d velocity = player.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yawDelta = player.lastYaw - player.getYaw();
        float pitchDelta = player.lastPitch - player.getPitch();

        matrices.push();
        ViewModel viewModel = FunctionManager.getFunction(ViewModel.class);
        if (viewModel != null) {
            viewModel.apply(matrices, arm);
        }

        beautifulHands.applyHandOffset(matrices, arm);
        beautifulHands.applyMovement(matrices, arm, horizontalSpeed, velocity.y, yawDelta, pitchDelta, swingProgress, frameStep);

        if (item.isEmpty()) {
            minar$applyViewModelRotation(matrices, arm);
            renderArmHoldingItem(matrices, queue, light, equipProgress, beautifulHands.getArmSwingProgress(swingProgress), arm);
            matrices.pop();
            return true;
        }

        float swingRot = beautifulHands.swingRotation(swingProgress);
        float swing = beautifulHands.easeInOutBack(MathHelper.sin(swingProgress * (float) Math.PI));
        boolean alternateLeft = beautifulHands.useLeftAttack(minar$isAttackPressed(), swingProgress);

        minar$applyHoldMyItemsSwing(matrices, beautifulHands, item, arm, side, swing, swingRot, alternateLeft);

        matrices.push();
        minar$applyHeldArmPose(matrices, item, arm, side, equipProgress, swingProgress);
        
        matrices.push();
        minar$applyViewModelRotation(matrices, arm);
        renderArmHoldingItem(matrices, queue, light, 0.0F, beautifulHands.getArmSwingProgress(swingProgress), arm);
        matrices.pop();

        minar$applyHeldItemPose(matrices, beautifulHands, item, side, swing, swingRot, horizontalSpeed, frameStep);
        minar$applyViewModelRotation(matrices, arm);
        renderItem(player, item, minar$getBeautifulHandsDisplayContext(item, right), matrices, queue, light);

        matrices.pop();
        matrices.pop();
        return true;
    }

    @Unique
    private ItemDisplayContext minar$getBeautifulHandsDisplayContext(ItemStack stack, boolean right) {
        if (minar$usesFirstPersonModel(stack)) {
            return right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        }

        return right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Unique
    private Arm minar$armFromRenderMode(ItemDisplayContext renderMode) {
        return renderMode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? Arm.LEFT : Arm.RIGHT;
    }

    @Unique
    private void minar$applyViewModelRotation(MatrixStack matrices, Arm arm) {
        ViewModel viewModel = FunctionManager.getFunction(ViewModel.class);
        if (viewModel != null) {
            viewModel.applyRotation(matrices, arm);
        }
    }

    @Unique
    private void minar$applyHoldMyItemsSwing(MatrixStack matrices, BeautifulHands beautifulHands, ItemStack stack, Arm arm,
                                             int side, float swing, float swingRot, boolean alternateLeft) {
        if (beautifulHands.useMb3dCompat()) return;

        boolean leftSlash = alternateLeft || stack.isIn(ItemTags.AXES) || stack.getUseAction() == UseAction.SPEAR
                || stack.getUseAction() == UseAction.TRIDENT || stack.getUseAction() == UseAction.BLOCK;
        if (stack.isIn(ItemTags.SHOVELS)) {
            matrices.translate(0.0F, 0.15F * swingRot, -0.25F * swingRot - 0.2F * swing);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15.0F * swingRot));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-35.0F * swingRot + 30.0F * swing));
            return;
        }

        if (leftSlash && stack.isIn(ItemTags.SWORDS)) {
            matrices.translate(0.8F * side * swingRot, 0.3F * swingRot, -0.5F * swing);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15.0F * swingRot * side));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-20.0F * swingRot));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-70.0F * swingRot * side));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
        } else if (!leftSlash && stack.isIn(ItemTags.SWORDS)) {
            matrices.translate(-0.55F * side * swingRot, -0.8F * swingRot, -0.77F * swing);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(5.0F * swingRot * side));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swingRot));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(70.0F * swingRot * side));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(50.0F * swing));
        } else if (minar$isToolLike(stack)) {
            matrices.translate(0.1F * side * swingRot, 0.1F * swingRot, -0.5F * swing);
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swingRot));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * swingRot * side));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
        } else {
            matrices.translate(0.1F * side * swingRot, 0.1F * swingRot, -0.1F * swing);
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-30.0F * swingRot));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10.0F * swingRot * side));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(40.0F * swing));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(10.0F * swing * side));
        }
    }

    @Unique
    private void minar$applyHeldArmPose(MatrixStack matrices, ItemStack stack, Arm arm, int side, float equipProgress, float swingProgress) {
        if (stack.getUseAction() == UseAction.BLOCK) {
            matrices.translate(0.0F, -0.2F, 0.0F);
        } else if (stack.isIn(ItemTags.LANTERNS) || stack.isIn(ItemTags.HANGING_SIGNS)) {
            matrices.translate(0.1F * side, 0.0F, -0.1F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F));
        }

        matrices.translate(side, -equipProgress * 0.3F, 0.3F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * side));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F * side));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F));
        minar$applyAltSwing(matrices, arm, swingProgress);
        matrices.scale(0.9F, 0.9F, 0.9F);

        if (minar$isThrowable(stack)) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F));
            matrices.translate(-0.15F * side, 0.1F, 0.1F);
            return;
        }

        if (stack.getUseAction() == UseAction.BLOCK) {
            matrices.translate(side * 0.22F, -0.04F, -0.06F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(18.0F * side));
        } else if (stack.getUseAction() == UseAction.BOW || stack.isOf(Items.CROSSBOW)) {
            matrices.translate(side * -0.05F, -0.05F, -0.18F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18.0F));
        } else if (stack.getUseAction() == UseAction.TRIDENT || stack.getUseAction() == UseAction.SPEAR) {
            matrices.translate(side * -0.18F, 0.0F, -0.15F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(20.0F * side));
        } else {
            matrices.translate(side * 0.08F, 0.02F, -0.02F);
        }
    }

    @Unique
    private void minar$applyHeldItemPose(MatrixStack matrices, BeautifulHands beautifulHands,
                                         ItemStack stack, int side, float swing,
                                         float swingRot, double horizontalSpeed, double frameStep) {
        if (minar$usesFirstPersonModel(stack)) {
            minar$applyFirstPersonHeldItemPose(matrices, stack, side, swing, horizontalSpeed, frameStep, beautifulHands);
            return;
        }

        matrices.translate(0.0F, -0.5F, -0.1F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-65.0F * side));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F));

        UseAction useAction = stack.getUseAction();
        if (useAction == UseAction.BLOCK) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(160.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-60.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70.0F));
            matrices.scale(0.75F, 0.75F, 0.75F);
            matrices.translate(0.32F * side, 0.35F, 0.15F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F * side));
        } else if (useAction == UseAction.BOW || stack.isOf(Items.CROSSBOW)) {
            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(75.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(60.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F * side));
            matrices.translate(-0.1F * side, -0.2F, 0.0F);
            matrices.scale(1.12F, 1.12F, 1.12F);
        } else if (useAction == UseAction.TRIDENT || useAction == UseAction.SPEAR) {
            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(75.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F - 40.0F * swingRot));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F * side));
            matrices.translate(-0.3F * side, 0.1F * swingRot, -0.1F * swingRot);
            matrices.scale(1.15F, 1.15F, 1.15F);
        } else if (stack.isIn(ItemTags.SHOVELS)) {
            matrices.translate(0.07F * side, 0.0F, 0.05F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15.0F - 80.0F * swingRot + 30.0F * swing));
        } else {
            // Revert base transformation applied earlier
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(10.0F));
            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(-65.0F * side));
            matrices.translate(0.0F, 0.5F, 0.1F);

            // Perfect mathematical alignment with the arm (vanilla third person)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(92.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-41.0F * side));
            matrices.translate(side * -0.075F, -0.5375F, 0.5125F);

            // Apply user offsets
            matrices.translate(beautifulHands.getItemX() * side, beautifulHands.getItemY(), beautifulHands.getItemZ());
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(beautifulHands.getItemRotX()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(beautifulHands.getItemRotY() * side));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(beautifulHands.getItemRotZ() * side));

            if (useAction != UseAction.BLOCK) {
                matrices.scale(1.2F, 1.2F, 1.2F);
            }
        }

        if (stack.isOf(Items.NETHER_STAR) || stack.isOf(Items.END_CRYSTAL)) {
            float idle = (float) (System.nanoTime() / 1_000_000_000.0D);
            matrices.translate(0.0F, 0.25F + 0.02F * MathHelper.sin(idle * 3.0F), 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(3.0F * MathHelper.sin(idle * 6.0F)));
            float scale = 1.0F + 0.01F * MathHelper.sin(idle * 12.0F);
            matrices.scale(scale, scale, scale);
        }
    }

    @Unique
    private void minar$applyFirstPersonHeldItemPose(MatrixStack matrices, ItemStack stack, int side,
                                                   float swing, double horizontalSpeed, double frameStep,
                                                   BeautifulHands beautifulHands) {
        matrices.translate(-0.18F * side, 0.18F, -0.28F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-18.0F * side));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8.0F * side));

        if (stack.getUseAction() == UseAction.EAT || stack.getUseAction() == UseAction.DRINK) {
            matrices.translate(0.08F * side, -0.04F, 0.04F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(12.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-10.0F * side));
        } else if (Block.getBlockFromItem(stack.getItem()) != net.minecraft.block.Blocks.AIR) {
            matrices.translate(0.0F, -0.02F, 0.02F);
            matrices.scale(0.9F, 0.9F, 0.9F);
        }

        if (minar$isSoftItem(stack)) {
            beautifulHands.applySoftItemBounce(matrices, swing, horizontalSpeed, frameStep);
        }
    }

    @Unique
    private void minar$applyAltSwing(MatrixStack matrices, Arm arm, float swingProgress) {
        int side = arm == Arm.RIGHT ? 1 : -1;
        float swing = MathHelper.sin(swingProgress * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + swing * 0.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
    }

    @Unique
    private boolean minar$isAttackPressed() {
        return net.minecraft.client.MinecraftClient.getInstance().options.attackKey.isPressed();
    }

    @Unique
    private boolean minar$isThrowable(ItemStack stack) {
        return stack.isOf(Items.EXPERIENCE_BOTTLE)
                || stack.isOf(Items.EGG)
                || stack.isOf(Items.ENDER_EYE)
                || stack.isOf(Items.SNOWBALL)
                || stack.isOf(Items.ENDER_PEARL)
                || stack.isOf(Items.SPLASH_POTION)
                || stack.isOf(Items.LINGERING_POTION);
    }

    @Unique
    private boolean minar$isToolLike(ItemStack stack) {
        return stack.isIn(ItemTags.SWORDS)
                || stack.isIn(ItemTags.AXES)
                || stack.isIn(ItemTags.PICKAXES)
                || stack.isIn(ItemTags.HOES)
                || stack.isIn(ItemTags.SHOVELS)
                || stack.isIn(ItemTags.MELEE_WEAPON_ENCHANTABLE)
                || stack.isIn(ItemTags.MINING_ENCHANTABLE)
                || stack.getItem() instanceof FishingRodItem
                || stack.isOf(Items.SHEARS)
                || stack.isOf(Items.CARROT_ON_A_STICK)
                || stack.isOf(Items.WARPED_FUNGUS_ON_A_STICK);
    }

    @Unique
    private boolean minar$isSimpleHeldItem(ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.getItem());
        boolean simpleBlock = block != net.minecraft.block.Blocks.AIR
                && !block.getDefaultState().isIn(BlockTags.RAILS)
                && !block.getDefaultState().isIn(BlockTags.CLIMBABLE)
                && !block.getDefaultState().isIn(BlockTags.COMBINATION_STEP_SOUND_BLOCKS);
        return simpleBlock
                || stack.getUseAction() == UseAction.EAT
                || stack.getUseAction() == UseAction.DRINK
                || stack.getUseAction() == UseAction.BRUSH
                || stack.isIn(ItemTags.BANNERS)
                || stack.isIn(ItemTags.LANTERNS)
                || stack.isOf(Items.STRING)
                || stack.isOf(Items.REDSTONE)
                || stack.isOf(Items.LEVER)
                || stack.isOf(Items.TRIPWIRE_HOOK);
    }

    @Unique
    private boolean minar$usesFirstPersonModel(ItemStack stack) {
        return minar$isSimpleHeldItem(stack);
    }

    @Unique
    private boolean minar$usesVanillaFirstPersonFallback(ItemStack stack) {
        return minar$isSimpleHeldItem(stack);
    }

    @Unique
    private boolean minar$isSoftItem(ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.getItem());
        return stack.isOf(Items.FEATHER)
                || stack.isOf(Items.SLIME_BALL)
                || stack.isOf(Items.PUFFERFISH)
                || stack.isOf(Items.SLIME_BLOCK)
                || stack.isOf(Items.HONEY_BLOCK)
                || block.getDefaultState().isIn(BlockTags.FLOWERS)
                || block.getDefaultState().isIn(BlockTags.LEAVES)
                || block.getDefaultState().isIn(BlockTags.SAPLINGS)
                || block.getDefaultState().isIn(BlockTags.SWORD_EFFICIENT);
    }
}
