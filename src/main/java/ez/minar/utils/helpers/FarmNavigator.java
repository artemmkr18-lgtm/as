package ez.minar.utils.helpers;

import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.MoveCorrection;
import ez.minar.system.managers.RotationManager.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Lightweight walk-to-goal helper used instead of Baritone.
 */
public final class FarmNavigator {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private FarmNavigator() {
    }

    public static void stop() {
        if (MC.options == null) return;
        MC.options.forwardKey.setPressed(false);
        MC.options.backKey.setPressed(false);
        MC.options.leftKey.setPressed(false);
        MC.options.rightKey.setPressed(false);
        MC.options.jumpKey.setPressed(false);
        RotationManager.reset();
    }

    public static boolean goTo(BlockPos pos, double arriveDist) {
        if (MC.player == null || pos == null) return true;
        return goTo(Vec3d.ofCenter(pos), arriveDist);
    }

    public static boolean goTo(Vec3d target, double arriveDist) {
        if (MC.player == null || target == null || MC.options == null) return true;

        double dx = target.x - MC.player.getX();
        double dz = target.z - MC.player.getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq <= arriveDist * arriveDist) {
            MC.options.forwardKey.setPressed(false);
            MC.options.jumpKey.setPressed(false);
            return true;
        }

        float yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(target.y - MC.player.getEyeY(), Math.sqrt(distSq))),
                -90.0,
                90.0
        );
        RotationManager.rotate(new Rotation(yaw, pitch), MoveCorrection.SILENT, 180.0F, 180.0F, 90.0F);
        MC.options.forwardKey.setPressed(true);
        MC.options.jumpKey.setPressed(MC.player.horizontalCollision && MC.player.isOnGround());
        return false;
    }

    public static void lookAt(Vec3d target) {
        if (MC.player == null || target == null) return;
        RotationManager.rotate(RotationManager.getRotationTo(target), MoveCorrection.SILENT, 180.0F, 180.0F, 90.0F);
    }

    public static void lookAtBlock(BlockPos pos) {
        lookAt(Vec3d.ofCenter(pos));
    }
}
