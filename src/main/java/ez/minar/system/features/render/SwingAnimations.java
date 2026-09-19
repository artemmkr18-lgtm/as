package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@NewFunction(name = "Swing Animations", desc = "Кастомные анимации ударов от первого лица", category = Category.RENDER)
public class SwingAnimations extends Function {
    private static final float DEFAULT_SPEED = 7.0F;

    public static SwingAnimations Instance;

    private final ModeSetting mode = new ModeSetting("Мод", "Никакой", "Swipe", "Down", "Smooth", "Smooth 2",
            "Power", "Feast", "Twist", "Default", "Self", "Self 2", "Forward", "Touch", "BlockHit", "Pander", "Curt");
    private final NumberSetting power = new NumberSetting("Сила", 3.0, 0.0, 10.0, 1.0);
    private final NumberSetting hitStrength = new NumberSetting("Сила взмаха", 1.0, 0.5, 3.0, 0.1);
    public final NumberSetting speed = new NumberSetting("Скорость", 7.0, 0.0, 10.0, 1.0);
    public final NumberSetting angle = new NumberSetting("Угол", 90.0, 0.0, 360.0, 5.0);
    private int activeSwingDuration;
    private boolean swingDurationLocked;

    public SwingAnimations() {
        Instance = this;
        addSettings(mode, power, hitStrength, speed, angle);
        mode.runnable(this::updateVisibility);
        updateVisibility();
    }

    @Override
    public void onEnable() {
        resetSwingDuration();
    }

    @Override
    public void onDisable() {
        resetSwingDuration();
    }

    public boolean isMode(String value) {
        return mode.isEnabled(value);
    }

    public boolean shouldOverrideVanilla() {
        return isEnabled() && !mode.isEnabled("Никакой");
    }

    public void renderSwordAnimation(MatrixStack matrices, float swingProgress, float equipProgress, Arm arm,
                                     float tickProgress, float lastEquipProgress, float currentEquipProgress) {
        swingProgress = MathHelper.clamp(swingProgress, 0.0F, 1.0F);
        float anim = (float) Math.sin(swingProgress * Math.PI);
        float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sin2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float sinSmooth = MathHelper.sin(swingProgress * (float) Math.PI) * 0.5F;
        float strength = (float) hitStrength.getValue();
        int side = arm == Arm.RIGHT ? 1 : -1;

        switch (mode.getActiveMode()) {
            case "Никакой" -> renderVanilla(matrices, swingProgress, equipProgress, arm);
            case "Twist" -> {
                matrices.translate(side * 0.56F, -0.36F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -90.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((sin1 - sin2) * 60.0F * side * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0F));
                matrices.translate(0.0F, -0.1F, 0.05F);
            }
            case "Swipe" -> {
                matrices.translate(side * 0.56F, -0.32F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * sin1 * -5.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * sin1 * -120.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70.0F));
            }
            case "Default" -> {
                matrices.translate(side * 0.56F, -0.52F - sin2 * 0.5F * strength, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0F * side));
            }
            case "Down" -> {
                matrices.translate(side * 0.56F, -0.32F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5.0F * strength));
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F));
            }
            case "Smooth" -> {
                matrices.translate(side * 0.56F, -0.42F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + sin1 * -20.0F * strength)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * sin2 * -20.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
                matrices.translate(0.0F, -0.1F, 0.0F);
            }
            case "Smooth 2" -> {
                matrices.translate(side * 0.56F, -0.42F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
                matrices.translate(0.0F, -0.1F, 0.0F);
            }
            case "Power" -> {
                matrices.translate(side * 0.56F, -0.32F, -0.72F);
                matrices.translate(-sinSmooth * sinSmooth * sin1 * side * strength, 0.0F, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(61.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * strength));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * sin1 * -5.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * sin1 * -30.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSmooth * -60.0F * strength));
            }
            case "Feast" -> {
                matrices.translate(side * 0.56F, -0.32F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 75.0F * side * strength));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -45.0F * strength));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F * side));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35.0F * side));
            }
            case "Self 2" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -30.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (-angle.getValue() - power.getValue() * 10.0 * sin2)));
            }
            case "Forward" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                matrices.translate(0.0F, 0.0F, -0.3F * sin2);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -35.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * sin2 * 35.0F));
            }
            case "Self" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 90.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -60.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (-angle.getValue() - power.getValue() * 10.0 * sin2)));
            }
            case "Touch" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                matrices.scale(1F, 1F, (float) (1.0 + anim * power.getValue() / 4.0));
                matrices.translate(0.0F, 0.0F, -0.265F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F));
            }
            case "Curt" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                float sqrtSwing = MathHelper.sqrt(swingProgress);
                float sinMain = MathHelper.sin(sqrtSwing * (float) Math.PI);
                float sinExtra = MathHelper.sin(swingProgress * (float) Math.PI);
                matrices.translate(side * (0.4F - sinMain * 0.2F), -0.2F + sinMain * 0.3F, -0.5F - sinExtra * 0.2F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 91.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * (-40.0F + sinMain * -100.0F)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60.0F));
            }
            case "Pander" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                matrices.scale(0.8F, 0.8F, 0.8F);
                float anim2 = 1.0F - MathHelper.lerp(tickProgress, lastEquipProgress, currentEquipProgress);
                matrices.translate(side * (0.3F - anim * 0.15F), 0.2F - anim2 * 0.12F, -0.15F - anim * 0.13F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (76.0F - 10.0F * anim)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * (-16.0F - 8.0F * anim)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-83.0F - 26.0F * anim));
            }
            case "BlockHit" -> {
                matrices.translate(side * 0.56F, -0.52F, -0.72F);
                float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * g * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
                matrices.translate(side * 0.4F, 0.2F, 0.2F);
                matrices.translate(side * -0.5F, 0.08F, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 20.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 20.0F));
            }
        }
    }

    private void renderVanilla(MatrixStack matrices, float swingProgress, float equipProgress, Arm arm) {
        int side = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float g = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * ((float) Math.PI * 2.0F));
        float h = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        matrices.translate(side * f, g, h);
        float f2 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + f2 * -20.0F)));
        float g2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * g2 * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g2 * -80.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
    }

    public int getSwingDuration(int vanillaDuration, boolean handSwinging, int handSwingTicks) {
        if (!swingDurationLocked || !handSwinging || handSwingTicks < 0) {
            float speedValue = Math.max((float) speed.getValue(), 0.1F);
            activeSwingDuration = Math.max(1, Math.round(vanillaDuration * DEFAULT_SPEED / speedValue));
            swingDurationLocked = handSwinging;
        }

        return activeSwingDuration;
    }

    private void resetSwingDuration() {
        activeSwingDuration = 0;
        swingDurationLocked = false;
    }

    private void updateVisibility() {
        boolean usesLegacyPower = mode.isEnabled("Self") || mode.isEnabled("Self 2") || mode.isEnabled("Touch");
        boolean usesHitStrength = mode.isEnabled("Swipe") || mode.isEnabled("Down") || mode.isEnabled("Smooth")
                || mode.isEnabled("Smooth 2") || mode.isEnabled("Power") || mode.isEnabled("Feast")
                || mode.isEnabled("Twist") || mode.isEnabled("Default");
        power.setVisible(usesLegacyPower);
        hitStrength.setVisible(usesHitStrength);
        angle.setVisible(mode.isEnabled("Self") || mode.isEnabled("Self 2"));
    }
}
