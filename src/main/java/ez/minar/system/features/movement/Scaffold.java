package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

@NewFunction(name = "Scaffold", desc = "Строит под вами блоки без пакетов (Сайлент ротация)", category = Category.MOVEMENT)
public class Scaffold extends Function {

    private final ModeSetting mode = new ModeSetting("Режим", "Grim", "Polar");
    private final ez.minar.system.settings.impl.ListSetting moveCorrection = new ez.minar.system.settings.impl.ListSetting("Коррекция", "Silent", "None");
    private final NumberSetting rotationSpeed = new NumberSetting("Скорость ротации", 90.0, 10.0, 180.0, 5.0);

    private BlockPos currentTargetBlock;
    private Direction currentTargetFace;
    private Vec3d currentHitVec;
    private RotationManager.Rotation smoothTargetRotation;
    private int aimedTicks;
    private boolean isRotating;

    public Scaffold() {
        addSettings(mode, moveCorrection, rotationSpeed);
    }

    @Override
    public void onEnable() {
        resetTarget();
        isRotating = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (isRotating) {
            RotationManager.reset();
            isRotating = false;
        }
        resetTarget();
        super.onDisable();
    }

    @EventHandler
    public void onTick(UpdateEvent event) {
        if (nullCheck.all()) return;

        int selectedSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                selectedSlot = i;
                break;
            }
        }

        if (selectedSlot == -1) {
            if (isRotating) {
                RotationManager.reset();
                isRotating = false;
            }
            resetTarget();
            return;
        }

        double yPos = mc.player.getY() - 1;
        BlockPos posUnder = new BlockPos((int) Math.floor(mc.player.getX()), (int) Math.floor(yPos), (int) Math.floor(mc.player.getZ()));

        PlacementTarget target = findTargetBlock(posUnder);
        if (target == null) {
            Vec3d velocity = mc.player.getVelocity();
            BlockPos predicted = new BlockPos(
                    (int) Math.floor(mc.player.getX() + velocity.x * 0.45),
                    (int) Math.floor(yPos),
                    (int) Math.floor(mc.player.getZ() + velocity.z * 0.45)
            );
            if (!predicted.equals(posUnder)) {
                target = findTargetBlock(predicted);
            }
        }

        if (target != null) {
            boolean changedTarget = currentTargetBlock == null
                    || currentTargetFace == null
                    || !currentTargetBlock.equals(target.block())
                    || currentTargetFace != target.face();
            if (changedTarget) {
                currentTargetBlock = target.block();
                currentTargetFace = target.face();
                currentHitVec = target.hitVec();
                smoothTargetRotation = null;
                aimedTicks = 0;
            } else if (!mode.isEnabled("Polar")) {
                currentHitVec = target.hitVec();
            }

            RotationManager.Rotation exactRot = RotationManager.getRotationTo(currentHitVec);
            float rotSpeed = (float) rotationSpeed.getValue();

            RotationManager.MoveCorrection correction = RotationManager.MoveCorrection.NONE;
            if (moveCorrection.isEnabled("Silent")) correction = RotationManager.MoveCorrection.SILENT;

            RotationManager.Rotation targetRot = mode.isEnabled("Grim")
                    ? getSmoothGrimRotation(exactRot, rotSpeed)
                    : exactRot;

            RotationManager.rotate(targetRot, correction, rotSpeed, rotSpeed, rotSpeed);
            isRotating = true;

            RotationManager.Rotation current = RotationManager.getCurrentRotation();
            if (isAimedAt(exactRot, current)) {
                aimedTicks++;
            } else {
                aimedTicks = 0;
            }

            if (aimedTicks >= 1) {
                int oldSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(selectedSlot);

                BlockHitResult hitResult = new BlockHitResult(currentHitVec, currentTargetFace, currentTargetBlock, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);

                mc.player.getInventory().setSelectedSlot(oldSlot);

                if (mode.isEnabled("Grim")) {
                    resetTarget();
                } else {
                    aimedTicks = 0;
                }
            }
        } else {
            if (mode.isEnabled("Polar") && currentHitVec != null) {
                RotationManager.MoveCorrection correction = moveCorrection.isEnabled("Silent")
                        ? RotationManager.MoveCorrection.SILENT
                        : RotationManager.MoveCorrection.NONE;
                float rotSpeed = (float) rotationSpeed.getValue();
                RotationManager.rotate(RotationManager.getRotationTo(currentHitVec), correction, rotSpeed, rotSpeed, rotSpeed);
                isRotating = true;
                return;
            }

            if (isRotating) {
                RotationManager.reset();
                isRotating = false;
            }
            resetTarget();
        }
    }

    private PlacementTarget findTargetBlock(BlockPos posUnder) {
        if (!mc.world.getBlockState(posUnder).isReplaceable()) return null;

        List<BlockPos> offsets = new ArrayList<>();
        offsets.add(new BlockPos(0, -1, 0));
        offsets.add(new BlockPos(1, 0, 0));
        offsets.add(new BlockPos(-1, 0, 0));
        offsets.add(new BlockPos(0, 0, 1));
        offsets.add(new BlockPos(0, 0, -1));

        for (BlockPos offset : offsets) {
            BlockPos adjacent = posUnder.add(offset);
            BlockState state = mc.world.getBlockState(adjacent);
            if (!state.isReplaceable() && !state.isAir()) {
                Direction face = getDirectionFromOffset(offset).getOpposite();
                Vec3d hitVec = new Vec3d(
                        adjacent.getX() + 0.5 + face.getOffsetX() * 0.5,
                        adjacent.getY() + 0.5 + face.getOffsetY() * 0.5,
                        adjacent.getZ() + 0.5 + face.getOffsetZ() * 0.5
                );
                return new PlacementTarget(adjacent, face, hitVec);
            }
        }

        return null;
    }

    private Direction getDirectionFromOffset(BlockPos offset) {
        if (offset.getX() > 0) return Direction.EAST;
        if (offset.getX() < 0) return Direction.WEST;
        if (offset.getY() > 0) return Direction.UP;
        if (offset.getY() < 0) return Direction.DOWN;
        if (offset.getZ() > 0) return Direction.SOUTH;
        if (offset.getZ() < 0) return Direction.NORTH;
        return Direction.DOWN;
    }

    private boolean isAimedAt(RotationManager.Rotation exact, RotationManager.Rotation current) {
        float yawDelta = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(exact.yaw() - current.yaw()));
        float pitchDelta = Math.abs(exact.pitch() - current.pitch());
        return yawDelta < 8.0F && pitchDelta < 8.0F;
    }

    private RotationManager.Rotation getSmoothGrimRotation(RotationManager.Rotation exact, float rotSpeed) {
        RotationManager.Rotation current = RotationManager.getCurrentRotation();
        float smooth = Math.min(0.55F, Math.max(0.18F, rotSpeed / 220.0F));

        if (smoothTargetRotation == null) {
            smoothTargetRotation = current;
        }

        float yaw = smoothTargetRotation.yaw()
                + net.minecraft.util.math.MathHelper.wrapDegrees(exact.yaw() - smoothTargetRotation.yaw()) * smooth;
        float pitch = smoothTargetRotation.pitch() + (exact.pitch() - smoothTargetRotation.pitch()) * smooth;
        smoothTargetRotation = new RotationManager.Rotation(yaw, pitch);
        return smoothTargetRotation;
    }

    private void resetTarget() {
        currentTargetBlock = null;
        currentTargetFace = null;
        currentHitVec = null;
        smoothTargetRotation = null;
        aimedTicks = 0;
    }

    private record PlacementTarget(BlockPos block, Direction face, Vec3d hitVec) {
    }
}
