package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.commands.CommandFeedback;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NewFunction(name = "AutoVillageTrade", desc = "Авто-покупка товаров у жителей с складом", category = Category.MISC)
public class AutoVillageTrade extends Function {

    private final TextSetting routePos1 = new TextSetting("Точка 1", "0 64 0", 32);
    private final TextSetting routePos2 = new TextSetting("Точка 2", "0 64 0", 32);
    private final TextSetting chestPosSetting = new TextSetting("Сундук", "0 64 0", 32);
    private final ModeSetting tradeMode = new ModeSetting(
            "Что покупать",
            "Золотой слиток",
            "Золотой слиток", "Редстоун", "Лазурит", "Жемчуг Эндера", "Бутылочка опыта",
            "Стекло", "Бирка", "Стрелы", "Хлеб", "Золотая морковь", "Кварцевый блок", "Седло"
    );
    private final NumberSetting maxPrice = new NumberSetting("Макс. цена", 64.0, 1.0, 64.0, 1.0);
    private final NumberSetting emeraldReserve = new NumberSetting("Запас изумрудов", 64.0, 0.0, 2304.0, 64.0);
    private final NumberSetting villagerRadius = new NumberSetting("Радиус жителя", 4.0, 2.0, 8.0, 0.5);
    private final NumberSetting delayMs = new NumberSetting("Задержка (мс)", 120.0, 50.0, 1000.0, 10.0);
    private final NumberSetting rescanSec = new NumberSetting("КД рескана (сек)", 45.0, 5.0, 300.0, 5.0);
    private final BooleanSetting autoEmeralds = new BooleanSetting("Авто-изумруды", true);

    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer navTimer = new FarmTimer();
    private final FarmTimer navFailTimer = new FarmTimer();

    private final Map<UUID, VillagerOffer> offers = new HashMap<>();
    private final List<BlockPos> route = new ArrayList<>();

    private TradeState state = TradeState.IDLE;
    private UUID targetVillager;
    private int tradeIndex = -1;
    private int routeIndex;
    private int scanCycle;
    private BlockPos navGoal;
    private BlockPos chestPos;
    private int depositStable;
    private int lastItemCount = -1;
    private long lastScanTime;
    private String activeMode;

    public AutoVillageTrade() {
        addSettings(routePos1, routePos2, chestPosSetting, tradeMode, maxPrice, emeraldReserve,
                villagerRadius, delayMs, rescanSec, autoEmeralds);
    }

