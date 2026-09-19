package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
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
    private final BooleanSetting cullInvisible = new BooleanSetting("Невидимые сущности", true);
    private final BooleanSetting cullAllFar = new BooleanSetting("Все дальние сущности", true);
    private final NumberSetting entityDistance = new NumberSetting("Дистанция сущностей", 32.0, 8.0, 128.0, 1.0);

    private final BooleanSetting noShadows = new BooleanSetting("Тени сущностей", true);
    private final ModeSetting particlesMode = new ModeSetting("Частицы", "Меньше", "Все", "Меньше", "Нет");
    private final BooleanSetting noWeather = new BooleanSetting("Осадки", true);
    private final BooleanSetting noClouds = new BooleanSetting("Облака", true);
    private final BooleanSetting noSky = new BooleanSetting("Небо", true);

    private final BooleanSetting cullBlockEntities = new BooleanSetting("Дальние блок-сущности", true);
    private final NumberSetting blockEntityDistance = new NumberSetting("Дистанция блок-сущностей", 64.0, 16.0, 256.0, 4.0);

    private final BooleanSetting throttleLightmap = new BooleanSetting("Обновление света", false);
    private final NumberSetting lightmapInterval = new NumberSetting("Интервал света", 4.0, 2.0, 20.0, 1.0);

    private int particleCounter;
    private int lightmapCounter;

    public Optimization() {
        Instance = this;
        setEnabled(false);
        addSettings(cullItems, cullXp, cullProjectiles, cullDecor, cullArmorStands, cullAllFar, entityDistance,
                noShadows, particlesMode, noWeather, noClouds, noSky,
                cullBlockEntities, blockEntityDistance,
                throttleLightmap, lightmapInterval);
    }

    @Override
    public void onDisable() {
        particleCounter = 0;
        lightmapCounter = 0;
    }

    public boolean shouldCullEntity(Entity entity, double squaredDistance) {
        if (mc.player == null || mc.world == null) return false;
        if (entity == mc.player || entity.getType() == EntityType.PLAYER) return false;

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

    public boolean shouldUpdateLightmap() {
        FullBright fullBright = FunctionManager.getFunction(FullBright.class);
        if (fullBright != null && fullBright.isEnabled()) return true;

        lightmapCounter++;
        int interval = Math.max(1, (int) lightmapInterval.getValue());
        return lightmapCounter % interval == 0;
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

    public static boolean shouldUpdateLightmapStatic() {
        Optimization optimization = Instance;
        if (optimization == null || !optimization.isEnabled()) return true;
        return optimization.shouldUpdateLightmap();
    }
}
