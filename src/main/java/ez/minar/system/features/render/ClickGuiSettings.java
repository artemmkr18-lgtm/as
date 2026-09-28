package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import lombok.Getter;

import java.awt.Color;

/**
 * Внешний вид ClickGUI: цветовая схема (те же режимы, что у HUD) и масштаб окна.
 * Drop-вариант убран — открывается всегда {@link ez.minar.system.menu.ClickGui}.
 */
@NewFunction(name = "ClickGUI", desc = "Цвет и масштаб интерфейса", category = Category.RENDER)
public class ClickGuiSettings extends Function {

    public static final String MODE_THEME = "Тема";
    public static final String MODE_STATIC = "Статик";
    public static final String MODE_GRADIENT = "Градиент";
    public static final String MODE_RAINBOW = "Радуга";
    public static final String MODE_PULSE = "Пульс";

    @Getter private final ModeSetting colorMode =
            new ModeSetting("Цвет", MODE_THEME, MODE_STATIC, MODE_GRADIENT, MODE_RAINBOW, MODE_PULSE);
    @Getter private final ColorSetting color1 = new ColorSetting("Цвет 1", new Color(0, 170, 255));
    @Getter private final ColorSetting color2 = new ColorSetting("Цвет 2", new Color(150, 0, 255));
    @Getter private final NumberSetting speed = new NumberSetting("Скорость", 1.0, 0.1, 5.0, 0.1);
    @Getter private final NumberSetting offset = new NumberSetting("Смещение", 100.0, 0.0, 1000.0, 10.0);
    @Getter private final NumberSetting scale = new NumberSetting("Масштаб", 1.0, 0.5, 1.3, 0.05);

    public ClickGuiSettings() {
        addSettings(colorMode, color1, color2, speed, offset, scale);
        colorMode.runnable(this::updateVisibility);
    }

    private void updateVisibility() {
        boolean theme = colorMode.isEnabled(MODE_THEME);
        color1.setVisible(!theme);
        color2.setVisible(colorMode.isEnabled(MODE_GRADIENT) || colorMode.isEnabled(MODE_PULSE));
    }

    /**
     * Акцент интерфейса в фазе {@code position} (0..1 — положение элемента в анимации),
     * тем же способом, что HUD: тема клиента, статик, градиент, радуга или пульс.
     */
    public Color getAccent(float position) {
        double time = System.currentTimeMillis() / 1000.0 * speed.getValue();
        double off = offset.getValue() / 1000.0;
        float wave = (float) ((Math.sin(time + position * Math.PI * 2 * off) + 1) / 2.0);

        return switch (colorMode.getActiveMode()) {
            case MODE_STATIC -> color1.getColor();
            case MODE_GRADIENT -> lerp(color1.getColor(), color2.getColor(), wave);
            case MODE_RAINBOW -> Color.getHSBColor((float) (((time + position * off) % 1.0 + 1.0) % 1.0), 0.7f, 1f);
            case MODE_PULSE -> lerp(color1.getColor(), color2.getColor(),
                    (float) ((Math.sin(time * 2) + 1) / 2.0));
            default -> {
                Color t1 = ThemeManager.getGuiAccent();
                Color t2 = ThemeManager.twoColors ? ThemeManager.Theme_Color2 : lerp(t1, Color.WHITE, 0.25f);
                yield lerp(t1, t2, wave);
            }
        };
    }

    private static Color lerp(Color a, Color b, float t) {
        float k = Math.clamp(t, 0f, 1f);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }
}