    @Override
    public void onEnable() {
        rebuildRoute();
        chestPos = parsePos(chestPosSetting);
        activeMode = tradeMode.getActiveMode();
        state = route.isEmpty() ? TradeState.IDLE : TradeState.SCAN_ROUTE;
        targetVillager = null;
        tradeIndex = -1;
        routeIndex = 0;
        scanCycle = 0;
        navGoal = null;
        depositStable = 0;
        lastItemCount = -1;
        lastScanTime = 0L;
        actionTimer.reset();
        navTimer.reset();
        navFailTimer.reset();
        if (route.isEmpty()) {
            msg("Задайте две точки маршрута (Pos1 и Pos2)", Formatting.RED);
        } else {
            msg("Сканирую жителей: " + activeMode, Formatting.GREEN);
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        FarmNavigator.stop();
        RotationManager.reset();
        FarmInventory.closeScreen();
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (!tradeMode.getActiveMode().equals(activeMode)) {
            activeMode = tradeMode.getActiveMode();
            offers.clear();
            beginScan();
            return;
        }

        if (route.isEmpty()) {
            rebuildRoute();
            if (route.isEmpty()) {
                return;
            }
            beginScan();
        }

        if (state == TradeState.PUT_STORAGE && mc.currentScreen instanceof GenericContainerScreen screen) {
            depositItems(screen.getScreenHandler());
            return;
        }

        switch (state) {
            case IDLE -> tickIdle();
            case SCAN_ROUTE -> tickScanRoute();
            case OPEN_SCAN -> openVillager(TradeState.WAIT_SCAN_SCREEN);
            case WAIT_SCAN_SCREEN -> waitMerchant(TradeState.READ_SCAN, TradeState.SCAN_ROUTE);
            case READ_SCAN -> readScanOffers();
            case CLOSE_SCAN -> closeScreen(TradeState.SCAN_ROUTE);
            case MOVE_TO_TRADE -> moveToTrade();
            case OPEN_TRADE -> openVillager(TradeState.WAIT_TRADE_SCREEN);
            case WAIT_TRADE_SCREEN -> waitMerchant(TradeState.BUY_TRADE, TradeState.IDLE);
            case BUY_TRADE -> buyTrade();
            case CLOSE_TRADE -> closeScreen(TradeState.IDLE);
            case MOVE_TO_STORAGE -> moveToStorage();
            case OPEN_STORAGE -> openStorage();
            case WAIT_STORAGE_SCREEN -> waitStorage();
            case WAIT_RESTOCK -> waitRestock();
        }
    }

    private void tickIdle() {
        if (needsStorage()) {
            startStorage();
            return;
        }
        if (autoEmeralds.isEnabled() && FarmInventory.count(Items.EMERALD) < (int) emeraldReserve.getValue()) {
            msg("Мало изумрудов — пополните вручную или отключите «Авто-изумруды»", Formatting.YELLOW);
        }
        VillagerOffer best = pickBestOffer();
        if (best != null) {
            targetVillager = best.uuid;
            tradeIndex = best.tradeIndex;
            state = TradeState.MOVE_TO_TRADE;
            navFailTimer.reset();
            navTimer.reset();
            return;
        }
        if (shouldRescan()) {
            beginScan();
        } else {
            state = TradeState.WAIT_RESTOCK;
            actionTimer.reset();
        }
    }

    private void tickScanRoute() {
        if (routeIndex >= route.size()) {
            lastScanTime = System.currentTimeMillis();
            state = TradeState.IDLE;
            actionTimer.reset();
            msg("Скан завершён, доступных сделок: " + offers.values().stream().filter(o -> o.valid && !o.exhausted).count(), Formatting.GREEN);
            return;
        }
        BlockPos waypoint = route.get(routeIndex);
        if (nearPos(waypoint, 1.2)) {
            VillagerEntity villager = findVillagerNear(waypoint);
            if (villager == null) {
                routeIndex++;
            } else {
                targetVillager = villager.getUuid();
                state = TradeState.OPEN_SCAN;
                actionTimer.reset();
            }
        } else {
            goTo(waypoint, 0);
        }
    }

    private void moveToTrade() {
        VillagerOffer offer = offers.get(targetVillager);
        VillagerEntity villager = findVillager(targetVillager);
        if (offer == null || villager == null || !offer.valid || offer.exhausted) {
            state = TradeState.IDLE;
            return;
        }
        if (FarmInventory.count(Items.EMERALD) < offer.price) {
            state = TradeState.IDLE;
            return;
        }
        offer.lastPos = villager.getBlockPos();
        if (nearEntity(villager, villagerRadius.getValue() + 0.5)) {
            FarmNavigator.stop();
            state = TradeState.OPEN_TRADE;
            actionTimer.reset();
        } else {
            goTo(villager.getBlockPos(), 2);
        }
    }

    private void openVillager(TradeState nextWait) {
        VillagerEntity villager = findVillager(targetVillager);
        if (villager == null || !villager.isAlive()) {
            markExhausted(targetVillager);
            state = nextWait == TradeState.WAIT_SCAN_SCREEN ? TradeState.SCAN_ROUTE : TradeState.IDLE;
            return;
        }
        if (!nearEntity(villager, villagerRadius.getValue() + 1.0)) {
            state = nextWait == TradeState.WAIT_SCAN_SCREEN ? TradeState.SCAN_ROUTE : TradeState.IDLE;
            return;
        }
        rotateTo(villager.getEyePos());
        if (playerRot().differenceValue(RotationManager.getRotationTo(villager.getEyePos())) > 7.0F) {
            return;
        }
        if (actionTimer.tryReset(actionDelayMs())) {
            mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            state = nextWait;
            actionTimer.reset();
        }
    }

    private void waitMerchant(TradeState success, TradeState fail) {
        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler) {
            state = success;
            actionTimer.reset();
        } else if (actionTimer.passed(2500L)) {
            markExhausted(targetVillager);
            state = fail;
            actionTimer.reset();
        }
    }

