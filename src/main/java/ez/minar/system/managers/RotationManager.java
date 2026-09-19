package ez.minar.system.managers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class RotationManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final long IDLE_TIMEOUT_MS = 120L;
    private static final float MAX_SERVER_ROTATION_STEP = 30.0F;

    private static Rotation currentRotation = Rotation.ZERO;
    private static Rotation previousRotation = Rotation.ZERO;
    private static Rotation renderRotation = Rotation.ZERO;
    private static Rotation targetRotation;
    private static float yawSpeed;
    private static float pitchSpeed;
    private static float returnSpeed;
    private static MoveCorrection moveCorrection = MoveCorrection.NONE;
    private static long lastRotateTime;
    private static State state = State.IDLE;
    private static boolean skipGcd;
    private static boolean keepClientRenderPitch;
    private static float returnYawVelocity;
    private static float returnPitchVelocity;

    public static void update() {
        previousRotation = currentRotation;

        if (mc.player == null) {
            reset();
            return;
        }

        if (targetRotation == null) {
            currentRotation = getPlayerRotation();
            state = State.IDLE;
            return;
        }

        if (System.currentTimeMillis() - lastRotateTime > IDLE_TIMEOUT_MS) {
            Rotation playerRotation = getPlayerRotation();
            if (currentRotation.differenceValue(playerRotation) < 1.0F) {
                targetRotation = null;
                currentRotation = playerRotation;
                state = State.IDLE;
                return;
            }

            state = State.ROTATING_BACK;

            float yawDiff = getAngleDifference(currentRotation.yaw(), playerRotation.yaw());
            float pitchDiff = getAngleDifference(currentRotation.pitch(), playerRotation.pitch());

            float desiredYawVelocity = Math.min(returnSpeed, Math.max(0.35F, Math.abs(yawDiff) * 0.20F));
            float desiredPitchVelocity = Math.min(returnSpeed, Math.max(0.25F, Math.abs(pitchDiff) * 0.18F));
            returnYawVelocity = moveTowards(returnYawVelocity, desiredYawVelocity, 1.15F);
            returnPitchVelocity = moveTowards(returnPitchVelocity, desiredPitchVelocity, 0.90F);

            float yawStep = Math.min(Math.abs(yawDiff), returnYawVelocity);
            float pitchStep = Math.min(Math.abs(pitchDiff), returnPitchVelocity);

            if (yawStep >= Math.abs(yawDiff) && pitchStep >= Math.abs(pitchDiff)) {
                reset();
                return;
            }

            Rotation backStep = new Rotation(
                    moveTowardsAngle(currentRotation.yaw(), playerRotation.yaw(), yawStep),
                    moveTowardsAngle(currentRotation.pitch(), playerRotation.pitch(), pitchStep)
            );
            currentRotation = skipGcd ? backStep : correctRotation(backStep);
            return;
        }

        state = State.ROTATING;
        returnYawVelocity = 0.0F;
        returnPitchVelocity = 0.0F;
        Rotation fwdStep = new Rotation(
                moveTowardsAngle(currentRotation.yaw(), targetRotation.yaw(), yawSpeed),
                moveTowardsAngle(currentRotation.pitch(), targetRotation.pitch(), pitchSpeed)
        );
        currentRotation = skipGcd ? fwdStep : correctRotation(fwdStep);
    }

    public static void updateRender(float tickDelta) {
        float yaw = interpolateAngle(previousRotation.yaw(), currentRotation.yaw(), tickDelta);
        float pitch = MathHelper.lerp(tickDelta, previousRotation.pitch(), currentRotation.pitch());
        renderRotation = new Rotation(yaw, pitch <= -85.0F ? 0.0F : pitch);
    }

    public static void rotate(Rotation rotation, MoveCorrection moveCorrection, float yawSpeed, float pitchSpeed, float returnSpeed) {
        rotate(rotation, moveCorrection, yawSpeed, pitchSpeed, returnSpeed, false);
    }

    public static void rotate(Rotation rotation, MoveCorrection moveCorrection, float yawSpeed, float pitchSpeed, float returnSpeed, boolean skipGcd) {
        rotate(rotation, moveCorrection, yawSpeed, pitchSpeed, returnSpeed, skipGcd, MAX_SERVER_ROTATION_STEP);
    }

    public static void rotate(Rotation rotation, MoveCorrection moveCorrection, float yawSpeed, float pitchSpeed, float returnSpeed, boolean skipGcd, float maxStep) {
        if (mc.player == null) return;

        float baseAngle = (currentRotation != null && state != State.IDLE) ? currentRotation.yaw() : getPlayerRotation().yaw();
        float adjustedYaw = adjustAngle(baseAngle, rotation.yaw());
        targetRotation = new Rotation(adjustedYaw, MathHelper.clamp(rotation.pitch(), -90.0F, 90.0F));
        RotationManager.yawSpeed = clampServerStep(yawSpeed, maxStep);
        RotationManager.pitchSpeed = clampServerStep(pitchSpeed, maxStep);
        RotationManager.returnSpeed = clampServerStep(returnSpeed, maxStep);
        RotationManager.moveCorrection = moveCorrection;
        RotationManager.skipGcd = skipGcd;
        lastRotateTime = System.currentTimeMillis();
        state = State.ROTATING;

        Rotation stepped = new Rotation(
                moveTowardsAngle(currentRotation.yaw(), targetRotation.yaw(), RotationManager.yawSpeed),
                moveTowardsAngle(currentRotation.pitch(), targetRotation.pitch(), RotationManager.pitchSpeed)
        );
        currentRotation = skipGcd ? stepped : correctRotation(stepped);
    }

    public static void rotate(Rotation rotation, float yawSpeed, float pitchSpeed, float returnSpeed) {
        rotate(rotation, MoveCorrection.NONE, yawSpeed, pitchSpeed, returnSpeed, false);
    }

    public static void reset() {
        targetRotation = null;
        state = State.IDLE;
        moveCorrection = MoveCorrection.NONE;
        skipGcd = false;
        keepClientRenderPitch = false;
        returnYawVelocity = 0.0F;
        returnPitchVelocity = 0.0F;
        if (mc.player != null) {
            currentRotation = getPlayerRotation();
            previousRotation = currentRotation;
            renderRotation = currentRotation;
        }
    }

    public static void releaseSmooth(float speed) {
        if (mc.player == null) {
            reset();
            return;
        }
        if (targetRotation == null) {
            targetRotation = currentRotation;
        }
        if (state != State.ROTATING_BACK) {
            returnYawVelocity = 0.0F;
            returnPitchVelocity = 0.0F;
        }
        returnSpeed = clampServerStep(speed);
        lastRotateTime = 0L;
        state = State.ROTATING_BACK;
    }

    public static boolean isActive() {
        return mc.player != null && state != State.IDLE;
    }

    public static float getYaw(float fallback) {
        return isActive() ? currentRotation.yaw() : fallback;
    }

    public static float getPitch(float fallback) {
        return isActive() ? currentRotation.pitch() : fallback;
    }

    public static float getRenderYaw(float fallback) {
        return isActive() ? renderRotation.yaw() : fallback;
    }

    public static float getRenderPitch(float fallback) {
        return isActive() && !keepClientRenderPitch ? renderRotation.pitch() : fallback;
    }

    public static Rotation getCurrentRotation() {
        return currentRotation;
    }

    public static void setKeepClientRenderPitch(boolean value) {
        keepClientRenderPitch = value;
    }

    public static boolean shouldCorrectMovement() {
        return isActive() && moveCorrection != MoveCorrection.NONE;
    }

    public static boolean shouldCorrectInputSilently() {
        return isActive() && moveCorrection == MoveCorrection.SILENT;
    }

    public static Vec2f correctMovementInput(float forward, float strafe) {
        if (mc.player == null || !shouldCorrectInputSilently() || (forward == 0.0F && strafe == 0.0F)) {
            return new Vec2f(strafe, forward);
        }

        double angle = MathHelper.wrapDegrees(Math.toDegrees(direction(mc.player.getYaw(), forward, strafe)));
        float closestForward = forward;
        float closestStrafe = strafe;
        float closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward++) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe++) {
                if (predictedForward == 0.0F && predictedStrafe == 0.0F) continue;

                double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(currentRotation.yaw(), predictedForward, predictedStrafe)));
                float difference = (float) Math.abs(MathHelper.wrapDegrees(angle - predictedAngle));
                if (difference < closestDifference) {
                    closestDifference = difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        return new Vec2f(closestStrafe, closestForward);
    }

    public static Rotation getRotationTo(Vec3d pos) {
        Vec3d eyes = mc.player.getEyePos();
        double deltaX = pos.x - eyes.x;
        double deltaY = pos.y - eyes.y;
        double deltaZ = pos.z - eyes.z;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        return new Rotation(yaw, pitch);
    }

    public static Vec3d getNearestPoint(net.minecraft.entity.LivingEntity entity) {
        Vec3d eyes = mc.player.getEyePos();
        net.minecraft.util.math.Box box = entity.getBoundingBox();
        double x = MathHelper.clamp(eyes.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eyes.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eyes.z, box.minZ, box.maxZ);
        Vec3d nearest = new Vec3d(x, y, z);
        // When clipped into the hitbox, nearest ≈ eyes → degenerate yaw/pitch (atan2 of ~0).
        // Aim at body center so rotation and hit-aim checks stay valid.
        if (eyes.squaredDistanceTo(nearest) < 1.0E-4) {
            return box.getCenter();
        }
        return nearest;
    }

    private static Rotation getPlayerRotation() {
        return mc.player == null ? Rotation.ZERO : new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private static Rotation correctRotation(Rotation rotation) {
        double d = mc.options.getMouseSensitivity().getValue() * 0.6000000238418579 + 0.20000000298023224;
        double f = d * d * d * 8.0;
        
        float deltaYaw = MathHelper.wrapDegrees(rotation.yaw() - currentRotation.yaw());
        float deltaPitch = rotation.pitch() - currentRotation.pitch();
        
        int mouseX = (int) Math.round(deltaYaw / (f * 0.15));
        int mouseY = (int) Math.round(deltaPitch / (f * 0.15));
        
        float fixedDeltaYaw = (float) ((double) mouseX * f) * 0.15F;
        float fixedDeltaPitch = (float) ((double) mouseY * f) * 0.15F;
        
        float newYaw = currentRotation.yaw() + fixedDeltaYaw;
        float newPitch = MathHelper.clamp(currentRotation.pitch() + fixedDeltaPitch, -90.0F, 90.0F);
        
        return new Rotation(newYaw, newPitch);
    }

    private static float clampServerStep(float speed) {
        return clampServerStep(speed, MAX_SERVER_ROTATION_STEP);
    }

    private static float clampServerStep(float speed, float maxStep) {
        return MathHelper.clamp(speed, 0.0F, Math.max(0.0F, maxStep));
    }

    private static float moveTowardsAngle(float current, float target, float speed) {
        float difference = getAngleDifference(current, target);
        return Math.abs(difference) <= speed ? target : current + Math.signum(difference) * speed;
    }

    private static float moveTowards(float current, float target, float step) {
        if (current < target) return Math.min(current + step, target);
        return Math.max(current - step, target);
    }

    private static float getAngleDifference(float current, float target) {
        return MathHelper.wrapDegrees(target - current);
    }

    private static float adjustAngle(float currentAngle, float targetAngle) {
        float difference = MathHelper.wrapDegrees(targetAngle - currentAngle);
        return currentAngle + difference;
    }

    private static float interpolateAngle(float start, float end, float delta) {
        return start + MathHelper.wrapDegrees(end - start) * delta;
    }

    private static double direction(float yaw, float forward, float strafe) {
        if (forward < 0.0F) {
            yaw += 180.0F;
        }

        float factor = 1.0F;
        if (forward < 0.0F) {
            factor = -0.5F;
        } else if (forward > 0.0F) {
            factor = 0.5F;
        }

        if (strafe > 0.0F) {
            yaw -= 90.0F * factor;
        }
        if (strafe < 0.0F) {
            yaw += 90.0F * factor;
        }

        return Math.toRadians(yaw);
    }

    private enum State {
        IDLE,
        ROTATING,
        ROTATING_BACK
    }

    public enum MoveCorrection {
        NONE,
        DIRECT,
        SILENT
    }

    public record Rotation(float yaw, float pitch) {
        public static final Rotation ZERO = new Rotation(0.0F, 0.0F);

        public float differenceValue(Rotation other) {
            return Math.abs(MathHelper.wrapDegrees(other.yaw - yaw)) + Math.abs(MathHelper.wrapDegrees(other.pitch - pitch));
        }
    }
}
