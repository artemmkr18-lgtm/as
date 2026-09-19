package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.ColorSetting;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.awt.Color;

@NewFunction(
        name = "WorldRecolor",
        desc = "Изменяет цвет освещения мира",
        category = Category.RENDER
)
public class WorldRecolor extends Function {
    private static final float COLOR_MARKER = 0.001f;

    private final ColorSetting color = new ColorSetting("Цвет", Color.WHITE);

    public WorldRecolor() {
        addSettings(color);
    }

    public Vector3fc encodeLightColor(Vector3fc vanillaColor) {
        if (!isEnabled()) {
            return vanillaColor;
        }

        Color selected = color.getColor();
        return new Vector3f(
                -(selected.getRed() / 255.0f + COLOR_MARKER),
                selected.getGreen() / 255.0f,
                selected.getBlue() / 255.0f
        );
    }
}
