package ez.minar.utils.render.scissor;

import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;

public class Scissor {
    private static final Deque<Rectangle> stack = new ArrayDeque<>();
    public static void enableScissor(int x1, int y1, int x2, int y2) {
        push(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public static void enableScissor(double x1, double y1, double x2, double y2) {
        push(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public static void disableScissor() {
        pop();
    }

    public static void push(double x, double y, double w, double h) {
        push(x, y, w, h, false);
    }

    public static void push(double x, double y, double w, double h, boolean overrideContext) {
        if (overrideContext) {
            RenderUtil.addOverrideTask(() -> push(x, y, w, h, false));
            return;
        }

        Rectangle current = new Rectangle(
                RenderUtil.transformUiX(x),
                RenderUtil.transformUiY(y),
                Math.max(0, RenderUtil.transformUiSize(w)),
                Math.max(0, RenderUtil.transformUiSize(h)));
        if (!stack.isEmpty()) current = intersect(current, stack.peek());
        stack.push(current);

        apply(current);
    }

    public static void pushVertical(double y, double h) {
        pushVertical(y, h, false);
    }

    public static void pushVertical(double y, double h, boolean overrideContext) {
        double width = RenderUtil.getFixedScaledWidth();
        push(0, y, width, h, overrideContext);
    }

    public static void popVertical() {
        pop(false);
    }

    public static void popVertical(boolean overrideContext) {
        pop(overrideContext);
    }

    public static void pop() {
        pop(false);
    }

    public static void pop(boolean overrideContext) {
        if (overrideContext) {
            RenderUtil.addOverrideTask(() -> pop(false));
            return;
        }

        if (!stack.isEmpty()) stack.pop();

        if (stack.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            apply(stack.peek());
        }
    }

    public static void drawInnerShadow(float size, Color shadow) {
        if (stack.isEmpty() || size <= 0f) return;
        Rectangle r = stack.peek();
        if (r.w <= 0 || r.h <= 0) return;

        Color transparent = new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), 0);

        float x = (float) r.x, y = (float) r.y;
        float w = (float) r.w, h = (float) r.h;
        float s = Math.min(size, Math.min(w, h) * 0.5f);

        RenderUtil.rect(x, y, w, s, 0, 0, 0, 0,
                shadow, shadow, transparent,
                shadow, shadow, transparent,
                transparent, transparent, transparent);

        // Bottom
        RenderUtil.rect(x, y + h - s, w, s, 0, 0, 0, 0,
                transparent, transparent, transparent,
                shadow, shadow, transparent,
                shadow, shadow, transparent);

        // Left
        RenderUtil.rect(x, y + s, s, Math.max(0, h - s * 2f), 0, 0, 0, 0,
                shadow, transparent, transparent,
                shadow, transparent, transparent,
                shadow, transparent, transparent);

        // Right
        RenderUtil.rect(x + w - s, y + s, s, Math.max(0, h - s * 2f), 0, 0, 0, 0,
                transparent, transparent, shadow,
                transparent, transparent, shadow,
                transparent, transparent, shadow);
    }

    private static Rectangle intersect(Rectangle a, Rectangle b) {
        double nx = Math.max(a.x, b.x);
        double ny = Math.max(a.y, b.y);
        double nw = Math.min(a.x + a.w, b.x + b.w) - nx;
        double nh = Math.min(a.y + a.h, b.y + b.h) - ny;
        return new Rectangle(nx, ny, Math.max(0, nw), Math.max(0, nh));
    }

    private static void apply(Rectangle rect) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = (float) mc.getWindow().getWidth() / Math.max(1f, RenderUtil.getFixedScaledWidth());

        int scissorX = (int) Math.floor(rect.x * scale);
        int scissorY = (int) Math.floor(mc.getWindow().getHeight() - (rect.y + rect.h) * scale);
        int scissorW = (int) Math.ceil(rect.w * scale);
        int scissorH = (int) Math.ceil(rect.h * scale);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.max(0, scissorX), Math.max(0, scissorY),
                Math.max(0, scissorW), Math.max(0, scissorH));
    }

    private record Rectangle(double x, double y, double w, double h) {
    }
}
