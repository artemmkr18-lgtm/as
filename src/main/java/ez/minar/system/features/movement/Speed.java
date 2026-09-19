package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.TimerManager;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.helpers.MoveUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;

@NewFunction(name = "Speed", desc = "\u0423\u0441\u043a\u043e\u0440\u044f\u0435\u0442 \u0438\u0433\u0440\u043e\u043a\u0430", category = Category.MOVEMENT)
public class Speed extends Function {

    private final ModeSetting mode = new ModeSetting(
            "\u0420\u0435\u0436\u0438\u043c",
            "HolyWorld",
            "MetaHVH",
            "\u041b\u0435\u0433\u0438\u0442\u043d\u044b\u0439"
    );

    private final NumberSetting vanillaSpeed = new NumberSetting("\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", 1.5, 1.0, 5.0, 0.1);

    public Speed() {
        mode.runnable(this::updateVisibility);
        updateVisibility();
        addSettings(mode, vanillaSpeed);
    }

    private void updateVisibility() {
        vanillaSpeed.setVisible(mode.isEnabled("MetaHVH"));
    }

    @Override
    public void onDisable() {
        TimerManager.reset();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || !MoveUtil.isMoving()) return;

        if (mode.isEnabled("MetaHVH")) {
            MoveUtil.setMotion(vanillaSpeed.getValue() / 3.0);
        } else if (mode.isEnabled("HolyWorld")) {
            applyCollisionBoost(0.35, 0.0205);
        }
    }

    private void applyCollisionBoost(double radius, double perCollision) {
        int collisions = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (isValidCollisionBoostEntity(entity)
                    && mc.player.getBoundingBox().expand(radius).intersects(entity.getBoundingBox())) {
                collisions++;
            }
        }

        if (collisions <= 0) return;

        double[] dir = MoveUtil.calculateDirection(perCollision * collisions);
        mc.player.addVelocity(dir[0], 0.0, dir[1]);
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (nullCheck.all()) return;
        if (!mode.isEnabled("\u041b\u0435\u0433\u0438\u0442\u043d\u044b\u0439")) return;
        if (!canUseLegitInput(event)) return;

        event.setSprint(true);
        if (mc.player.isOnGround()) {
            event.setJump(true);
        }
    }

    private boolean canUseLegitInput(InputEvent event) {
        return event.getForward() > 0.0F
                && !event.isSneak()
                && canLegitSprint()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing()
                && !mc.player.hasVehicle()
                && !mc.player.isUsingItem();
    }

    private boolean canLegitSprint() {
        return !mc.player.hasBlindnessEffect()
                && mc.player.getHungerManager().canSprint();
    }

    private boolean isValidCollisionBoostEntity(Entity entity) {
        if (entity == null || entity == mc.player || entity instanceof ArmorStandEntity) {
            return false;
        }

        return entity instanceof LivingEntity
                || entity.getType().toString().toLowerCase(java.util.Locale.ROOT).contains("boat");
    }
}