package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@NewFunction(name = "SmoothCamera", desc = "Плавное догоняние камеры", category = Category.RENDER)
public class SmoothCamera extends Function {
    private static final double REFERENCE_FPS = 60.0;
    private static final float MAX_DELTA_SECONDS = 0.1f;

    public static SmoothCamera Instance;

    private final NumberSetting speed = new NumberSetting("Скорость", 0.08, 0.01, 0.3, 0.01);
    private final BooleanSetting thirdPersonOnly = new BooleanSetting("Только от третьего лица", true);

    private final float[] rotationOut = new float[2];
    private float smoothYaw;
    private float smoothPitch;
    private Vec3d smoothPos;
    private boolean initialized;

    public SmoothCamera() {
        Instance = this;
        addSettings(speed, thirdPersonOnly);
    }

    public static SmoothCamera getInstance() {
        return Instance;
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        initialized = false;
        smoothPos = null;
    }

    public float[] smoothRotation(float targetYaw, float targetPitch) {
        float factor = nextFactor();
        if (!initialized) {
            smoothYaw = targetYaw;
            smoothPitch = targetPitch;
            initialized = true;
        } else {
            smoothYaw += MathHelper.wrapDegrees(targetYaw - smoothYaw) * factor;
            smoothPitch += (targetPitch - smoothPitch) * factor;
        }
        rotationOut[0] = smoothYaw;
        rotationOut[1] = smoothPitch;
        return rotationOut;
    }

    public Vec3d smoothPosition(Vec3d target) {
        float factor = nextFactor();
        if (smoothPos == null) {
            smoothPos = target;
            return smoothPos;
        }
        smoothPos = new Vec3d(
                smoothPos.x + (target.x - smoothPos.x) * factor,
                smoothPos.y + (target.y - smoothPos.y) * factor,
                smoothPos.z + (target.z - smoothPos.z) * factor);
        return smoothPos;
    }

    public boolean shouldApply() {
        if (!isEnabled()) return false;
        if (!thirdPersonOnly.isEnabled()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.options != null && !mc.options.getPerspective().isFirstPerson();
    }

    /**
     * The slider is a per-frame lerp factor at 60 FPS; this keeps the feel identical when the frame
     * time differs instead of making the camera stiffer on high FPS. Read from the tick counter so
     * rotation and position, which both ask within the same frame, agree on one value.
     */
    private float nextFactor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float deltaSeconds = MAX_DELTA_SECONDS;
        if (mc.getRenderTickCounter() != null) {
            deltaSeconds = Math.clamp(mc.getRenderTickCounter().getDynamicDeltaTicks() / 20.0f,
                    0.001f, MAX_DELTA_SECONDS);
        }
        double base = 1.0 - speed.getValue();
        if (base <= 0.0) return 1f;
        return (float) Math.clamp(1.0 - Math.pow(base, deltaSeconds * REFERENCE_FPS), 0.0, 1.0);
    }
}
