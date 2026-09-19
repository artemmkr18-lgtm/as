package ez.minar.system.features.render;

import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

final class HudDrag {
    static final float SCALE = 1.0f;
    static final int GRID = 8;
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final double[] CURSOR_X = new double[1], CURSOR_Y = new double[1];
    private static HudDrag owner;
    private static boolean down, previousDown, pressed, editing, ownerSeen, freeMove;
    private static float mouseX, mouseY;
    private final NumberSetting x, y;
    private float grabX, grabY;
    private final float[] placed = new float[2];

    HudDrag(String name, float x, float y) {
        this(new NumberSetting(name + " X", x, 0, 16384, 0.5),
                new NumberSetting(name + " Y", y, 0, 16384, 0.5));
    }

    HudDrag(NumberSetting x, NumberSetting y) {
        this.x = x;
        this.y = y;
        x.setVisible(false);
        y.setVisible(false);
    }

    NumberSetting[] settings() { return new NumberSetting[]{x, y}; }
    float x() { return (float) x.getValue(); }
    float y() { return (float) y.getValue(); }
    void position(float px, float py) { x.setValue(px); y.setValue(py); }

    static void beginFrame() {
        editing = MC.currentScreen instanceof ChatScreen;
        ownerSeen = false;
        if (!editing) { cancelAll(); return; }
        long handle = MC.getWindow().getHandle();
        GLFW.glfwGetCursorPos(handle, CURSOR_X, CURSOR_Y);
        mouseX = (float) (CURSOR_X[0] * RenderUtil.getFixedScaledWidth() / MC.getWindow().getWidth());
        mouseY = (float) (CURSOR_Y[0] * RenderUtil.getFixedScaledHeight() / MC.getWindow().getHeight());
        freeMove = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        pressed = down && !previousDown;
        if (!down) owner = null;
    }

    static void drawGrid(DrawContext context) {
        if (!editing || owner == null) return;
        int width = RenderUtil.getFixedScaledWidth(), height = RenderUtil.getFixedScaledHeight();
        for (int x = 0; x < width; x += GRID) context.fill(x, 0, x + 1, height, x % 32 == 0 ? 0x18FFFFFF : 0x08FFFFFF);
        for (int y = 0; y < height; y += GRID) context.fill(0, y, width, y + 1, y % 32 == 0 ? 0x18FFFFFF : 0x08FFFFFF);
    }

    static void endFrame() {
        if (!ownerSeen) owner = null;
        previousDown = down;
    }

    static void cancelAll() {
        owner = null;
        pressed = down = previousDown = editing = false;
    }

    void cancel() { if (owner == this) owner = null; }

    float[] place(float width, float height) { return place(width, height, 0); }

    float[] place(float width, float height, float bottomInset) {
        float maxX = Math.max(0, RenderUtil.getFixedScaledWidth() - width * SCALE);
        float maxY = Math.max(0, RenderUtil.getFixedScaledHeight() - (height + bottomInset) * SCALE);
        float px = Math.clamp(x(), 0, maxX), py = Math.clamp(y(), 0, maxY);
        if (editing && pressed && owner == null && mouseX >= px && mouseX <= px + width * SCALE
                && mouseY >= py && mouseY <= py + height * SCALE) {
            owner = this;
            pressed = false;
            grabX = mouseX - px;
            grabY = mouseY - py;
        }
        if (owner == this) {
            ownerSeen = true;
            px = mouseX - grabX;
            py = mouseY - grabY;
            if (!freeMove) {
                px = Math.round(px / GRID) * GRID;
                py = Math.round(py / GRID) * GRID;
            }
            px = Math.clamp(px, 0, maxX);
            py = Math.clamp(py, 0, maxY);
            position(px, py);
        }
        placed[0] = px;
        placed[1] = py;
        // Stored positions stay in screen space; only each widget's contents are scaled.
        RenderUtil.setUiScale(px, py, SCALE);
        return placed;
    }
}
