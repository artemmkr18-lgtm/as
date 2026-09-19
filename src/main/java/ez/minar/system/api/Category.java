package ez.minar.system.api;

import lombok.Getter;

public enum Category {
    COMBAT("Combat", "\uE002"),
    MOVEMENT("Movement", "C"),
    RENDER("Render", "X"),
    PLAYER("Player", "\uE012"),
    MISC("Misc", "M"),
    MENU("Menu", "V"),
    CONFIG("Config", "G");

    @Getter private final String name;
    @Getter private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getDisplayName() {
        return ez.minar.system.managers.LocalizationManager.get(this.name);
    }
}
