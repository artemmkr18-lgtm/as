package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.AuctionHelper;
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.function.Predicate;

@NewFunction(name = "MoneyFarm", desc = "Покупает ресурсы, крафтит изумрудные предметы и продаёт", category = Category.MISC)
public class MoneyFarm extends Function {
    private final ModeSetting product = new ModeSetting("Product", "Sword", "Sword", "Pickaxe");
    private final TextSetting sellPrice = new TextSetting("Sell price", "40000", 32);
    private final TextSetting maxWoodPrice = new TextSetting("Max wood price", "5000", 32);
    private final NumberSetting delay = new NumberSetting("Delay ms", 120.0, 50.0, 2000.0, 25.0);
    private final BooleanSetting autoBuy = new BooleanSetting("Auto buy", true);
    private final BooleanSetting autoSell = new BooleanSetting("Auto sell", true);

    private final FarmTimer tickTimer = new FarmTimer();
    private final FarmTimer actionTimer = new FarmTimer();
    private FarmState state = FarmState.IDLE;
    private CraftPhase craftPhase = CraftPhase.ITEM;
    private BlockPos craftingTable;
    private boolean purchaseConfirmed;
    private int listedCount;
    private long lastSellGui;

    public MoneyFarm() {
        addSettings(product, sellPrice, maxWoodPrice, delay, autoBuy, autoSell);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        FarmNavigator.stop();
        reset();
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        String lower = AuctionHelper.strip(event.getPacket().content().getString()).toLowerCase(Locale.ROOT);
        if (lower.contains("вы успешно купили")) purchaseConfirmed = true;
        if (lower.contains("выстав") && lower.contains("продаж")) listedCount++;
        if (lower.contains("у вас купили")) listedCount = Math.max(0, listedCount - 1);
        if (lower.contains("не удалось выставить")) listedCount = 0;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null) return;
        if (!tickTimer.tryReset(50L)) return;

