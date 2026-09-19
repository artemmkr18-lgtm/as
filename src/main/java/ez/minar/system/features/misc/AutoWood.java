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
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

@NewFunction(name = "AutoWood", desc = "Выращивает и рубит дерево 2×2, сброс/продажа", category = Category.MISC)
public class AutoWood extends Function {

    private final ModeSetting treeType = new ModeSetting("Что добывать", "Тёмный дуб", "Тропик дерево", "Тёмный дуб", "Еловое дерево");
    private final ModeSetting outputMode = new ModeSetting("Что делать с деревом", "Ничего", "Ничего", "Продавать на ауке", "Складывать в сундук");
    private final NumberSetting woodThreshold = new NumberSetting("Порог дерева", 64.0, 64.0, 640.0, 64.0);
    private final BooleanSetting repairAxe = new BooleanSetting("Чинить топор", false);
    private final NumberSetting repairThreshold = new NumberSetting("Порог починки топора", 300.0, 100.0, 2031.0, 100.0);
    private final BooleanSetting refillBoneFromChest = new BooleanSetting("Пополнять муку из сундука", true);
    private final NumberSetting chestRadius = new NumberSetting("Радиус поиска сундуков", 12.0, 4.0, 40.0, 1.0);

    private boolean initialized;
    private FarmState farmState = FarmState.SETUP;
    private final List<List<BlockPos>> plots = new ArrayList<>();
    private BlockPos homePos;
    private int plotIndex;
    private int waitPlot = -1;
    private BlockPos breakPos;
    private int tickSkip;
    private SellPhase sellPhase = SellPhase.NONE;
    private int sellAttempts;
    private SupplyTask supplyTask = SupplyTask.NONE;
    private SupplyPhase supplyPhase = SupplyPhase.FIND_CHEST;
    private BlockPos chestPos;
    private boolean returnAfterChest;
    private boolean movedFromChest;
    private int openAttempts;
    private boolean repairing;
    private int repairSlot = -1;
    private int repairDamage = -1;
    private float savedPitch;
    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer navTimer = new FarmTimer();
    private final FarmTimer navFailTimer = new FarmTimer();
    private final FarmTimer chestTimer = new FarmTimer();
    private final FarmTimer waitTimer = new FarmTimer();
    private final FarmTimer sellCooldown = new FarmTimer();
    private final FarmTimer depositCooldown = new FarmTimer();
    private final Queue<Runnable> craftQueue = new ArrayDeque<>();

    public AutoWood() {
        woodThreshold.setVisible(false);
        repairThreshold.setVisible(false);
        outputMode.runnable(() -> woodThreshold.setVisible(outputMode.isEnabled("Ничего")));
        repairAxe.runnable(() -> repairThreshold.setVisible(repairAxe.isEnabled()));
        addSettings(treeType, outputMode, woodThreshold, repairAxe, repairThreshold, refillBoneFromChest, chestRadius);
    }

    @Override
    public void onEnable() {
        initialized = false;
        farmState = FarmState.SETUP;
        plots.clear();
        homePos = null;
        plotIndex = 0;
        waitPlot = -1;
        breakPos = null;
        tickSkip = 0;
        sellPhase = SellPhase.NONE;
        resetSupply();
    }

    @Override
    public void onDisable() {
        finishRepair();
        FarmNavigator.stop();
        resetSupply();
        sellPhase = SellPhase.NONE;
        RotationManager.reset();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        if (supplyTask != SupplyTask.NONE) {
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                if (supplyTask == SupplyTask.DEPOSIT) handleDeposit(screen);
                else handleWithdraw(screen);
                return;
            }
            runSupply();
            return;
        }

        if (sellPhase != SellPhase.NONE) {
            runSell();
            return;
        }

        if (!initialized) {
            setupPlots();
            return;
        }

        if (repairAxe.isEnabled() && findDamagedAxe() >= 0) {
            supplyTask = SupplyTask.REPAIR;
            beginSupply();
            return;
        }
        if (shouldSell()) {
            sellPhase = SellPhase.EQUIP;
            sellAttempts = 0;
            actionTimer.reset();
            return;
        }
        if (shouldDeposit()) {
            supplyTask = SupplyTask.DEPOSIT;
            beginSupply();
            return;
        }

