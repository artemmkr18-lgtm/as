package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

@NewFunction(name = "Оптимизация", desc = "Повышает FPS: отсекает лишние сущности, частицы и эффекты", category = Category.RENDER)
public class Optimization extends Function {
    public static Optimization Instance;

    private final BooleanSetting cullItems = new BooleanSetting("Предметы", true);
    private final BooleanSetting cullXp = new BooleanSetting("Опыт", true);
    private final BooleanSetting cullProjectiles = new BooleanSetting("Снаряды", true);
    private final BooleanSetting cullDecor = new BooleanSetting("Рамки и картины", true);
    private final BooleanSetting cullArmorStands = new BooleanSetting("Стойки для брони", true);
    private final BooleanSetting cullAllFar = new BooleanSetting("Все дальние сущности", false);
    private final NumberSetting entityDistance = new NumberSetting("Дистанция сущностей", 48.0, 16.0, 128.0, 4.0);

    private final BooleanSetting noShadows = new BooleanSetting("Тени сущностей", false);
    private final ModeSetting particlesMode = new ModeSetting("Частицы", "Все", "Все", "Меньше", "Нет");
    private final BooleanSetting noWeather = new BooleanSetting("Осадки", false);
    private final BooleanSetting noClouds = new BooleanSetting("Облака", false);
    private final BooleanSetting noSky = new BooleanSetting("Небо", false);

    private final BooleanSetting cullBlockEntities = new BooleanSetting("Дальние блок-сущности", false);
    private final NumberSetting blockEntityDistance = new NumberSetting("Дистанция блок-сущностей", 64.0, 16.0, 256.0, 4.0);

    private int particleCounter;

    public Optimization() {
        Instance = this;
        setEnabled(false);
        addSettings(cullItems, cullXp, cullProjectiles, cullDecor, cullArmorStands, cullAllFar, entityDistance,
                noShadows, particlesMode, noWeather, noClouds, noSky,
                cullBlockEntities, blockEntityDistance);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        particleCounter = 0;
    }

    public boolean shouldCullEntity(Entity entity, double squaredDistance) {
        if (entity == null) return false;

        double maxDistance = entityDistance.getValue();
        if (squaredDistance <= maxDistance * maxDistance) return false;

        EntityType<?> type = entity.getType();
        if (cullItems.isEnabled() && type == EntityType.ITEM) return true;
        if (cullXp.isEnabled() && type == EntityType.EXPERIENCE_ORB) return true;
        if (cullDecor.isEnabled() && (type == EntityType.ITEM_FRAME
                || type == EntityType.GLOW_ITEM_FRAME
                || type == EntityType.PAINTING)) return true;
        if (cullArmorStands.isEnabled() && type == EntityType.ARMOR_STAND) return true;
        if (cullProjectiles.isEnabled() && isProjectile(type)) return true;

        return cullAllFar.isEnabled();
    }

    private boolean isProjectile(EntityType<?> type) {
        return type == EntityType.ARROW
                || type == EntityType.SPECTRAL_ARROW
                || type == EntityType.TRIDENT
                || type == EntityType.SNOWBALL
                || type == EntityType.EGG
                || type == EntityType.ENDER_PEARL
                || type == EntityType.EXPERIENCE_BOTTLE
                || type == EntityType.FISHING_BOBBER
                || type == EntityType.FIREBALL
                || type == EntityType.SMALL_FIREBALL
                || type == EntityType.WIND_CHARGE;
    }

    public boolean shouldDropParticle() {
        String mode = particlesMode.getActiveMode();
        if (mode.equals("Нет")) return true;
        if (mode.equals("Меньше")) {
            particleCounter++;
            return particleCounter % 2 == 0;
        }
        return false;
    }

    public boolean shouldSkipWeather() {
        return noWeather.isEnabled();
    }

    public boolean shouldSkipClouds() {
        return noClouds.isEnabled();
    }

    public boolean shouldSkipSky() {
        return noSky.isEnabled();
    }

    public double getBlockEntityMaxDistanceSq() {
        if (!cullBlockEntities.isEnabled()) return Double.MAX_VALUE;
        double distance = blockEntityDistance.getValue();
        return distance * distance;
    }

    // === Статические хелперы для миксинов ===

    public static boolean shouldCullEntityStatic(Entity entity, double squaredDistance) {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return false;
        return optimization.shouldCullEntity(entity, squaredDistance);
    }

    public static boolean shouldDisableShadows() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return false;
        return optimization.noShadows.isEnabled();
    }

    public static boolean shouldDropParticleStatic() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return false;
        return optimization.shouldDropParticle();
    }

    public static boolean shouldSkipWeatherStatic() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return false;
        return optimization.shouldSkipWeather();
    }

    public static boolean shouldSkipCloudsStatic() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return false;
        return optimization.shouldSkipClouds();
    }

    public static boolean shouldSkipSkyStatic() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return false;
        return optimization.shouldSkipSky();
    }

    public static double getBlockEntityMaxDistanceSqStatic() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return Double.MAX_VALUE;
        return optimization.getBlockEntityMaxDistanceSq();
    }
}
