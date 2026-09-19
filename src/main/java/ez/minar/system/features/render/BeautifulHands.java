package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@NewFunction(name = "BeautifulHands", desc = "Shows first-person hands while holding items", category = Category.RENDER)
public class BeautifulHands extends Function {
    public static BeautifulHands Instance;

    private final NumberSetting animationSpeed = new NumberSetting("Скорость анимаций", 30.0, 1.0, 80.0, 1.0);
    private final NumberSetting swingSpeed = new NumberSetting("Скорость взмаха", 6.0, 1.0, 20.0, 1.0);
    private final BooleanSetting movementAnimation = new BooleanSetting("Анимация движения", true);
    private final BooleanSetting punchingAnimation = new BooleanSetting("Пустая рука", true);

    private final NumberSetting rightX = new NumberSetting("Правая X", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting rightY = new NumberSetting("Правая Y", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting rightZ = new NumberSetting("Правая Z", 0.0, -2.0, 2.0, 0.05);

    private final NumberSetting leftX = new NumberSetting("Левая X", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting leftY = new NumberSetting("Левая Y", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting leftZ = new NumberSetting("Левая Z", 0.0, -2.0, 2.0, 0.05);

    private final NumberSetting itemX = new NumberSetting("Позиция X", 0.7, -2.0, 2.0, 0.05);
    private final NumberSetting itemY = new NumberSetting("Позиция Y", 1.3, -2.0, 2.0, 0.05);
    private final NumberSetting itemZ = new NumberSetting("Позиция Z", 0.2, -2.0, 2.0, 0.05);
    private final NumberSetting itemRotX = new NumberSetting("Вращение X (Меч)", -70.0, -180.0, 180.0, 5.0);
    private final NumberSetting itemRotY = new NumberSetting("Вращение Y (Меч)", 25.0, -180.0, 180.0, 5.0);
    private final NumberSetting itemRotZ = new NumberSetting("Вращение Z (Меч)", -15.0, -180.0, 180.0, 5.0);

    private long lastFrameTime = System.nanoTime();
    private float swingAngleY;
    private float swingAngleX;
    private float swingVelocityY;
    private float swingVelocityX;
    private float swingVelocityZ;
    private float softItemVelocity;
    private float softItemScale;
    private float walkCounter;
    private boolean leftAttack;
    private boolean wasAttacking;

    public BeautifulHands() {
        Instance = this;
        addSettings(animationSpeed, swingSpeed, movementAnimation, punchingAnimation,
                rightX, rightY, rightZ, leftX, leftY, leftZ, itemX, itemY, itemZ, itemRotX, itemRotY, itemRotZ);
    }

    public void applyHandOffset(MatrixStack matrices, Arm arm) {
        if (!isEnabled()) return;

        if (arm == Arm.RIGHT) {
            matrices.translate(rightX.getValue(), rightY.getValue(), rightZ.getValue());
        } else {
            matrices.translate(leftX.getValue(), leftY.getValue(), leftZ.getValue());
        }
    }

    public float getArmSwingProgress(float swingProgress) {
        return 0.0F;
    }

    public double frameStep() {
        long now = System.nanoTime();
        double delta = Math.min((now - lastFrameTime) / 1_000_000_000.0, 0.05);
        lastFrameTime = now;
        return delta * animationSpeed.getValue();
    }

    public boolean shouldRenderEmptyHand() {
        return isEnabled() && punchingAnimation.isEnabled();
    }

    public boolean useMovementAnimation() {
        return movementAnimation.isEnabled();
    }

    public boolean useMb3dCompat() {
        return false;
    }

    public int getSwingDuration(int vanillaDuration) {
        return isEnabled() ? Math.max(1, (int) Math.round(swingSpeed.getValue())) : vanillaDuration;
    }

    public boolean useLeftAttack(boolean attackKeyPressed, float swingProgress) {
        if (attackKeyPressed && !wasAttacking && swingProgress == 0.0F) {
            leftAttack = !leftAttack;
        }

        wasAttacking = attackKeyPressed;
        return leftAttack;
    }

    public float easeInOutBack(float x) {
        float c1 = 1.70158F;
        float c2 = c1 * 1.525F;
        return x < 0.5F
                ? (float) (Math.pow(2.0F * x, 2.0D) * ((c2 + 1.0F) * 2.0F * x - c2) / 2.0D)
                : (float) ((Math.pow(2.0F * x - 2.0F, 2.0D) * ((c2 + 1.0F) * (x * 2.0F - 2.0F) + c2) + 2.0D) / 2.0D);
    }

    public float swingRotation(float swingProgress) {
        return swingProgress < 0.6F
                ? MathHelper.sin(MathHelper.clamp(swingProgress, 0.0F, 0.12506F) * 12.56F)
                : MathHelper.sin(MathHelper.clamp(swingProgress, 0.62532F, 0.75038F) * 12.56F);
    }

    public void applyMovement(MatrixStack matrices, Arm arm, double horizontalSpeed, double verticalSpeed,
                              float yawDelta, float pitchDelta, float swingProgress, double frameStep) {
        if (!movementAnimation.isEnabled()) return;

        int side = arm == Arm.RIGHT ? 1 : -1;
        walkCounter += (float) (horizontalSpeed * 0.7D * frameStep);
        matrices.translate(
                side * MathHelper.sin(walkCounter) * horizontalSpeed * 0.05D,
                MathHelper.cos(walkCounter * 2.0F) * horizontalSpeed * 0.045D,
                MathHelper.sin(walkCounter * 0.5F) * horizontalSpeed * 0.025D
        );

        swingVelocityY += yawDelta * 0.015F * frameStep;
        swingVelocityY += swingProgress * 0.65F * frameStep;
        swingVelocityX += pitchDelta * 0.015F * frameStep;
        swingVelocityY -= swingAngleY * 0.1F * frameStep;
        swingVelocityX -= swingAngleX * 0.1F * frameStep;
        swingVelocityY *= (float) Math.pow(0.88F, frameStep);
        swingVelocityX *= (float) Math.pow(0.88F, frameStep);
        swingAngleY += swingVelocityY * frameStep;
        swingAngleX += swingVelocityX * frameStep;
        swingVelocityZ += (float) (((arm == Arm.RIGHT ? -horizontalSpeed : horizontalSpeed) * 15.0D - swingVelocityZ) * 0.1D * frameStep);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swingAngleY * 0.65F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingAngleX * 0.65F + (float) verticalSpeed * -2.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swingVelocityZ * 0.25F));
    }

    public void applySoftItemBounce(MatrixStack matrices, float swingProgress, double horizontalSpeed, double frameStep) {
        softItemVelocity += swingProgress * 0.03F * frameStep;
        if (horizontalSpeed > 0.09D) {
            softItemVelocity += (float) (-0.05D * horizontalSpeed * frameStep);
        }

        softItemVelocity -= softItemScale * 0.18F * frameStep;
        softItemVelocity *= (float) Math.pow(0.82F, frameStep);
        softItemScale += softItemVelocity * frameStep;
        softItemScale = MathHelper.clamp(softItemScale, -0.18F, 0.18F);
        matrices.scale(1.0F, 1.0F + softItemScale * -2.0F, 1.0F);
    }

    public float getItemX() { return (float) itemX.getValue(); }
    public float getItemY() { return (float) itemY.getValue(); }
    public float getItemZ() { return (float) itemZ.getValue(); }
    public float getItemRotX() { return (float) itemRotX.getValue(); }
    public float getItemRotY() { return (float) itemRotY.getValue(); }
    public float getItemRotZ() { return (float) itemRotZ.getValue(); }
}