        if (mc.currentScreen != null) return;
        tickSkip++;
        if (farmState == FarmState.WAIT_FELL && !waitTimer.passed(2000L)) return;
        if (farmState != FarmState.WAIT_FELL && tickSkip < 2) return;

        if (farmState == FarmState.FARM) farmTick();
        else if (farmState == FarmState.WAIT_FELL) {
            farmState = FarmState.FARM;
            tickSkip = 0;
        }
    }

    private void setupPlots() {
        plots.clear();
        List<BlockPos> used = new ArrayList<>();
        scanPlot(saplingBlock(), used, false);
        scanPlot(logBlock(), used, true);
        if (plots.isEmpty()) {
            msg("Поставьте саженцы 2×2 рядом и включите модуль", Formatting.RED);
            toggle();
            return;
        }
        homePos = mc.player.getBlockPos();
        initialized = true;
        farmState = FarmState.FARM;
        plotIndex = 0;
        tickSkip = 0;
        msg("Найдено площадок 2×2: " + plots.size(), Formatting.GREEN);
    }

    private void scanPlot(Block marker, List<BlockPos> used, boolean logsOnly) {
        BlockPos origin = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(origin.add(-6, -3, -6), origin.add(6, 3, 6))) {
            if (!is2x2(pos, marker)) continue;
            List<BlockPos> quad = List.of(pos, pos.east(), pos.south(), pos.east().south());
            if (quad.stream().anyMatch(used::contains)) continue;
            if (logsOnly && quad.stream().anyMatch(p -> mc.world.getBlockState(p.down()).getBlock() == marker)) continue;
            if (quad.stream().allMatch(this::canReachPlot)) {
                plots.add(new ArrayList<>(quad));
                used.addAll(quad);
            }
        }
    }

    private void farmTick() {
        for (int i = 0; i < plots.size(); i++) {
            if (hasLogs(i) && chopPlot(i)) return;
        }
        for (List<BlockPos> plot : plots) {
            for (BlockPos pos : plot) {
                BlockState st = mc.world.getBlockState(pos);
                if (st.getBlock() != saplingBlock()) {
                    replantOrClear(pos, st);
                    return;
                }
            }
        }
        boneMealNext();
    }

    private boolean chopPlot(int index) {
        List<BlockPos> plot = plots.get(index);
        BlockPos target = null;
        BlockHitResult hit = null;
        for (BlockPos pos : plot) {
            if (mc.world.getBlockState(pos).getBlock() != logBlock()) continue;
            BlockHitResult h = raycast(pos);
            if (h != null) {
                target = pos;
                hit = h;
                break;
            }
        }
        if (target == null) return false;
        if (!selectAxe()) {
            if (waitTimer.tryReset(15_000L)) msg("Нет топора в хотбаре", Formatting.YELLOW);
            return true;
        }
        RotationManager.Rotation rot = RotationManager.getRotationTo(hit.getPos());
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
        if (playerRot().differenceValue(rot) > 6.0F) return true;
        mc.interactionManager.attackBlock(target, hit.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        waitPlot = index;
        waitTimer.reset();
        farmState = FarmState.WAIT_FELL;
        tickSkip = 0;
        return true;
    }

    private void replantOrClear(BlockPos pos, BlockState state) {
        if (state.getBlock() == leavesBlock()) {
            breakPos = null;
            BlockHitResult hit = raycast(pos);
            if (hit == null) return;
            RotationManager.Rotation rot = RotationManager.getRotationTo(hit.getPos());
            RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
            if (playerRot().differenceValue(rot) > 6.0F) return;
            if (!pos.equals(breakPos)) {
                mc.interactionManager.attackBlock(pos, hit.getSide());
                breakPos = pos;
            } else mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        breakPos = null;
        if (!state.isReplaceable()) {
            if (waitTimer.tryReset(15_000L)) msg("Место посадки занято", Formatting.YELLOW);
            return;
        }
        int slot = ensureHotbar(saplingItem());
        if (slot < 0) {
            if (waitTimer.tryReset(15_000L)) msg("Нет саженцев", Formatting.YELLOW);
            return;
        }
        if (isDirt(pos.down()) && aligned(pos.down())) {
            int prev = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            interact(pos.down());
            mc.player.getInventory().setSelectedSlot(prev);
            tickSkip = 0;
        }
    }

    private void boneMealNext() {
        if (plots.isEmpty()) return;
        for (int step = 0; step < plots.size(); step++) {
            int idx = (plotIndex + step) % plots.size();
            BlockPos sap = null;
            for (BlockPos p : plots.get(idx)) {
                if (mc.world.getBlockState(p).getBlock() == saplingBlock()) {
                    sap = p;
                    break;
                }
            }
            if (sap == null) continue;
            int slot = ensureHotbar(Items.BONE_MEAL);
            if (slot < 0) {
                if (refillBoneFromChest.isEnabled()) {
                    supplyTask = SupplyTask.BONEMEAL;
                    beginSupply();
                } else {
                    msg("Закончилась костная мука", Formatting.RED);
                    toggle();
                }
                return;
            }
            if (isDirt(sap.down()) && aligned(sap)) {
                int prev = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(slot);
                interact(sap);
                mc.player.getInventory().setSelectedSlot(prev);
                plotIndex = (idx + 1) % plots.size();
                tickSkip = 0;
                return;
            }
        }
    }

    private void runSell() {
        if (mc.currentScreen != null && sellPhase != SellPhase.WAIT_RESULT) {
            FarmInventory.closeScreen();
        }
        Item logItem = logItem();
        switch (sellPhase) {
            case EQUIP -> {
                if (FarmInventory.count(logItem) < 64) {
                    sellPhase = SellPhase.NONE;
                    return;
                }
                int best = bestHotbarStack(logItem);
                if (best >= 0) {
                    mc.player.getInventory().setSelectedSlot(best);
                    if (mc.player.getMainHandStack().isOf(logItem)) {
                        sellPhase = SellPhase.COMMAND;
                        actionTimer.reset();
                    }
                } else moveBestLogToHotbar(logItem);
            }
            case COMMAND -> {
                if (!mc.player.getMainHandStack().isOf(logItem)) {
                    sellPhase = SellPhase.EQUIP;
                    return;
                }
                FarmInventory.sendCommand("ah sell auto");
                actionTimer.reset();
                sellPhase = SellPhase.CONFIRM;
            }
            case CONFIRM -> {
                if (actionTimer.tryReset(1000L)) {
                    FarmInventory.sendCommand("ah sell auto confirm");
                    actionTimer.reset();
                    sellPhase = SellPhase.WAIT_RESULT;
                }
            }
            case WAIT_RESULT -> {
                if (!mc.player.getMainHandStack().isOf(logItem)) {
                    sellAttempts = 0;
                    sellPhase = FarmInventory.count(logItem) >= 64 ? SellPhase.EQUIP : SellPhase.NONE;
                    return;
                }
                if (actionTimer.passed(6000L)) {
                    sellAttempts++;
                    if (sellAttempts >= 3) {
                        msg("Не удалось продать на ауке", Formatting.RED);
                        sellCooldown.reset();
                        sellPhase = SellPhase.NONE;
                    } else {
                        sellPhase = SellPhase.COMMAND;
                        actionTimer.reset();
                    }
                }
            }
            default -> sellPhase = SellPhase.NONE;
        }
    }

    private void beginSupply() {
        returnAfterChest = true;
        movedFromChest = false;
        openAttempts = 0;
        chestPos = null;
        repairing = false;
        repairSlot = -1;
        craftQueue.clear();
        actionTimer.reset();
        navTimer.reset();
        navFailTimer.reset();
        chestTimer.reset();
        supplyPhase = switch (supplyTask) {
            case REPAIR -> FarmInventory.count(Items.EXPERIENCE_BOTTLE) > 0 ? SupplyPhase.REPAIRING : SupplyPhase.FIND_CHEST;
            case BONEMEAL -> hasBoneSource() ? SupplyPhase.CRAFTING : SupplyPhase.FIND_CHEST;
            default -> SupplyPhase.FIND_CHEST;
        };
    }

    private void runSupply() {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GenericContainerScreen)) return;
        switch (supplyPhase) {
            case FIND_CHEST -> {
                chestPos = findChest(supplyTask);
                if (chestPos == null) fail("Не найден сундук «" + chestName(supplyTask) + "»");
                else if (nearChest(chestPos) && aligned(chestPos)) supplyPhase = SupplyPhase.OPENING;
                else supplyPhase = SupplyPhase.GOING;
            }
            case GOING -> goChest();
            case OPENING -> {
                if (actionTimer.tryReset(200L)) {
                    interact(chestPos);
                    supplyPhase = SupplyPhase.WAIT_GUI;
                }
            }
            case WAIT_GUI -> {
                if (mc.currentScreen instanceof GenericContainerScreen) return;
                if (actionTimer.passed(2500L)) {
                    openAttempts++;
                    if (openAttempts > 3) fail("Не удалось открыть сундук");
                    else supplyPhase = SupplyPhase.OPENING;
                }
            }
            case CRAFTING -> craftBone();
            case REPAIRING -> repairAxeFlow();
            case RETURNING -> {
                if (homePos != null && mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(homePos)) > 0.7) {
                    if (navTimer.tryReset(1500L)) FarmNavigator.goTo(homePos, 1.0);
                    if (navFailTimer.passed(20_000L)) finishSupply();
                } else finishSupply();
            }
        }
    }

    private void handleWithdraw(GenericContainerScreen screen) {
        GenericContainerScreenHandler h = screen.getScreenHandler();
        int chest = h.slots.size() - 36;
        if (chest <= 0) {
            closeFail("Сундук пуст");
            return;
        }
        if (!chestTimer.tryReset(120L)) return;
        if (withdrawReady()) {
            closeNext();
            return;
        }
        int slot = findWithdraw(h, chest);
        if (slot < 0) {
            if (movedFromChest) closeNext();
            else closeFail("Нет нужных предметов в сундуке");
            return;
        }
        FarmInventory.quickMove(h.syncId, slot);
        movedFromChest = true;
    }

    private void handleDeposit(GenericContainerScreen screen) {
        GenericContainerScreenHandler h = screen.getScreenHandler();
        int chest = h.slots.size() - 36;
        if (chest <= 0) {
            FarmInventory.closeScreen();
            finishSupply();
            return;
        }
        if (!chestTimer.tryReset(120L)) return;
        Item log = logItem();
        for (int i = chest; i < h.slots.size(); i++) {
            if (h.getSlot(i).getStack().isOf(log) && chestHasSpace(h, chest, h.getSlot(i).getStack())) {
                FarmInventory.quickMove(h.syncId, i);
                movedFromChest = true;
                return;
            }
        }
        if (!movedFromChest) {
            depositCooldown.reset();
            msg("Сундук переполнен", Formatting.RED);
        }
        FarmInventory.closeScreen();
        finishSupply();
    }

    private void craftBone() {
        if (mc.currentScreen != null) return;
        if (!craftQueue.isEmpty()) {
            if (chestTimer.tryReset(90L)) craftQueue.poll().run();
            return;
        }
        if (FarmInventory.count(Items.BONE_MEAL) >= 128) {
            supplyPhase = SupplyPhase.RETURNING;
            navTimer.reset();
            navFailTimer.reset();
            return;
        }
        int src = -1;
        for (int i = 9; i <= 44; i++) {
            Item it = mc.player.currentScreenHandler.getSlot(i).getStack().getItem();
            if (it == Items.BONE || it == Items.BONE_BLOCK) {
                src = i;
                break;
            }
        }
        if (src < 0) {
            finishSupply();
            return;
        }
        final int srcSlot = src;
        int sync = mc.player.currentScreenHandler.syncId;
        craftQueue.add(() -> FarmInventory.pickup(sync, srcSlot));
        craftQueue.add(() -> FarmInventory.pickup(sync, 1));
        craftQueue.add(() -> FarmInventory.click(sync, 0, 0, SlotActionType.QUICK_MOVE));
        craftQueue.add(() -> {
            for (int i = 1; i <= 4; i++) {
                if (mc.player.currentScreenHandler.getSlot(i).hasStack()) {
                    FarmInventory.click(sync, i, 0, SlotActionType.QUICK_MOVE);
                }
            }
        });
    }

    private void repairAxeFlow() {
        if (mc.currentScreen != null) return;
        int sync = mc.player.currentScreenHandler.syncId;
        if (!repairing) {
            int slot = findDamagedAxe();
            if (slot < 0) {
                finishSupply();
                return;
            }
            if (FarmInventory.count(Items.EXPERIENCE_BOTTLE) == 0) {
                supplyPhase = SupplyPhase.FIND_CHEST;
                return;
            }
            repairSlot = slot;
            savedPitch = mc.player.getPitch();
            mc.player.getInventory().setSelectedSlot(slot);
            FarmInventory.click(sync, 45, slot, SlotActionType.SWAP);
            if (!selectXp()) {
                FarmInventory.click(sync, 45, slot, SlotActionType.SWAP);
                supplyPhase = SupplyPhase.FIND_CHEST;
                return;
            }
            repairing = true;
            repairDamage = -1;
            actionTimer.reset();
            return;
        }
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty() && off.isDamageable() && off.getDamage() != 0) {
            if (mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE && !selectXp()) {
                finishRepair();
                supplyPhase = SupplyPhase.FIND_CHEST;
                return;
            }
            int dmg = off.getDamage();
            if (repairDamage < 0) repairDamage = dmg;
            else if (dmg < repairDamage) {
                repairDamage = dmg;
                actionTimer.reset();
            } else if (actionTimer.passed(4000L)) {
                finishRepair();
                fail("Топор не чинится");
                return;
            }
            if (actionTimer.tryReset(120L)) {
                mc.player.setPitch(90.0F);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } else {
            finishRepair();
            supplyPhase = SupplyPhase.RETURNING;
            navTimer.reset();
            navFailTimer.reset();
        }
    }

    private void goChest() {
        if (chestPos == null || !isContainer(chestPos)) {
            supplyPhase = SupplyPhase.FIND_CHEST;
            return;
        }
        if (nearChest(chestPos) && aligned(chestPos)) {
            FarmNavigator.stop();
            supplyPhase = SupplyPhase.OPENING;
            return;
        }
        if (navTimer.tryReset(1500L)) FarmNavigator.goTo(chestPos, 2.0);
        if (navFailTimer.passed(15_000L)) fail("Не удалось дойти до сундука");
    }

    private void closeNext() {
        FarmInventory.closeScreen();
        supplyPhase = switch (supplyTask) {
            case REPAIR -> SupplyPhase.REPAIRING;
            case BONEMEAL -> SupplyPhase.CRAFTING;
            default -> SupplyPhase.RETURNING;
        };
        if (supplyPhase == SupplyPhase.RETURNING) {
            navTimer.reset();
            navFailTimer.reset();
        }
        craftQueue.clear();
    }

    private void closeFail(String text) {
        FarmInventory.closeScreen();
        fail(text);
    }

    private void finishSupply() {
        FarmNavigator.stop();
        resetSupply();
        farmState = FarmState.FARM;
        tickSkip = 0;
    }

    private void finishRepair() {
        if (!repairing || mc.player == null) {
            repairing = false;
            return;
        }
        int sync = mc.player.currentScreenHandler.syncId;
        FarmInventory.click(sync, 45, repairSlot, SlotActionType.SWAP);
        if (repairSlot >= 0) mc.player.getInventory().setSelectedSlot(repairSlot);
        mc.player.setPitch(savedPitch);
        repairing = false;
    }

    private void resetSupply() {
        supplyTask = SupplyTask.NONE;
        supplyPhase = SupplyPhase.FIND_CHEST;
        chestPos = null;
        craftQueue.clear();
    }

    private boolean shouldSell() {
        if (!outputMode.isEnabled("Продавать на ауке") || farmState != FarmState.FARM && farmState != FarmState.WAIT_FELL) return false;
        if (!sellCooldown.passed(30_000L)) return false;
        return FarmInventory.count(logItem()) >= (int) woodThreshold.getValue();
    }

    private boolean shouldDeposit() {
        if (!outputMode.isEnabled("Складывать в сундук") || farmState != FarmState.FARM && farmState != FarmState.WAIT_FELL) return false;
        if (!depositCooldown.passed(30_000L)) return false;
        return FarmInventory.count(logItem()) >= (int) woodThreshold.getValue();
    }

    private boolean withdrawReady() {
        return switch (supplyTask) {
            case REPAIR -> FarmInventory.count(Items.EXPERIENCE_BOTTLE) >= 64;
            case BONEMEAL -> FarmInventory.count(Items.BONE_MEAL) + FarmInventory.count(Items.BONE) * 3 >= 128;
            default -> true;
        };
    }

    private int findWithdraw(GenericContainerScreenHandler h, int chest) {
        for (int i = 0; i < chest; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            if (supplyTask == SupplyTask.REPAIR && s.isOf(Items.EXPERIENCE_BOTTLE)) return i;
            if (supplyTask == SupplyTask.BONEMEAL && (s.isOf(Items.BONE_MEAL) || s.isOf(Items.BONE) || s.isOf(Items.BONE_BLOCK))) return i;
        }
        return -1;
    }

    private BlockPos findChest(SupplyTask task) {
        BlockPos origin = homePos != null ? homePos : mc.player.getBlockPos();
        int r = (int) chestRadius.getValue();
        BlockPos best = null;
        double dist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(origin.add(-r, -5, -r), origin.add(r, 5, r))) {
            if (!isContainer(pos) || !labelMatches(pos, task)) continue;
            double d = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos));
            if (d < dist) {
                dist = d;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private boolean labelMatches(BlockPos chest, SupplyTask task) {
        String label = signText(chest).toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return false;
        String[][] rules = switch (task) {
            case REPAIR -> new String[][]{{"опыт"}, {"кост", "мука", "лут", "дерев"}};
            case BONEMEAL -> new String[][]{{"кост", "мука"}, {"опыт", "лут", "дерев"}};
            case DEPOSIT -> new String[][]{{"лут", "дерев"}, {"опыт", "кост", "мука"}};
            default -> new String[0][0];
        };
        boolean ok = false;
        for (String k : rules[0]) if (label.contains(k)) ok = true;
        if (!ok) return false;
        for (String ban : rules[1]) if (label.contains(ban)) return false;
        return true;
    }

    private String signText(BlockPos chest) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : BlockPos.iterate(chest.add(-2, -1, -2), chest.add(2, 2, 2))) {
            BlockEntity be = mc.world.getBlockEntity(p);
            if (be instanceof SignBlockEntity sign) {
                for (boolean filtered : new boolean[]{false, true}) {
                    for (Text t : sign.getFrontText().getMessages(filtered)) {
                        if (t != null && !t.getString().isBlank()) sb.append(t.getString()).append(' ');
                    }
                    for (Text t : sign.getBackText().getMessages(filtered)) {
                        if (t != null && !t.getString().isBlank()) sb.append(t.getString()).append(' ');
                    }
                }
            }
        }
        return sb.toString().replaceAll("§.", "").trim();
    }

    private boolean hasLogs(int index) {
        return plots.get(index).stream().anyMatch(p -> mc.world.getBlockState(p).getBlock() == logBlock());
    }

    private boolean is2x2(BlockPos pos, Block block) {
        return mc.world.getBlockState(pos).getBlock() == block
                && mc.world.getBlockState(pos.east()).getBlock() == block
                && mc.world.getBlockState(pos.south()).getBlock() == block
                && mc.world.getBlockState(pos.east().south()).getBlock() == block;
    }

    private boolean canReachPlot(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= 20.25;
    }

    private boolean nearChest(BlockPos pos) {
        return mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos)) <= 4.5;
    }

    private boolean aligned(BlockPos pos) {
        RotationManager.Rotation rot = RotationManager.getRotationTo(Vec3d.ofCenter(pos));
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
        return playerRot().differenceValue(rot) <= 6.0F;
    }

    private void interact(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private BlockHitResult raycast(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        for (double a : new double[]{0.5, 0.2, 0.8}) {
            for (double b : new double[]{0.5, 0.2, 0.8}) {
                for (double c : new double[]{0.5, 0.2, 0.8}) {
                    Vec3d end = new Vec3d(pos.getX() + a, pos.getY() + b, pos.getZ() + c);
                    BlockHitResult hit = mc.world.raycast(new RaycastContext(
                            eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return hit;
                }
            }
        }
        return null;
    }

    private boolean selectAxe() {
        if (mc.player.getMainHandStack().getItem() instanceof AxeItem) return true;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                mc.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private int findDamagedAxe() {
        if (!repairAxe.isEnabled()) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof AxeItem && s.getMaxDamage() - s.getDamage() <= (int) repairThreshold.getValue()) return i;
        }
        return -1;
    }

    private boolean selectXp() {
        int slot = FarmInventory.findInv(Items.EXPERIENCE_BOTTLE);
        if (slot < 0) return false;
        if (slot >= 36) mc.player.getInventory().setSelectedSlot(slot - 36);
        else FarmInventory.click(mc.player.playerScreenHandler.syncId, slot, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP);
        return true;
    }

    private int ensureHotbar(Item item) {
        int hot = FarmInventory.findHotbar(item);
        if (hot >= 0) return hot;
        int inv = FarmInventory.findInv(item);
        if (inv < 0) return -1;
        int empty = emptyHotbarSlot();
        if (empty < 0) return -1;
        FarmInventory.click(mc.player.currentScreenHandler.syncId, inv, empty, SlotActionType.SWAP);
        return empty;
    }

    private int emptyHotbarSlot() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        for (int i = 0; i < 9; i++) {
            Item it = mc.player.getInventory().getStack(i).getItem();
            if (!(it instanceof AxeItem) && it != saplingItem() && it != Items.BONE_MEAL && it != Items.EXPERIENCE_BOTTLE) return i;
        }
        return -1;
    }

    private int bestHotbarStack(Item item) {
        int slot = -1;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(item) && s.getCount() > count) {
                count = s.getCount();
                slot = i;
            }
        }
        return slot;
    }

    private void moveBestLogToHotbar(Item item) {
        int inv = -1;
        int count = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(item) && s.getCount() > count) {
                count = s.getCount();
                inv = i;
            }
        }
        if (inv >= 0) {
            int hot = emptyHotbarSlot();
            if (hot < 0) hot = 0;
            FarmInventory.click(mc.player.currentScreenHandler.syncId, inv, hot, SlotActionType.SWAP);
        }
    }

    private boolean chestHasSpace(GenericContainerScreenHandler h, int chest, ItemStack stack) {
        for (int i = 0; i < chest; i++) {
            ItemStack o = h.getSlot(i).getStack();
            if (o.isEmpty()) return true;
            if (o.getItem() == stack.getItem() && o.getCount() < o.getMaxCount()) return true;
        }
        return false;
    }

    private boolean isDirt(BlockPos pos) {
        Block b = mc.world.getBlockState(pos).getBlock();
        return b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL;
    }

    private boolean isContainer(BlockPos pos) {
        BlockEntity be = mc.world.getBlockEntity(pos);
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private boolean hasBoneSource() {
        return FarmInventory.count(Items.BONE) > 0 || FarmInventory.count(Items.BONE_BLOCK) > 0;
    }

    private Item saplingItem() {
        return switch (treeType.getActiveMode()) {
            case "Тропик дерево" -> Items.JUNGLE_SAPLING;
            case "Еловое дерево" -> Items.SPRUCE_SAPLING;
            default -> Items.DARK_OAK_SAPLING;
        };
    }

    private Block saplingBlock() {
        return switch (treeType.getActiveMode()) {
            case "Тропик дерево" -> Blocks.JUNGLE_SAPLING;
            case "Еловое дерево" -> Blocks.SPRUCE_SAPLING;
            default -> Blocks.DARK_OAK_SAPLING;
        };
    }

    private Block logBlock() {
        return switch (treeType.getActiveMode()) {
            case "Тропик дерево" -> Blocks.JUNGLE_LOG;
            case "Еловое дерево" -> Blocks.SPRUCE_LOG;
            default -> Blocks.DARK_OAK_LOG;
        };
    }

    private Block leavesBlock() {
        return switch (treeType.getActiveMode()) {
            case "Тропик дерево" -> Blocks.JUNGLE_LEAVES;
            case "Еловое дерево" -> Blocks.SPRUCE_LEAVES;
            default -> Blocks.DARK_OAK_LEAVES;
        };
    }

    private Item logItem() {
        return switch (treeType.getActiveMode()) {
            case "Тропик дерево" -> Items.JUNGLE_LOG;
            case "Еловое дерево" -> Items.SPRUCE_LOG;
            default -> Items.DARK_OAK_LOG;
        };
    }

    private RotationManager.Rotation playerRot() {
        return new RotationManager.Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private String chestName(SupplyTask task) {
        return switch (task) {
            case REPAIR -> "опыт";
            case BONEMEAL -> "костная мука";
            case DEPOSIT -> "лут/дерево";
            default -> "";
        };
    }

    private void fail(String text) {
        msg(text, Formatting.RED);
        FarmNavigator.stop();
        resetSupply();
        toggle();
    }

    private void msg(String text, Formatting color) {
        CommandFeedback.message("[AutoWood] " + text, color);
    }

    private enum FarmState { SETUP, FARM, WAIT_FELL }
    private enum SellPhase { NONE, EQUIP, COMMAND, CONFIRM, WAIT_RESULT }
    private enum SupplyTask { NONE, REPAIR, BONEMEAL, DEPOSIT }
    private enum SupplyPhase { FIND_CHEST, GOING, OPENING, WAIT_GUI, CRAFTING, REPAIRING, RETURNING }
}
