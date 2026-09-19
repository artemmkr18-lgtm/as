package ez.minar.utils.helpers;

import ez.minar.system.settings.impl.KeybindSetting;
import org.lwjgl.glfw.GLFW;

public class KeyHelper {

    public static String getKeyName(int key) {
        if (key <= 0) return "None";

        if (KeybindSetting.isMouseKey(key)) {
            return switch (KeybindSetting.getMouseButton(key)) {
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "M.Mouse";
                case GLFW.GLFW_MOUSE_BUTTON_4 -> "Mouse 4";
                case GLFW.GLFW_MOUSE_BUTTON_5 -> "Mouse 5";
                default -> "Mouse " + KeybindSetting.getMouseButton(key);
            };
        }

        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) key);
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) key);
        }

        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L.Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R.Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L.Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R.Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L.Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R.Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            default -> {
                String name = GLFW.glfwGetKeyName(key, 0);
                yield name == null ? "Key " + key : name.toUpperCase();
            }
        };
    }
}
