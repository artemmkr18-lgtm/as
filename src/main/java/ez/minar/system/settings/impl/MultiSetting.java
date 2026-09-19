package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiSetting extends Setting {
    private final Map<String, Boolean> options = new HashMap<>();
    @Getter private final List<String> enabled = new ArrayList<>();
    @Getter private final List<String> optionsList = new ArrayList<>();
    private Runnable runnable;

    public MultiSetting(String name, String... defaultOptions) {
        super(name);
        for (String opt : defaultOptions) {
            options.put(opt, true);
            enabled.add(opt);
            optionsList.add(opt);
        }
    }

    public boolean isEnabled(String option) {
        return options.getOrDefault(option, false);
    }

    public void toggle(String option) {
        if (options.containsKey(option)) {
            boolean value = !options.get(option);
            options.put(option, value);
            if (value && !enabled.contains(option)) {
                enabled.add(option);
            } else if (!value) {
                enabled.remove(option);
            }
            runCallback();
        }
    }

    public void setEnabledOptions(Set<String> enabledOptions) {
        enabled.clear();
        for (String option : optionsList) {
            boolean value = enabledOptions.contains(option);
            options.put(option, value);
            if (value) {
                enabled.add(option);
            }
        }
        runCallback();
    }

    public void runnable(Runnable runnable) {
        this.runnable = runnable;
        runCallback();
    }

    private void runCallback() {
        if (runnable != null) {
            runnable.run();
        }
    }
}
