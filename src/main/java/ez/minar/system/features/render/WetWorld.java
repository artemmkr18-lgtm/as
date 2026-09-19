package ez.minar.system.features.render;

import ez.minar.mixins.interfaces.IGameRenderer;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.WetWorldPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

@NewFunction(name = "WetWorld", desc = "Делает блоки мокрыми с отражениями в реальном времени", category = Category.RENDER)
public class WetWorld extends Function {
    public static WetWorld Instance;

    public final ModeSetting quality = new ModeSetting("Quality", "High", "Low", "Medium", "High", "Ultra");
    public final NumberSetting reflectionStrength = new NumberSetting("Reflection Strength", 0.75, 0.1, 1.5, 0.05);
    public final NumberSetting darkening = new NumberSetting("Wet Darkening", 0.30, 0.0, 0.8, 0.05);
    public final BooleanSetting ripples = new BooleanSetting("Ripples", true);
    public final NumberSetting rippleSpeed = new NumberSetting("Ripple Speed", 1.0, 0.1, 3.0, 0.1);
    public final BooleanSetting rainOnly = new BooleanSetting("Rain Only", false);

    public WetWorld() {
        Instance = this;
        addSettings(quality, reflectionStrength, darkening, ripples, rippleSpeed, rainOnly);
        ripples.runnable(() -> rippleSpeed.setVisible(ripples.isEnabled()));
    }

    public int getQualityIndex() {
        if (quality.isEnabled("Low")) return 0;
        if (quality.isEnabled("Medium")) return 1;
        if (quality.isEnabled("High")) return 2;
        if (quality.isEnabled("Ultra")) return 3;
        return 2;
    }

    public static void renderWorld(WorldRenderContext context) {
        WetWorld wetWorld = FunctionManager.getFunction(WetWorld.class);
        if (wetWorld == null || !wetWorld.isEnabled()) {
            return;
        }

        wetWorld.render(context);
    }

    private void render(WorldRenderContext context) {
        if (mc.world == null || mc.gameRenderer == null) {
            return;
        }

        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        float rainGradient = mc.world.getRainGradient(tickProgress);

        if (rainOnly.isEnabled() && rainGradient <= 0.01f) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();

        float fov = ((IGameRenderer) mc.gameRenderer).minar$getFov(camera, tickProgress, true);
        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(fov);
        if (proj == null) {
            return;
        }

        Matrix4f invProj = new Matrix4f(proj).invert();
        Matrix4f viewRot = new Matrix4f().rotation(camera.getRotation());

        float effectiveStrength = (float) reflectionStrength.getValue();
        if (rainOnly.isEnabled()) {
            effectiveStrength *= Math.clamp(rainGradient, 0.0f, 1.0f);
        }

        float time = (float) ((System.currentTimeMillis() % 120_000L) / 1000.0);

        Fog fog = Fog.Instance;
        float fogStart = 0f;
        float fogEnd = 10000f;
        float fogActive = 0f;
        Color fogColor = new Color(0, 0, 0, 0);
        float fogBrightness = 1.0f;

        if (fog != null && fog.isEnabled()) {
            fogStart = 8.0f;
            fogEnd = 64.0f;
            fogColor = ThemeManager.getThemeColor();
            fogBrightness = 1.0f;
            fogActive = 1.0f;
        } else if (mc.options != null) {
            int viewDist = mc.options.getClampedViewDistance();
            fogEnd = viewDist * 16.0f;
            fogStart = fogEnd * 0.75f;
        }

        WetWorldPipeline.render(
                proj,
                invProj,
                viewRot,
                camPos,
                fov,
                effectiveStrength,
                (float) darkening.getValue(),
                time,
                getQualityIndex(),
                ripples.isEnabled(),
                (float) rippleSpeed.getValue(),
                rainGradient,
                fogStart,
                fogEnd,
                fogActive,
                fogColor,
                fogBrightness
        );
    }
}
