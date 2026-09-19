package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

@NewFunction(name = "ViewModel", desc = "Кастомная позиция рук от первого лица", category = Category.RENDER)
public class ViewModel extends Function {
    public static ViewModel Instance;

    private final ModeSetting editHand = new ModeSetting("Рука", "Правая", "Левая");

    private final NumberSetting rightX = new NumberSetting("Правая X", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting rightY = new NumberSetting("Правая Y", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting rightZ = new NumberSetting("Правая Z", 0.0, -2.0, 2.0, 0.05);

    private final NumberSetting leftX = new NumberSetting("Левая X", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting leftY = new NumberSetting("Левая Y", 0.0, -2.0, 2.0, 0.05);
    private final NumberSetting leftZ = new NumberSetting("Левая Z", 0.0, -2.0, 2.0, 0.05);

    private final BooleanSetting rightRotation = new BooleanSetting("Правая вращение", false);
    private final ModeSetting rightRotationAxis = new ModeSetting("Правая ось вращения", "X", "Y", "Z");
    private final NumberSetting rightRotationSpeed = new NumberSetting("Правая скорость вращения", 120.0, -720.0, 720.0, 5.0);

    private final BooleanSetting leftRotation = new BooleanSetting("Левая вращение", false);
    private final ModeSetting leftRotationAxis = new ModeSetting("Левая ось вращения", "X", "Y", "Z");
    private final NumberSetting leftRotationSpeed = new NumberSetting("Левая скорость вращения", 120.0, -720.0, 720.0, 5.0);
    private final BooleanSetting staticHands = new BooleanSetting("Статичные руки", false);

    public ViewModel() {
        Instance = this;
        addSettings(editHand, rightX, rightY, rightZ, leftX, leftY, leftZ,
                rightRotation, rightRotationAxis, rightRotationSpeed,
                leftRotation, leftRotationAxis, leftRotationSpeed, staticHands);
        editHand.runnable(this::updateVisibility);
        rightRotation.runnable(this::updateVisibility);
        leftRotation.runnable(this::updateVisibility);
        updateVisibility();
    }

    public void apply(MatrixStack matrices, Arm arm) {
        if (!isEnabled()) return;

        if (arm == Arm.RIGHT) {
            matrices.translate(rightX.getValue(), rightY.getValue(), rightZ.getValue());
        } else {
            matrices.translate(leftX.getValue(), leftY.getValue(), leftZ.getValue());
        }
    }

    public void applyRotation(MatrixStack matrices, Arm arm) {
        if (!isEnabled() || !isRotationEnabled(arm)) return;

        float angle = (float) ((System.nanoTime() / 1_000_000_000.0) * getRotationSpeed(arm) % 360.0);

        switch (getRotationAxis(arm)) {
            case "Y" -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            case "Z" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            default -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(arm == Arm.RIGHT ? -angle : angle));
        }
    }

    public boolean isStaticHandsEnabled() {
        return staticHands.isEnabled();
    }

    private boolean isRotationEnabled(Arm arm) {
        return arm == Arm.RIGHT ? rightRotation.isEnabled() : leftRotation.isEnabled();
    }

    private String getRotationAxis(Arm arm) {
        return arm == Arm.RIGHT ? rightRotationAxis.getActiveMode() : leftRotationAxis.getActiveMode();
    }

    private double getRotationSpeed(Arm arm) {
        return arm == Arm.RIGHT ? rightRotationSpeed.getValue() : leftRotationSpeed.getValue();
    }

    private void updateVisibility() {
        boolean right = editHand.isEnabled("Правая");
        rightX.setVisible(right);
        rightY.setVisible(right);
        rightZ.setVisible(right);
        rightRotation.setVisible(right);
        rightRotationAxis.setVisible(right && rightRotation.isEnabled());
        rightRotationSpeed.setVisible(right && rightRotation.isEnabled());

        leftX.setVisible(!right);
        leftY.setVisible(!right);
        leftZ.setVisible(!right);
        leftRotation.setVisible(!right);
        leftRotationAxis.setVisible(!right && leftRotation.isEnabled());
        leftRotationSpeed.setVisible(!right && leftRotation.isEnabled());
    }
}
