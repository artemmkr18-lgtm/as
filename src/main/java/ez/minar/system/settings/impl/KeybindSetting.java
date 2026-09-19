package ez.minar.system.settings.impl;

import ez.minar.system.settings.Setting;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting {
    private static final int MOUSE_BIND_OFFSET = 1000;

    @Getter
    @Setter
    int keybind;

    private boolean wasMouseDown;

    public KeybindSetting(String name, int keybind) {
        super(name);
        this.keybind = keybind;
    }

    public static int toMouseKey(int button) {
        return MOUSE_BIND_OFFSET + button;
    }

    public static boolean isMouseKey(int keybind) {
        return keybind >= MOUSE_BIND_OFFSET;
    }

    public static int getMouseButton(int keybind) {
        return keybind - MOUSE_BIND_OFFSET;
    }

    public static boolean isBindableMouseButton(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || button == GLFW.GLFW_MOUSE_BUTTON_4
                || button == GLFW.GLFW_MOUSE_BUTTON_5;
    }

    public boolean matchesKey(int key) {
        return keybind > 0 && !isMouseKey(keybind) && keybind == key;
    }

    public boolean consumeMouseClick(long windowHandle) {
        if (!isMouseKey(keybind)) {
            wasMouseDown = false;
            return false;
        }

        boolean down = GLFW.glfwGetMouseButton(windowHandle, getMouseButton(keybind)) == GLFW.GLFW_PRESS;
        boolean clicked = down && !wasMouseDown;
        wasMouseDown = down;
        return clicked;
    }
}
