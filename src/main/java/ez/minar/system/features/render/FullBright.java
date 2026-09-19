package ez.minar.system.features.render;

import ez.minar.mixins.interfaces.ISimpleOption;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

@NewFunction(name = "FullBright", desc = "Полная яркость мира с 3 режимами", category = Category.RENDER)
public class FullBright extends Function {
    public static FullBright Instance;

    private final ModeSetting mode = new ModeSetting("Режим", "Гамма", "Ночное видение", "Динамичный");
    private final NumberSetting dynamicSpeed = new NumberSetting("Скорость", 50.0, 5.0, 200.0, 5.0);
    private final NumberSetting lightThreshold = new NumberSetting("Порог света", 4.0, 0.0, 15.0, 1.0);

    private double savedGamma = -1;
    private double currentDynamicGamma;
    private boolean wasNightVisionApplied;

    public FullBright() {
        Instance = this;
        addSettings(mode, dynamicSpeed, lightThreshold);
    }

    @Override
    public void onEnable() {
        saveGamma();
        currentDynamicGamma = getGamma();
    }

    @Override
    public void onDisable() {
        restoreGamma();
        removeNightVision();
        wasNightVisionApplied = false;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        switch (mode.getActiveMode()) {
            case "Гамма" -> handleGamma();
            case "Ночное видение" -> handleNightVision();
            case "Динамичный" -> handleDynamic();
        }
    }

    // === Гамма ===
    private void handleGamma() {
        removeNightVision();
        wasNightVisionApplied = false;
        setGamma(1000.0);
    }

    // === Ночное видение ===
    private void handleNightVision() {
        setGamma(savedGamma != -1 ? savedGamma : 1.0);

        StatusEffectInstance existing = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (existing == null || existing.getDuration() < 400) {
            mc.player.setStatusEffect(
                    new StatusEffectInstance(
                            StatusEffects.NIGHT_VISION,
                            Integer.MAX_VALUE,  // бесконечная длительность
                            0,                  // уровень
                            false,              // ambient
                            false,              // показывать частицы  — нет
                            false               // показывать иконку  — нет (невидимый эффект)
                    ),
                    null
            );
            wasNightVisionApplied = true;
        }
    }

    // === Динамичный ===
    private void handleDynamic() {
        removeNightVision();
        wasNightVisionApplied = false;

        boolean isDark = isPlayerInDarkSpace();
        double target = isDark ? 1000.0 : (savedGamma != -1 ? savedGamma : 1.0);
        double speed = dynamicSpeed.getValue() / 1000.0;

        if (currentDynamicGamma < target) {
            currentDynamicGamma = Math.min(currentDynamicGamma + speed * (target - currentDynamicGamma + 1.0), target);
        } else if (currentDynamicGamma > target) {
            currentDynamicGamma = Math.max(currentDynamicGamma - speed * (currentDynamicGamma - target + 1.0), target);
        }

        setGamma(currentDynamicGamma);
    }

    /**
     * Проверяет, находится ли игрок в тёмном замкнутом пространстве.
     * Оценивает уровень освещения блока и неба в позиции игрока.
     */
    private boolean isPlayerInDarkSpace() {
        if (mc.world == null || mc.player == null) return false;

        BlockPos pos = mc.player.getBlockPos();
        int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight = mc.world.getLightLevel(LightType.SKY, pos);
        int threshold = (int) lightThreshold.getValue();

        // Если и блочный, и небесный свет ниже порога — считаем тёмным пространством
        return blockLight <= threshold && skyLight <= threshold;
    }

    // === Утилиты для гаммы ===

    private void saveGamma() {
        savedGamma = getGamma();
    }

    private void restoreGamma() {
        if (savedGamma != -1) {
            setGamma(savedGamma);
            savedGamma = -1;
        }
    }

    @SuppressWarnings("unchecked")
    private void setGamma(double value) {
        if (mc.options == null) return;
        ((ISimpleOption) (Object) mc.options.getGamma()).minar$setValue(value);
    }

    private double getGamma() {
        if (mc.options == null) return 1.0;
        return mc.options.getGamma().getValue();
    }

    // === Утилиты для ночного видения ===

    private void removeNightVision() {
        if (wasNightVisionApplied && mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}
