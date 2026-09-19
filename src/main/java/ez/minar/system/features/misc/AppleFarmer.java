package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.commands.CommandFeedback;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.KeybindSetting;
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
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
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

@NewFunction(name = "AppleFarmer", desc = "Авто-ферма яблок: посадка, костная мука, сбор листвы", category = Category.MISC)
public class AppleFarmer extends Function {

    private final NumberSetting distance = new NumberSetting("Дистанция", 4.5, 3.0, 4.5, 0.1);
    private final BooleanSetting autoChest = new BooleanSetting("Авто-пополнение из сундуков", true);
    private final NumberSetting repairThreshold = new NumberSetting("Чинить при прочности <", 150.0, 20.0, 1000.0, 10.0);
    private final NumberSetting chestRadius = new NumberSetting("Радиус поиска сундуков", 12.0, 4.0, 40.0, 1.0);
    private final NumberSetting unloadSlots = new NumberSetting("Разгрузка при слотах ≤", 3.0, 0.0, 10.0, 1.0);
    private final KeybindSetting setSpotKey = new KeybindSetting("Задать точку", -1);

    private FarmState farmState = FarmState.FIND_SPOT;
    private BlockPos plantPos;
    private BlockPos anchorPos;
    private Direction facing = Direction.NORTH;
    private final List<BlockPos> leaves = new ArrayList<>();
    private BlockPos breakingPos;
    private int tickSkip;
    private int stackTimer;

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
    private final FarmTimer unloadCooldown = new FarmTimer();
    private final Queue<Runnable> craftQueue = new ArrayDeque<>();
    private boolean chestFullWarn;

    public AppleFarmer() {
        repairThreshold.setVisible(false);
        chestRadius.setVisible(false);
        unloadSlots.setVisible(false);
        autoChest.runnable(() -> {
            boolean on = autoChest.isEnabled();
            repairThreshold.setVisible(!on);
            chestRadius.setVisible(!on);
            unloadSlots.setVisible(!on);
        });
        addSettings(distance, autoChest, repairThreshold, chestRadius, unloadSlots, setSpotKey);
    }

    @Override
    public void onEnable() {
        farmState = FarmState.FIND_SPOT;
        plantPos = null;
        leaves.clear();
        breakingPos = null;
        tickSkip = 0;
        stackTimer = 0;
        resetSupply();
    }

    @Override
    public void onDisable() {
        finishRepair();
        FarmNavigator.stop();
        resetSupply();
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (nullCheck.all() || mc.currentScreen != null) return;
        int key = setSpotKey.getKeybind();
        if (key <= 0) return;
        boolean pressed = KeybindSetting.isMouseKey(key)
                ? setSpotKey.consumeMouseClick(mc.getWindow().getHandle())
                : false;
        if (!pressed) return;
        anchorPos = mc.player.getBlockPos();
        facing = mc.player.getHorizontalFacing();
        CommandFeedback.message("Точка фермы: " + anchorPos.toShortString(), Formatting.GREEN);
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        if (autoChest.isEnabled()) {
            if (supplyTask != SupplyTask.NONE && mc.currentScreen instanceof GenericContainerScreen screen) {
                if (supplyTask == SupplyTask.UNLOAD) handleUnload(screen);
                else handleWithdraw(screen);
                return;
            }
            if (supplyTask != SupplyTask.NONE) {
                runSupplyPhase();
                return;
            }
            SupplyTask needed = detectSupply();
            if (needed != SupplyTask.NONE) {
                supplyTask = needed;
                beginSupply();
                return;
            }
        }

        if (mc.currentScreen != null) return;

        stackTimer++;
        if (stackTimer > 4) {
            restackHotbar();
            stackTimer = 0;
        }

        tickSkip++;
        if (farmState != FarmState.BREAKING && tickSkip < 2) return;

        switch (farmState) {
            case FIND_SPOT -> findSpot();
            case PLACE -> placeSapling();
            case BONEMEAL -> boneMeal();
            case SCAN_TREE -> scanTree();
            case BREAKING -> breakLeaves();
        }
    }

