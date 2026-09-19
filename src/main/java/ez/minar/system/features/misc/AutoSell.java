package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.AuctionHelper;
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

@NewFunction(name = "AutoSell", desc = "Автопродажа предметов через /ah sell и sellgui", category = Category.MISC)
public class AutoSell extends Function {
    public static AutoSell instance;

    private final ModeSetting server = new ModeSetting("Server", "FunTime", "HolyWorld");
    private final ModeSetting trigger = new ModeSetting("Trigger", "Bind", "Auto");
    private final ModeSetting sellMode = new ModeSetting("Sell mode", "One", "One", "Stack", "Fixed");
    private final NumberSetting markup = new NumberSetting("Markup %", 10.0, 0.0, 100.0, 1.0);
    private final NumberSetting sellDelay = new NumberSetting("Sell delay", 500.0, 50.0, 5000.0, 50.0);
    private final KeybindSetting sellKey = new KeybindSetting("Sell key", -1);
    private final TextSetting fixedPrice = new TextSetting("Fixed price", "40000", 32);

    private final FarmTimer tickTimer = new FarmTimer();
    private final FarmTimer actionTimer = new FarmTimer();
    private SellState state = SellState.IDLE;
    private boolean selling;
    private long sellPrice;
    private String searchName = "";
    private Item targetItem = Items.AIR;
    private String targetDisplay = "";
    private int sellGuiPlaced;
    private boolean sellGuiPending;
    private long lastSellGuiCmd;

    public AutoSell() {
        instance = this;
        addSettings(server, trigger, sellMode, markup, sellDelay, sellKey, fixedPrice);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (selling) finish(false);
        reset();
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        if (!selling) return;
        String lower = AuctionHelper.strip(event.getPacket().content().getString()).toLowerCase(Locale.ROOT);

        if (lower.contains("выстав") && lower.contains("продаж")) {
            sellGuiPending = false;
            if (sellMode.isEnabled("Fixed")) {
                finish(true);
            } else {
                state = SellState.IDLE;
                actionTimer.reset();
            }
            return;
        }

        if (lower.contains("не удалось выставить") || lower.contains("освободите хранилище")) {
            notify("§c[AutoSell] Хранилище заполнено.");
            finish(false);
            return;
        }

        if (lower.contains("у вас купили") || lower.contains("купил у вас")) {
            if (sellMode.isEnabled("Fixed")) {
                state = SellState.RELIST;
            } else {
                finish(true);
            }
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null) return;

        handleBind();
        if (trigger.isEnabled("Auto") && !selling && !AuctionHelper.buyingActive) {
            if (tickTimer.tryReset(500L) && hasSellableItem()) {
                beginSell();
            }
        }
        if (!selling) return;
        if (!tickTimer.tryReset(50L)) return;

        switch (state) {
            case IDLE -> {
                if (server.isEnabled("HolyWorld")) state = SellState.HOLY_PREP;
                else if (sellMode.isEnabled("Fixed")) state = SellState.SELLGUI_PREP;
                else state = SellState.PREPARE;
                actionTimer.reset();
            }
            case PREPARE -> tickPrepare();
            case SEARCH -> tickSearch();
            case SCAN -> tickScan();
            case SELL_CMD -> tickSellCmd();
            case SELLGUI_PREP -> tickSellGuiPrep();
            case SELLGUI_FILL -> tickSellGuiFill();
            case SELLGUI_CONFIRM -> tickSellGuiConfirm();
            case HOLY_PREP -> tickHolyPrep();
            case HOLY_CONFIRM -> tickHolyConfirm();
            case RELIST -> tickRelist();
        }
    }

    private void handleBind() {
        if (!trigger.isEnabled("Bind") || selling) return;
        int key = sellKey.getKeybind();
        if (key <= 0) return;
        boolean pressed = KeybindSetting.isMouseKey(key)
                ? sellKey.consumeMouseClick(mc.getWindow().getHandle())
                : GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
        if (pressed && hasHandItem()) beginSell();
    }

    private void tickPrepare() {
        if (!ensureTargetItem()) {
            finish(false);
            return;
        }
        ItemStack hand = mc.player.getMainHandStack();
        searchName = resolveSearchName(hand);
        if (searchName.isBlank()) {
            notify("§c[AutoSell] Не удалось определить имя предмета.");
            finish(false);
            return;
        }
        if (sellMode.isEnabled("One") && hand.getCount() > 1) {
            splitOne(hand);
            if (!actionTimer.tryReset(150L)) return;
        }
        state = SellState.SEARCH;
        actionTimer.reset();
    }

