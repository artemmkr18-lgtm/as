package ez.minar.system.autobuy.flipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.manager.AutoBuyManager;
import ez.minar.system.autobuy.network.CommandSender;
import ez.minar.system.autobuy.util.TimerUtil;
import ez.minar.system.commands.CommandFeedback;
import ez.minar.utils.helpers.AuctionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AutoBuyFlipperEngine {
    private static final AutoBuyFlipperEngine INSTANCE = new AutoBuyFlipperEngine();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    public static AutoBuyFlipperEngine getInstance() {
        return INSTANCE;
    }

    public enum State {
        IDLE("Остановлен"),
        MARKET_SEARCH_SEND("Поиск цен на аукционе..."),
        MARKET_SEARCH_WAIT("Ожидание окна поиска..."),
        MARKET_SEARCH_READ("Чтение цен на рынке..."),
        MARKET_SEARCH_CLOSE("Закрытие окна поиска..."),
        SNIPE_OPEN_SEND("Открытие аукциона /ah..."),
        SNIPE_WAIT("Ожидание окна /ah..."),
        SNIPE_MONITORING("Снайпинг и обновление слота 46..."),
        BUYING_WAIT_CONFIRM("Подтверждение покупки лота..."),
        RESELL_CLOSE_AH("Закрытие аукциона после покупки..."),
        RESELL_SELECT_ITEM("Выбор предмета в руку..."),
        RESELL_SEND_COMMAND("Выставление лота на продажу..."),
        PAUSE("Пауза перед следующим шагом...");

        private final String label;
        State(String label) {
            this.label = label;
        }
        public String getLabel() {
            return ez.minar.system.managers.LocalizationManager.get(label);
        }
    }

    // Configurable parameters
    private boolean enabled = false;
    private String targetItem = "Изумрудный меч";
    private int rotationIndex = 0;
    private int marketSearchAttempts = 0;
    private static final int MAX_SEARCH_ATTEMPTS = 3;
    private int buyDiscountPercent = 10; // 10% below market min price
    private int sellMarkupPercent = 10;  // 10% above base market price
    private long marketScanInterval = 4 * 60 * 1000L; // 4 minutes
    private long refreshDelay = 300L; // slot 46 click delay (ms)

    // Live state
    private State state = State.IDLE;
    private long baseMarketPrice = 0L;
    private long targetBuyPrice = 0L;
    private long targetSellPrice = 0L;
    private long lastBoughtPrice = 0L;
    private long lastMarketScanTime = 0L;

    // Statistics
    private int totalBought = 0;
    private int totalSold = 0;
    private long totalProfit = 0L;

    // History
    public static class FlipperHistoryEntry {
        public final String time;
        public final String item;
        public final long buyPrice;
        public final long sellPrice;
        public final long profit;

        public FlipperHistoryEntry(String time, String item, long buyPrice, long sellPrice, long profit) {
            this.time = time;
            this.item = item;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.profit = profit;
        }
    }
    private final List<FlipperHistoryEntry> history = new ArrayList<>();

    // Timers
    private final TimerUtil actionTimer = TimerUtil.create();
    private final TimerUtil refreshTimer = TimerUtil.create();
    private final TimerUtil stateTimeoutTimer = TimerUtil.create();

    private AutoBuyFlipperEngine() {
        loadConfig();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            selectRotationTarget(rotationIndex);
            marketSearchAttempts = 0;
            // Never snipe on a threshold left over from a previous session.
            baseMarketPrice = 0L;
            targetBuyPrice = 0L;
            targetSellPrice = 0L;
            state = State.MARKET_SEARCH_SEND;
            lastMarketScanTime = 0L; // force immediate market analysis
            actionTimer.resetCounter();
            stateTimeoutTimer.resetCounter();
            CommandFeedback.message("[AutoBuy] Запущен для предмета: " + targetItem, Formatting.GREEN);
        } else {
            state = State.IDLE;
            CommandFeedback.message("[AutoBuy] Остановлен.", Formatting.RED);
        }
        saveConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public State getState() {
        return state;
    }

    public String getTargetItem() {
        return targetItem;
    }

    public void setTargetItem(String targetItem) {
        this.targetItem = targetItem;
        rotationIndex = Math.max(0, rotationPool().indexOf(targetItem));
        // reset prices on item change
        this.baseMarketPrice = 0L;
        this.targetBuyPrice = 0L;
        this.targetSellPrice = 0L;
        if (enabled) {
            this.lastMarketScanTime = 0L;
            this.state = State.MARKET_SEARCH_SEND;
            actionTimer.resetCounter();
        }
        saveConfig();
    }

    /** Enabled items from the AutoBuy list, in registry order; empty means "keep the manual target". */
    private List<String> rotationPool() {
        List<String> names = new ArrayList<>();
        for (AutoBuyableItem item : AutoBuyManager.getInstance().getAllItems()) {
            if (item.isEnabled()) names.add(item.getDisplayName());
        }
        return names;
    }

    private void selectRotationTarget(int index) {
        List<String> pool = rotationPool();
        if (pool.isEmpty()) return;
        rotationIndex = Math.floorMod(index, pool.size());
        applyTarget(pool.get(rotationIndex));
    }

    private void advanceRotationTarget() {
        selectRotationTarget(rotationIndex + 1);
    }

    private void applyTarget(String name) {
        if (name == null || name.isEmpty() || name.equals(targetItem)) return;
        targetItem = name;
        baseMarketPrice = 0L;
        targetBuyPrice = 0L;
        targetSellPrice = 0L;
    }

    public int getBuyDiscountPercent() {
        return buyDiscountPercent;
    }

    public void setBuyDiscountPercent(int percent) {
        this.buyDiscountPercent = Math.max(1, Math.min(95, percent));
        recalculatePrices();
        saveConfig();
    }

    public int getSellMarkupPercent() {
        return sellMarkupPercent;
    }

    public void setSellMarkupPercent(int percent) {
        this.sellMarkupPercent = Math.max(1, Math.min(200, percent));
        recalculatePrices();
        saveConfig();
    }

    public long getBaseMarketPrice() {
        return baseMarketPrice;
    }

    public long getTargetBuyPrice() {
        return targetBuyPrice;
    }

    public long getTargetSellPrice() {
        return targetSellPrice;
    }

    public long getLastMarketScanTime() {
        return lastMarketScanTime;
    }

    public long getMarketScanInterval() {
        return marketScanInterval;
    }

    public int getTotalBought() {
        return totalBought;
    }

    public int getTotalSold() {
        return totalSold;
    }

    public long getTotalProfit() {
        return totalProfit;
    }

    public List<FlipperHistoryEntry> getHistory() {
        return history;
    }

    public void clearHistory() {
        history.clear();
    }

    private void recalculatePrices() {
        if (baseMarketPrice > 0L) {
            targetBuyPrice = (long) (baseMarketPrice * (1.0 - buyDiscountPercent / 100.0));
            targetSellPrice = (long) (baseMarketPrice * (1.0 + sellMarkupPercent / 100.0));
        }
    }

    public void onTick() {
        if (!enabled) {
            state = State.IDLE;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) return;

        // Safety timeout: if stuck in any transient state for > 8 seconds, restart to search or snipe
        if (state != State.IDLE && state != State.SNIPE_MONITORING) {
            if (stateTimeoutTimer.hasTimeElapsed(8000)) {
                stateTimeoutTimer.resetCounter();
                if (baseMarketPrice > 0L) {
                    player.closeHandledScreen();
                    state = State.SNIPE_OPEN_SEND;
                } else {
                    player.closeHandledScreen();
                    state = State.MARKET_SEARCH_SEND;
                }
                actionTimer.resetCounter();
                return;
            }
        } else {
            stateTimeoutTimer.resetCounter();
        }

        switch (state) {
            case MARKET_SEARCH_SEND -> {
                if (actionTimer.hasTimeElapsed(350)) {
                    if (++marketSearchAttempts > MAX_SEARCH_ATTEMPTS) {
                        CommandFeedback.message("[AutoBuy] Окно аукциона не открылось для '" + targetItem
                                + "' за " + MAX_SEARCH_ATTEMPTS + " попыток — перехожу к следующему предмету",
                                Formatting.RED);
                        advanceRotationTarget();
                        marketSearchAttempts = 1;
                    }
                    CommandFeedback.message("[AutoBuy] Запрос рынка: " + targetItem, Formatting.YELLOW);
                    CommandSender.sendCommand(player, "/ah search " + targetItem);
                    actionTimer.resetCounter();
                    state = State.MARKET_SEARCH_WAIT;
                }
            }

            case MARKET_SEARCH_WAIT -> {
                // The server may answer with a mode/server picker first; only the lot page counts.
                if (mc.currentScreen instanceof GenericContainerScreen screen && AuctionHelper.isAuctionScreen(screen)) {
                    actionTimer.resetCounter();
                    state = State.MARKET_SEARCH_READ;
                }
            }

            case MARKET_SEARCH_READ -> {
                // Wait 400ms for lots to load from server
                if (actionTimer.hasTimeElapsed(400)) {
                    if (mc.currentScreen instanceof GenericContainerScreen screen && AuctionHelper.isAuctionScreen(screen)) {
                        marketSearchAttempts = 0;
                        GenericContainerScreenHandler handler = screen.getScreenHandler();
                        long cheapest = Long.MAX_VALUE;
                        int foundCount = 0;

                        int limit = Math.min(45, handler.slots.size());
                        for (int i = 0; i < limit; i++) {
                            Slot slot = handler.getSlot(i);
                            if (slot.hasStack()) {
                                ItemStack stack = slot.getStack();
                                if (matchesTargetItem(stack)) {
                                    long price = AuctionHelper.readLotPrice(slot);
                                    if (price > 0L && price < cheapest) {
                                        cheapest = price;
                                        foundCount++;
                                    }
                                }
                            }
                        }

                        if (foundCount > 0 && cheapest != Long.MAX_VALUE) {
                            baseMarketPrice = cheapest;
                            recalculatePrices();
                            lastMarketScanTime = System.currentTimeMillis();
                            CommandFeedback.message("[AutoBuy] Рынок обновлен [" + targetItem + "]: мин. " + baseMarketPrice + "$ | Скупка <= "
                                    + targetBuyPrice + "$ | Продажа: " + targetSellPrice + "$", Formatting.GREEN);
                        } else {
                            CommandFeedback.message("[AutoBuy] Лоты '" + targetItem + "' не найдены на рынке, поиск продолжается...", Formatting.YELLOW);
                        }
                    }
                    player.closeHandledScreen();
                    actionTimer.resetCounter();
                    state = State.MARKET_SEARCH_CLOSE;
                }
            }

            case MARKET_SEARCH_CLOSE -> {
                if (actionTimer.hasTimeElapsed(300)) {
                    actionTimer.resetCounter();
                    state = State.SNIPE_OPEN_SEND;
                }
            }

            case SNIPE_OPEN_SEND -> {
                if (actionTimer.hasTimeElapsed(300)) {
                    // Bare /ah lands on the mode/server pickers; the search page is the lots page.
                    CommandSender.sendCommand(player, "/ah search " + targetItem);
                    actionTimer.resetCounter();
                    state = State.SNIPE_WAIT;
                }
            }

            case SNIPE_WAIT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen && AuctionHelper.isAuctionScreen(screen)) {
                    actionTimer.resetCounter();
                    refreshTimer.resetCounter();
                    state = State.SNIPE_MONITORING;
                }
            }

            case SNIPE_MONITORING -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isAuctionScreen(screen)) {
                    // Screen was closed unexpectedly
                    if (actionTimer.hasTimeElapsed(1000)) {
                        state = State.SNIPE_OPEN_SEND;
                        actionTimer.resetCounter();
                    }
                    return;
                }

                // Check 4-minute market recheck interval
                if (lastMarketScanTime > 0L && System.currentTimeMillis() - lastMarketScanTime >= marketScanInterval) {
                    advanceRotationTarget();
                    player.closeHandledScreen();
                    actionTimer.resetCounter();
                    state = State.MARKET_SEARCH_SEND;
                    return;
                }

                GenericContainerScreenHandler handler = screen.getScreenHandler();
                int limit = Math.min(45, handler.slots.size());

                // Scan current slots for cheap deals
                if (targetBuyPrice > 0L) {
                    for (int i = 0; i < limit; i++) {
                        Slot slot = handler.getSlot(i);
                        if (slot.hasStack() && matchesTargetItem(slot.getStack())) {
                            long price = AuctionHelper.readLotPrice(slot);
                            if (price > 0L && price <= targetBuyPrice) {
                                // DEAL FOUND! Click slot to buy
                                lastBoughtPrice = price;
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, player);
                                actionTimer.resetCounter();
                                state = State.BUYING_WAIT_CONFIRM;
                                return;
                            }
                        }
                    }
                }

                // If no deal found, click slot 46 to refresh page
                if (refreshTimer.hasTimeElapsed(refreshDelay)) {
                    if (handler.slots.size() > 46) {
                        mc.interactionManager.clickSlot(handler.syncId, 46, 0, SlotActionType.QUICK_MOVE, player);
                    }
                    refreshTimer.resetCounter();
                }
            }

            case BUYING_WAIT_CONFIRM -> {
                // If a confirmation dialog opened, click confirm button
                if (mc.currentScreen instanceof GenericContainerScreen confirmScreen && AuctionHelper.isConfirmScreen(confirmScreen)) {
                    int confirmSlot = AuctionHelper.findConfirmSlot(confirmScreen.getScreenHandler());
                    if (confirmSlot >= 0) {
                        mc.interactionManager.clickSlot(confirmScreen.getScreenHandler().syncId, confirmSlot, 0, SlotActionType.PICKUP, player);
                    }
                }

                // If purchase was completed or timeout
                if (actionTimer.hasTimeElapsed(800)) {
                    onPurchaseSuccess(targetItem, lastBoughtPrice);
                }
            }

            case RESELL_CLOSE_AH -> {
                if (actionTimer.hasTimeElapsed(250)) {
                    player.closeHandledScreen();
                    actionTimer.resetCounter();
                    state = State.RESELL_SELECT_ITEM;
                }
            }

            case RESELL_SELECT_ITEM -> {
                if (actionTimer.hasTimeElapsed(250)) {
                    // Find item in inventory and hold in hand
                    int held = selectItemInHand(player, targetItem);
                    if (held >= 0) {
                        actionTimer.resetCounter();
                        state = State.RESELL_SEND_COMMAND;
                    } else {
                        // Item not in inventory yet, wait or return to snipe
                        if (actionTimer.hasTimeElapsed(1200)) {
                            state = State.SNIPE_OPEN_SEND;
                            actionTimer.resetCounter();
                        }
                    }
                }
            }

            case RESELL_SEND_COMMAND -> {
                if (actionTimer.hasTimeElapsed(300)) {
                    long sellPrice = targetSellPrice > 0L ? targetSellPrice : (long) (lastBoughtPrice * 1.1);
                    CommandSender.sendCommand(player, "/ah sell " + sellPrice);
                    totalSold++;
                    CommandFeedback.message("[AutoBuy] Выставлен на аукцион за " + sellPrice + "$!", Formatting.AQUA);
                    actionTimer.resetCounter();
                    state = State.PAUSE;
                }
            }

            case PAUSE -> {
                if (actionTimer.hasTimeElapsed(400)) {
                    actionTimer.resetCounter();
                    state = State.SNIPE_OPEN_SEND;
                }
            }
        }
    }

    public void onChatMessage(String message) {
        if (!enabled) return;
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("вы успешно купили") || lower.contains("вы приобрели")
                || lower.contains("успешно приобрели") || lower.contains("you bought")) {
            if (state == State.BUYING_WAIT_CONFIRM || state == State.SNIPE_MONITORING) {
                onPurchaseSuccess(targetItem, lastBoughtPrice);
            }
        }
    }

    private void onPurchaseSuccess(String itemName, long price) {
        totalBought++;
        long profit = (targetSellPrice > 0L ? targetSellPrice : (long) (price * 1.1)) - price;
        totalProfit += profit;
        history.add(0, new FlipperHistoryEntry(TIME_FMT.format(new Date()), itemName, price, targetSellPrice, profit));
        CommandFeedback.message("[AutoBuy] Куплен " + itemName + " за " + price + "$! (Ожидаемый профит: +" + profit + "$)", Formatting.GREEN);
        actionTimer.resetCounter();
        state = State.RESELL_CLOSE_AH;
        saveConfig();
    }

    public boolean matchesTargetItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String q = AuctionHelper.normalize(targetItem);
        String name = AuctionHelper.normalize(stack.getName().getString());
        if (name.contains(q) || q.contains(name)) return true;

        // Check if query is emerald sword and item is sword
        if (q.contains("изумруд") && q.contains("меч")) {
            return name.contains("изумруд") || name.contains("emerald");
        }
        return false;
    }

    private int selectItemInHand(ClientPlayerEntity player, String itemName) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // 1. Check hotbar slots (0 to 8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (matchesTargetItem(stack)) {
                player.getInventory().setSelectedSlot(i);
                return i;
            }
        }

        // 2. Check main inventory (slots 9 to 35) and swap to selected hotbar slot
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (matchesTargetItem(stack)) {
                int currentHotbar = player.getInventory().getSelectedSlot();
                // Swap main inventory slot i into currentHotbar
                mc.interactionManager.clickSlot(
                        player.playerScreenHandler.syncId,
                        i,
                        currentHotbar,
                        SlotActionType.SWAP,
                        player
                );
                return currentHotbar;
            }
        }

        return -1;
    }

    public void saveConfig() {
        try {
            File dir = new File("minar");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "autobuy_flipper.json");
            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("targetItem", targetItem);
            json.addProperty("buyDiscountPercent", buyDiscountPercent);
            json.addProperty("sellMarkupPercent", sellMarkupPercent);
            json.addProperty("marketScanInterval", marketScanInterval);
            json.addProperty("refreshDelay", refreshDelay);
            json.addProperty("baseMarketPrice", baseMarketPrice);
            json.addProperty("totalBought", totalBought);
            json.addProperty("totalSold", totalSold);
            json.addProperty("totalProfit", totalProfit);

            try (FileWriter writer = new FileWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception ignored) {}
    }

    public void loadConfig() {
        try {
            File file = new File("minar/autobuy_flipper.json");
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("targetItem")) targetItem = json.get("targetItem").getAsString();
                    if (json.has("buyDiscountPercent")) buyDiscountPercent = json.get("buyDiscountPercent").getAsInt();
                    if (json.has("sellMarkupPercent")) sellMarkupPercent = json.get("sellMarkupPercent").getAsInt();
                    if (json.has("marketScanInterval")) marketScanInterval = json.get("marketScanInterval").getAsLong();
                    if (json.has("refreshDelay")) refreshDelay = json.get("refreshDelay").getAsLong();
                    if (json.has("baseMarketPrice")) baseMarketPrice = json.get("baseMarketPrice").getAsLong();
                    if (json.has("totalBought")) totalBought = json.get("totalBought").getAsInt();
                    if (json.has("totalSold")) totalSold = json.get("totalSold").getAsInt();
                    if (json.has("totalProfit")) totalProfit = json.get("totalProfit").getAsLong();
                    recalculatePrices();
                }
            }
        } catch (Exception ignored) {}
    }
}
