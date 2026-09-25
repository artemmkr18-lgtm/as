package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.winter.WinterSnowfall;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4f;

import java.awt.Color;

@NewFunction(name = "Atmosphere", desc = "Зимняя атмосфера: снегопад, снег на блоках и дымка", category = Category.RENDER)
public class Atmosphere extends Function {
    public static Atmosphere Instance;

    public final NumberSetting radius = new NumberSetting("Радиус", 28.0, 8.0, 64.0, 1.0);
    public final NumberSetting snowDensity = new NumberSetting("Плотность снега", 1.8, 0.25, 4.0, 0.05);
    public final NumberSetting snowWind = new NumberSetting("Ветер", 1.0, 0.0, 3.0, 0.05);
    public final NumberSetting snowFlakeSize = new NumberSetting("Размер хлопьев", 1.0, 0.45, 2.5, 0.05);
    public final NumberSetting snowFallSpeed = new NumberSetting("Скорость падения", 1.0, 0.35, 2.5, 0.05);
    public final NumberSetting blizzard = new NumberSetting("Метель", 0.35, 0.0, 1.0, 0.05);
    public final BooleanSetting groundSnow = new BooleanSetting("Снег на земле", true);
    public final NumberSetting snowDepth = new NumberSetting("Толщина снега", 0.12, 0.03, 0.35, 0.01);
    public final BooleanSetting skyOnly = new BooleanSetting("Только под небом", true);
    public final NumberSetting winterHaze = new NumberSetting("Зимняя дымка", 0.72, 0.0, 1.0, 0.05);
    public final BooleanSetting colorGrade = new BooleanSetting("Цветокоррекция", true);
    public final ColorSetting snowColor = new ColorSetting("Цвет снега", new Color(0xEA, 0xF4, 0xFF));

    private final WinterSnowfall winterSnowfall = WinterSnowfall.getInstance();
    private final WinterSnowfall.Config winterConfig = new WinterSnowfall.Config();

    public Atmosphere() {
        Instance = this;
        addSettings(radius, snowDensity, snowWind, snowFlakeSize, snowFallSpeed, blizzard,
                groundSnow, snowDepth, skyOnly, winterHaze, colorGrade, snowColor);
        groundSnow.runnable(() -> snowDepth.setVisible(groundSnow.isEnabled()));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        winterSnowfall.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        winterSnowfall.reset();
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Instance.renderWorld(context, camera);
    }

    public void renderWorld(WorldRenderContext context, Camera camera) {
        if (!isEnabled()) return;

        winterConfig.radius = (float) radius.getValue();
        winterConfig.height = 24.0f;
        winterConfig.density = (float) snowDensity.getValue();
        winterConfig.flakeSize = (float) snowFlakeSize.getValue();
        winterConfig.fallSpeed = (float) snowFallSpeed.getValue();
        winterConfig.wind = (float) snowWind.getValue();
        winterConfig.opacity = 1.0f;
        winterConfig.blizzard = (float) blizzard.getValue();
        winterConfig.skyOnly = skyOnly.isEnabled();
        winterConfig.groundSnow = groundSnow.isEnabled();
        winterConfig.groundRadius = Math.min((float) radius.getValue() * 1.25f, 44.0f);
        winterConfig.groundDepth = (float) snowDepth.getValue();
        winterConfig.color = snowColor.getColor().getRGB();

        winterSnowfall.render(context, camera, winterConfig);
    }

    public void applyFog(FogData data) {
        float haze = (float) winterHaze.getValue();
        if (haze <= 0.01f) return;
        float viewDistance = (float) radius.getValue() * 1.5f;
        float start = Math.max(2.0f, viewDistance * MathHelper.lerp(haze, 0.45f, 0.08f));
        float end = Math.max(start + 4.0f, viewDistance * MathHelper.lerp(haze, 1.15f, 0.70f));
        data.environmentalStart = Math.min(data.environmentalStart, start);
        data.environmentalEnd = Math.min(data.environmentalEnd, end);
    }

    public Vector4f applyColor(Vector4f vanillaColor) {
        if (!colorGrade.isEnabled()) return vanillaColor;
        Color col = snowColor.getColor();
        Vector4f winterTint = new Vector4f(
                col.getRed() / 255.0f,
                col.getGreen() / 255.0f,
                col.getBlue() / 255.0f,
                1.0f
        );
        return new Vector4f(vanillaColor).lerp(winterTint, 0.18f);
    }
}
