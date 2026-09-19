package ez.minar.system.autobuy.ui;

import ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine;
import ez.minar.system.menu.ThemeManager;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.scissor.Scissor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.List;

public class AutoBuyMenuScreen extends Screen {
    private static final float PANEL_WIDTH = 540f;
    private static final float PANEL_HEIGHT = 350f;
    private static final float HEADER_HEIGHT = 48f;
    private static final float PADDING = 14f;

    private static final String[] TABS = {"Флиппер", "Предметы", "Настройки", "История"};
    private static final String[] POPULAR_ITEMS = {
            "Изумрудный меч", "Крушитель", "Сфера Химеры", "Сфера Титана",
            "Талисман карателя", "Талисман гармонии", "Тотем бессмертия", "Шалкер"
    };

    private final Screen parent;
    private final AutoBuyFlipperEngine engine = AutoBuyFlipperEngine.getInstance();
    private int currentTab = 0;
    private long openedAt;

    // Search / custom item input
    private String customItemInput = "";
    private boolean inputFocused = false;

    // Scroll
    private float historyScroll = 0f;

    public AutoBuyMenuScreen(Screen parent) {
        super(Text.literal("AutoBuy"));
        this.parent = parent;
    }

    public AutoBuyMenuScreen() {
        this(null);
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        customItemInput = engine.getTargetItem();
        super.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        long now = System.currentTimeMillis();
        float progress = Math.min(1f, (now - openedAt) / 190f);
        float opacity = Easings.OutCubic(progress);

        float fixedWidth = RenderUtil.getFixedScaledWidth();
        float fixedHeight = RenderUtil.getFixedScaledHeight();
        float centerX = fixedWidth / 2f;
        float centerY = fixedHeight / 2f;
        float panelX = centerX - PANEL_WIDTH / 2f;
        float panelY = centerY - PANEL_HEIGHT / 2f;

        float fixedMouseX = RenderUtil.convertX(mouseX);
        float fixedMouseY = RenderUtil.convertY(mouseY);

        // Panel background
        RenderUtil.rect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 10f,
                withOpacity(new Color(18, 18, 22, 245), opacity));

        // Header
        RenderUtil.rect(panelX + 1f, panelY + 1f, PANEL_WIDTH - 2f, HEADER_HEIGHT - 1f,
                0f, 0f, 9f, 9f, withOpacity(new Color(255, 255, 255, 8), opacity));

        // Header titles
        RenderUtil.text(context, panelX + PADDING, panelY + 11f, "AutoBuy", 13f,
                withOpacity(new Color(240, 240, 244), opacity));
        RenderUtil.text(context, panelX + PADDING, panelY + 27f, ez.minar.system.managers.LocalizationManager.get("Автоматический перекупщик аукциона"),
                7.4f, withOpacity(new Color(132, 132, 140), opacity));

        // Header tabs
        float tabX = panelX + PANEL_WIDTH - 240f;
        float tabY = panelY + 18f;
        for (int i = 0; i < TABS.length; i++) {
            boolean active = currentTab == i;
            String tabName = ez.minar.system.managers.LocalizationManager.get(TABS[i]);
            float tw = Msdf.width(tabName, 8f);
            RenderUtil.text(context, tabX, tabY, tabName, 8f, withOpacity(active ? Color.WHITE : new Color(132, 132, 140), opacity));
            if (active) {
                RenderUtil.rect(tabX, tabY + 12f, tw, 1.5f, 0f, withOpacity(ThemeManager.getThemeColor(), opacity));
            }
            tabX += tw + 14f;
        }

        // Tab Content
        float contentX = panelX + 18f;
        float contentY = panelY + HEADER_HEIGHT + 10f;
        float contentW = PANEL_WIDTH - 36f;
        float contentH = PANEL_HEIGHT - HEADER_HEIGHT - 20f;

