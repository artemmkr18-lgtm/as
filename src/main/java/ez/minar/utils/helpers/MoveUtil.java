package ez.minar.utils.helpers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec2f;

public class MoveUtil {

    public static boolean isMoving() {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.player != null
                && mc.player.input.getMovementInput().lengthSquared() > 1.0E-7f;
    }

    public static double[] calculateDirection(double speed) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            return new double[]{0.0, 0.0};
        }

        Vec2f input = mc.player.input.getMovementInput();

        float strafe = input.x;
        float forward = input.y;
        float yaw = mc.player.getYaw();

        ez.minar.system.features.combat.TargetStrafe targetStrafe = ez.minar.system.api.FunctionManager.getFunction(ez.minar.system.features.combat.TargetStrafe.class);
        if (targetStrafe != null && targetStrafe.isEnabled() && targetStrafe.getTarget() != null) {
            yaw = ez.minar.system.managers.RotationManager.getRotationTo(targetStrafe.getTarget().getBoundingBox().getCenter()).yaw();
            forward = 1.0F;
            strafe = (float) (targetStrafe.getDirection() * targetStrafe.getStrafePower());
        }

        if (forward != 0.0F) {
            if (strafe > 0.0F) {
                yaw += forward > 0.0F ? -45F : 45F;
            } else if (strafe < 0.0F) {
                yaw += forward > 0.0F ? 45F : -45F;
            }

            strafe = 0.0F;
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }

        double sin = Math.sin(Math.toRadians(yaw + 90.0F));
        double cos = Math.cos(Math.toRadians(yaw + 90.0F));

        double motionX = forward * speed * cos + strafe * speed * sin;
        double motionZ = forward * speed * sin - strafe * speed * cos;

        return new double[]{motionX, motionZ};
    }

    public static void setMotion(double speed) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || !isMoving()) {
            return;
        }

        double[] dir = calculateDirection(speed);

        mc.player.setVelocity(
                dir[0],
                mc.player.getVelocity().y,
                dir[1]
        );
    }

    public static void setMotion(double speed, double y) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || !isMoving()) {
            return;
        }

        double[] dir = calculateDirection(speed);

        mc.player.setVelocity(
                dir[0],
                y,
                dir[1]
        );
    }

    public static void setMotionY(double y) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            return;
        }

        mc.player.setVelocity(
                mc.player.getVelocity().x,
                y,
                mc.player.getVelocity().z
        );
    }
}