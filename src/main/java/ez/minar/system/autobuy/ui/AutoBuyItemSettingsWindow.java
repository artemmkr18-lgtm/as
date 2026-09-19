package ez.minar.system.autobuy.ui;

import ez.minar.system.autobuy.config.AutoBuyConfigFile;
import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.originalitems.ItemRegistry;
import ez.minar.system.autobuy.settings.AutoBuyItemSettings;
import ez.minar.system.autobuy.settings.AutoBuySettingsManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class AutoBuyItemSettingsWindow {
    private final AutoBuyableItem item;
    private final AutoBuyItemSettings settings;
    private int x, y, width, height;

    private boolean editingPrice = false;
    private boolean editingQuantity = false;
    private String priceText;
    private String quantityText;

    public AutoBuyItemSettingsWindow(AutoBuyableItem item) {
        this.item = item;
        this.settings = item.getSettings();
        this.priceText = String.valueOf(settings.getBuyBelow());
        this.quantityText = String.valueOf(settings.getMinQuantity());
        this.width = 220;
        this.height = settings.isCanHaveQuantity() ? 135 : 105;
    }

    public void position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, TextRenderer tr) {
        // Modal background
        context.fill(x, y, x + width, y + height, new Color(20, 20, 24, 240).getRGB());
        // Border
        drawOutline(context, x, y, width, height, new Color(60, 60, 75, 255).getRGB());

        // Header bar
        context.fill(x, y, x + width, y + 26, new Color(30, 30, 36, 255).getRGB());
        ItemStack stack = item.createItemStack();
        context.drawItem(stack, x + 5, y + 5);
        context.drawText(tr, item.getDisplayName(), x + 26, y + 9, 0xFFFFFF, false);

        // Close icon (X)
        boolean hoverClose = mouseX >= x + width - 20 && mouseX <= x + width - 4 && mouseY >= y + 4 && mouseY <= y + 20;
        context.drawText(tr, "✕", x + width - 16, y + 9, hoverClose ? 0xFF5555 : 0xAAAAAA, false);

        // Field 1: Buy below
        int field1Y = y + 36;
        context.drawText(tr, "Покупать ниже ($):", x + 10, field1Y + 3, 0xCCCCCC, false);
        int input1X = x + width - 85;
        int input1W = 75;
        int input1H = 16;
        context.fill(input1X, field1Y, input1X + input1W, field1Y + input1H, editingPrice ? new Color(45, 45, 55).getRGB() : new Color(30, 30, 35).getRGB());
        drawOutline(context, input1X, field1Y, input1W, input1H, editingPrice ? 0xFF66AAFF : 0xFF555555);
        String pDisplay = priceText + (editingPrice ? "_" : "$");
        context.drawText(tr, pDisplay, input1X + 4, field1Y + 4, 0x55FF55, false);

        // Field 2: Min quantity (if applicable)
        if (settings.isCanHaveQuantity()) {
            int field2Y = y + 62;
            context.drawText(tr, "Количество от:", x + 10, field2Y + 3, 0xCCCCCC, false);
            int input2X = x + width - 85;
            int input2W = 75;
            int input2H = 16;
            context.fill(input2X, field2Y, input2X + input2W, field2Y + input2H, editingQuantity ? new Color(45, 45, 55).getRGB() : new Color(30, 30, 35).getRGB());
            drawOutline(context, input2X, field2Y, input2W, input2H, editingQuantity ? 0xFF66AAFF : 0xFF555555);
            String qDisplay = quantityText + (editingQuantity ? "_" : " шт.");
            context.drawText(tr, qDisplay, input2X + 4, field2Y + 4, 0xFFAAAA, false);
        }

        // Save button
        int btnY = y + height - 26;
        int btnX = x + 10;
        int btnW = width - 20;
        int btnH = 18;
        boolean hoverSave = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        context.fill(btnX, btnY, btnX + btnW, btnY + btnH, hoverSave ? new Color(40, 140, 60).getRGB() : new Color(30, 100, 45).getRGB());
        drawOutline(context, btnX, btnY, btnW, btnH, hoverSave ? 0xFF88FF88 : 0xFF55AA55);
        int saveTextW = tr.getWidth("Сохранить");
        context.drawText(tr, "Сохранить", btnX + (btnW - saveTextW) / 2, btnY + 5, 0xFFFFFF, false);
    }

    private void drawOutline(DrawContext context, int ox, int oy, int ow, int oh, int color) {
        context.fill(ox, oy, ox + ow, oy + 1, color);
        context.fill(ox, oy + oh - 1, ox + ow, oy + oh, color);
        context.fill(ox, oy, ox + 1, oy + oh, color);
        context.fill(ox + ow - 1, oy, ox + ow, oy + oh, color);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Close button
        if (mouseX >= x + width - 20 && mouseX <= x + width - 4 && mouseY >= y + 4 && mouseY <= y + 20) {
            saveAndClose();
            return true;
        }

        // Field 1
        int field1Y = y + 36;
        int input1X = x + width - 85;
        if (mouseX >= input1X && mouseX <= input1X + 75 && mouseY >= field1Y && mouseY <= field1Y + 16) {
            editingPrice = true;
            editingQuantity = false;
            return true;
        }

        // Field 2
        if (settings.isCanHaveQuantity()) {
            int field2Y = y + 62;
            int input2X = x + width - 85;
            if (mouseX >= input2X && mouseX <= input2X + 75 && mouseY >= field2Y && mouseY <= field2Y + 16) {
                editingQuantity = true;
                editingPrice = false;
                return true;
            }
        }

        // Save button
        int btnY = y + height - 26;
        int btnX = x + 10;
        int btnW = width - 20;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 18) {
            saveAndClose();
            return true;
        }

        // Click outside textboxes clears editing
        editingPrice = false;
        editingQuantity = false;
        return isHovered(mouseX, mouseY);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            saveAndClose();
            return true;
        }

        if (editingPrice) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !priceText.isEmpty()) {
                priceText = priceText.substring(0, priceText.length() - 1);
                return true;
            }
        } else if (editingQuantity) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !quantityText.isEmpty()) {
                quantityText = quantityText.substring(0, quantityText.length() - 1);
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (Character.isDigit(chr)) {
            if (editingPrice && priceText.length() < 9) {
                priceText += chr;
                return true;
            } else if (editingQuantity && quantityText.length() < 4) {
                quantityText += chr;
                return true;
            }
        }
        return false;
    }

    public void saveAndClose() {
        try {
            int p = priceText.isEmpty() ? 0 : Integer.parseInt(priceText);
            settings.setBuyBelow(p);
        } catch (NumberFormatException ignored) {}

        if (settings.isCanHaveQuantity()) {
            try {
                int q = quantityText.isEmpty() ? 1 : Integer.parseInt(quantityText);
                settings.setMinQuantity(Math.max(1, q));
            } catch (NumberFormatException ignored) {}
        }

        AutoBuySettingsManager.getInstance().saveSettings(item.getDisplayName(), settings);
        AutoBuyConfigFile.save();
    }
}
