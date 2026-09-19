package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.MoveCorrection;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.settings.impl.MultiListSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

@NewFunction(name = "AutoPotion", desc = "Кидает взрывные зелья под себя", category = Category.COMBAT)
public class AutoPotion extends Function {

    private final MultiListSetting potions = new MultiListSetting("Бафать", "Силу", "Скорость", "Огнестойкость");

    private final List<Integer> currentSlotsToThrow = new ArrayList<>();
    private final List<RegistryEntry<StatusEffect>> effectsToWaitFor = new ArrayList<>();
    private boolean isWaitingForEffects = false;
    private long throwStartTime = 0;
    private long lastThrowTime = 0;

    public AutoPotion() {
        addSettings(potions);
    }

    @Override
    public void onDisable() {
        currentSlotsToThrow.clear();
        effectsToWaitFor.clear();
        isWaitingForEffects = false;
        RotationManager.reset();
    }

    @EventHandler
    public void onTick(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.currentScreen != null) {
            String screenName = mc.currentScreen.getClass().getSimpleName();
            if (screenName.contains("Downloading") || screenName.contains("Receiving") || screenName.contains("class_434")) {
                return;
            }
        }

        if (mc.player.isGliding() || (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() != Items.SHIELD)) {
            return;
        }

        if (currentSlotsToThrow.isEmpty() && !isWaitingForEffects) {
            if (System.currentTimeMillis() - lastThrowTime < 1000L) {
                return;
            }

            for (PotionType type : PotionType.values()) {
                if (isPotionNeeded(type)) {
                    int slot = getPotionIndexHb(type.getEffect());
                    if (slot != -1 && !currentSlotsToThrow.contains(slot)) {
                        currentSlotsToThrow.add(slot);
                        effectsToWaitFor.add(type.getEffect());
                    }
                }
            }
        }

        if (!currentSlotsToThrow.isEmpty()) {
            float targetYaw = RotationManager.getYaw(mc.player.getYaw());
            if (RotationManager.getPitch(mc.player.getPitch()) >= 85.0F) {
                int slot = currentSlotsToThrow.remove(0);

                int oldItem = mc.player.getInventory().getSelectedSlot();
                float oldPitch = mc.player.getPitch();
                float oldYaw = mc.player.getYaw();

                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.setPitch(90.0F);
                mc.player.setYaw(targetYaw);

                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);

                mc.player.getInventory().setSelectedSlot(oldItem);
                mc.player.setPitch(oldPitch);
                mc.player.setYaw(oldYaw);

                if (currentSlotsToThrow.isEmpty()) {
                    isWaitingForEffects = true;
                    throwStartTime = System.currentTimeMillis();
                }
            }
            RotationManager.rotate(new Rotation(targetYaw, 90.0F), MoveCorrection.SILENT, 360.0F, 360.0F, 360.0F);
            return;
        }

        if (isWaitingForEffects) {
            float targetYaw = RotationManager.getYaw(mc.player.getYaw());
            RotationManager.rotate(new Rotation(targetYaw, 90.0F), MoveCorrection.SILENT, 360.0F, 360.0F, 360.0F);

            boolean hasAllEffects = true;
            for (RegistryEntry<StatusEffect> effect : effectsToWaitFor) {
                if (!mc.player.hasStatusEffect(effect)) {
                    hasAllEffects = false;
                    break;
                }
            }

            if (hasAllEffects || System.currentTimeMillis() - throwStartTime > 1000L) {
                isWaitingForEffects = false;
                effectsToWaitFor.clear();
                lastThrowTime = System.currentTimeMillis();
                RotationManager.reset();
            }
        }
    }

    private boolean isPotionNeeded(PotionType type) {
        return potions.isEnabled(type.getName()) && !mc.player.hasStatusEffect(type.getEffect());
    }

    private int getPotionIndexHb(RegistryEntry<StatusEffect> effect) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.SPLASH_POTION) {
                PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null) {
                    for (StatusEffectInstance inst : contents.getEffects()) {
                        if (inst.getEffectType() == effect) {
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    private enum PotionType {
        STRENGTH(StatusEffects.STRENGTH, "Силу"),
        SPEED(StatusEffects.SPEED, "Скорость"),
        FIRE_RESISTANCE(StatusEffects.FIRE_RESISTANCE, "Огнестойкость");

        private final RegistryEntry<StatusEffect> effect;
        private final String name;

        PotionType(RegistryEntry<StatusEffect> effect, String name) {
            this.effect = effect;
            this.name = name;
        }

        public RegistryEntry<StatusEffect> getEffect() {
            return effect;
        }

        public String getName() {
            return name;
        }
    }
}
