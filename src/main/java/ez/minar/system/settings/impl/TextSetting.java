package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;

public class TextSetting extends Setting {
    @Getter private String value;
    @Getter private boolean editing;
    @Getter private final int maxLength;

    public TextSetting(String name, String defaultValue) {
        this(name, defaultValue, 96);
    }

    public TextSetting(String name, String defaultValue, int maxLength) {
        super(name);
        this.maxLength = Math.max(1, maxLength);
        setValue(defaultValue);
    }

    public void setValue(String value) {
        String sanitized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        this.value = sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    public void append(char chr) {
        if (value.length() >= maxLength || Character.isISOControl(chr)) {
            return;
        }

        setValue(value + chr);
    }

    public void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        for (int i = 0; i < text.length(); i++) {
            append(text.charAt(i));
        }
    }

    public void backspace() {
        if (!value.isEmpty()) {
            value = value.substring(0, value.length() - 1);
        }
    }
}
