package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;

public class ColorSetting extends Setting {
    @Getter @Setter Color color;
    public ColorSetting(String name, Color color) {
        super(name);
        this.color = color;
    }
}
