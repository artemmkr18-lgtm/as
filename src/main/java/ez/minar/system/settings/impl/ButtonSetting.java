package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;

public class ButtonSetting extends Setting {
    private final String buttonText;
    private final Runnable action;
    private String storedValue;

    public ButtonSetting(String name, String buttonText, Runnable action) {
        this(name, buttonText, "", action);
    }

    public ButtonSetting(String name, String buttonText, String storedValue, Runnable action) {
        super(name);
        this.buttonText = buttonText;
        this.storedValue = storedValue;
        this.action = action;
    }

    public String getButtonText() {
        return buttonText;
    }

    public void press() {
        if (action != null) {
            action.run();
        }
    }

    public String getStoredValue() {
        return storedValue;
    }

    public void setStoredValue(String storedValue) {
        this.storedValue = storedValue == null ? "" : storedValue;
    }
}
