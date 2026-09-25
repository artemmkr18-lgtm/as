package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import org.joml.Vector4f;

import java.awt.Color;

@NewFunction(name = "Fog", desc = "Кастомный атмосферный туман для мира", category = Category.RENDER)
public class Fog extends Function {
    public static Fog Instance;

    private final NumberSetting startDistance = new NumberSetting("Дистанция старта", 8.0, 0.0, 256.0, 1.0);
    private final NumberSetting endDistance = new NumberSetting("Дистанция конца", 64.0, 4.0, 512.0, 1.0);
    private final NumberSetting skyDistance = new NumberSetting("Дистанция неба", 96.0, 4.0, 512.0, 1.0);
    private final NumberSetting cloudDistance = new NumberSetting("Дистанция облаков", 128.0, 4.0, 512.0, 1.0);
    private final NumberSetting colorIntensity = new NumberSetting("Яркость", 0.45, 0.0, 1.0, 0.05);

    public Fog() {
        Instance = this;
        addSettings(startDistance, endDistance, skyDistance, cloudDistance, colorIntensity);
    }

    public static void applyAtmosphericFog(FogData data) {
        if (Instance != null && Instance.isEnabled()) {
            float start = (float) Instance.startDistance.getValue();
            float end = Math.max(start + 1.0f, (float) Instance.endDistance.getValue());
            data.environmentalStart = start;
            data.environmentalEnd = end;
            data.skyEnd = (float) Instance.skyDistance.getValue();
            data.cloudEnd = (float) Instance.cloudDistance.getValue();
            return;
        }

        Atmosphere atmosphere = FunctionManager.getFunction(Atmosphere.class);
        if (atmosphere != null && atmosphere.isEnabled()) {
            atmosphere.applyFog(data);
        }
    }

    public static Vector4f applyAtmosphericColor(Camera camera, Vector4f vanillaColor) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return vanillaColor;
        }

        if (Instance != null && Instance.isEnabled()) {
            Color fogColor = ThemeManager.getThemeColor();
            float intensity = (float) Instance.colorIntensity.getValue();
            Vector4f configuredColor = new Vector4f(
                    fogColor.getRed() / 255.0f,
                    fogColor.getGreen() / 255.0f,
                    fogColor.getBlue() / 255.0f,
                    1.0f
            );
            return new Vector4f(vanillaColor).lerp(configuredColor, intensity);
        }

        Atmosphere atmosphere = FunctionManager.getFunction(Atmosphere.class);
        if (atmosphere != null && atmosphere.isEnabled()) {
            return atmosphere.applyColor(vanillaColor);
        }

        return vanillaColor;
    }
}
