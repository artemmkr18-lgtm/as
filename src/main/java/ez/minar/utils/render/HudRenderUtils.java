package ez.minar.utils.render;

import ez.minar.system.features.render.HUD;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.util.function.Predicate;

public final class HudRenderUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private HudRenderUtils() {
    }

    public static void drawHotbarSlotHighlight(DrawContext context, int slot, Color color) {
        if (slot < 0 || slot > 8 || mc.player == null) {
            return;
        }
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float x;
        float y;
        if (HUD.Instance != null && HUD.Instance.isEnabled() && HUD.Instance.hotbar.isEnabled()) {
            float startX = (float) screenWidth / 2.0f - 100.0f;
            float startY = (float) screenHeight - 29.0f;
            x = startX + 6.0f + (float) slot * 21.5f;
            y = startY + 5.5f;
        } else {
            int startX = screenWidth / 2 - 91;
            float startY = (float) (screenHeight - 22);
            x = startX + slot * 20 + 3;
            y = startY + 3.0f;
        }

        RenderUtil.rect(x, y, 18.0f, 18.0f, 4.0f, 4.0f, 4.0f, 4.0f,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, color.getAlpha())));
        RenderUtil.outline(x, y, 18.0f, 18.0f, 4.0f, 0.75f,
                new Color(255, 255, 255, 60));
    }

    public static boolean highlightItemInHotbar(DrawContext context, Item item, Color color) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                drawHotbarSlotHighlight(context, i, color);
                return true;
            }
        }
        return false;
    }

    public static boolean highlightMatchingInHotbar(DrawContext context, Predicate<ItemStack> predicate, Color color) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                drawHotbarSlotHighlight(context, i, color);
                return true;
            }
        }
        return false;
    }

    public static void rotateAround(Matrix3x2fStack matrixStack, float x, float y, float degrees) {
        matrixStack.pushMatrix();
        matrixStack.translate(x, y);
        matrixStack.rotate((float) Math.toRadians(degrees));
        matrixStack.translate(-x, -y);
    }

    public static void scaleAround(Matrix3x2fStack matrixStack, float x, float y, float scale) {
        matrixStack.pushMatrix();
        matrixStack.translate(x, y);
        matrixStack.scale(scale, scale);
        matrixStack.translate(-x, -y);
    }

    public static void popMatrix(Matrix3x2fStack matrixStack) {
        matrixStack.popMatrix();
    }

    public static void enableBlend(boolean additive) {
        // Modern 1.21.11 uses custom GPU render passes
    }

    public static void disableBlend() {
        // Modern 1.21.11 uses custom GPU render passes
    }
}
