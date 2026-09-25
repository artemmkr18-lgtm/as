package ez.minar.system.features.render;

import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

final class HudDrag {
    static final int GRID = 8;
    static final double MIN_SCALE = 0.5, MAX_SCALE = 2.0;
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final double[] CURSOR_X = new double[1], CURSOR_Y = new double[1];
    private static HudDrag owner;
    private static boolean down, previousDown, pressed, editing, ownerSeen, freeMove;
    private static boolean rightDown, previousRightDown, rightPressed;
    private static boolean overlayBlocking;
    private static float mouseX, mouseY;
    private final String label;
    private final NumberSetting x, y, scale;
    private float grabX, grabY;
    private final float[] placed = new float[2];

    HudDrag(String name, float x, float y) {
        this(name, new NumberSetting(name + " X", x, 0, 16384, 0.5),
                new NumberSetting(name + " Y", y, 0, 16384, 0.5),
                new NumberSetting(name + " Масштаб", 1, MIN_SCALE, MAX_SCALE, 0.05));
    }

    HudDrag(String label, NumberSetting x, NumberSetting y, NumberSetting scale) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.scale = scale;
        x.setVisible(false);
        y.setVisible(false);
        scale.setVisible(false);
    }

    NumberSetting[] settings() { return new NumberSetting[]{x, y, scale}; }
    float x() { return (float) x.getValue(); }
    float y() { return (float) y.getValue(); }
    void position(float px, float py) { x.setValue(px); y.setValue(py); }

    String label() { return label; }
    float scale() { return (float) scale.getValue(); }
    NumberSetting scaleSetting() { return scale; }

    static void beginFrame() {
        editing = MC.currentScreen instanceof ChatScreen;
        ownerSeen = false;
        overlayBlocking = false;
        if (!editing) { cancelAll(); return; }
        long handle = MC.getWindow().getHandle();
        GLFW.glfwGetCursorPos(handle, CURSOR_X, CURSOR_Y);
        mouseX = (float) (CURSOR_X[0] * RenderUtil.getFixedScaledWidth() / MC.getWindow().getWidth());
        mouseY = (float) (CURSOR_Y[0] * RenderUtil.getFixedScaledHeight() / MC.getWindow().getHeight());
        freeMove = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        pressed = down && !previousDown;
        rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        rightPressed = rightDown && !previousRightDown;
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
        previousRightDown = rightDown;
    }

    static void cancelAll() {
        owner = null;
        pressed = down = previousDown = editing = false;
        rightDown = previousRightDown = rightPressed = false;
    }

    static boolean wasRightPressed() { return rightPressed; }
    static float mouseX() { return mouseX; }
    static float mouseY() { return mouseY; }
    static boolean isEditing() { return editing; }
    static boolean isLeftDown() { return down; }
    static boolean wasLeftPressed() { return pressed; }
    static void setOverlayBlocking(boolean value) { overlayBlocking = value; }

    void cancel() { if (owner == this) owner = null; }

    float[] place(float width, float height) { return place(width, height, 0); }

    float[] place(float width, float height, float bottomInset) {
        float s = scale();
        float maxX = Math.max(0, RenderUtil.getFixedScaledWidth() - width * s);
        float maxY = Math.max(0, RenderUtil.getFixedScaledHeight() - (height + bottomInset) * s);
        float px = Math.clamp(x(), 0, maxX), py = Math.clamp(y(), 0, maxY);
        if (editing && pressed && !overlayBlocking && owner == null && mouseX >= px && mouseX <= px + width * s
                && mouseY >= py && mouseY <= py + height * s) {
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
        RenderUtil.setUiScale(px, py, s);
        // An element opts into its appear pop right after placing itself; everything else must not
        // inherit the previous card's.
        RenderUtil.resetPopScale();
        return placed;
    }
}
