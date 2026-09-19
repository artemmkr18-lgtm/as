package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.autobuy.flipper.AutoBuyFlipperEngine;
import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.manager.AutoBuyManager;
import ez.minar.system.autobuy.network.*;
import ez.minar.system.autobuy.ui.AutoBuyMenuScreen;
import ez.minar.system.autobuy.util.TimerUtil;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketReceiveEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ButtonSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@NewFunction(name = "AutoBuy", desc = "Автоматическая скупка и перепродажа на аукционе", category = Category.MISC)
public class AutoBuy extends Function {
    private final ModeSetting mode = new ModeSetting("Режим", "Умный перекуп", "Сетевой (Rich)");
    private final ModeSetting leaveType = new ModeSetting("Тип обхода", "Проверяющий", "Покупающий");
    private final NumberSetting timer2 = new NumberSetting("Таймер обновления аукциона", 350, 350, 750, 10);
    private final BooleanSetting bypassDelay = new BooleanSetting("Обход задержки 1.16.5 анках", false);
    private final BooleanSetting bypassDelay1214 = new BooleanSetting("Обход задержки 1.21.4 анках", false);
    private final BooleanSetting autoStorage = new BooleanSetting("Автоскладирование", false);
    private final ButtonSetting openGuiButton = new ButtonSetting("Меню AutoBuy", "Открыть", () -> mc.setScreen(new AutoBuyMenuScreen()));

    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();
    private final NetworkManager networkManager = new NetworkManager();
    private final ServerManager serverManager;
    private final StorageManager storageManager;
    private final AuctionHandler auctionHandler;
    private final AfkHandler afkHandler = new AfkHandler();

    private final TimerUtil openTimer = TimerUtil.create();
    private final TimerUtil updateTimer = TimerUtil.create();
    private final TimerUtil buyTimer = TimerUtil.create();
    private final TimerUtil switchTimer = TimerUtil.create();
    private final TimerUtil enterDelayTimer = TimerUtil.create();
    private final TimerUtil ahSpamTimer = TimerUtil.create();
    private final TimerUtil connectionCheckTimer = TimerUtil.create();
    private final TimerUtil auctionRequestTimer = TimerUtil.create();

    private boolean open = false;
    private boolean serverInAuction = false;
    private boolean justEntered = false;
    private boolean spammingAh = false;
    private boolean waitingForAuctionOpen = false;
    private final List<AutoBuyableItem> cachedEnabledItems = new ArrayList<>();

    public AutoBuy() {
        serverManager = new ServerManager(bypassDelay, bypassDelay1214);
        storageManager = new StorageManager(autoStorage);
        auctionHandler = new AuctionHandler(autoBuyManager);

        addSettings(mode, leaveType, timer2, bypassDelay, bypassDelay1214, autoStorage, openGuiButton);
    }

    @Override
    public void onEnable() {
        if (mode.isEnabled("Умный перекуп")) {
            AutoBuyFlipperEngine.getInstance().setEnabled(true);
            return;
        }

        resetTimers();
        resetState();

        if (leaveType.isEnabled("Покупающий") && (bypassDelay.isEnabled() || bypassDelay1214.isEnabled())) {
            mc.options.pauseOnLostFocus = false;
        }

        cacheEnabledItems();
        networkManager.start(leaveType.getActiveMode());
        autoBuyManager.setEnabled(true);
    }

    @Override
    public void onDisable() {
        AutoBuyFlipperEngine.getInstance().setEnabled(false);
        networkManager.stop();
        serverManager.reset();
        storageManager.reset();
        afkHandler.resetMovementKeys(mc.options);
        autoBuyManager.setEnabled(false);
    }

    private void resetTimers() {
        openTimer.resetCounter();
        updateTimer.resetCounter();
        buyTimer.resetCounter();
        switchTimer.resetCounter();
        enterDelayTimer.resetCounter();
        ahSpamTimer.resetCounter();
        connectionCheckTimer.resetCounter();
        auctionRequestTimer.resetCounter();
        serverManager.resetTimers();
        storageManager.resetTimers();
        afkHandler.resetTimers();
    }