    private void readScanOffers() {
        if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            state = TradeState.SCAN_ROUTE;
            return;
        }
        storeOffer(targetVillager, handler.getRecipes());
        state = TradeState.CLOSE_SCAN;
        actionTimer.reset();
    }

    private void buyTrade() {
        if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            state = TradeState.IDLE;
            return;
        }
        VillagerOffer offer = storeOffer(targetVillager, handler.getRecipes());
        if (offer == null || !offer.valid || offer.exhausted || offer.tradeIndex < 0 || offer.price > maxPriceInt()) {
            state = TradeState.CLOSE_TRADE;
            actionTimer.reset();
            return;
        }
        if (FarmInventory.count(Items.EMERALD) < offer.price) {
            state = TradeState.CLOSE_TRADE;
            actionTimer.reset();
            return;
        }
        if (!hasSpaceFor(targetItem())) {
            FarmInventory.closeScreen();
            startStorage();
            return;
        }
        if (!actionTimer.tryReset(actionDelayMs())) {
            return;
        }
        tradeIndex = offer.tradeIndex;
        handler.setRecipeIndex(tradeIndex);
        handler.switchTo(tradeIndex);
        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(tradeIndex));
        if (handler.getSlot(2).hasStack() && handler.getSlot(2).getStack().isOf(targetItem())) {
            FarmInventory.click(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE);
            offer.bought += handler.getSlot(2).getStack().getCount();
            offer.spentEmeralds += offer.price;
        }
        actionTimer.reset();
    }

    private void closeScreen(TradeState next) {
        if (actionTimer.tryReset(150L)) {
            FarmInventory.closeScreen();
            state = next;
            actionTimer.reset();
        }
    }

    private void startStorage() {
        chestPos = parsePos(chestPosSetting);
        if (chestPos == null || !isStorage(chestPos)) {
            msg("Укажите сундук для складирования", Formatting.RED);
            state = TradeState.IDLE;
            return;
        }
        FarmInventory.closeScreen();
        depositStable = 0;
        lastItemCount = -1;
        state = TradeState.MOVE_TO_STORAGE;
        navFailTimer.reset();
        navTimer.reset();
    }

    private void moveToStorage() {
        if (chestPos == null || !isStorage(chestPos)) {
            state = TradeState.IDLE;
            return;
        }
        if (nearPos(chestPos, 3.5) && canSeeBlock(chestPos)) {
            FarmNavigator.stop();
            state = TradeState.OPEN_STORAGE;
            actionTimer.reset();
        } else {
            goTo(chestPos, 2);
        }
    }

    private void openStorage() {
        if (chestPos == null) {
            state = TradeState.IDLE;
            return;
        }
        rotateTo(Vec3d.ofCenter(chestPos));
        if (playerRot().differenceValue(RotationManager.getRotationTo(Vec3d.ofCenter(chestPos))) > 4.0F) {
            return;
        }
        if (actionTimer.tryReset(actionDelayMs())) {
            interactBlock(chestPos);
            state = TradeState.WAIT_STORAGE_SCREEN;
            actionTimer.reset();
        }
    }

    private void waitStorage() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            state = TradeState.PUT_STORAGE;
            actionTimer.reset();
        } else if (actionTimer.passed(2500L)) {
            state = TradeState.OPEN_STORAGE;
            actionTimer.reset();
        }
    }

    private void depositItems(GenericContainerScreenHandler handler) {
        int count = FarmInventory.count(targetItem());
        if (count <= 0) {
            FarmInventory.closeScreen();
            state = TradeState.IDLE;
            return;
        }
        if (!actionTimer.tryReset(150L)) {
            return;
        }
        if (lastItemCount == count) {
            depositStable++;
            if (depositStable >= 5) {
                msg("Сундук заполнен или предмет не перекладывается", Formatting.RED);
                FarmInventory.closeScreen();
                state = TradeState.IDLE;
                return;
            }
        } else {
            depositStable = 0;
        }
        lastItemCount = count;
        int chestSlots = handler.getRows() * 9;
        for (int slot = chestSlots; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isOf(targetItem())) {
                FarmInventory.click(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE);
                return;
            }
        }
        FarmInventory.closeScreen();
        state = TradeState.IDLE;
    }

    private void waitRestock() {
        BlockPos center = routeCenter();
        if (center != null && !nearPos(center, 1.5)) {
            goTo(center, 0);
        } else {
            FarmNavigator.stop();
        }
        if (actionTimer.passed(rescanMs())) {
            beginScan();
        }
    }

    private VillagerOffer storeOffer(UUID uuid, TradeOfferList recipes) {
        if (uuid == null || recipes == null) {
            return null;
        }
        VillagerOffer offer = offers.computeIfAbsent(uuid, VillagerOffer::new);
        VillagerEntity villager = findVillager(uuid);
        if (villager != null) {
            offer.entityId = villager.getId();
            offer.lastPos = villager.getBlockPos();
        }
        offer.scanCycle = scanCycle;
        offer.valid = false;
        offer.tradeIndex = -1;
        offer.price = Integer.MAX_VALUE;
        offer.resultCount = 1;
        offer.exhausted = true;
        Item want = targetItem();

        for (int i = 0; i < recipes.size(); i++) {
            TradeOffer trade = recipes.get(i);
            ItemStack sell = trade.getSellItem();
            if (sell.isEmpty() || !sell.isOf(want)) {
                continue;
            }
            int price = emeraldPrice(trade);
            int count = Math.max(1, sell.getCount());
            if (!offer.valid || pricePerItem(price, count) < pricePerItem(offer.price, offer.resultCount)) {
                offer.valid = true;
                offer.tradeIndex = i;
                offer.price = price;
                offer.resultCount = count;
                offer.uses = trade.getUses();
                offer.maxUses = trade.getMaxUses();
                offer.exhausted = trade.isDisabled() || price > maxPriceInt();
            }
        }
        return offer;
    }

    private VillagerOffer pickBestOffer() {
        int emeralds = FarmInventory.count(Items.EMERALD);
        VillagerOffer best = null;
        double bestScore = Double.MAX_VALUE;
        for (VillagerOffer offer : offers.values()) {
            if (!offer.valid || offer.exhausted || offer.tradeIndex < 0 || offer.price > maxPriceInt()) {
                continue;
            }
            if (emeralds < offer.price) {
                continue;
            }
            double score = pricePerItem(offer.price, offer.resultCount) * 1000.0 + distanceTo(offer.lastPos);
            if (score < bestScore) {
                bestScore = score;
                best = offer;
            }
        }
        return best;
    }
    private boolean shouldRescan() {
        if (offers.isEmpty()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanTime >= rescanMs()) {
            return true;
        }
        return offers.values().stream()
                .anyMatch(o -> o.exhausted && now - o.updatedAt >= rescanMs());
    }

    private void beginScan() {
        scanCycle++;
        routeIndex = 0;
        targetVillager = null;
        tradeIndex = -1;
        navGoal = null;
        state = TradeState.SCAN_ROUTE;
        actionTimer.reset();
        navTimer.reset();
        navFailTimer.reset();
        msg("Сканирую жителей: " + activeMode, Formatting.GREEN);
    }

    private void markExhausted(UUID uuid) {
        if (uuid == null) {
            return;
        }
        VillagerOffer offer = offers.computeIfAbsent(uuid, VillagerOffer::new);
        offer.exhausted = true;
        offer.updatedAt = System.currentTimeMillis();
    }

    private VillagerEntity findVillagerNear(BlockPos pos) {
        VillagerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        double radiusSq = villagerRadius.getValue() * villagerRadius.getValue();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof VillagerEntity villager) || !villager.isAlive()) {
                continue;
            }
            VillagerOffer known = offers.get(villager.getUuid());
            if (known != null && known.scanCycle == scanCycle) {
                continue;
            }
            double dist = villager.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (dist <= radiusSq && dist < bestDist) {
                bestDist = dist;
                best = villager;
            }
        }
        return best;
    }

    private VillagerEntity findVillager(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof VillagerEntity villager && uuid.equals(villager.getUuid())) {
                return villager;
            }
        }
        return null;
    }

    private void rebuildRoute() {
        route.clear();
        BlockPos a = parsePos(routePos1);
        BlockPos b = parsePos(routePos2);
        if (a == null || b == null) {
            return;
        }
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) {
            route.add(a);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            BlockPos pos = new BlockPos(
                    a.getX() + Math.round(dx * (i / (float) steps)),
                    a.getY(),
                    a.getZ() + Math.round(dz * (i / (float) steps))
            );
            if (route.isEmpty() || !route.get(route.size() - 1).equals(pos)) {
                route.add(pos);
            }
        }
    }

    private BlockPos routeCenter() {
        BlockPos a = parsePos(routePos1);
        BlockPos b = parsePos(routePos2);
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
    }

    private void goTo(BlockPos pos, int near) {
        if (pos == null) {
            return;
        }
        if (!pos.equals(navGoal)) {
            navGoal = pos.toImmutable();
            navTimer.reset();
            navFailTimer.reset();
        }
        if (navTimer.tryReset(1500L)) {
            FarmNavigator.goTo(navGoal, near > 0 ? near : 1.0);
        }
        if (navFailTimer.passed(20_000L)) {
            navGoal = null;
            navFailTimer.reset();
        }
    }

    private boolean needsStorage() {
        return chestPos != null && FarmInventory.count(targetItem()) > 0 && !hasSpaceFor(targetItem());
    }

    private boolean hasSpaceFor(Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                return true;
            }
            if (stack.isOf(item) && stack.getCount() < stack.getMaxCount()) {
                return true;
            }
        }
        return false;
    }

    private int emeraldPrice(TradeOffer trade) {
        int total = 0;
        ItemStack first = trade.getDisplayedFirstBuyItem();
        ItemStack second = trade.getDisplayedSecondBuyItem();
        if (!first.isEmpty() && first.isOf(Items.EMERALD)) {
            total += first.getCount();
        }
        if (!second.isEmpty() && second.isOf(Items.EMERALD)) {
            total += second.getCount();
        }
        return total <= 0 ? Integer.MAX_VALUE : total;
    }

    private double pricePerItem(int price, int count) {
        return (double) price / Math.max(1, count);
    }

    private double distanceTo(BlockPos pos) {
        if (pos == null || mc.player == null) {
            return Double.MAX_VALUE;
        }
        return mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private boolean nearPos(BlockPos pos, double dist) {
        return distanceTo(pos) <= dist * dist;
    }

    private boolean nearEntity(VillagerEntity villager, double dist) {
        return mc.player.squaredDistanceTo(villager) <= dist * dist;
    }

    private boolean canSeeBlock(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult hit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eye, Vec3d.ofCenter(pos),
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private void interactBlock(BlockPos pos) {
        Direction dir = Direction.getFacing(
                mc.player.getX() - (pos.getX() + 0.5),
                mc.player.getY() - (pos.getY() + 0.5),
                mc.player.getZ() - (pos.getZ() + 0.5)
        );
        Vec3d hit = Vec3d.ofCenter(pos).add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, dir, pos, false));
    }

    private boolean isStorage(BlockPos pos) {
        BlockEntity be = mc.world.getBlockEntity(pos);
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    }

    private Item targetItem() {
        return switch (tradeMode.getActiveMode()) {
            case "Редстоун" -> Items.REDSTONE;
            case "Лазурит" -> Items.LAPIS_LAZULI;
            case "Жемчуг Эндера" -> Items.ENDER_PEARL;
            case "Бутылочка опыта" -> Items.EXPERIENCE_BOTTLE;
            case "Стекло" -> Items.GLASS;
            case "Бирка" -> Items.NAME_TAG;
            case "Стрелы" -> Items.ARROW;
            case "Хлеб" -> Items.BREAD;
            case "Золотая морковь" -> Items.GOLDEN_CARROT;
            case "Кварцевый блок" -> Items.QUARTZ_BLOCK;
            case "Седло" -> Items.SADDLE;
            default -> Items.GOLD_INGOT;
        };
    }

    private int maxPriceInt() {
        return Math.max(1, (int) Math.round(maxPrice.getValue()));
    }

    private long actionDelayMs() {
        return Math.max(50L, Math.round(delayMs.getValue()));
    }

    private long rescanMs() {
        return Math.max(1000L, Math.round(rescanSec.getValue() * 1000.0));
    }

    private void rotateTo(Vec3d point) {
        RotationManager.Rotation rot = RotationManager.getRotationTo(point);
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
    }

    private RotationManager.Rotation playerRot() {
        return new RotationManager.Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private BlockPos parsePos(TextSetting setting) {
        return parsePos(setting.getValue());
    }

    private BlockPos parsePos(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void msg(String text, Formatting color) {
        CommandFeedback.message("[AutoVillageTrade] " + text, color);
    }

    private enum TradeState {
        IDLE, SCAN_ROUTE, OPEN_SCAN, WAIT_SCAN_SCREEN, READ_SCAN, CLOSE_SCAN,
        MOVE_TO_TRADE, OPEN_TRADE, WAIT_TRADE_SCREEN, BUY_TRADE, CLOSE_TRADE,
        MOVE_TO_STORAGE, OPEN_STORAGE, WAIT_STORAGE_SCREEN, PUT_STORAGE, WAIT_RESTOCK
    }

    private static final class VillagerOffer {
        final UUID uuid;
        BlockPos lastPos;
        int entityId;
        int scanCycle = -1;
        boolean valid;
        boolean exhausted = true;
        int tradeIndex = -1;
        int price = Integer.MAX_VALUE;
        int resultCount = 1;
        int uses;
        int maxUses;
        int bought;
        int spentEmeralds;
        long updatedAt;

        VillagerOffer(UUID uuid) {
            this.uuid = uuid;
        }
    }
}
