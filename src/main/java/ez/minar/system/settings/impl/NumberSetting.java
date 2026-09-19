package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;
import lombok.Setter;

import java.util.function.DoubleSupplier;

public class NumberSetting extends Setting {
    @Getter @Setter private double value;
    @Getter @Setter private double min, max, step;
    private Runnable runnable;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name);
        this.value = Math.clamp(defaultValue, min, max);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public NumberSetting(String name, double defaultValue, DoubleSupplier min, DoubleSupplier max, double step) {
        super(name);
        this.value = Math.clamp(defaultValue, min.getAsDouble(), max.getAsDouble());
        this.min = min.getAsDouble();
        this.max = max.getAsDouble();
        this.step = step;
    }

    public void setValue(double value) {
        this.value = Math.clamp(value, min, max);
        if (runnable != null) {
            runnable.run();
        }
    }

    public void runnable(Runnable runnable) {
        this.runnable = runnable;
        runnable.run();
    }
}