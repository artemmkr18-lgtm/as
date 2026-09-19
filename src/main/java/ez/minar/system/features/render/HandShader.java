package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.HandShaderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.awt.Color;

@NewFunction(name = "HandShader", desc = "Огненно-дымный шлейф и красивые шейдеры на руках и предметах", category = Category.RENDER)
public class HandShader extends Function {
    public static HandShader Instance;

    private final ModeSetting mode = new ModeSetting("Режим", "Красивый", "Дым", "Стекло", "Плазма");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Цвет предмета", "Цвет темы", "Кастомный");
    private final ColorSetting customColor = new ColorSetting("Основной цвет", new Color(0x66, 0x33, 0xFF));
    private final ColorSetting customTipColor = new ColorSetting("Цвет пламени", new Color(0xFF, 0x55, 0x22));

    private final NumberSetting intensity = new NumberSetting("Интенсивность", 1.0, 0.1, 2.5, 0.05);
    private final NumberSetting speed = new NumberSetting("Скорость", 1.15, 0.2, 3.0, 0.05);
    private final NumberSetting height = new NumberSetting("Высота пламени", 0.08, 0.01, 0.35, 0.01);
    private final NumberSetting wind = new NumberSetting("Ветер", 1.0, 0.0, 3.0, 0.1);
    private final NumberSetting wave = new NumberSetting("Волны", 1.0, 0.0, 3.0, 0.1);
    private final NumberSetting glow = new NumberSetting("Глоу", 2.0, 0.5, 5.0, 0.1);
    private final NumberSetting smoke = new NumberSetting("Плотность дыма", 0.55, 0.0, 1.0, 0.05);
    private final NumberSetting alpha = new NumberSetting("Прозрачность", 0.95, 0.1, 1.0, 0.05);
    private final BooleanSetting hideHands = new BooleanSetting("Скрыть текстуру рук", false);

    public HandShader() {
        Instance = this;
        addSettings(
                mode,
                colorMode,
                customColor,
                customTipColor,
                intensity,
                speed,
                height,
                wind,
                wave,
                glow,
                smoke,
                alpha,
                hideHands
        );

        mode.runnable(this::updateVisibility);
        colorMode.runnable(this::updateVisibility);
        updateVisibility();
    }

    private void updateVisibility() {
        customColor.setVisible(colorMode.isEnabled("Кастомный"));
        customTipColor.setVisible(colorMode.isEnabled("Кастомный") && mode.isEnabled("Красивый"));
        height.setVisible(mode.isEnabled("Красивый"));
        wind.setVisible(mode.isEnabled("Красивый"));
        wave.setVisible(mode.isEnabled("Красивый"));
        smoke.setVisible(mode.isEnabled("Дым"));
    }

    public void renderMask(Runnable renderer) {
        if (isEnabled()) {
            HandShaderPipeline.renderMask(renderer);
        }
    }

    public void drawPostEffect() {
        if (!isEnabled()) return;

        Color main = getMainColor();
        Color tip = getTipColor(main);

        int modeIndex = 0;
        if (mode.isEnabled("Дым")) modeIndex = 1;
        else if (mode.isEnabled("Стекло")) modeIndex = 2;
        else if (mode.isEnabled("Плазма")) modeIndex = 3;

        HandShaderPipeline.draw(
                modeIndex,
                (float) intensity.getValue(),
                (float) speed.getValue(),
                (float) height.getValue(),
                (float) wind.getValue(),
                (float) wave.getValue(),
                (float) glow.getValue(),
                (float) smoke.getValue(),
                new Color(main.getRed(), main.getGreen(), main.getBlue(), (int) (alpha.getValue() * 255)),
                tip
        );
    }

    public boolean shouldHideHands() {
        return isEnabled() && hideHands.isEnabled();
    }

    public Color getMainColor() {
        if (colorMode.isEnabled("Цвет темы")) {
            return ThemeManager.getThemeColor();
        }
        if (colorMode.isEnabled("Цвет предмета")) {
            return heldItemColor();
        }
        return customColor.getColor();
    }

    public Color getTipColor(Color base) {
        if (colorMode.isEnabled("Кастомный")) {
            return customTipColor.getColor();
        }
        // Автоматический огненный градиент на основе базового цвета
        return new Color(
                Math.min(255, (int) (base.getRed() * 1.35 + 40)),
                Math.max(0, (int) (base.getGreen() * 0.75)),
                Math.max(0, (int) (base.getBlue() * 0.65))
        );
    }

    private Color heldItemColor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Color.WHITE;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) {
            stack = mc.player.getOffHandStack();
        }
        if (stack.isEmpty()) {
            return Color.WHITE;
        }

        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (path.contains("diamond")) {
            return new Color(0x55, 0xDD, 0xE0);
        }
        if (path.contains("netherite")) {
            return new Color(0x8B, 0x4A, 0xC7);
        }
        if (path.contains("gold")) {
            return new Color(0xFF, 0xD4, 0x5A);
        }
        if (path.contains("iron")) {
            return new Color(0xD8, 0xDE, 0xE8);
        }
        if (path.contains("emerald")) {
            return new Color(0x35, 0xD0, 0x6F);
        }
        if (path.contains("redstone")) {
            return new Color(0xE2, 0x3B, 0x3B);
        }
        if (path.contains("lapis")) {
            return new Color(0x31, 0x56, 0xD4);
        }
        if (path.contains("totem")) {
            return new Color(0xF3, 0xA4, 0x3B);
        }
        if (path.contains("crystal")) {
            return new Color(0xDA, 0x49, 0xE2);
        }
        return new Color(0x66, 0x33, 0xFF);
    }
}
