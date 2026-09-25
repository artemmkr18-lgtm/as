package ez.minar.utils.helpers;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public class MonitorInfoProvider {
    private static long lastMonitorHandle = 0;
    private static int lastRefreshRate = 60;
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_NS = 1_000_000_000L;

    public static void updateDisplayInfo() {
        long now = System.nanoTime();
        if (now - lastCheckTime < CHECK_INTERVAL_NS) {
            return;
        }
        lastCheckTime = now;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        long window = client.getWindow().getHandle();
        long monitor = GLFW.glfwGetWindowMonitor(window);

        if (monitor == 0) {
            monitor = getMonitorFromWindowPosition(window, client.getWindow().getWidth(), client.getWindow().getHeight());
        }

        if (monitor != lastMonitorHandle) {
            lastRefreshRate = detectRefreshRateFromMonitor(monitor);
            lastMonitorHandle = monitor;
        }
    }

    public static int getRefreshRate() {
        return lastRefreshRate;
    }

    private static int detectRefreshRateFromMonitor(long monitor) {
        if (monitor == 0) {
            return 60;
        }
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        return (mode != null && mode.refreshRate() > 0) ? mode.refreshRate() : 60;
    }

    private static long getMonitorFromWindowPosition(long window, int width, int height) {
        int[] wx = new int[1], wy = new int[1];
        GLFW.glfwGetWindowPos(window, wx, wy);
        int centerX = wx[0] + width / 2;
        int centerY = wy[0] + height / 2;

        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors == null) {
            return GLFW.glfwGetPrimaryMonitor();
        }

        int[] mx = new int[1], my = new int[1];
        for (int i = 0; i < monitors.limit(); i++) {
            long monitor = monitors.get(i);
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            if (mode == null) continue;

            if (centerX >= mx[0] && centerX < mx[0] + mode.width() &&
                centerY >= my[0] && centerY < my[0] + mode.height()) {
                return monitor;
            }
        }
        return GLFW.glfwGetPrimaryMonitor();
    }
}