    private void findSpot() {
        if (anchorPos == null) {
            anchorPos = mc.player.getBlockPos();
            facing = mc.player.getHorizontalFacing();
        }
        BlockPos front = anchorPos.offset(facing);
        BlockPos plant = anchorPos.offset(facing, 2);
        BlockState plantState = mc.world.getBlockState(plant);
        if (!isPassable(front) || !isPassable(front.up())) {
            msg("Между вами и местом посадки должен быть свободный блок", Formatting.RED);
            toggle();
            return;
        }
        if (plantState.isOf(Blocks.OAK_SAPLING)) {
            plantPos = plant;
            farmState = FarmState.BONEMEAL;
        } else if (isTreeBlock(plantState)) {
            plantPos = plant;
            farmState = FarmState.SCAN_TREE;
        } else if (isDirt(plant.down()) && plantState.isReplaceable()) {
            plantPos = plant;
            farmState = FarmState.PLACE;
        } else {
            msg("Встаньте напротив места посадки (земля через один блок)", Formatting.RED);
            toggle();
        }
        tickSkip = 0;
    }

    private void placeSapling() {
        if (plantPos == null) {
            farmState = FarmState.FIND_SPOT;
            return;
        }
        BlockState state = mc.world.getBlockState(plantPos);
        if (state.isOf(Blocks.OAK_SAPLING)) {
            farmState = FarmState.BONEMEAL;
            return;
        }
        if (!state.isReplaceable()) {
            farmState = FarmState.FIND_SPOT;
            return;
        }
        int slot = hotbarSlot(Items.OAK_SAPLING);
        if (slot < 0) {
            if (autoChest.isEnabled()) {
                farmState = FarmState.FIND_SPOT;
            } else {
                msg("Нет саженцев", Formatting.RED);
                toggle();
            }
            return;
        }
        if (alignedBlock(plantPos.down())) {
            int prev = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            interact(plantPos.down());
            mc.player.getInventory().setSelectedSlot(prev);
            farmState = FarmState.BONEMEAL;
            tickSkip = 0;
        }
    }

    private void boneMeal() {
        if (plantPos == null) return;
        BlockState state = mc.world.getBlockState(plantPos);
        if (isLog(state)) {
            farmState = FarmState.SCAN_TREE;
            return;
        }
        if (state.isReplaceable()) {
            farmState = FarmState.PLACE;
            return;
        }
        if (!state.isOf(Blocks.OAK_SAPLING)) {
            farmState = FarmState.FIND_SPOT;
            return;
        }
        int slot = hotbarSlot(Items.BONE_MEAL);
        if (slot < 0) {
            if (autoChest.isEnabled()) farmState = FarmState.FIND_SPOT;
            else {
                msg("Нет костной муки", Formatting.RED);
                toggle();
            }
            return;
        }
        if (alignedBlock(plantPos)) {
            int prev = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            interact(plantPos);
            mc.player.getInventory().setSelectedSlot(prev);
            tickSkip = 0;
        }
    }