    private void resetState() {
        open = false;
        serverInAuction = false;
        justEntered = false;
        spammingAh = false;
        waitingForAuctionOpen = false;
        cachedEnabledItems.clear();
        networkManager.clearQueues();
        auctionHandler.clear();
    }

    private void cacheEnabledItems() {
        cachedEnabledItems.clear();
        for (AutoBuyableItem item : autoBuyManager.getAllItems()) {
            if (item.isEnabled()) {
                cachedEnabledItems.add(item);
            }
        }
    }

    @EventHandler
    public void onPacket(PacketReceiveEvent e) {
        if (e.getPacket() instanceof GameMessageS2CPacket gameMessage) {
            Text content = gameMessage.content();
            String message = content.getString();

            AutoBuyFlipperEngine.getInstance().onChatMessage(message);

            if (message.contains("Вы уже подключены к этому серверу!")) {
                if (mc.player != null) {
                    serverManager.switchToNextServer(mc.player, networkManager, leaveType.isEnabled("Покупающий"));
                }
                return;
            }

            if (leaveType.isEnabled("Покупающий")) {
                PurchaseHandler.handlePurchaseMessage(message, autoBuyManager);
            }
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (mode.isEnabled("Умный перекуп")) {
            AutoBuyFlipperEngine.getInstance().onTick();
            return;
        }

        if (!autoBuyManager.isEnabled()) return;

        handleConnectionStatus();
        afkHandler.handle(mc);
        storageManager.handle(mc, open);

        if (storageManager.isActive()) {
            return;
        }

        if (storageManager.handlePostStorage(mc, enterDelayTimer, ahSpamTimer)) {
            justEntered = true;
        }

        boolean wasInHub = serverManager.isInHub();
        serverManager.updateHubStatus(mc.world);

        if (serverManager.shouldJoinAnarchy(bypassDelay.isEnabled(), bypassDelay1214.isEnabled())) {
            serverManager.joinAnarchyFromHub(mc.player);
        }

        if (wasInHub && !serverManager.isInHub()) {
            handleServerSwitch();
        }

        if (serverManager.isWaitingForServerLoad() || ServerSwitchHandler.isWaitingForServerLoad()) {
            if (ServerSwitchHandler.hasTimedOut() || (!wasInHub && !serverManager.isInHub())) {
                serverManager.setWaitingForServerLoad(false);
                ServerSwitchHandler.setWaitingForServerLoad(false);
                handleServerSwitch();
            }
        }

        handleAhSpam();
        handleAuction();
        handleServerAutoSwitch();
        handleCheckerAuctionRequest();
    }

    private void handleConnectionStatus() {
        if (leaveType.isEnabled("Проверяющий")) {
            if (connectionCheckTimer.hasTimeElapsed(5000)) {
                if (!networkManager.isConnectedToServer()) {
                    networkManager.start(leaveType.getActiveMode());
                }
                connectionCheckTimer.resetCounter();
            }
        }
    }

    private void handleServerSwitch() {
        justEntered = true;
        enterDelayTimer.resetCounter();
        switchTimer.resetCounter();
        storageManager.resetMaxShulkers();
        waitingForAuctionOpen = false;
        auctionRequestTimer.resetCounter();
    }

    private void handleAhSpam() {
        if (bypassDelay.isEnabled() || bypassDelay1214.isEnabled()) {
            if (justEntered && enterDelayTimer.hasTimeElapsed(2000)) {
                if (!spammingAh) {
                    spammingAh = true;
                    ahSpamTimer.resetCounter();
                }
            }
            if (spammingAh && !afkHandler.isPerformingAction()) {
                if (ahSpamTimer.hasTimeElapsed(1250)) {
                    if (mc.player != null && mc.player.networkHandler != null) {
                        CommandSender.sendCommand(mc.player, "/ah");
                    }
                    ahSpamTimer.resetCounter();
                }
            }
        }
    }

    private void handleCheckerAuctionRequest() {
        if (leaveType.isEnabled("Проверяющий")) {
            if (!open && !waitingForAuctionOpen) {
                if (auctionRequestTimer.hasTimeElapsed(3000)) {
                    if (networkManager.isConnectedToServer()) {
                        CommandSender.openAuction();
                        waitingForAuctionOpen = true;
                        auctionRequestTimer.resetCounter();
                    }
                }
            }
            if (waitingForAuctionOpen && auctionRequestTimer.hasTimeElapsed(5000)) {
                waitingForAuctionOpen = false;
                auctionRequestTimer.resetCounter();
            }
        }
    }

    private void handleAuction() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString();
            int syncId = screen.getScreenHandler().syncId;
            List<Slot> slots = screen.getScreenHandler().slots;

            if (title.contains("Аукцион") || title.contains("Аукционы") || title.contains("Поиск")) {
                if (!open) {
                    enterAuction();
                    return;
                }

                storageManager.handleAuctionEnter();

                if (leaveType.isEnabled("Покупающий")) {
                    handleBuyerMode(screen, syncId, slots);
                } else if (leaveType.isEnabled("Проверяющий")) {
                    handleCheckerMode(slots);
                }
            } else if (title.contains("Подозрительная цена")) {
                auctionHandler.handleSuspiciousPrice(mc, syncId, slots);
                openTimer.resetCounter();
                buyTimer.resetCounter();
            } else {
                exitAuction();
            }
        } else {
            exitAuction();
        }
    }

    private void enterAuction() {
        open = true;
        openTimer.resetCounter();
        updateTimer.resetCounter();
        buyTimer.resetCounter();
        storageManager.notifyAuctionEnter();
        serverInAuction = true;
        auctionHandler.clear();
        justEntered = false;
        spammingAh = false;
        waitingForAuctionOpen = false;
        storageManager.clearStorageCompleted();
        if (!storageManager.getPostStorageTimer().hasTimeElapsed(2000)) {
            storageManager.disableStartStorage();
        }
        cacheEnabledItems();
        if (leaveType.isEnabled("Проверяющий")) {
            networkManager.notifyAuctionEnter();
        }
        if (leaveType.isEnabled("Покупающий")) {
            networkManager.requestAuctionOpen();
        }
    }

    private void exitAuction() {
        if (open) {
            open = false;
            serverInAuction = false;
            auctionHandler.clear();
            if (leaveType.isEnabled("Проверяющий")) {
                networkManager.notifyAuctionLeave();
            }
        }
    }

    private void handleBuyerMode(GenericContainerScreen screen, int syncId, List<Slot> slots) {
        long clientCount = networkManager.getClientInAuctionCount();

        if (networkManager.getQueueSize() > 30) {
            auctionHandler.updateAuction(mc, syncId);
            networkManager.sendUpdateToClients();
            updateTimer.resetCounter();
            networkManager.clearQueues();
            return;
        }

        if (!storageManager.hasReachedMaxShulkers()) {
            BuyRequest request = networkManager.pollRequest();
            if (request != null) {
                auctionHandler.handleBuyRequest(mc, syncId, slots, request, networkManager);
            }
        }

        if (auctionHandler.shouldUpdate()) {
            auctionHandler.updateAuction(mc, syncId);
            networkManager.sendUpdateToClients();
            updateTimer.resetCounter();
            networkManager.clearQueues();
        }

        if (updateTimer.hasTimeElapsed((long) timer2.getValue()) && serverInAuction &&
                clientCount > 0 && networkManager.isQueuesEmpty()) {
            auctionHandler.updateAuction(mc, syncId);
            networkManager.sendUpdateToClients();
            updateTimer.resetCounter();
        }
    }

    private void handleCheckerMode(List<Slot> slots) {
        List<Slot> bestSlots = auctionHandler.findMatchingSlots(slots, cachedEnabledItems);
        if (!bestSlots.isEmpty()) {
            auctionHandler.processBestSlots(bestSlots, networkManager);
            buyTimer.resetCounter();
        }
    }

    private void handleServerAutoSwitch() {
        if (leaveType.isEnabled("Покупающий") && (bypassDelay.isEnabled() || bypassDelay1214.isEnabled())) {
            if (!serverManager.isInHub() && switchTimer.hasTimeElapsed(60000)) {
                if (mc.player != null) {
                    serverManager.switchToNextServer(mc.player, networkManager, true);
                }
            }
        }
    }
}
