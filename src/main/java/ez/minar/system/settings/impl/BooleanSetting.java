package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;

public class BooleanSetting extends Setting {
    @Getter private boolean enabled;
    private Runnable runnable;

    public BooleanSetting(String name, boolean enabled) {
        super(name);
        this.enabled = enabled; 
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (runnable != null) {
            runnable.run();
        }
    }

    public void runnable(Runnable runnable) {
        this.runnable = runnable;
        runnable.run();
    }
}
