package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.ShaderSkyPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldTerrainRenderContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;

@NewFunction(name = "ShaderSky", desc = "Анимированное шейдерное небо", category = Category.RENDER)
public class ShaderSky extends Function {
    private final ModeSetting shaderMode = new ModeSetting("Shader", "Full", "WebShader", "Plasma", "ChamsFill", "BaseWarp", "Waves", "Water");
    private final BooleanSetting themeColor = new BooleanSetting("Theme color", true);
    private final ColorSetting color = new ColorSetting("Color", new Color(138, 180, 248));
    private final NumberSetting alpha = new NumberSetting("Alpha", 1.0, 0.1, 1.0, 0.05);
    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.1, 3.0, 0.05);

    public ShaderSky() {
        addSettings(shaderMode, themeColor, color, alpha, speed);
        themeColor.runnable(this::updateVisibility);
        updateVisibility();
    }

    public static void renderWorld(WorldTerrainRenderContext context) {
        ShaderSky shaderSky = FunctionManager.getFunction(ShaderSky.class);
        if (shaderSky == null || !shaderSky.isEnabled()) {
            return;
        }

        shaderSky.render(context);
    }

    private void render(WorldTerrainRenderContext context) {
        Quaternionf orientation = context.worldState().cameraRenderState.orientation;
        Vector3f right = orientation.transform(new Vector3f(1f, 0f, 0f));
        Vector3f up = orientation.transform(new Vector3f(0f, 1f, 0f));
        Vector3f forward = orientation.transform(new Vector3f(0f, 0f, -1f));

        ShaderSkyPipeline.draw(selectedColor(), (float) alpha.getValue(), (float) speed.getValue(), getShaderMode(),
                right, up, forward);
    }

    private void updateVisibility() {
        color.setVisible(!themeColor.isEnabled());
    }

    private Color selectedColor() {
        Color selected = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();
        if (!themeColor.isEnabled()) {
            return selected;
        }

        return new Color(brighten(selected.getRed()), brighten(selected.getGreen()), brighten(selected.getBlue()));
    }

    private static int brighten(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.35f), 0, 255);
    }

    private int getShaderMode() {
        if (shaderMode.isEnabled("Full")) return 0;
        if (shaderMode.isEnabled("WebShader")) return 2;
        if (shaderMode.isEnabled("Plasma")) return 4;
        if (shaderMode.isEnabled("ChamsFill")) return 6;
        if (shaderMode.isEnabled("BaseWarp")) return 8;
        if (shaderMode.isEnabled("Waves")) return 10;
        if (shaderMode.isEnabled("Water")) return 12;
        return 0;
    }
}
