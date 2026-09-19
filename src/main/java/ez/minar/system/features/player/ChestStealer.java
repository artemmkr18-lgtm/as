package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;

@NewFunction(name = "ChestStealer", desc = "Legit transfers items from opened chests", category = Category.PLAYER)
public class ChestStealer extends Function {
    private static final String[] BLOCKED_TITLES = {
            "auction", "warp", "warps", "menu", "shop", "case", "cases",
            "kit", "kits", "duel", "select", "settings", "reward", "donate",
            "\u0430\u0443\u043a\u0446\u0438\u043e\u043d",
            "\u0432\u0430\u0440\u043f",
            "\u043c\u0435\u043d\u044e",
            "\u0432\u044b\u0431\u043e\u0440 \u043d\u0430\u0431\u043e\u0440\u0430",
            "\u043a\u0435\u0439\u0441",
            "\u043c\u0430\u0433\u0430\u0437\u0438\u043d",
            "\u0434\u0443\u044d\u043b",
            "\u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430",
            "\u043d\u0430\u0433\u0440\u0430\u0434"
    };

    private final NumberSetting delay = new NumberSetting("Delay", 120.0, 0.0, 1000.0, 5.0);
    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", false);

    private long lastClickTime;
    private int lastSyncId = -1;

    public ChestStealer() {
        addSettings(delay, autoClose);
    }

    @Override
    public void onDisable() {
        lastClickTime = 0L;
        lastSyncId = -1;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()
                || mc.interactionManager == null
                || !(mc.currentScreen instanceof GenericContainerScreen screen)
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()
                || isBlockedTitle(screen.getTitle().getString())) {
            resetContainer();
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (handler.syncId != lastSyncId) {
            lastSyncId = handler.syncId;
            lastClickTime = 0L;
        }

        int slot = nextChestSlot(handler);
        if (slot == -1) {
            if (autoClose.isEnabled()) {
                mc.player.closeHandledScreen();
            }
            return;
        }

        if (!hasReached((long) delay.getValue())) {
            return;
        }

        mc.interactionManager.clickSlot(
                handler.syncId,
                slot,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
        );
        lastClickTime = System.currentTimeMillis();
    }

    private int nextChestSlot(GenericContainerScreenHandler handler) {
        int chestSize = handler.getRows() * 9;
        for (int slotId = 0; slotId < chestSize; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot != null && slot.hasStack()) {
                return slotId;
            }
        }
        return -1;
    }

    private boolean hasReached(long delayMs) {
        return delayMs <= 0L || System.currentTimeMillis() - lastClickTime >= delayMs;
    }

    private boolean isBlockedTitle(String title) {
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        for (String blockedTitle : BLOCKED_TITLES) {
            if (lowerTitle.contains(blockedTitle)) {
                return true;
            }
        }
        return false;
    }

    private void resetContainer() {
        lastSyncId = -1;
        lastClickTime = 0L;
    }
}