        switch (currentTab) {
            case 0 -> renderFlipperTab(context, contentX, contentY, contentW, contentH, fixedMouseX, fixedMouseY, opacity);
            case 1 -> renderItemsTab(context, contentX, contentY, contentW, contentH, fixedMouseX, fixedMouseY, opacity);
            case 2 -> renderSettingsTab(context, contentX, contentY, contentW, contentH, fixedMouseX, fixedMouseY, opacity);
            case 3 -> renderHistoryTab(context, contentX, contentY, contentW, contentH, fixedMouseX, fixedMouseY, opacity);
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderFlipperTab(DrawContext context, float x, float y, float w, float h, float mx, float my, float opacity) {
        // Target item showcase card
        float topCardH = 68f;
        RenderUtil.rect(x, y, w, topCardH, 8f, withOpacity(new Color(25, 25, 32, 230), opacity));
        RenderUtil.outline(x, y, w, topCardH, 8f, 1f, withOpacity(new Color(50, 50, 65, 180), opacity));

        // Icon placeholder / sword
        ItemStack icon = getDisplayStack(engine.getTargetItem());
        context.drawItem(icon, (int) (x + 12f), (int) (y + 12f));

        RenderUtil.text(context, x + 38f, y + 12f, engine.getTargetItem(), 11f, withOpacity(Color.WHITE, opacity));

        // Status badge
        boolean active = engine.isEnabled();
        Color statusBg = active ? new Color(30, 110, 50, 220) : new Color(55, 55, 65, 200);
        Color statusText = active ? new Color(130, 255, 150) : new Color(180, 180, 190);
        String statusStr = active ? "● " + engine.getState().getLabel() : "○ " + ez.minar.system.managers.LocalizationManager.get("Остановлен");
        float badgeW = Msdf.width(statusStr, 7.5f) + 12f;
        RenderUtil.rect(x + 38f, y + 28f, badgeW, 14f, 4f, withOpacity(statusBg, opacity));
        RenderUtil.text(context, x + 44f, y + 31f, statusStr, 7.5f, withOpacity(statusText, opacity));

        // Next market scan timer info
        if (active && engine.getLastMarketScanTime() > 0L) {
            long elapsed = System.currentTimeMillis() - engine.getLastMarketScanTime();
            long remain = Math.max(0L, engine.getMarketScanInterval() - elapsed);
            long sec = (remain / 1000L) % 60L;
            long min = (remain / 1000L) / 60L;
            String timeStr = ez.minar.system.managers.LocalizationManager.get("Перепроверка рынка через: %d:%02d", min, sec);
            RenderUtil.text(context, x + 44f + badgeW + 8f, y + 31f, timeStr, 7.5f, withOpacity(new Color(150, 150, 165), opacity));
        }

        // 3 Price Cards
        float cardY = y + topCardH + 8f;
        float cardW = (w - 16f) / 3f;
        float cardH = 58f;

        // Card 1: Market Min
        renderPriceCard(context, x, cardY, cardW, cardH, ez.minar.system.managers.LocalizationManager.get("Мин. на рынке"),
                engine.getBaseMarketPrice() > 0L ? formatMoney(engine.getBaseMarketPrice()) : ez.minar.system.managers.LocalizationManager.get("Ожидание..."),
                new Color(70, 130, 240), opacity);

        // Card 2: Buy Target
        renderPriceCard(context, x + cardW + 8f, cardY, cardW, cardH, ez.minar.system.managers.LocalizationManager.get("Скупка") + " (-" + engine.getBuyDiscountPercent() + "%)",
                engine.getTargetBuyPrice() > 0L ? formatMoney(engine.getTargetBuyPrice()) : "—",
                new Color(40, 210, 100), opacity);

        // Card 3: Sell Target
        renderPriceCard(context, x + (cardW + 8f) * 2f, cardY, cardW, cardH, ez.minar.system.managers.LocalizationManager.get("Продажа") + " (+" + engine.getSellMarkupPercent() + "%)",
                engine.getTargetSellPrice() > 0L ? formatMoney(engine.getTargetSellPrice()) : "—",
                new Color(255, 170, 40), opacity);

        // Profit summary pill
        float pillY = cardY + cardH + 8f;
        long profitPerItem = engine.getTargetSellPrice() > 0L ? engine.getTargetSellPrice() - engine.getTargetBuyPrice() : 0L;
        String profitStr = profitPerItem > 0L ? ez.minar.system.managers.LocalizationManager.get("Ожидаемый профит: +%s с каждого предмета", formatMoney(profitPerItem)) : ez.minar.system.managers.LocalizationManager.get("Ожидание анализа цен...");
        RenderUtil.rect(x, pillY, w, 24f, 6f, withOpacity(new Color(25, 35, 28, 200), opacity));
        RenderUtil.outline(x, pillY, w, 24f, 6f, 1f, withOpacity(new Color(40, 120, 60, 180), opacity));
        RenderUtil.text(context, x + 12f, pillY + 7f, profitStr, 8f, withOpacity(new Color(120, 255, 140), opacity));

        // Stats Counters Bar
        float statsY = pillY + 30f;
        String statsStr = ez.minar.system.managers.LocalizationManager.get("Куплено: %d шт.   |   Выставлено: %d шт.   |   Заработано: +%s",
                engine.getTotalBought(), engine.getTotalSold(), formatMoney(engine.getTotalProfit()));
        RenderUtil.text(context, x + 6f, statsY, statsStr, 8f, withOpacity(new Color(180, 180, 195), opacity));

        // Big Toggle Button
        float btnW = 200f;
        float btnH = 32f;
        float btnX = x + (w - btnW) / 2f;
        float btnY = y + h - btnH - 6f;
        boolean hoverBtn = inside(mx, my, btnX, btnY, btnW, btnH);

        Color btnColor = active
                ? (hoverBtn ? new Color(200, 50, 50) : new Color(170, 35, 35))
                : (hoverBtn ? new Color(35, 180, 75) : ThemeManager.getThemeColor());

        RenderUtil.rect(btnX, btnY, btnW, btnH, 8f, withOpacity(btnColor, opacity));
        RenderUtil.outline(btnX, btnY, btnW, btnH, 8f, 1.5f, withOpacity(Color.WHITE, opacity * 0.3f));

        String btnText = active ? "■  " + ez.minar.system.managers.LocalizationManager.get("Остановить AutoBuy") : "▶  " + ez.minar.system.managers.LocalizationManager.get("Запустить AutoBuy");
        float btnTw = Msdf.width(btnText, 9.5f);
        RenderUtil.text(context, btnX + (btnW - btnTw) / 2f, btnY + 10f, btnText, 9.5f, withOpacity(Color.WHITE, opacity));
    }

    private void renderPriceCard(DrawContext context, float x, float y, float w, float h, String title, String val, Color accent, float opacity) {
        RenderUtil.rect(x, y, w, h, 6f, withOpacity(new Color(24, 24, 30, 220), opacity));
        RenderUtil.outline(x, y, w, h, 6f, 1f, withOpacity(new Color(45, 45, 55, 180), opacity));
        RenderUtil.rect(x + 2f, y + 2f, 3f, h - 4f, 1.5f, withOpacity(accent, opacity));
        RenderUtil.text(context, x + 10f, y + 8f, title, 7.5f, withOpacity(new Color(150, 150, 160), opacity));
        RenderUtil.text(context, x + 10f, y + 24f, val, 11f, withOpacity(accent, opacity));
    }

    private void renderItemsTab(DrawContext context, float x, float y, float w, float h, float mx, float my, float opacity) {
        RenderUtil.text(context, x, y, ez.minar.system.managers.LocalizationManager.get("Выбор предмета для перепродажи:"), 9f, withOpacity(Color.WHITE, opacity));

        // Custom item input field
        float inputY = y + 16f;
        float inputW = w - 90f;
        float inputH = 26f;
        RenderUtil.rect(x, inputY, inputW, inputH, 6f, withOpacity(new Color(28, 28, 36, 230), opacity));
        RenderUtil.outline(x, inputY, inputW, inputH, 6f, 1f,
                withOpacity(inputFocused ? ThemeManager.getThemeColor() : new Color(55, 55, 70), opacity));

        String displayInput = customItemInput.isEmpty() ? (inputFocused ? "_" : ez.minar.system.managers.LocalizationManager.get("Введите предмет (например, Изумрудный меч)...")) : customItemInput + (inputFocused ? "_" : "");
        RenderUtil.text(context, x + 8f, inputY + 8f, displayInput, 8.5f,
                withOpacity(customItemInput.isEmpty() && !inputFocused ? new Color(110, 110, 125) : Color.WHITE, opacity));

        // Apply button
        float applyX = x + inputW + 8f;
        float applyW = 82f;
        boolean hoverApply = inside(mx, my, applyX, inputY, applyW, inputH);
        RenderUtil.rect(applyX, inputY, applyW, inputH, 6f,
                withOpacity(hoverApply ? ThemeManager.getThemeColor().brighter() : ThemeManager.getThemeColor(), opacity));
        RenderUtil.text(context, applyX + 16f, inputY + 8f, ez.minar.system.managers.LocalizationManager.get("Выбрать"), 8.5f, withOpacity(Color.WHITE, opacity));

        // Popular Items Grid
        float gridY = inputY + 40f;
        RenderUtil.text(context, x, gridY - 8f, ez.minar.system.managers.LocalizationManager.get("Популярные предметы:"), 8f, withOpacity(new Color(160, 160, 175), opacity));

        int cols = 4;
        float itemW = (w - 18f) / cols;
        float itemH = 50f;

        for (int i = 0; i < POPULAR_ITEMS.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float ix = x + col * (itemW + 6f);
            float iy = gridY + row * (itemH + 6f);

            boolean selected = POPULAR_ITEMS[i].equalsIgnoreCase(engine.getTargetItem());
            boolean hovered = inside(mx, my, ix, iy, itemW, itemH);

            Color bg = selected ? new Color(30, 45, 60, 240) : (hovered ? new Color(32, 32, 40, 230) : new Color(24, 24, 30, 200));
            RenderUtil.rect(ix, iy, itemW, itemH, 6f, withOpacity(bg, opacity));
            RenderUtil.outline(ix, iy, itemW, itemH, 6f, 1f,
                    withOpacity(selected ? ThemeManager.getThemeColor() : new Color(50, 50, 60), opacity));

            ItemStack stack = getDisplayStack(POPULAR_ITEMS[i]);
            context.drawItem(stack, (int) (ix + 6f), (int) (iy + 16f));

            String popItemName = ez.minar.system.managers.LocalizationManager.get(POPULAR_ITEMS[i]);
            RenderUtil.text(context, ix + 30f, iy + 14f, popItemName, 8f,
                    withOpacity(selected ? Color.WHITE : new Color(190, 190, 205), opacity));
            if (selected) {
                RenderUtil.text(context, ix + 30f, iy + 26f, ez.minar.system.managers.LocalizationManager.get("Активен"), 7f,
                        withOpacity(new Color(120, 255, 140), opacity));
            }
        }
    }

    private void renderSettingsTab(DrawContext context, float x, float y, float w, float h, float mx, float my, float opacity) {
        RenderUtil.text(context, x, y, ez.minar.system.managers.LocalizationManager.get("Параметры работы автобая:"), 9.5f, withOpacity(Color.WHITE, opacity));

        float rowY = y + 18f;
        float rowH = 38f;

        // Setting 1: Buy discount
        renderSettingRow(context, x, rowY, w, rowH, ez.minar.system.managers.LocalizationManager.get("Скидка покупки от мин. цены рынка"),
                "-" + engine.getBuyDiscountPercent() + "%", "[- 1%]", "[+ 1%]", mx, my, opacity);
        rowY += rowH + 6f;

        // Setting 2: Sell markup
        renderSettingRow(context, x, rowY, w, rowH, ez.minar.system.managers.LocalizationManager.get("Наценка при перепродаже лота"),
                "+" + engine.getSellMarkupPercent() + "%", "[- 1%]", "[+ 1%]", mx, my, opacity);
        rowY += rowH + 6f;

        // Setting 3: Scan interval
        long min = engine.getMarketScanInterval() / (60 * 1000L);
        renderSettingRow(context, x, rowY, w, rowH, ez.minar.system.managers.LocalizationManager.get("Интервал перепроверки рынка"),
                min + " мин", "[- 1м]", "[+ 1м]", mx, my, opacity);
        rowY += rowH + 6f;

        // Setting 4: Refresh slot 46 delay
        renderSettingRow(context, x, rowY, w, rowH, ez.minar.system.managers.LocalizationManager.get("Задержка обновления страницы (слот 46)"),
                "300 мс", "", "", mx, my, opacity);
    }

    private void renderSettingRow(DrawContext context, float x, float y, float w, float h, String title, String val, String b1, String b2, float mx, float my, float opacity) {
        RenderUtil.rect(x, y, w, h, 6f, withOpacity(new Color(24, 24, 30, 220), opacity));
        RenderUtil.outline(x, y, w, h, 6f, 1f, withOpacity(new Color(45, 45, 55, 180), opacity));

        RenderUtil.text(context, x + 12f, y + 13f, title, 8.5f, withOpacity(Color.WHITE, opacity));

        float rx = x + w - 160f;
        RenderUtil.text(context, rx, y + 13f, val, 9.5f, withOpacity(new Color(130, 255, 150), opacity));

        if (!b1.isEmpty()) {
            float b1X = x + w - 75f;
            float b2X = x + w - 38f;
            RenderUtil.rect(b1X, y + 8f, 32f, 22f, 4f, withOpacity(new Color(40, 40, 50), opacity));
            RenderUtil.text(context, b1X + 5f, y + 13f, b1, 7.5f, withOpacity(Color.WHITE, opacity));

            RenderUtil.rect(b2X, y + 8f, 32f, 22f, 4f, withOpacity(new Color(40, 40, 50), opacity));
            RenderUtil.text(context, b2X + 5f, y + 13f, b2, 7.5f, withOpacity(Color.WHITE, opacity));
        }
    }

    private void renderHistoryTab(DrawContext context, float x, float y, float w, float h, float mx, float my, float opacity) {
        List<AutoBuyFlipperEngine.FlipperHistoryEntry> entries = engine.getHistory();

        // Clear button
        float clrW = 90f;
        float clrH = 20f;
        float clrX = x + w - clrW;
        boolean hoverClr = inside(mx, my, clrX, y, clrW, clrH);
        RenderUtil.rect(clrX, y, clrW, clrH, 4f, withOpacity(hoverClr ? new Color(180, 40, 40) : new Color(120, 30, 30), opacity));
        RenderUtil.text(context, clrX + 8f, y + 5f, ez.minar.system.managers.LocalizationManager.get("Очистить лог"), 7.5f, withOpacity(Color.WHITE, opacity));

        RenderUtil.text(context, x, y + 5f, ez.minar.system.managers.LocalizationManager.get("История завершенных сделок:"), 9f, withOpacity(Color.WHITE, opacity));

        float listY = y + 26f;
        float listH = h - 30f;

        if (entries.isEmpty()) {
            RenderUtil.text(context, x + 10f, listY + 30f, ez.minar.system.managers.LocalizationManager.get("История покупок пуста. Запустите автобай для начала скупки!"), 8.5f,
                    withOpacity(new Color(140, 140, 155), opacity));
            return;
        }

        Scissor.push(x, listY, w, listH);
        float itemY = listY - historyScroll;
        for (AutoBuyFlipperEngine.FlipperHistoryEntry e : entries) {
            if (itemY + 28f >= listY && itemY <= listY + listH) {
                RenderUtil.rect(x, itemY, w, 28f, 4f, withOpacity(new Color(25, 25, 32, 220), opacity));
                RenderUtil.outline(x, itemY, w, 28f, 4f, 1f, withOpacity(new Color(45, 45, 55, 180), opacity));

                RenderUtil.text(context, x + 8f, itemY + 8f, "[" + e.time + "]", 7.5f, withOpacity(new Color(130, 130, 140), opacity));
                RenderUtil.text(context, x + 68f, itemY + 8f, ez.minar.system.managers.LocalizationManager.get(e.item), 8f, withOpacity(Color.WHITE, opacity));
                RenderUtil.text(context, x + 230f, itemY + 8f, ez.minar.system.managers.LocalizationManager.get("Куплен: ") + formatMoney(e.buyPrice), 8f, withOpacity(new Color(255, 200, 80), opacity));
                RenderUtil.text(context, x + 340f, itemY + 8f, ez.minar.system.managers.LocalizationManager.get("Продажа: ") + formatMoney(e.sellPrice), 8f, withOpacity(new Color(80, 200, 255), opacity));
                RenderUtil.text(context, x + 440f, itemY + 8f, "+" + formatMoney(e.profit), 8.5f, withOpacity(new Color(100, 255, 120), opacity));
            }
            itemY += 32f;
        }
        Scissor.pop();
    }

    private ItemStack getDisplayStack(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("меч")) return new ItemStack(Items.DIAMOND_SWORD);
        if (lower.contains("крушитель")) return new ItemStack(Items.NETHERITE_SWORD);
        if (lower.contains("сфера")) return new ItemStack(Items.PLAYER_HEAD);
        if (lower.contains("талисман")) return new ItemStack(Items.TOTEM_OF_UNDYING);
        if (lower.contains("тотем")) return new ItemStack(Items.TOTEM_OF_UNDYING);
        if (lower.contains("шалкер")) return new ItemStack(Items.SHULKER_BOX);
        if (lower.contains("яблоко")) return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        return new ItemStack(Items.NETHERITE_INGOT);
    }

