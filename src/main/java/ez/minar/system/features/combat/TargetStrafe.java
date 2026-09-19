package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.EventPriority;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.MoveCorrection;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

@NewFunction(name = "TargetStrafe", desc = "Стрейф вокруг цели AttackAura", category = Category.COMBAT)
public class TargetStrafe extends Function {
    private static final float ROTATION_SPEED = 180.0F;

    private final NumberSetting strafePower = new NumberSetting("Strafe Power", 0.0, 0.0, 1.0, 0.05);
    private final ListSetting directionMode = new ListSetting("Direction", "Auto", "Left", "Right");
    private final BooleanSetting autoJump = new BooleanSetting("Auto Jump", true);
    private final BooleanSetting switchOnCollision = new BooleanSetting("Collision Switch", true);

    private LivingEntity target;
    private int direction = 1;

    public TargetStrafe() {
        addSettings(strafePower, directionMode, autoJump, switchOnCollision);
    }

    @Override
    public void onEnable() {
        target = null;
        direction = 1;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUpdate(UpdateEvent event) {
        target = getAuraTarget();
        if (!canStrafe()) {
            target = null;
            return;
        }

        updateDirection();
        rotateToTarget();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInput(InputEvent event) {
        if (!canStrafe()) return;

        ez.minar.system.features.player.ElytraHelper helper = FunctionManager.getFunction(ez.minar.system.features.player.ElytraHelper.class);
        if (helper != null && helper.isWorking()) {
            return;
        }

        event.setForward(1.0F);
        event.setStrafe((float) (direction * strafePower.getValue()));

        if (autoJump.isEnabled() && mc.player.isOnGround() && !event.isSneak()) {
            event.setJump(true);
        }
    }

    private LivingEntity getAuraTarget() {
        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura == null || !aura.isEnabled()) return null;
        return aura.getTarget();
    }

    private boolean canStrafe() {
        if (nullCheck.all() || target == null || !target.isAlive()) return false;
        return !mc.player.isSneaking() && !mc.player.hasVehicle();
    }

    private void updateDirection() {
        if (directionMode.isEnabled("Left")) {
            direction = 1;
            return;
        }
        if (directionMode.isEnabled("Right")) {
            direction = -1;
            return;
        }

        if (mc.options.leftKey.isPressed()) {
            direction = 1;
        } else if (mc.options.rightKey.isPressed()) {
            direction = -1;
        } else if (switchOnCollision.isEnabled() && mc.player.horizontalCollision) {
            direction = -direction;
        }
    }

    private void rotateToTarget() {
        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura != null && aura.isAttackWindowReady()) {
            return;
        }
        Rotation rotation = RotationManager.getRotationTo(getTargetCenter());
        RotationManager.rotate(rotation, MoveCorrection.SILENT, ROTATION_SPEED, ROTATION_SPEED, ROTATION_SPEED);
    }

    private Vec3d getTargetCenter() {
        return target.getBoundingBox().getCenter();
    }

    public LivingEntity getTarget() {
        return target;
    }

    public int getDirection() {
        return direction;
    }

    public double getStrafePower() {
        return strafePower.getValue();
    }
}