    private void tickSearch() {
        if (mc.currentScreen != null) {
            FarmInventory.closeScreen();
            if (!actionTimer.tryReset(120L)) return;
        }
        FarmInventory.sendCommand("ah search " + searchName);
        state = SellState.SCAN;
        actionTimer.reset();
    }

    private void tickScan() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isAuctionScreen(screen)) {
            if (actionTimer.passed(8000L)) finish(false);
            return;
        }
        if (!actionTimer.tryReset(350L)) return;

        double bestUnit = Double.MAX_VALUE;
        int container = AuctionHelper.containerSlots(screen);
        for (int i = 0; i < Math.min(container, screen.getScreenHandler().slots.size()); i++) {
            long lot = AuctionHelper.readLotPrice(screen.getScreenHandler().getSlot(i));
            if (lot <= 0L) continue;
            int count = Math.max(1, screen.getScreenHandler().getSlot(i).getStack().getCount());
            bestUnit = Math.min(bestUnit, (double) lot / count);
        }

        ItemStack hand = mc.player.getMainHandStack();
        int amount = sellMode.isEnabled("Stack") ? Math.max(1, hand.getCount()) : 1;
        if (bestUnit < Double.MAX_VALUE) {
            sellPrice = (long) Math.ceil(bestUnit * amount);
        } else {
            sellPrice = AuctionHelper.parseMoney(fixedPrice.getValue());
        }

        double factor = 1.0 + markup.getValue() / 100.0;
        sellPrice = Math.max(1L, (long) (sellPrice * factor));
        FarmInventory.closeScreen();
        state = SellState.SELL_CMD;
        actionTimer.reset();
    }

    private void tickSellCmd() {
        if (!actionTimer.tryReset((long) sellDelay.getValue())) return;
        FarmInventory.sendCommand("ah sell " + sellPrice);
        if (sellMode.isEnabled("Fixed")) {
            state = SellState.IDLE;
        } else {
            finish(true);
        }
    }

    private void tickSellGuiPrep() {
        if (!ensureTargetItem()) {
            finish(false);
            return;
        }
        sellPrice = AuctionHelper.parseMoney(fixedPrice.getValue());
        if (sellPrice <= 0L) {
            notify("§c[AutoSell] Цена не задана.");
            finish(false);
            return;
        }
        if (System.currentTimeMillis() - lastSellGuiCmd < 1200L) return;
        if (mc.currentScreen != null) FarmInventory.closeScreen();
        FarmInventory.sendCommand("ah sellgui " + sellPrice);
        lastSellGuiCmd = System.currentTimeMillis();
        sellGuiPlaced = 0;
        sellGuiPending = true;
        state = SellState.SELLGUI_FILL;
        actionTimer.reset();
    }

    private void tickSellGuiFill() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isSellGui(screen)) {
            if (actionTimer.passed(7000L)) state = SellState.SELLGUI_PREP;
            return;
        }
        if (!actionTimer.tryReset(80L)) return;

        ScreenHandler handler = screen.getScreenHandler();
        int empty = AuctionHelper.findEmptySellSlot(screen);
        int itemSlot = AuctionHelper.findMatchingSlot(handler, handler.slots.size() - 36, targetItem, targetDisplay);
        if (empty >= 0 && itemSlot >= 0 && sellGuiPlaced < 9) {
            if (sellMode.isEnabled("One")) {
                FarmInventory.click(handler.syncId, itemSlot, 0, SlotActionType.PICKUP);
                FarmInventory.click(handler.syncId, empty, 1, SlotActionType.PICKUP);
                if (!handler.getCursorStack().isEmpty()) {
                    FarmInventory.click(handler.syncId, itemSlot, 0, SlotActionType.PICKUP);
                }
            } else {
                FarmInventory.click(handler.syncId, itemSlot, 0, SlotActionType.PICKUP);
                FarmInventory.click(handler.syncId, empty, 0, SlotActionType.PICKUP);
            }
            sellGuiPlaced++;
            return;
        }

        if (sellGuiPlaced > 0 || !hasMatchingInInventory()) {
            state = SellState.SELLGUI_CONFIRM;
            actionTimer.reset();
        } else if (actionTimer.passed(5000L)) {
            finish(false);
        }
    }

    private void tickSellGuiConfirm() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isSellGui(screen)) {
            if (sellGuiPending && actionTimer.passed(12000L)) finish(false);
            return;
        }
        int button = AuctionHelper.findSellButton(screen);
        if (button >= 0 && actionTimer.tryReset(300L)) {
            FarmInventory.click(screen.getScreenHandler().syncId, button, 0, SlotActionType.PICKUP);
            actionTimer.reset();
            return;
        }
        if (actionTimer.passed(12000L)) finish(false);
    }

    private void tickHolyPrep() {
        if (!ensureTargetItem()) {
            finish(false);
            return;
        }
        if (mc.currentScreen != null) FarmInventory.closeScreen();
        if (actionTimer.tryReset((long) sellDelay.getValue())) {
            FarmInventory.sendCommand("ah sell auto");
            state = SellState.HOLY_CONFIRM;
            actionTimer.reset();
        }
    }

    private void tickHolyConfirm() {
        if (actionTimer.tryReset(1000L)) {
            FarmInventory.sendCommand("ah sell auto confirm");
            finish(true);
        }
    }

    private void tickRelist() {
        FarmInventory.closeScreen();
        if (actionTimer.tryReset(500L)) {
            state = SellState.SELLGUI_PREP;
            actionTimer.reset();
        }
    }

    private void beginSell() {
        if (AuctionHelper.buyingActive) return;
        if (!hasHandItem() && !ensureTargetItem()) return;
        selling = true;
        state = SellState.IDLE;
        actionTimer.reset();
        tickTimer.reset();
    }

    private void finish(boolean success) {
        selling = false;
        sellGuiPending = false;
        sellGuiPlaced = 0;
        state = SellState.IDLE;
        FarmInventory.closeScreen();
        if (!success) actionTimer.reset();
    }

    private void reset() {
        selling = false;
        sellGuiPending = false;
        state = SellState.IDLE;
        sellPrice = 0L;
        searchName = "";
        targetItem = Items.AIR;
        targetDisplay = "";
        sellGuiPlaced = 0;
        tickTimer.reset();
        actionTimer.reset();
    }

    private boolean hasHandItem() {
        return mc.player != null && !mc.player.getMainHandStack().isEmpty();
    }

    private boolean hasSellableItem() {
        return hasHandItem() || ensureTargetItem();
    }

    private boolean ensureTargetItem() {
        if (mc.player == null) return false;
        ItemStack hand = mc.player.getMainHandStack();
        if (!hand.isEmpty()) {
            targetItem = hand.getItem();
            targetDisplay = hand.getName().getString();
            return true;
        }
        AuctionHelper.PurchaseInfo info = AuctionHelper.getLastPurchase();
        if (info != null) {
            targetItem = info.item();
            targetDisplay = info.displayName();
            if (selectMatchingItem()) return true;
        }
        return selectMatchingItem();
    }

    private boolean selectMatchingItem() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesTarget(stack)) {
                mc.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesTarget(stack)) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        mc.player.getInventory().getSelectedSlot(),
                        SlotActionType.SWAP,
                        mc.player
                );
                return true;
            }
        }
        return false;
    }

    private boolean hasMatchingInInventory() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (matchesTarget(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private boolean matchesTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (targetItem != Items.AIR && !stack.isOf(targetItem)) return false;
        if (targetDisplay == null || targetDisplay.isBlank()) return true;
        return AuctionHelper.matchesName(stack, targetDisplay);
    }

    private void splitOne(ItemStack hand) {
        if (mc.player == null || hand.getCount() <= 1) return;
        int empty = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                empty = i;
                break;
            }
        }
        if (empty < 0) return;
        int hotbar = mc.player.getInventory().getSelectedSlot() + 36;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, hotbar, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, hotbar, 1, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, empty, 0, SlotActionType.PICKUP, mc.player);
    }

    private String resolveSearchName(ItemStack stack) {
        String name = AuctionHelper.strip(stack.getName().getString());
        if (name.contains("TIER WHITE")) return "вайт";
        if (name.contains("TIER BLACK")) return "блэк";
        if (name.contains("Рассадник монстров")) return "Спавнер";
        return name;
    }

    private void notify(String msg) {
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(msg));
        }
    }

    private enum SellState {
        IDLE, PREPARE, SEARCH, SCAN, SELL_CMD, SELLGUI_PREP, SELLGUI_FILL, SELLGUI_CONFIRM, HOLY_PREP, HOLY_CONFIRM, RELIST
    }
}