    private void scanTree() {
        leaves.clear();
        if (plantPos == null) {
            farmState = FarmState.PLACE;
            return;
        }
        double reach = Math.min(distance.getValue(), 4.5);
        int r = (int) Math.ceil(reach) + 1;
        BlockPos center = anchorPos != null ? anchorPos : mc.player.getBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 8; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState st = mc.world.getBlockState(pos);
                    if (!isLeafOrLog(st) || !inReach(pos, reach)) continue;
                    if (isLog(st) && !nearTrunk(pos, plantPos)) continue;
                    leaves.add(pos);
                }
            }
        }
        if (leaves.isEmpty()) farmState = FarmState.PLACE;
        else {
            leaves.sort(this::sortBreak);
            breakingPos = null;
            farmState = FarmState.BREAKING;
        }
    }

    private void breakLeaves() {
        leaves.removeIf(p -> !isLeafOrLog(mc.world.getBlockState(p)) || !inReach(p, Math.min(distance.getValue(), 4.5)));
        if (leaves.isEmpty()) {
            farmState = FarmState.PLACE;
            breakingPos = null;
            return;
        }
        BlockPos target = leaves.stream().filter(this::canSee).findFirst().orElse(null);
        if (target == null) {
            farmState = FarmState.SCAN_TREE;
            breakingPos = null;
            tickSkip = 0;
            return;
        }
        BlockHitResult hit = raycastBlock(target);
        if (hit == null) {
            breakingPos = null;
            return;
        }
        BlockState st = mc.world.getBlockState(target);
        if (isLog(st)) selectTool(true);
        else if (isLeaf(st)) selectTool(false);
        RotationManager.Rotation rot = RotationManager.getRotationTo(hit.getPos());
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
        if (playerRot().differenceValue(rot) > 6.0F) return;
        if (!target.equals(breakingPos)) {
            mc.interactionManager.attackBlock(target, hit.getSide());
            breakingPos = target;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(target, hit.getSide());
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private SupplyTask detectSupply() {
        if (shouldUnload()) return SupplyTask.UNLOAD;
        if (repairThreshold.getValue() > 0 && findRepairTool() >= 0) return SupplyTask.REPAIR;
        if (farmState == FarmState.FIND_SPOT || farmState == FarmState.PLACE || farmState == FarmState.BONEMEAL) {
            if (FarmInventory.count(Items.BONE_MEAL) == 0) return SupplyTask.BONEMEAL;
            if (FarmInventory.count(Items.OAK_SAPLING) == 0) return SupplyTask.SAPLING;
        }
        return SupplyTask.NONE;
    }

    private boolean shouldUnload() {
        if (chestFullWarn && !unloadCooldown.passed(30_000L)) return false;
        chestFullWarn = false;
        return FarmInventory.freeSlots() <= (int) unloadSlots.getValue()
                && (FarmInventory.count(Items.OAK_LOG) > 0
                || FarmInventory.count(Items.APPLE) > 0
                || FarmInventory.count(Items.STICK) > 0
                || FarmInventory.count(Items.OAK_SAPLING) > 64);
    }

    private void beginSupply() {
        returnAfterChest = false;
        movedFromChest = false;
        openAttempts = 0;
        chestPos = null;
        repairing = false;
        repairSlot = -1;
        craftQueue.clear();
        breakingPos = null;
        leaves.clear();
        actionTimer.reset();
        navTimer.reset();
        chestTimer.reset();
        switch (supplyTask) {
            case REPAIR -> supplyPhase = FarmInventory.count(Items.EXPERIENCE_BOTTLE) > 0
                    ? SupplyPhase.REPAIRING : SupplyPhase.FIND_CHEST;
            case BONEMEAL -> supplyPhase = hasBoneCraft() ? SupplyPhase.CRAFTING : SupplyPhase.FIND_CHEST;
            default -> supplyPhase = SupplyPhase.FIND_CHEST;
        }
    }

    private void runSupplyPhase() {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GenericContainerScreen)) return;
        switch (supplyPhase) {
            case FIND_CHEST -> findChest();
            case GOING -> goChest();
            case ROTATING -> {
                if (chestPos != null && alignedBlock(chestPos)) supplyPhase = SupplyPhase.OPENING;
            }
            case OPENING -> {
                if (actionTimer.tryReset(200L)) {
                    interact(chestPos);
                    supplyPhase = SupplyPhase.WAIT_GUI;
                    actionTimer.reset();
                }
            }
            case WAIT_GUI -> {
                if (mc.currentScreen instanceof GenericContainerScreen) return;
                if (actionTimer.passed(2500L)) {
                    openAttempts++;
                    if (openAttempts > 3) failSupply("Не удалось открыть сундук");
                    else supplyPhase = SupplyPhase.ROTATING;
                }
            }
            case CRAFTING -> craftBoneMeal();
            case REPAIRING -> repairTool();
            case RETURNING -> returnToSpot();
            case FACING -> finishSupply();
        }
    }

    private void findChest() {
        chestPos = findLabeledChest(supplyTask);
        if (chestPos == null) failSupply("Не найден сундук «" + chestLabel(supplyTask) + "»");
        else if (inReach(chestPos, distance.getValue()) && alignedBlock(chestPos)) {
            supplyPhase = SupplyPhase.ROTATING;
            actionTimer.reset();
        } else {
            returnAfterChest = true;
            supplyPhase = SupplyPhase.GOING;
            navTimer.reset();
            navFailTimer.reset();
        }
    }

    private void goChest() {
        if (chestPos == null || !isContainer(chestPos)) {
            supplyPhase = SupplyPhase.FIND_CHEST;
            return;
        }
        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(chestPos)) <= distance.getValue() && alignedBlock(chestPos)) {
            FarmNavigator.stop();
            supplyPhase = SupplyPhase.ROTATING;
            actionTimer.reset();
            return;
        }
        if (navTimer.tryReset(1500L)) FarmNavigator.goTo(chestPos, 2.0);
        if (navFailTimer.passed(15_000L)) failSupply("Не удалось дойти до сундука");
    }

    private void handleWithdraw(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int chestSlots = handler.slots.size() - 36;
        if (chestSlots <= 0) {
            closeFail("Сундук пуст");
            return;
        }
        if (!chestTimer.tryReset(120L)) return;
        if (supplyReady(supplyTask)) {
            closeAndNext();
            return;
        }
        int slot = findChestItem(handler, chestSlots, supplyTask);
        if (slot < 0) {
            if (movedFromChest) closeAndNext();
            else closeFail("В сундуке нет нужных предметов");
            return;
        }
        FarmInventory.quickMove(handler.syncId, slot);
        movedFromChest = true;
    }

    private void handleUnload(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int chestSlots = handler.slots.size() - 36;
        if (chestSlots <= 0) {
            closeFail("Сундук пуст");
            return;
        }
        if (!chestTimer.tryReset(120L)) return;
        for (int i = chestSlots; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (shouldDump(stack) && hasChestSpace(handler, chestSlots, stack)) {
                FarmInventory.quickMove(handler.syncId, i);
                movedFromChest = true;
                return;
            }
        }
        if (!movedFromChest) {
            chestFullWarn = true;
            unloadCooldown.reset();
            msg("Сундук «яблоки» переполнен", Formatting.RED);
        }
        FarmInventory.closeScreen();
        finishSupply();
    }

    private void craftBoneMeal() {
        if (mc.currentScreen != null) return;
        if (!craftQueue.isEmpty()) {
            if (chestTimer.tryReset(90L)) {
                craftQueue.poll().run();
            }
            return;
        }
        if (FarmInventory.count(Items.BONE_MEAL) >= 128) {
            supplyPhase = SupplyPhase.RETURNING;
            navTimer.reset();
            navFailTimer.reset();
            return;
        }
        int src = craftSourceSlot();
        if (src < 0) {
            supplyPhase = SupplyPhase.RETURNING;
            return;
        }
        int sync = mc.player.currentScreenHandler.syncId;
        craftQueue.add(() -> FarmInventory.pickup(sync, src));
        craftQueue.add(() -> FarmInventory.pickup(sync, 1));
        craftQueue.add(() -> FarmInventory.click(sync, 0, 0, SlotActionType.QUICK_MOVE));
        craftQueue.add(this::clearCraftGrid);
    }

    private void repairTool() {
        if (mc.currentScreen != null) return;
        int sync = mc.player.playerScreenHandler.syncId;
        if (!repairing) {
            int slot = findRepairTool();
            if (slot < 0) {
                finishSupply();
                return;
            }
            if (FarmInventory.count(Items.EXPERIENCE_BOTTLE) == 0) {
                supplyPhase = SupplyPhase.FIND_CHEST;
                return;
            }
            if (!mc.player.getOffHandStack().isEmpty()) {
                int empty = emptyInvSlot();
                if (empty < 0) {
                    failSupply("Освободите офф-хенд для починки");
                    return;
                }
                FarmInventory.pickup(sync, 45);
                FarmInventory.pickup(sync, empty);
                return;
            }
            repairSlot = slot;
            savedPitch = mc.player.getPitch();
            mc.player.getInventory().setSelectedSlot(slot);
            FarmInventory.click(sync, 45, slot, SlotActionType.SWAP);
            if (!selectXpBottle()) {
                FarmInventory.click(sync, 45, slot, SlotActionType.SWAP);
                mc.player.getInventory().setSelectedSlot(slot);
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
            if (mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE && !selectXpBottle()) {
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
                failSupply("Инструмент не чинится");
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

    private void returnToSpot() {
        if (returnAfterChest && anchorPos != null) {
            if (!mc.player.getBlockPos().equals(anchorPos) && mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(anchorPos)) > 0.7) {
                if (navTimer.tryReset(1500L)) FarmNavigator.goTo(anchorPos, 1.0);
                if (navFailTimer.passed(20_000L)) {
                    FarmNavigator.stop();
                    supplyPhase = SupplyPhase.FACING;
                }
            } else {
                FarmNavigator.stop();
                supplyPhase = SupplyPhase.FACING;
                actionTimer.reset();
            }
        } else supplyPhase = SupplyPhase.FACING;
    }

    private void finishSupply() {
        FarmNavigator.stop();
        resetSupply();
        farmState = FarmState.FIND_SPOT;
        tickSkip = 0;
        breakingPos = null;
        leaves.clear();
    }

    private void finishRepair() {
        if (!repairing || mc.player == null) {
            repairing = false;
            return;
        }
        int sync = mc.player.playerScreenHandler.syncId;
        FarmInventory.click(sync, 45, repairSlot, SlotActionType.SWAP);
        if (repairSlot >= 0) mc.player.getInventory().setSelectedSlot(repairSlot);
        mc.player.setPitch(savedPitch);
        if (!mc.player.getOffHandStack().isEmpty()) {
            int empty = emptyInvSlot();
            if (empty >= 0) {
                FarmInventory.pickup(sync, 45);
                FarmInventory.pickup(sync, empty);
            }
        }
        repairing = false;
    }

    private void resetSupply() {
        supplyTask = SupplyTask.NONE;
        supplyPhase = SupplyPhase.FIND_CHEST;
        chestPos = null;
        returnAfterChest = false;
        movedFromChest = false;
        openAttempts = 0;
        craftQueue.clear();
    }

    private void closeAndNext() {
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
        actionTimer.reset();
        chestTimer.reset();
        craftQueue.clear();
    }

    private void closeFail(String message) {
        FarmInventory.closeScreen();
        failSupply(message);
    }

    private void failSupply(String message) {
        msg(message, Formatting.RED);
        FarmNavigator.stop();
        resetSupply();
        toggle();
    }

    private BlockPos findLabeledChest(SupplyTask task) {
        BlockPos origin = anchorPos != null ? anchorPos : mc.player.getBlockPos();
        int r = (int) chestRadius.getValue();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(origin.add(-r, -5, -r), origin.add(r, 5, r))) {
            if (!isContainer(pos) || !matchesChestLabel(pos, task)) continue;
            double d = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos));
            if (d < bestDist) {
                bestDist = d;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private boolean matchesChestLabel(BlockPos chest, SupplyTask task) {
        String label = readSignNear(chest).toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return false;
        String need = switch (task) {
            case REPAIR -> "опыт";
            case BONEMEAL -> "кост";
            case SAPLING, UNLOAD -> "яблок";
            default -> "";
        };
        if (!label.contains(need)) return false;
        for (String ban : bannedLabels(task)) {
            if (label.contains(ban)) return false;
        }
        return true;
    }

    private String[] bannedLabels(SupplyTask task) {
        return switch (task) {
            case REPAIR -> new String[]{"кост", "яблок"};
            case BONEMEAL -> new String[]{"опыт", "яблок"};
            case SAPLING, UNLOAD -> new String[]{"опыт", "кост"};
            default -> new String[0];
        };
    }

    private String readSignNear(BlockPos chest) {
        StringBuilder sb = new StringBuilder();
        // Signs must be adjacent to the chest; also read filtered client text (1.21+).
        for (BlockPos pos : BlockPos.iterate(chest.add(-2, -1, -2), chest.add(2, 2, 2))) {
            BlockEntity be = mc.world.getBlockEntity(pos);
            if (!(be instanceof SignBlockEntity sign)) continue;
            appendSignText(sb, sign.getFrontText());
            appendSignText(sb, sign.getBackText());
        }
        return sb.toString().replaceAll("(?i)[§&].", "").trim();
    }

    private void appendSignText(StringBuilder sb, net.minecraft.block.entity.SignText text) {
        if (text == null) return;
        for (boolean filtered : new boolean[]{false, true}) {
            for (Text line : text.getMessages(filtered)) {
                if (line == null) continue;
                String s = line.getString();
                if (!s.isBlank()) sb.append(s).append(' ');
            }
        }
    }

    private int findChestItem(GenericContainerScreenHandler handler, int chestSlots, SupplyTask task) {
        for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (matchesWithdraw(stack.getItem(), task)) return i;
        }
        return -1;
    }

    private boolean matchesWithdraw(Item item, SupplyTask task) {
        return switch (task) {
            case REPAIR -> item == Items.EXPERIENCE_BOTTLE;
            case BONEMEAL -> item == Items.BONE_MEAL || item == Items.BONE || item == Items.BONE_BLOCK;
            case SAPLING -> item == Items.OAK_SAPLING;
            default -> false;
        };
    }

    private boolean supplyReady(SupplyTask task) {
        return switch (task) {
            case REPAIR -> FarmInventory.count(Items.EXPERIENCE_BOTTLE) >= 64;
            case BONEMEAL -> boneMealUnits() >= 128;
            case SAPLING -> FarmInventory.count(Items.OAK_SAPLING) >= 64;
            default -> true;
        };
    }

    private boolean shouldDump(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.OAK_LOG || item == Items.APPLE || item == Items.STICK) return true;
        return item == Items.OAK_SAPLING && FarmInventory.count(Items.OAK_SAPLING) > 64;
    }

    private boolean hasChestSpace(GenericContainerScreenHandler handler, int chestSlots, ItemStack stack) {
        for (int i = 0; i < chestSlots; i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (other.isEmpty()) return true;
            if (other.getItem() == stack.getItem() && other.getCount() < other.getMaxCount()) return true;
        }
        return false;
    }

    private boolean hasBoneCraft() {
        return FarmInventory.count(Items.BONE) > 0 || FarmInventory.count(Items.BONE_BLOCK) > 0;
    }

    private int boneMealUnits() {
        return FarmInventory.count(Items.BONE_MEAL)
                + FarmInventory.count(Items.BONE) * 3
                + FarmInventory.count(Items.BONE_BLOCK) * 9;
    }

    private int craftSourceSlot() {
        for (int i = 9; i <= 44; i++) {
            Item item = mc.player.currentScreenHandler.getSlot(i).getStack().getItem();
            if (item == Items.BONE || item == Items.BONE_BLOCK) return i;
        }
        return -1;
    }

    private void clearCraftGrid() {
        int sync = mc.player.currentScreenHandler.syncId;
        for (int i = 1; i <= 4; i++) {
            if (mc.player.currentScreenHandler.getSlot(i).hasStack()) {
                FarmInventory.click(sync, i, 0, SlotActionType.QUICK_MOVE);
            }
        }
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            int empty = emptyInvSlot();
            if (empty >= 0) FarmInventory.pickup(sync, empty);
        }
    }

    private int findRepairTool() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            if (!(stack.getItem() instanceof AxeItem) && !(stack.getItem() instanceof HoeItem)) continue;
            if (stack.getMaxDamage() - stack.getDamage() <= (int) repairThreshold.getValue()) return i;
        }
        return -1;
    }

    private boolean selectXpBottle() {
        int slot = FarmInventory.findInv(Items.EXPERIENCE_BOTTLE);
        if (slot < 0) return false;
        if (slot >= 36 && slot <= 44) {
            mc.player.getInventory().setSelectedSlot(slot - 36);
            return true;
        }
        FarmInventory.click(mc.player.playerScreenHandler.syncId, slot, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP);
        return true;
    }

    private int emptyInvSlot() {
        for (int i = 9; i <= 44; i++) {
            if (!mc.player.currentScreenHandler.getSlot(i).hasStack()) return i;
        }
        return -1;
    }

    private void restackHotbar() {
        for (int hot = 0; hot < 9; hot++) {
            ItemStack stack = mc.player.getInventory().getStack(hot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() != Items.BONE_MEAL && stack.getItem() != Items.OAK_SAPLING) continue;
            if (stack.getCount() >= 64) continue;
            int best = -1;
            int bestCount = stack.getCount();
            for (int i = 9; i < 36; i++) {
                ItemStack other = mc.player.getInventory().getStack(i);
                if (other.getItem() != stack.getItem() || other.getCount() <= bestCount) continue;
                best = i;
                bestCount = other.getCount();
                if (bestCount == 64) break;
            }
            if (best >= 0) {
                FarmInventory.click(mc.player.playerScreenHandler.syncId, best, hot, SlotActionType.SWAP);
                return;
            }
        }
    }

    private int hotbarSlot(Item item) {
        int hot = FarmInventory.findHotbar(item);
        if (hot >= 0) return hot;
        int inv = FarmInventory.findInv(item);
        if (inv < 0) return -1;
        int empty = emptyHotbar();
        if (empty < 0) return -1;
        FarmInventory.click(mc.player.playerScreenHandler.syncId, inv, empty, SlotActionType.SWAP);
        return empty;
    }

    private int emptyHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (!(item instanceof AxeItem) && !(item instanceof HoeItem)
                    && item != Items.OAK_SAPLING && item != Items.BONE_MEAL
                    && item != Items.BONE && item != Items.BONE_BLOCK
                    && item != Items.EXPERIENCE_BOTTLE) return i;
        }
        return -1;
    }

    private void selectTool(boolean axe) {
        ItemStack hand = mc.player.getMainHandStack();
        if (axe && hand.getItem() instanceof AxeItem) return;
        if (!axe && hand.getItem() instanceof HoeItem) return;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (axe && s.getItem() instanceof AxeItem) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
            if (!axe && s.getItem() instanceof HoeItem) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private boolean alignedBlock(BlockPos pos) {
        RotationManager.Rotation rot = RotationManager.getRotationTo(facePos(pos));
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
        return playerRot().differenceValue(rot) <= 6.0F;
    }

    private void interact(BlockPos pos) {
        Vec3d hit = facePos(pos);
        BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private Vec3d facePos(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    private BlockHitResult raycastBlock(BlockPos pos) {
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

    private boolean canSee(BlockPos pos) {
        return raycastBlock(pos) != null;
    }

    private boolean inReach(BlockPos pos, double reach) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }

    private boolean nearTrunk(BlockPos pos, BlockPos trunk) {
        return Math.abs(pos.getX() - trunk.getX()) <= 4 && Math.abs(pos.getZ() - trunk.getZ()) <= 4;
    }

    private int sortBreak(BlockPos a, BlockPos b) {
        boolean logA = isLog(mc.world.getBlockState(a));
        boolean logB = isLog(mc.world.getBlockState(b));
        if (logA != logB) return logA ? 1 : -1;
        if (logA) return Integer.compare(a.getY(), b.getY());
        Vec3d eye = mc.player.getEyePos();
        return Double.compare(eye.squaredDistanceTo(Vec3d.ofCenter(a)), eye.squaredDistanceTo(Vec3d.ofCenter(b)));
    }

    private boolean isTreeBlock(BlockState state) {
        return isLog(state) || isLeaf(state);
    }

    private boolean isLeafOrLog(BlockState state) {
        return isLog(state) || isLeaf(state);
    }

    private boolean isLog(BlockState state) {
        return state.isOf(Blocks.OAK_LOG);
    }

    private boolean isLeaf(BlockState state) {
        return state.isOf(Blocks.OAK_LEAVES);
    }

    private boolean isPassable(BlockPos pos) {
        BlockState st = mc.world.getBlockState(pos);
        return st.isAir() || st.isReplaceable();
    }

    private boolean isDirt(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL;
    }

    private boolean isContainer(BlockPos pos) {
        BlockEntity be = mc.world.getBlockEntity(pos);
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private RotationManager.Rotation playerRot() {
        return new RotationManager.Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private String chestLabel(SupplyTask task) {
        return switch (task) {
            case REPAIR -> "опыт";
            case BONEMEAL -> "кости";
            case SAPLING, UNLOAD -> "яблоки";
            default -> "";
        };
    }

    private void msg(String text, Formatting color) {
        CommandFeedback.message("[AppleFarmer] " + text, color);
    }

    private enum FarmState { FIND_SPOT, PLACE, BONEMEAL, SCAN_TREE, BREAKING }
    private enum SupplyTask { NONE, REPAIR, BONEMEAL, SAPLING, UNLOAD }
    private enum SupplyPhase { FIND_CHEST, GOING, ROTATING, OPENING, WAIT_GUI, CRAFTING, REPAIRING, RETURNING, FACING }
}