    private String formatMoney(long amount) {
        if (amount >= 1_000_000) {
            return String.format("%.2f млн $", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%d тыс $", amount / 1000);
        }
        return amount + " $";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float fixedWidth = RenderUtil.getFixedScaledWidth();
        float fixedHeight = RenderUtil.getFixedScaledHeight();
        float panelX = fixedWidth / 2f - PANEL_WIDTH / 2f;
        float panelY = fixedHeight / 2f - PANEL_HEIGHT / 2f;

        float mx = RenderUtil.convertX((float) click.x());
        float my = RenderUtil.convertY((float) click.y());
        int button = click.button();

        // Header tabs clicks
        float tabX = panelX + PANEL_WIDTH - 240f;
        float tabY = panelY + 18f;
        for (int i = 0; i < TABS.length; i++) {
            float tw = Msdf.width(TABS[i], 8f);
            if (inside(mx, my, tabX, tabY, tw, 18f) && button == 0) {
                currentTab = i;
                inputFocused = false;
                return true;
            }
            tabX += tw + 14f;
        }

        float contentX = panelX + 18f;
        float contentY = panelY + HEADER_HEIGHT + 10f;
        float contentW = PANEL_WIDTH - 36f;
        float contentH = PANEL_HEIGHT - HEADER_HEIGHT - 20f;

        // Tab 0 clicks: Start/Stop button
        if (currentTab == 0 && button == 0) {
            float btnW = 200f;
            float btnH = 32f;
            float btnX = contentX + (contentW - btnW) / 2f;
            float btnY = contentY + contentH - btnH - 6f;
            if (inside(mx, my, btnX, btnY, btnW, btnH)) {
                engine.setEnabled(!engine.isEnabled());
                return true;
            }
        }

        // Tab 1 clicks: Items tab
        if (currentTab == 1 && button == 0) {
            float inputY = contentY + 16f;
            float inputW = contentW - 90f;
            float inputH = 26f;
            if (inside(mx, my, contentX, inputY, inputW, inputH)) {
                inputFocused = true;
                return true;
            } else {
                inputFocused = false;
            }

            // Apply button
            float applyX = contentX + inputW + 8f;
            if (inside(mx, my, applyX, inputY, 82f, inputH)) {
                if (!customItemInput.isBlank()) {
                    engine.setTargetItem(customItemInput.trim());
                }
                return true;
            }

            // Popular items grid
            float gridY = inputY + 40f;
            int cols = 4;
            float itemW = (contentW - 18f) / cols;
            float itemH = 50f;
            for (int i = 0; i < POPULAR_ITEMS.length; i++) {
                int col = i % cols;
                int row = i / cols;
                float ix = contentX + col * (itemW + 6f);
                float iy = gridY + row * (itemH + 6f);
                if (inside(mx, my, ix, iy, itemW, itemH)) {
                    engine.setTargetItem(POPULAR_ITEMS[i]);
                    customItemInput = POPULAR_ITEMS[i];
                    return true;
                }
            }
        }

        // Tab 2 clicks: Settings
        if (currentTab == 2 && button == 0) {
            float rowY = contentY + 18f;
            float rowH = 38f;

            // Row 1: Buy discount
            if (inside(mx, my, contentX + contentW - 75f, rowY + 8f, 32f, 22f)) {
                engine.setBuyDiscountPercent(engine.getBuyDiscountPercent() - 1);
                return true;
            }
            if (inside(mx, my, contentX + contentW - 38f, rowY + 8f, 32f, 22f)) {
                engine.setBuyDiscountPercent(engine.getBuyDiscountPercent() + 1);
                return true;
            }
            rowY += rowH + 6f;

            // Row 2: Sell markup
            if (inside(mx, my, contentX + contentW - 75f, rowY + 8f, 32f, 22f)) {
                engine.setSellMarkupPercent(engine.getSellMarkupPercent() - 1);
                return true;
            }
            if (inside(mx, my, contentX + contentW - 38f, rowY + 8f, 32f, 22f)) {
                engine.setSellMarkupPercent(engine.getSellMarkupPercent() + 1);
                return true;
            }
        }

        // Tab 3 clicks: History clear
        if (currentTab == 3 && button == 0) {
            float clrW = 90f;
            float clrH = 20f;
            float clrX = contentX + contentW - clrW;
            if (inside(mx, my, clrX, contentY, clrW, clrH)) {
                engine.clearHistory();
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == 3) {
            historyScroll = Math.max(0f, historyScroll - (float) (verticalAmount * 24f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }

        if (inputFocused) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !customItemInput.isEmpty()) {
                customItemInput = customItemInput.substring(0, customItemInput.length() - 1);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                if (!customItemInput.isBlank()) {
                    engine.setTargetItem(customItemInput.trim());
                }
                inputFocused = false;
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (inputFocused && input.isValidChar()) {
            if (customItemInput.length() < 32) {
                customItemInput += input.asString();
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private Color withOpacity(Color c, float op) {
        int a = (int) Math.clamp(c.getAlpha() * op, 0, 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
