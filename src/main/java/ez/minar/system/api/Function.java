package ez.minar.system.api;

import ez.minar.system.events.EventBus;
import ez.minar.system.features.misc.Sounds;
import ez.minar.system.features.render.HUD;
import ez.minar.system.settings.Setting;
import ez.minar.utils.helpers.NullCheck;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Function {
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    protected final NullCheck nullCheck = new NullCheck();
    @Getter private final String name;
    @Getter private final String desc;
    @Getter private final Category category;
    @Getter @Setter private int keybind;
    @Getter @Setter private boolean enabled;
    @Getter  private final List<Setting> settings = new ArrayList<>();

    public Function() {
        if (this.getClass().isAnnotationPresent(NewFunction.class)) {
            NewFunction annotation = this.getClass().getAnnotation(NewFunction.class);
            this.name = annotation.name();
            this.desc = annotation.desc();
            this.category = annotation.category();
            this.enabled = false;
        } else {
            throw new RuntimeException("Error " + this.getClass().getSimpleName());
        }
    }

    public String getDisplayName() {
        return ez.minar.system.managers.LocalizationManager.get(this.name);
    }

    public String getDisplayDesc() {
        return ez.minar.system.managers.LocalizationManager.get(this.desc);
    }

    public void addSettings(Setting... setting) {
        this.settings.addAll(Arrays.asList(setting));
    }

    public void toggle() {
        boolean wasEnabled = enabled;
        this.enabled = !this.enabled;
        playToggleSound(wasEnabled);
        if (enabled) {
            onEnable();
            EventBus.register(this);
        } else {
            onDisable();
            EventBus.unregister(this);
        }
        HUD.pushModuleNotification(this);
    }

    private void playToggleSound(boolean wasEnabled) {
        Sounds sounds = FunctionManager.getFunction(Sounds.class);
        boolean soundsModuleToggle = this instanceof Sounds && (wasEnabled || enabled);
        if ((sounds != null && sounds.isEnabled()) || soundsModuleToggle) {
            Sounds.playToggleSound(enabled);
        }
    }

    public void onEnable() {}
    public void onDisable() {}

    public void forceDisable() {
        if (!enabled) {
            return;
        }

        enabled = false;
        try {
            onDisable();
        } finally {
            EventBus.unregister(this);
        }
    }

    public void forceEnable() {
        if (enabled) {
            return;
        }

        enabled = true;
        onEnable();
        EventBus.register(this);
    }
}
