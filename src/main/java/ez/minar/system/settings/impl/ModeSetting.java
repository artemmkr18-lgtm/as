package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting {

    @Getter private String activeMode;
    @Getter private final List<String> modes;
    private Runnable runnable;

    public ModeSetting(String name, String... modes) {
        super(name);
        this.modes = Arrays.asList(modes);
        this.activeMode = modes[0];
    }

    public void setMode(String mode) {
        if (modes.contains(mode)) {
            this.activeMode = mode;
            if (runnable != null) {
                runnable.run();
            }
        }
    }

    public boolean isEnabled(String mode) {
        return this.activeMode.equalsIgnoreCase(mode);
    }

    public void runnable(Runnable runnable) {
        this.runnable = runnable;
        runnable.run();
    }
}