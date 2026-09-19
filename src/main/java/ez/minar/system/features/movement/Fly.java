package ez.minar.system.features.movement;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

@NewFunction(name = "Fly", desc = "Свапает на копье и делает выпад", category = Category.MOVEMENT)
public class Fly extends Function {
    private static final long LUNGE_DELAY_MS = 200L;
    private static final float VANILLA_BASE_FLY_SPEED = 0.05F;

    private final ModeSetting mode = new ModeSetting("Режим", "Полёт на копье", "Vanilla");
    private final NumberSetting speed = new NumberSetting("Скорость", 1.0, 0.1, 5.0, 0.1);

    private int previousSlot = -1;
    private long lastLungeTime;
    private boolean vanillaActive;
    private boolean wasAllowFlying;
    private boolean wasFlying;
    private float previousFlySpeed = VANILLA_BASE_FLY_SPEED;

    public Fly() {
        addSettings(mode, speed);
        mode.runnable(this::updateVisibility);
        updateVisibility();
    }

    private void updateVisibility() {
        speed.setVisible(mode.isEnabled("Vanilla"));
    }

    @Override
    public void onEnable() {
        previousSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
        lastLungeTime = 0L;
        vanillaActive = false;

        if (mc.player != null && mode.isEnabled("Vanilla")) {
            startVanillaFly();
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && previousSlot >= 0 && previousSlot < 9) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }

        if (mc.player != null) {
            stopVanillaFly();
        }

        previousSlot = -1;
        lastLungeTime = 0L;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null) return;

        updateVisibility();

        if (mode.isEnabled("Vanilla")) {
            if (!vanillaActive) {
                startVanillaFly();
            } else {
                applyVanillaFlySpeed();
            }
            return;
        }

        if (vanillaActive) {
            stopVanillaFly();
        }

        if (mc.currentScreen != null) return;
        if (!mode.isEnabled("Полёт на копье") || mc.player.isUsingItem()) return;
        if (System.currentTimeMillis() - lastLungeTime < LUNGE_DELAY_MS) return;

        int spearSlot = findSpearSlot();
        if (spearSlot == -1) return;

        ItemStack spearStack = mc.player.getInventory().getStack(spearSlot);
        PiercingWeaponComponent piercingWeapon = spearStack.get(DataComponentTypes.PIERCING_WEAPON);
        if (piercingWeapon == null) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(spearSlot);
        mc.interactionManager.attackWithPiercingWeapon(piercingWeapon);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (currentSlot != spearSlot) {
            mc.player.getInventory().setSelectedSlot(currentSlot);
        }

        lastLungeTime = System.currentTimeMillis();
    }

    private void startVanillaFly() {
        wasAllowFlying = mc.player.getAbilities().allowFlying;
        wasFlying = mc.player.getAbilities().flying;
        previousFlySpeed = mc.player.getAbilities().getFlySpeed();
        vanillaActive = true;
        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(VANILLA_BASE_FLY_SPEED * (float) speed.getValue());
        mc.player.sendAbilitiesUpdate();
    }

    private void applyVanillaFlySpeed() {
        float flySpeed = VANILLA_BASE_FLY_SPEED * (float) speed.getValue();
        boolean speedChanged = mc.player.getAbilities().getFlySpeed() != flySpeed;

        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().flying = true;
        if (speedChanged) {
            mc.player.getAbilities().setFlySpeed(flySpeed);
            mc.player.sendAbilitiesUpdate();
        }
    }

    private void stopVanillaFly() {
        if (!vanillaActive || mc.player == null) return;

        if (mc.player.isCreative() || mc.player.isSpectator()) {
            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = wasFlying;
        } else {
            mc.player.getAbilities().allowFlying = wasAllowFlying;
            mc.player.getAbilities().flying = false;
        }
        mc.player.getAbilities().setFlySpeed(previousFlySpeed);
        mc.player.sendAbilitiesUpdate();
        vanillaActive = false;
    }

    private int findSpearSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isIn(ItemTags.SPEARS)) {
                return slot;
            }
        }

        return -1;
    }
}
