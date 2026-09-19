package ez.minar.system.events;

import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.events.impl.KeyEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.render.HUD;
import ez.minar.system.features.render.ClickGuiSettings;
import ez.minar.system.menu.DropClickGui;
import ez.minar.system.managers.UnhookManager;
import ez.minar.system.menu.ClickGui;
import ez.minar.system.settings.impl.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventManager {
    private static final EventManager INSTANCE = new EventManager();

    MinecraftClient mc = MinecraftClient.getInstance();
    public static ClickGui clickGui = new ClickGui();
    public static final DropClickGui dropClickGui = new DropClickGui();

    public static void applyClickGuiStyle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != clickGui && client.currentScreen != dropClickGui) return;
        ClickGuiSettings settings = FunctionManager.getFunction(ClickGuiSettings.class);
        var selected = settings != null && settings.getStyle().isEnabled("Дроп") ? dropClickGui : clickGui;
        if (client.currentScreen != selected) client.setScreen(selected);
    }
    private final Map<Function, Boolean> mouseBindStates = new HashMap<>();

    public static EventManager getInstance() {
        return INSTANCE;
    }

    public static void call(Object event) {
        for (Function function : FunctionManager.getFunctions()) {
            if (function == null || !function.isEnabled()) continue;

            for (Method method : function.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(EventHandler.class)) continue;

                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1) continue;
                if (!params[0].isAssignableFrom(event.getClass())) continue;

                try {
                    method.setAccessible(true);
                    method.invoke(function, event);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @EventHandler
    public void Events(KeyEvent event) {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        int key = event.getInput().key();
        int action = event.getAction();

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT && action == GLFW.GLFW_PRESS) {
            if (mc.currentScreen == clickGui || mc.currentScreen == dropClickGui) {
                mc.currentScreen.close();
            } else {
                ClickGuiSettings settings = FunctionManager.getFunction(ClickGuiSettings.class);
                mc.setScreen(settings != null && settings.getStyle().isEnabled("Дроп") ? dropClickGui : clickGui);
            }
            return;
        }

        if (mc.currentScreen == null && action == GLFW.GLFW_PRESS) {
            List<Function> toggledFunctions = new ArrayList<>();
            for (Function function : FunctionManager.getFunctions()) {
                if (function.getKeybind() == key && function.getKeybind() > 0) {
                    toggledFunctions.add(function);
                }
            }
            toggleBatch(toggledFunctions);
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        if (mc.currentScreen != null || mc.getWindow() == null) {
            mouseBindStates.clear();
            return;
        }

        long window = mc.getWindow().getHandle();
        List<Function> toggledFunctions = new ArrayList<>();
        for (Function function : FunctionManager.getFunctions()) {
            int keybind = function.getKeybind();
            if (!KeybindSetting.isMouseKey(keybind)) {
                mouseBindStates.remove(function);
                continue;
            }

            boolean down = GLFW.glfwGetMouseButton(window, KeybindSetting.getMouseButton(keybind)) == GLFW.GLFW_PRESS;
            boolean wasDown = mouseBindStates.getOrDefault(function, false);
            if (down && !wasDown) {
                toggledFunctions.add(function);
            }
            mouseBindStates.put(function, down);
        }
        toggleBatch(toggledFunctions);
    }

    private void toggleBatch(List<Function> functions) {
        if (functions.isEmpty()) return;
        functions.forEach(Function::toggle);
    }
}
