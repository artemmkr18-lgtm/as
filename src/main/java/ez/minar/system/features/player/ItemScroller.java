package ez.minar.system.features.player;

import ez.minar.mixins.interfaces.IHandledScreen;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

@NewFunction(name = "ItemScroller", desc = "Быстрое перемещение предметов при зажатом Shift и ЛКМ", category = Category.PLAYER)
public class ItemScroller extends Function {
    private final NumberSetting cooldown = new NumberSetting("Cooldown", 30.0, 1.0, 200.0, 0.01);
    private long lastClickTime;

    public ItemScroller() {
        addSettings(cooldown);
    }

    @EventHandler
    public void onRender(Render2DEvent event) {
        if (nullCheck.all()
                || mc.currentScreen == null
                || mc.interactionManager == null
                || !(mc.currentScreen instanceof HandledScreen<?> screen)
                || !isShiftDown()
                || GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) != GLFW.GLFW_PRESS
                || !hasReached((long) cooldown.getValue())) {
            return;
        }

        Slot slot = ((IHandledScreen) screen).getFocusedSlot();
        if (slot == null || !slot.hasStack()) {
            return;
        }

        mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot.id,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
        );
        resetTimer();
    }

    private boolean isShiftDown() {
        return mc.options.sneakKey.isPressed()
                || InputUtil.isKeyPressed(mc.getWindow(), mc.options.sneakKey.getDefaultKey().getCode());
    }

    private boolean hasReached(long delay) {
        return System.currentTimeMillis() - lastClickTime >= delay;
    }

    private void resetTimer() {
        lastClickTime = System.currentTimeMillis();
    }
}
