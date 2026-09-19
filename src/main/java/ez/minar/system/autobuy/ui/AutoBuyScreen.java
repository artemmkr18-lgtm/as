package ez.minar.system.autobuy.ui;

import ez.minar.system.autobuy.config.AutoBuyConfigFile;
import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.manager.AutoBuyManager;
import ez.minar.system.autobuy.originalitems.ItemRegistry;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AutoBuyScreen extends Screen {
    private static final String[] CATEGORIES = {"Все", "Крушитель", "Талисманы", "Сферы", "Донаторские", "Зелья", "Разное"};
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private int selectedCategory = 0;
    private float scroll = 0;
    private float smoothedScroll = 0;

    private String searchQuery = "";
    private boolean searching = false;

    private boolean showHistory = false;
    private float historyScroll = 0;

    private AutoBuyItemSettingsWindow settingsWindow = null;
    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();

    public AutoBuyScreen() {
        super(Text.literal("AutoBuy Menu"));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark background tint
        context.fill(0, 0, width, height, new Color(12, 12, 14, 235).getRGB());

        // Header Panel (Y: 0 to 45)
        context.fill(0, 0, width, 45, new Color(18, 18, 22, 255).getRGB());
        context.fill(0, 44, width, 45, new Color(40, 40, 50, 255).getRGB());

        // Title
        context.drawText(textRenderer, "AutoBuy — Настройки скупки", 20, 16, 0xFFFFFF, false);

        // Global AutoBuy Toggle Button
        boolean abEnabled = autoBuyManager.isEnabled();
        int toggleBtnX = 220;
        int toggleBtnY = 12;
        int toggleBtnW = 85;
        int toggleBtnH = 20;
        boolean hoverToggle = mouseX >= toggleBtnX && mouseX <= toggleBtnX + toggleBtnW && mouseY >= toggleBtnY && mouseY <= toggleBtnY + toggleBtnH;
        context.fill(toggleBtnX, toggleBtnY, toggleBtnX + toggleBtnW, toggleBtnY + toggleBtnH,
                abEnabled ? (hoverToggle ? 0xFF2E9A4B : 0xFF238636) : (hoverToggle ? 0xFFDA3633 : 0xFFB62324));
        drawOutline(context, toggleBtnX, toggleBtnY, toggleBtnW, toggleBtnH, abEnabled ? 0xFF56D364 : 0xFFF85149);
        String toggleText = "Скупка: " + (abEnabled ? "ВКЛ" : "ВЫКЛ");
        int tw = textRenderer.getWidth(toggleText);
        context.drawText(textRenderer, toggleText, toggleBtnX + (toggleBtnW - tw) / 2, toggleBtnY + 6, 0xFFFFFF, false);

        // Search Field
        int searchX = toggleBtnX + toggleBtnW + 15;
        int searchY = 12;
        int searchW = 140;
        int searchH = 20;
        context.fill(searchX, searchY, searchX + searchW, searchY + searchH, searching ? 0xFF2A2A35 : 0xFF202026);
        drawOutline(context, searchX, searchY, searchW, searchH, searching ? 0xFF66AAFF : 0xFF454555);
        String sDisplay = searchQuery.isEmpty() && !searching ? "Поиск предметов..." : searchQuery + (searching ? "_" : "");
        int sColor = searchQuery.isEmpty() && !searching ? 0x777777 : 0xEEEEEE;
        context.drawText(textRenderer, sDisplay, searchX + 6, searchY + 6, sColor, false);

        // Purchase History Toggle Button
        int histBtnX = width - 130;
        int histBtnY = 12;
        int histBtnW = 110;
        int histBtnH = 20;
        boolean hoverHist = mouseX >= histBtnX && mouseX <= histBtnX + histBtnW && mouseY >= histBtnY && mouseY <= histBtnY + histBtnH;
        context.fill(histBtnX, histBtnY, histBtnX + histBtnW, histBtnY + histBtnH, showHistory ? 0xFF353B48 : (hoverHist ? 0xFF2D313A : 0xFF22252C));
        drawOutline(context, histBtnX, histBtnY, histBtnW, histBtnH, showHistory ? 0xFF718093 : 0xFF404552);
        String histTitle = "История покупок";
        int htw = textRenderer.getWidth(histTitle);
        context.drawText(textRenderer, histTitle, histBtnX + (histBtnW - htw) / 2, histBtnY + 6, 0xFFFFFF, false);

        // Categories bar (Y: 48 to 72)
        int catX = 20;
        int catY = 50;
        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            int cw = textRenderer.getWidth(cat) + 16;
            boolean isSel = (i == selectedCategory);
            boolean hoverCat = mouseX >= catX && mouseX <= catX + cw && mouseY >= catY && mouseY <= catY + 18;
            context.fill(catX, catY, catX + cw, catY + 18, isSel ? 0xFF3B4A68 : (hoverCat ? 0xFF2A2E3A : 0xFF1E2028));
            drawOutline(context, catX, catY, cw, 18, isSel ? 0xFF70A1FF : 0xFF3A3F4E);
            context.drawText(textRenderer, cat, catX + 8, catY + 5, isSel ? 0xFFFFFF : 0xAAAAAA, false);
            catX += cw + 6;
        }

        // Content Area (Y: 75 to height - 10)
        int contentY = 76;
        int contentH = height - contentY - 10;
        int contentW = showHistory ? width - 260 : width - 40;

        List<AutoBuyableItem> items = getFilteredItems();
        int totalRows = (items.size() + 1) / 2;
        int totalContentH = totalRows * 50 + 10;
        float maxScroll = Math.max(0, totalContentH - contentH);
        scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        smoothedScroll = MathHelper.lerp(0.2f, smoothedScroll, scroll);

        context.enableScissor(20, contentY, 20 + contentW, contentY + contentH);

        int cardW = 185;
        int cardH = 44;
        int gapX = 10;
        int startX = 20;
        float itemY = contentY + 6 + smoothedScroll;

        for (int i = 0; i < items.size(); i++) {
            AutoBuyableItem item = items.get(i);
            int col = i % 2;
            if (col == 0 && i > 0) itemY += 48;

            int cx = startX + col * (cardW + gapX);
            int cy = (int) itemY;

            if (cy + cardH >= contentY && cy <= contentY + contentH) {
                renderItemCard(context, item, cx, cy, mouseX, mouseY);
            }
        }

        context.disableScissor();

        // History Drawer (if open)
        if (showHistory) {
            renderHistoryDrawer(context, mouseX, mouseY, contentY, contentH);
        }

        // Settings Modal Window (if open)
        if (settingsWindow != null) {
            // dim background
            context.fill(0, 0, width, height, 0x66000000);
            settingsWindow.render(context, mouseX, mouseY, delta, textRenderer);
        }
    }

    private void renderItemCard(DrawContext context, AutoBuyableItem item, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 185 && mouseY >= y && mouseY <= y + 44;
        context.fill(x, y, x + 185, y + 44, hovered ? 0xFF242630 : 0xFF1C1D24);
        drawOutline(context, x, y, 185, 44, item.isEnabled() ? 0xFF355E3B : 0xFF30323C);

        // Item icon
        ItemStack stack = item.createItemStack();
        context.drawItem(stack, x + 6, y + 14);

        // Name
        String name = item.getDisplayName();
        if (textRenderer.getWidth(name) > 115) {
            name = textRenderer.trimToWidth(name, 110) + "..";
        }
        context.drawText(textRenderer, name, x + 28, y + 6, 0xFFFFFF, false);

        // Buy price
        context.drawText(textRenderer, "Цена: $" + item.getSettings().getBuyBelow(), x + 28, y + 18, 0x55FF55, false);

        // Quantity
        if (item.getSettings().isCanHaveQuantity()) {
            context.drawText(textRenderer, "От: " + item.getSettings().getMinQuantity() + " шт.", x + 28, y + 28, 0xAAAAAA, false);
        }

        // Toggle Switch on Right
        int swX = x + 155;
        int swY = y + 14;
        int swW = 22;
        int swH = 14;
        boolean swHover = mouseX >= swX && mouseX <= swX + swW && mouseY >= swY && mouseY <= swY + swH;
        context.fill(swX, swY, swX + swW, swY + swH, item.isEnabled() ? (swHover ? 0xFF38B249 : 0xFF2CA03B) : (swHover ? 0xFF4A4E58 : 0xFF3A3D46));
        drawOutline(context, swX, swY, swW, swH, item.isEnabled() ? 0xFF5CE66C : 0xFF555964);
        int dotX = item.isEnabled() ? swX + swW - 7 : swX + 2;
        context.fill(dotX, swY + 2, dotX + 5, swY + swH - 2, 0xFFFFFF);
    }

    private void renderHistoryDrawer(DrawContext context, int mouseX, int mouseY, int contentY, int contentH) {
        int hX = width - 230;
        int hW = 210;
        context.fill(hX, contentY, hX + hW, contentY + contentH, 0xFF181A20);
        drawOutline(context, hX, contentY, hW, contentH, 0xFF3A3F4E);

        context.drawText(textRenderer, "История покупок", hX + 10, contentY + 8, 0xFFFFFF, false);

        // Clear history button
        int clrX = hX + hW - 55;
        int clrY = contentY + 6;
        boolean hoverClr = mouseX >= clrX && mouseX <= clrX + 48 && mouseY >= clrY && mouseY <= clrY + 14;
        context.fill(clrX, clrY, clrX + 48, clrY + 14, hoverClr ? 0xFF8A2B2B : 0xFF5A1E1E);
        drawOutline(context, clrX, clrY, 48, 14, 0xFFB23B3B);
        context.drawText(textRenderer, "Очистить", clrX + 4, clrY + 3, 0xEEEEEE, false);

        List<PurchaseHistoryWindow.PurchaseRecord> records = PurchaseHistoryWindow.getPurchases();
        if (records.isEmpty()) {
            context.drawText(textRenderer, "Покупок пока нет", hX + 50, contentY + 50, 0x777777, false);
            return;
        }

        context.enableScissor(hX, contentY + 25, hX + hW, contentY + contentH);
        float rY = contentY + 28 + historyScroll;
        for (PurchaseHistoryWindow.PurchaseRecord rec : records) {
            if (rY + 36 >= contentY + 25 && rY <= contentY + contentH) {
                context.fill(hX + 6, (int) rY, hX + hW - 6, (int) rY + 34, 0xFF20222A);
                drawOutline(context, hX + 6, (int) rY, hW - 12, 34, 0xFF2E323D);
                if (rec.getItemStack() != null) {
                    context.drawItem(rec.getItemStack(), hX + 8, (int) rY + 9);
                }
                String name = rec.getItemName();
                if (textRenderer.getWidth(name) > 105) {
                    name = textRenderer.trimToWidth(name, 100) + "..";
                }
                context.drawText(textRenderer, name, hX + 28, (int) rY + 4, 0xFFFFFF, false);
                String priceStr = "$" + rec.getPrice() + (rec.getQuantity() > 1 ? " x" + rec.getQuantity() : "");
                context.drawText(textRenderer, priceStr, hX + 28, (int) rY + 14, 0x55FF55, false);
                context.drawText(textRenderer, TIME_FORMAT.format(new Date(rec.getTimestamp())), hX + 28, (int) rY + 23, 0x888888, false);
            }
            rY += 38;
        }
        context.disableScissor();
    }

    private void drawOutline(DrawContext context, int ox, int oy, int ow, int oh, int color) {
        context.fill(ox, oy, ox + ow, oy + 1, color);
        context.fill(ox, oy + oh - 1, ox + ow, oy + oh, color);
        context.fill(ox, oy + 1, ox + 1, oy + oh, color);
        context.fill(ox + ow - 1, oy, ox + 1, oy + oh, color);
    }

    private List<AutoBuyableItem> getFilteredItems() {
        List<AutoBuyableItem> base = switch (selectedCategory) {
            case 1 -> ItemRegistry.getKrush();
            case 2 -> ItemRegistry.getTalismans();
            case 3 -> ItemRegistry.getSpheres();
            case 4 -> ItemRegistry.getDonator();
            case 5 -> ItemRegistry.getPotions();
            case 6 -> ItemRegistry.getMisc();
            default -> ItemRegistry.getAllItems();
        };

        if (searchQuery.isBlank()) {
            return base;
        }

        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<AutoBuyableItem> filtered = new ArrayList<>();
        for (AutoBuyableItem item : base) {
            if (item.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Modal window intercepts clicks
        if (settingsWindow != null) {
            if (!settingsWindow.mouseClicked(mouseX, mouseY, button)) {
                // clicked outside -> save & close
                settingsWindow.saveAndClose();
                settingsWindow = null;
            }
            return true;
        }

        // Global toggle button
        if (button == 0 && mouseX >= 220 && mouseX <= 305 && mouseY >= 12 && mouseY <= 32) {
            autoBuyManager.setEnabled(!autoBuyManager.isEnabled());
            return true;
        }

        // Search field
        if (button == 0 && mouseX >= 320 && mouseX <= 460 && mouseY >= 12 && mouseY <= 32) {
            searching = true;
            return true;
        } else {
            searching = false;
        }

        // History button
        if (button == 0 && mouseX >= width - 130 && mouseX <= width - 20 && mouseY >= 12 && mouseY <= 32) {
            showHistory = !showHistory;
            return true;
        }

        // History clear button
        if (showHistory && button == 0) {
            int hX = width - 230;
            int hW = 210;
            int clrX = hX + hW - 55;
            int clrY = 76 + 6;
            if (mouseX >= clrX && mouseX <= clrX + 48 && mouseY >= clrY && mouseY <= clrY + 14) {
                PurchaseHistoryWindow.clear();
                return true;
            }
        }

        // Category tabs
        if (button == 0 && mouseY >= 50 && mouseY <= 68) {
            int catX = 20;
            for (int i = 0; i < CATEGORIES.length; i++) {
                int cw = textRenderer.getWidth(CATEGORIES[i]) + 16;
                if (mouseX >= catX && mouseX <= catX + cw) {
                    selectedCategory = i;
                    scroll = 0;
                    smoothedScroll = 0;
                    return true;
                }
                catX += cw + 6;
            }
        }

        // Items list clicks
        int contentY = 76;
        int contentH = height - contentY - 10;
        int contentW = showHistory ? width - 260 : width - 40;

        if (mouseX >= 20 && mouseX <= 20 + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            List<AutoBuyableItem> items = getFilteredItems();
            int cardW = 185;
            int cardH = 44;
            int gapX = 10;
            int startX = 20;
            float itemY = contentY + 6 + smoothedScroll;

            for (int i = 0; i < items.size(); i++) {
                AutoBuyableItem item = items.get(i);
                int col = i % 2;
                if (col == 0 && i > 0) itemY += 48;

                int cx = startX + col * (cardW + gapX);
                int cy = (int) itemY;

                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    // Left click on toggle switch -> toggle
                    int swX = cx + 155;
                    int swY = cy + 14;
                    if (button == 0 && mouseX >= swX && mouseX <= swX + 22 && mouseY >= swY && mouseY <= swY + 14) {
                        autoBuyManager.toggleItem(item);
                        AutoBuyConfigFile.save();
                        return true;
                    }

                    // Right click or left click anywhere else on the card -> open settings
                    if (button == 1 || button == 0) {
                        settingsWindow = new AutoBuyItemSettingsWindow(item);
                        settingsWindow.position(width / 2 - 110, height / 2 - 70);
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsWindow != null) return true;

        if (showHistory && mouseX >= width - 230) {
            historyScroll += (float) (verticalAmount * 25);
            return true;
        }

        scroll += (float) (verticalAmount * 30);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (settingsWindow != null) {
            return settingsWindow.keyPressed(keyCode, 0, 0);
        }

        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        char chr = input.asString().isEmpty() ? 0 : input.asString().charAt(0);
        if (settingsWindow != null) {
            return settingsWindow.charTyped(chr, 0);
        }

        if (searching && searchQuery.length() < 30) {
            searchQuery += chr;
            return true;
        }

        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (settingsWindow != null) {
            settingsWindow.saveAndClose();
            settingsWindow = null;
            return;
        }
        AutoBuyConfigFile.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