        switch (state) {
            case IDLE -> tickIdle();
            case BUY_WOOD_SEARCH -> tickBuyWoodSearch();
            case BUY_WOOD_AH -> tickBuyWoodAh();
            case BUY_WOOD_CONFIRM -> tickBuyWoodConfirm();
            case SHOP_OPEN -> tickShopOpen();
            case SHOP_EMERALD -> tickShopEmerald();
            case SHOP_CONFIRM -> tickShopConfirm();
            case FIND_TABLE -> tickFindTable();
            case OPEN_TABLE -> tickOpenTable();
            case CRAFT -> tickCraft();
            case TAKE_CRAFT -> tickTakeCraft();
            case SELL_PREP -> tickSellPrep();
            case SELL_GUI -> tickSellGui();
            case SELL_CONFIRM -> tickSellConfirm();
        }
    }

    private void tickIdle() {
        if (autoSell.isEnabled() && countProduct() > 0 && listedCount <= 0) {
            state = FarmState.SELL_PREP;
            actionTimer.reset();
            return;
        }
        if (needsEmeralds()) {
            state = FarmState.SHOP_OPEN;
            actionTimer.reset();
            return;
        }
        if (needsWood()) {
            state = FarmState.BUY_WOOD_SEARCH;
            actionTimer.reset();
            return;
        }
        if (needsSticks()) {
            craftPhase = CraftPhase.PLANKS;
            state = FarmState.FIND_TABLE;
            actionTimer.reset();
            return;
        }
        if (countProduct() < 3) {
            craftPhase = CraftPhase.ITEM;
            state = FarmState.FIND_TABLE;
            actionTimer.reset();
        }
    }

    private void tickBuyWoodSearch() {
        if (!autoBuy.isEnabled() || AuctionHelper.parseMoney(maxWoodPrice.getValue()) <= 0L) {
            state = FarmState.IDLE;
            return;
        }
        if (mc.currentScreen != null) FarmInventory.closeScreen();
        if (actionTimer.tryReset(step())) {
            FarmInventory.sendCommand("ah search Дерево");
            purchaseConfirmed = false;
            state = FarmState.BUY_WOOD_AH;
            actionTimer.reset();
        }
    }

    private void tickBuyWoodAh() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isAuctionScreen(screen)) {
            if (actionTimer.passed(8000L)) state = FarmState.IDLE;
            return;
        }
        long max = AuctionHelper.parseMoney(maxWoodPrice.getValue());
        ScreenHandler handler = screen.getScreenHandler();
        int best = -1;
        long bestPrice = Long.MAX_VALUE;
        int slots = AuctionHelper.containerSlots(screen);
        for (int i = 0; i < Math.min(slots, handler.slots.size()); i++) {
            if (!isWood(handler.getSlot(i).getStack())) continue;
            long price = AuctionHelper.readLotPrice(handler.getSlot(i));
            if (price <= 0L || price > max) continue;
            if (price < bestPrice) {
                bestPrice = price;
                best = i;
            }
        }
        if (best >= 0 && actionTimer.tryReset(step())) {
            FarmInventory.click(handler.syncId, best, 0, SlotActionType.QUICK_MOVE);
            state = FarmState.BUY_WOOD_CONFIRM;
            actionTimer.reset();
            return;
        }
        if (actionTimer.tryReset(step()) && handler.slots.size() > 49) {
            FarmInventory.click(handler.syncId, 49, 0, SlotActionType.PICKUP);
        } else if (actionTimer.passed(10000L)) {
            state = FarmState.IDLE;
        }
    }

    private void tickBuyWoodConfirm() {
        if (purchaseConfirmed) {
            FarmInventory.closeScreen();
            state = FarmState.IDLE;
            return;
        }
        if (mc.currentScreen instanceof GenericContainerScreen screen && AuctionHelper.isConfirmScreen(screen)) {
            int confirm = AuctionHelper.findConfirmSlot(screen.getScreenHandler());
            if (confirm >= 0 && actionTimer.tryReset(step())) {
                FarmInventory.click(screen.getScreenHandler().syncId, confirm, 0, SlotActionType.PICKUP);
            }
            return;
        }
        if (actionTimer.passed(6000L)) state = FarmState.IDLE;
    }

    private void tickShopOpen() {
        if (!needsEmeralds()) {
            state = FarmState.IDLE;
            return;
        }
        if (mc.currentScreen != null) FarmInventory.closeScreen();
        if (actionTimer.tryReset(step())) {
            FarmInventory.sendCommand("shop");
            state = FarmState.SHOP_EMERALD;
            actionTimer.reset();
        }
    }

    private void tickShopEmerald() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (actionTimer.passed(8000L)) state = FarmState.IDLE;
            return;
        }
        int gold = findItemSlot(screen, Items.GOLD_INGOT);
        if (gold >= 0 && actionTimer.tryReset(step())) {
            FarmInventory.click(screen.getScreenHandler().syncId, gold, 0, SlotActionType.PICKUP);
            actionTimer.reset();
            return;
        }
        int emerald = findNamedSlot(screen, "изумруд", Items.EMERALD, Items.PAPER);
        if (emerald >= 0 && actionTimer.tryReset(step())) {
            FarmInventory.click(screen.getScreenHandler().syncId, emerald, 1, SlotActionType.PICKUP);
            state = FarmState.SHOP_CONFIRM;
            actionTimer.reset();
            return;
        }
        if (actionTimer.passed(8000L)) state = FarmState.IDLE;
    }

    private void tickShopConfirm() {
        if (!needsEmeralds()) {
            FarmInventory.closeScreen();
            state = FarmState.IDLE;
            return;
        }
        if (mc.currentScreen instanceof GenericContainerScreen screen && AuctionHelper.isConfirmScreen(screen)) {
            int confirm = AuctionHelper.findConfirmSlot(screen.getScreenHandler());
            if (confirm >= 0 && actionTimer.tryReset(step())) {
                FarmInventory.click(screen.getScreenHandler().syncId, confirm, 0, SlotActionType.PICKUP);
            }
            return;
        }
        if (actionTimer.passed(5000L)) {
            FarmInventory.closeScreen();
            state = FarmState.IDLE;
        }
    }

    private void tickFindTable() {
        craftingTable = findCraftingTable();
        if (craftingTable == null) {
            if (actionTimer.passed(3000L)) state = FarmState.IDLE;
            return;
        }
        if (FarmNavigator.goTo(craftingTable, 2.4)) {
            state = FarmState.OPEN_TABLE;
            actionTimer.reset();
        }
    }

    private void tickOpenTable() {
        if (craftingTable == null) {
            state = FarmState.FIND_TABLE;
            return;
        }
        FarmNavigator.lookAtBlock(craftingTable);
        if (!actionTimer.tryReset(step())) return;
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(craftingTable),
                directionTo(craftingTable),
                craftingTable,
                false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (mc.currentScreen instanceof CraftingScreen) {
            FarmNavigator.stop();
            state = FarmState.CRAFT;
            actionTimer.reset();
        } else if (actionTimer.passed(5000L)) {
            state = FarmState.FIND_TABLE;
        }
    }

    private void tickCraft() {
        if (!(mc.currentScreen instanceof CraftingScreen screen)) {
            state = FarmState.FIND_TABLE;
            return;
        }
        CraftingScreenHandler handler = screen.getScreenHandler();
        int sync = handler.syncId;
        switch (craftPhase) {
            case PLANKS -> placeCraft(handler, sync, this::isLog, 1);
            case STICKS -> {
                placeCraft(handler, sync, this::isPlanks, 2);
                placeCraft(handler, sync, this::isPlanks, 5);
            }
            case ITEM -> {
                if (product.isEnabled("Sword")) {
                    placeItem(handler, sync, Items.EMERALD, 2);
                    placeItem(handler, sync, Items.EMERALD, 5);
                    placeItem(handler, sync, Items.STICK, 8);
                } else {
                    placeItem(handler, sync, Items.EMERALD, 1);
                    placeItem(handler, sync, Items.EMERALD, 2);
                    placeItem(handler, sync, Items.EMERALD, 3);
                    placeItem(handler, sync, Items.STICK, 5);
                    placeItem(handler, sync, Items.STICK, 8);
                }
            }
        }
        state = FarmState.TAKE_CRAFT;
        actionTimer.reset();
    }

    private void tickTakeCraft() {
        if (!(mc.currentScreen instanceof CraftingScreen screen)) {
            state = FarmState.IDLE;
            return;
        }
        if (actionTimer.tryReset(step())) {
            FarmInventory.click(screen.getScreenHandler().syncId, 0, 0, SlotActionType.QUICK_MOVE);
            FarmInventory.closeScreen();
            if (craftPhase == CraftPhase.PLANKS && needsSticks()) craftPhase = CraftPhase.STICKS;
            else if (craftPhase != CraftPhase.ITEM && needsSticks()) craftPhase = CraftPhase.STICKS;
            else craftPhase = CraftPhase.ITEM;
            state = FarmState.IDLE;
        }
    }

    private void tickSellPrep() {
        long price = AuctionHelper.parseMoney(sellPrice.getValue());
        if (price <= 0L || countProduct() <= 0) {
            state = FarmState.IDLE;
            return;
        }
        if (System.currentTimeMillis() - lastSellGui < 1200L) return;
        if (mc.currentScreen != null) FarmInventory.closeScreen();
        FarmInventory.sendCommand("ah sellgui " + price);
        lastSellGui = System.currentTimeMillis();
        state = FarmState.SELL_GUI;
        actionTimer.reset();
    }

    private void tickSellGui() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isSellGui(screen)) {
            if (actionTimer.passed(7000L)) state = FarmState.SELL_PREP;
            return;
        }
        if (!actionTimer.tryReset(80L)) return;
        ScreenHandler handler = screen.getScreenHandler();
        int empty = AuctionHelper.findEmptySellSlot(screen);
        int item = findProductSlot(handler);
        if (empty >= 0 && item >= 0) {
            FarmInventory.click(handler.syncId, item, 0, SlotActionType.PICKUP);
            FarmInventory.click(handler.syncId, empty, 0, SlotActionType.PICKUP);
            return;
        }
        state = FarmState.SELL_CONFIRM;
        actionTimer.reset();
    }

    private void tickSellConfirm() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !AuctionHelper.isSellGui(screen)) {
            if (actionTimer.passed(8000L)) state = FarmState.IDLE;
            return;
        }
        int button = AuctionHelper.findSellButton(screen);
        if (button >= 0 && actionTimer.tryReset(step())) {
            FarmInventory.click(screen.getScreenHandler().syncId, button, 0, SlotActionType.PICKUP);
            state = FarmState.IDLE;
        } else if (actionTimer.passed(8000L)) {
            FarmInventory.closeScreen();
            state = FarmState.IDLE;
        }
    }

    private int findProductSlot(ScreenHandler handler) {
        String target = product.isEnabled("Sword") ? "меч" : "кирк";
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            String name = AuctionHelper.strip(stack.getName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains("изумруд") && name.contains(target)) return i;
            if (product.isEnabled("Sword") && stack.isOf(Items.DIAMOND_SWORD)) return i;
            if (product.isEnabled("Pickaxe") && stack.isOf(Items.DIAMOND_PICKAXE)) return i;
        }
        return -1;
    }

    private int countProduct() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String name = AuctionHelper.strip(stack.getName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains("изумруд") && (name.contains("меч") || name.contains("кирк"))) total += stack.getCount();
        }
        return total;
    }

    private boolean needsEmeralds() {
        return FarmInventory.count(Items.EMERALD) < (product.isEnabled("Sword") ? 2 : 3);
    }

    private boolean needsSticks() {
        return FarmInventory.count(Items.STICK) < (product.isEnabled("Sword") ? 1 : 2);
    }

    private boolean needsWood() {
        return countWood() <= 0 && FarmInventory.count(Items.STICK) < 8;
    }

    private int countWood() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isLog(stack) || isPlanks(stack)) total += stack.getCount();
        }
        return total;
    }

    private boolean isWood(ItemStack stack) {
        return isLog(stack) || isPlanks(stack) || stack.isOf(Items.STICK)
                || AuctionHelper.strip(stack.getName().getString()).toLowerCase(Locale.ROOT).contains("дерев");
    }

    private boolean isLog(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private boolean isPlanks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_planks");
    }

    private BlockPos findCraftingTable() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(origin.add(-5, -3, -5), origin.add(5, 3, 5))) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) continue;
            double dist = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private Direction directionTo(BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private int findItemSlot(GenericContainerScreen screen, Item item) {
        int limit = AuctionHelper.containerSlots(screen);
        for (int i = 0; i < Math.min(limit, screen.getScreenHandler().slots.size()); i++) {
            if (screen.getScreenHandler().getSlot(i).getStack().isOf(item)) return i;
        }
        return -1;
    }

    private int findNamedSlot(GenericContainerScreen screen, String part, Item... items) {
        int limit = AuctionHelper.containerSlots(screen);
        for (int i = 0; i < Math.min(limit, screen.getScreenHandler().slots.size()); i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            for (Item item : items) {
                if (stack.isOf(item)) return i;
            }
            if (AuctionHelper.strip(stack.getName().getString()).toLowerCase(Locale.ROOT).contains(part)) return i;
        }
        return -1;
    }

    private void placeItem(CraftingScreenHandler handler, int sync, Item item, int gridSlot) {
        placeCraft(handler, sync, stack -> stack.isOf(item), gridSlot);
    }

    private void placeCraft(CraftingScreenHandler handler, int sync, Predicate<ItemStack> match, int gridSlot) {
        int source = -1;
        for (int i = 10; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && match.test(stack)) {
                source = i;
                break;
            }
        }
        if (source < 0) return;
        FarmInventory.click(sync, source, 0, SlotActionType.PICKUP);
        FarmInventory.click(sync, gridSlot, 1, SlotActionType.PICKUP);
        FarmInventory.click(sync, source, 0, SlotActionType.PICKUP);
    }

    private long step() {
        return Math.max(50L, Math.round(delay.getValue()));
    }

    private void reset() {
        state = FarmState.IDLE;
        craftPhase = CraftPhase.ITEM;
        craftingTable = null;
        purchaseConfirmed = false;
        listedCount = 0;
        tickTimer.reset();
        actionTimer.reset();
    }

    private enum FarmState {
        IDLE, BUY_WOOD_SEARCH, BUY_WOOD_AH, BUY_WOOD_CONFIRM, SHOP_OPEN, SHOP_EMERALD, SHOP_CONFIRM,
        FIND_TABLE, OPEN_TABLE, CRAFT, TAKE_CRAFT, SELL_PREP, SELL_GUI, SELL_CONFIRM
    }

    private enum CraftPhase {
        PLANKS, STICKS, ITEM
    }
}
