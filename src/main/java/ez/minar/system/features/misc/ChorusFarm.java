package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NewFunction(name = "ChorusFarm", desc = "Авто-ферма плодов хоруса с посадкой и складом", category = Category.MISC)
public class ChorusFarm extends Function {
    public final TextSetting pos1 = new TextSetting("Pos1", "0 64 0", 32);
    public final TextSetting pos2 = new TextSetting("Pos2", "0 64 0", 32);
    public final BooleanSetting shootFruits = new BooleanSetting("Сбивать плоды", true);
    public final NumberSetting harvestHeight = new NumberSetting("Высота для сбора", 6.0, 2.0, 24.0, 1.0);
    public final BooleanSetting autoPlant = new BooleanSetting("Авто-посадка", true);
    public final BooleanSetting chestDump = new BooleanSetting("Склад в сундук", true);

    private final FarmTimer scanTimer = new FarmTimer();
    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer navTimer = new FarmTimer();
    private final FarmTimer chestTimer = new FarmTimer();
    private final FarmTimer breakTimer = new FarmTimer();
    private final FarmTimer bowTimer = new FarmTimer();

    private final List<Target> targets = new ArrayList<>();
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    private final Set<BlockPos> growingRoots = new HashSet<>();

    private FarmPhase phase = FarmPhase.FARM;
    private Target currentTarget;
    private BlockPos chestPos;
    private BlockPos navGoal;
    private BlockPos breakingPos;
    private int chestSyncId = -1;
    private int chestStableChecks;
    private int chestOpenAttempts;
    private int lastDepositCount = -1;
    private int plantAttempts;
    private int bowShots;
    private long chestBlockedUntil;
    private boolean drawingBow;

    public ChorusFarm() {
        addSettings(pos1, pos2, shootFruits, harvestHeight, autoPlant, chestDump);
    }

    @Override
    public void onEnable() {
        phase = FarmPhase.FARM;
        targets.clear();
        skipped.clear();
        growingRoots.clear();
        currentTarget = null;
        chestPos = null;
        navGoal = null;
        breakingPos = null;
        chestSyncId = -1;
        chestStableChecks = 0;
        chestOpenAttempts = 0;
        lastDepositCount = -1;
        plantAttempts = 0;
        bowShots = 0;
        chestBlockedUntil = 0L;
        stopBow();
        scanTimer.reset();
        actionTimer.reset();
        navTimer.reset();
        chestTimer.reset();
        breakTimer.reset();
        bowTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        stopBow();
        FarmNavigator.stop();
        RotationManager.reset();
        FarmInventory.closeScreen();
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (nullCheck.all() || mc.interactionManager == null) {
            return;
        }

        BlockPos corner1 = parsePos(pos1);
        BlockPos corner2 = parsePos(pos2);
        if (corner1 == null || corner2 == null) {
            return;
        }

        if (phase != FarmPhase.FARM && System.currentTimeMillis() > chestBlockedUntil && chestBlockedUntil != 0L) {
            finishChestSession(true);
        }

        switch (phase) {
            case FARM -> tickFarm(corner1, corner2);
            case TO_CHEST -> tickToChest();
            case OPEN_CHEST -> tickOpenChest();
            case DEPOSIT -> tickDeposit();
        }
    }

    private void tickFarm(BlockPos corner1, BlockPos corner2) {
        if (chestDump.isEnabled() && shouldDeposit()) {
            BlockPos chest = findNearestChest(corner1, corner2);
            if (chest != null) {
                chestPos = chest;
                phase = FarmPhase.TO_CHEST;
                navTimer.reset();
                chestOpenAttempts = 0;
                chestBlockedUntil = System.currentTimeMillis() + 30000L;
                stopBow();
                FarmNavigator.stop();
                return;
            }
        }

        if (pickupItems(corner1, corner2)) {
            return;
        }

        if (currentTarget == null || !isTargetValid(currentTarget)) {
            currentTarget = null;
            if (scanTimer.tryReset(300L)) {
                rescan(corner1, corner2);
            }
            currentTarget = pickNearestTarget();
        }

        if (currentTarget == null) {
            stopBow();
            FarmNavigator.stop();
            return;
        }

        switch (currentTarget.type) {
            case SHOOT -> shoot(currentTarget);
            case PLANT -> plant(currentTarget);
            case CLEAR -> clearStem(currentTarget);
        }
    }

    private void shoot(Target target) {
        Vec3d aim = Vec3d.ofCenter(target.blockPos);
        double eyeDist = mc.player.getEyePos().distanceTo(aim);

        if (eyeDist > 28.0) {
            stopBow();
            if (!navigateTo(standNear(target.blockPos, parsePos(pos1), parsePos(pos2)))) {
                skipTarget(target.blockPos, 8000L);
            }
            return;
        }

        if (!ensureArrows()) {
            currentTarget = null;
            return;
        }
        if (ensureBowInHand()) {
            return;
        }

        aimAt(aim);
        if (RotationManager.getCurrentRotation().differenceValue(RotationManager.getRotationTo(aim)) > 2.6F) {
            return;
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof BowItem)) {
            return;
        }

        if (!drawingBow) {
            mc.options.useKey.setPressed(true);
            if (!mc.player.isUsingItem()) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            drawingBow = true;
            bowTimer.reset();
            return;
        }

        if (mc.player.getItemUseTime() < 12) {
            return;
        }

        if (bowTimer.tryReset(80L)) {
            stopBow();
            mc.player.swingHand(Hand.MAIN_HAND);
            bowShots++;
            if (bowShots >= 6) {
                skipTarget(target.blockPos, 6000L);
            }
        }
    }

    private void plant(Target target) {
        stopBow();
        if (!ensureFlowerInHand()) {
            currentTarget = null;
            return;
        }

        BlockHitResult hit = raycastFace(target.blockPos, Direction.UP);
        if (hit == null || mc.player.getEyePos().distanceTo(hit.getPos()) > 4.2) {
            navigateTo(standNear(target.blockPos.up(), parsePos(pos1), parsePos(pos2)));
            return;
        }

        aimAt(hit.getPos());
        if (RotationManager.getCurrentRotation().differenceValue(RotationManager.getRotationTo(hit.getPos())) > 4.0F) {
            return;
        }

        if (!actionTimer.tryReset(90L)) {
            return;
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        plantAttempts++;
        if (plantAttempts >= 4) {
            abortBlock(target.blockPos.up());
            skipTarget(target.blockPos, 8000L);
            plantAttempts = 0;
        }
        currentTarget = null;
    }

    private void clearStem(Target target) {
        stopBow();
        if (!ensureBreakTool()) {
            return;
        }

        double eyeDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(target.blockPos));
        if (eyeDist > 4.6) {
            if (!navigateTo(standNear(target.blockPos, parsePos(pos1), parsePos(pos2)))) {
                skipTarget(target.blockPos, 8000L);
            }
            return;
        }

        BlockHitResult hit = raycastBlock(target.blockPos);
        if (hit == null || mc.player.getEyePos().distanceTo(hit.getPos()) > 4.2) {
            navigateTo(standNear(target.blockPos, parsePos(pos1), parsePos(pos2)));
            return;
        }

        aimAt(hit.getPos());
        if (RotationManager.getCurrentRotation().differenceValue(RotationManager.getRotationTo(hit.getPos())) > 4.0F) {
            return;
        }

        if (!target.blockPos.equals(breakingPos)) {
            if (!actionTimer.tryReset(90L)) {
                return;
            }
            mc.interactionManager.attackBlock(target.blockPos, hit.getSide());
            breakingPos = target.blockPos;
            breakTimer.reset();
        } else {
            if (breakTimer.passed(2000L)) {
                abortBlock(target.blockPos);
                skipTarget(target.blockPos, 8000L);
                breakingPos = null;
                return;
            }
            if (!breakTimer.tryReset(45L)) {
                return;
            }
            mc.interactionManager.updateBlockBreakingProgress(target.blockPos, hit.getSide());
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void tickToChest() {
        if (!isChestBlock(chestPos)) {
            finishChestSession(false);
            return;
        }
        if (navTimer.passed(15000L)) {
            finishChestSession(true);
            return;
        }
        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(chestPos)) <= 4.5) {
            FarmNavigator.stop();
            phase = FarmPhase.OPEN_CHEST;
            actionTimer.reset();
            return;
        }
        FarmNavigator.goTo(chestPos, 1.8);
    }

    private void tickOpenChest() {
        if (!isChestBlock(chestPos)) {
            finishChestSession(false);
            return;
        }
        if (chestOpenAttempts >= 3) {
            finishChestSession(true);
            return;
        }
        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(chestPos)) > 4.6) {
            phase = FarmPhase.TO_CHEST;
            navTimer.reset();
            return;
        }
        aimAt(Vec3d.ofCenter(chestPos));
        if (!actionTimer.tryReset(300L)) {
            return;
        }
        BlockHitResult hit = raycastBlock(chestPos);
        if (hit == null) {
            hit = new BlockHitResult(Vec3d.ofCenter(chestPos), Direction.UP, chestPos, false);
        }
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        chestOpenAttempts++;
        phase = FarmPhase.DEPOSIT;
        chestTimer.reset();
    }

    private void tickDeposit() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)
                || mc.player.currentScreenHandler == null
                || !(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
            if (chestTimer.passed(4000L)) {
                phase = FarmPhase.TO_CHEST;
                navTimer.reset();
            }
            return;
        }

        chestSyncId = handler.syncId;
        if (!chestTimer.tryReset(50L)) {
            return;
        }

        int depositCount = countDepositItems(handler);
        if (lastDepositCount >= 0 && depositCount >= lastDepositCount) {
            chestStableChecks++;
        } else {
            chestStableChecks = 0;
        }
        lastDepositCount = depositCount;

        if (chestStableChecks >= 3) {
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[ChorusFarm] ").formatted(Formatting.RED)
                        .append(Text.literal("Сундук заполнен, освободите место").formatted(Formatting.WHITE)));
            }
            FarmInventory.closeScreen();
            finishChestSession(true);
            return;
        }

        int playerStart = handler.getRows() * 9;
        int slot = findDepositSlot(handler, playerStart);
        if (slot == -1 || depositCount == 0) {
            FarmInventory.closeScreen();
            finishChestSession(false);
            return;
        }

        FarmInventory.click(chestSyncId, slot, 0, SlotActionType.QUICK_MOVE);
    }

    private void rescan(BlockPos corner1, BlockPos corner2) {
        targets.clear();
        growingRoots.clear();
        int[] bounds = clampBounds(corner1, corner2);
        int floorY = Math.min(corner1.getY(), corner2.getY());
        int harvestY = floorY + (int) harvestHeight.getValue();
        List<BlockPos> roots = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterate(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isOf(Blocks.END_STONE) && autoPlant.isEnabled() && FarmInventory.count(Items.CHORUS_FLOWER) > 0) {
                BlockPos above = pos.up();
                BlockState aboveState = mc.world.getBlockState(above);
                if ((aboveState.isAir() || aboveState.isReplaceable()) && !isSkipped(above) && inZone(above, corner1, corner2)) {
                    targets.add(new Target(TargetType.PLANT, pos, Direction.UP));
                }
            }
            if ((state.isOf(Blocks.CHORUS_PLANT) || state.isOf(Blocks.CHORUS_FLOWER))
                    && mc.world.getBlockState(pos.down()).isOf(Blocks.END_STONE)
                    && !isSkipped(pos)) {
                roots.add(pos.toImmutable());
            }
        }

        Set<BlockPos> cluster = collectCluster(roots);
        for (BlockPos root : roots) {
            StemInfo info = analyzeStem(root, cluster);
            if (info.flowers.isEmpty()) {
                if (!growingRoots.contains(root)) {
                    growingRoots.add(root);
                } else if (!isSkipped(root)) {
                    targets.add(new Target(TargetType.CLEAR, root, null));
                }
                continue;
            }
            for (BlockPos flower : info.flowers) {
                if (isSkipped(flower)) {
                    continue;
                }
                if (shootFruits.isEnabled() && flower.getY() - root.getY() + 1 >= harvestHeight.getValue()) {
                    targets.add(new Target(TargetType.SHOOT, flower, null));
                } else if (!shootFruits.isEnabled() || flower.getY() <= harvestY) {
                    targets.add(new Target(TargetType.CLEAR, flower, null));
                }
            }
        }
    }

    private StemInfo analyzeStem(BlockPos root, Set<BlockPos> cluster) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> flowers = new ArrayList<>();
        queue.add(root);
        visited.add(root);

        while (!queue.isEmpty() && visited.size() < 400) {
            BlockPos pos = queue.poll();
            if (mc.world.getBlockState(pos).isOf(Blocks.CHORUS_FLOWER)) {
                flowers.add(pos.toImmutable());
            }
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) {
                    continue;
                }
                BlockPos next = pos.offset(dir);
                if (visited.contains(next) || !cluster.contains(next)) {
                    continue;
                }
                BlockState nextState = mc.world.getBlockState(next);
                if (nextState.isOf(Blocks.CHORUS_PLANT) || nextState.isOf(Blocks.CHORUS_FLOWER)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return new StemInfo(flowers);
    }

    private Set<BlockPos> collectCluster(List<BlockPos> roots) {
        Set<BlockPos> cluster = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos root : roots) {
            if (cluster.add(root)) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty() && cluster.size() < 1600) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) {
                    continue;
                }
                BlockPos next = pos.offset(dir);
                if (cluster.contains(next)) {
                    continue;
                }
                BlockState state = mc.world.getBlockState(next);
                if (state.isOf(Blocks.CHORUS_PLANT) || state.isOf(Blocks.CHORUS_FLOWER)) {
                    cluster.add(next);
                    queue.add(next);
                }
            }
        }
        return cluster;
    }

    private Target pickNearestTarget() {
        Vec3d eyes = mc.player.getEyePos();
        return targets.stream()
                .filter(this::isTargetValid)
                .filter(target -> !isSkipped(target.blockPos))
                .min(Comparator.comparingDouble(target -> {
                    double dist = eyes.squaredDistanceTo(aimPoint(target));
                    return target.type == TargetType.PLANT ? dist + 0.001 : dist;
                }))
                .orElse(null);
    }

    private boolean isTargetValid(Target target) {
        BlockState state = mc.world.getBlockState(target.blockPos);
        return switch (target.type) {
            case SHOOT -> shootFruits.isEnabled() && state.isOf(Blocks.CHORUS_FLOWER);
            case PLANT -> autoPlant.isEnabled()
                    && FarmInventory.count(Items.CHORUS_FLOWER) > 0
                    && state.isOf(Blocks.END_STONE)
                    && (mc.world.getBlockState(target.blockPos.up()).isAir()
                    || mc.world.getBlockState(target.blockPos.up()).isReplaceable());
            case CLEAR -> state.isOf(Blocks.CHORUS_PLANT) || state.isOf(Blocks.CHORUS_FLOWER);
        };
    }

    private boolean pickupItems(BlockPos corner1, BlockPos corner2) {
        if (!hasInventorySpace()) {
            return false;
        }
        ItemEntity item = findChorusItem(corner1, corner2);
        if (item == null) {
            return false;
        }
        double distSq = horizontalDistTo(item);
        if (distSq <= 1.7 || (distSq > 36.0 && currentTarget != null)) {
            return false;
        }
        stopBow();
        FarmNavigator.goTo(standNear(item.getBlockPos(), corner1, corner2), 1.2);
        return true;
    }

    private ItemEntity findChorusItem(BlockPos corner1, BlockPos corner2) {
        int floorY = Math.min(corner1.getY(), corner2.getY());
        Box box = Box.enclosing(corner1, corner2).expand(2.5);
        box = new Box(box.minX, floorY - 4, box.minZ, box.maxX, floorY + 32, box.maxZ);
        return mc.world.getEntitiesByClass(ItemEntity.class, box,
                        entity -> entity.isAlive()
                                && (entity.getStack().isOf(Items.CHORUS_FRUIT) || entity.getStack().isOf(Items.CHORUS_FLOWER)))
                .stream()
                .filter(entity -> !isSkipped(entity.getBlockPos()))
                .min(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .orElse(null);
    }

    private boolean shouldDeposit() {
        if (System.currentTimeMillis() < chestBlockedUntil && chestBlockedUntil != 0L) {
            return false;
        }
        int fruitSlots = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.CHORUS_FRUIT)) {
                fruitSlots++;
            }
        }
        return FarmInventory.freeSlots() == 0 || fruitSlots >= 4
                || (FarmInventory.count(Items.CHORUS_FRUIT) > 0 && FarmInventory.freeSlots() <= 2);
    }

    private int findDepositSlot(GenericContainerScreenHandler handler, int playerStart) {
        boolean reserveFlower = autoPlant.isEnabled();
        boolean reserved = false;
        for (int slot = playerStart; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isOf(Items.CHORUS_FRUIT)) {
                return slot;
            }
            if (stack.isOf(Items.CHORUS_FLOWER)) {
                if (!reserveFlower || reserved || FarmInventory.count(Items.CHORUS_FLOWER) > 128) {
                    return slot;
                }
                reserved = true;
            }
        }
        return -1;
    }

    private int countDepositItems(GenericContainerScreenHandler handler) {
        int total = 0;
        int playerStart = handler.getRows() * 9;
        for (int slot = playerStart; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isOf(Items.CHORUS_FRUIT)) {
                total += stack.getCount();
            } else if (stack.isOf(Items.CHORUS_FLOWER) && FarmInventory.count(Items.CHORUS_FLOWER) > 128) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void finishChestSession(boolean block) {
        chestBlockedUntil = block ? System.currentTimeMillis() + 30000L : 0L;
        chestPos = null;
        chestSyncId = -1;
        chestStableChecks = 0;
        chestOpenAttempts = 0;
        phase = FarmPhase.FARM;
        FarmNavigator.stop();
    }

    private boolean navigateTo(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!pos.equals(navGoal)) {
            navGoal = pos.toImmutable();
            navTimer.reset();
        }
        if (navTimer.passed(5000L)) {
            return false;
        }
        FarmNavigator.goTo(pos, 1.5);
        return true;
    }

    private boolean ensureBowInHand() {
        if (mc.player.getMainHandStack().getItem() instanceof BowItem) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BowItem) {
                mc.player.getInventory().setSelectedSlot(i);
                return false;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BowItem) {
                int screenSlot = i;
                FarmInventory.click(mc.player.currentScreenHandler.syncId, screenSlot,
                        mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP);
                return true;
            }
        }
        currentTarget = null;
        return true;
    }

    private boolean ensureArrows() {
        return FarmInventory.count(Items.ARROW) > 0
                || FarmInventory.count(Items.SPECTRAL_ARROW) > 0
                || FarmInventory.count(Items.TIPPED_ARROW) > 0;
    }

    private boolean ensureFlowerInHand() {
        if (mc.player.getMainHandStack().isOf(Items.CHORUS_FLOWER)) {
            return true;
        }
        if (FarmInventory.selectHotbar(Items.CHORUS_FLOWER)) {
            return true;
        }
        int invSlot = FarmInventory.findInv(Items.CHORUS_FLOWER);
        if (invSlot == -1) {
            return false;
        }
        int screenSlot = invSlot < 9 ? invSlot + 36 : invSlot;
        FarmInventory.click(mc.player.currentScreenHandler.syncId, screenSlot,
                mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP);
        return true;
    }

    private boolean ensureBreakTool() {
        ItemStack hand = mc.player.getMainHandStack();
        if (hand.getItem() instanceof BowItem || hand.isOf(Items.CHORUS_FLOWER)) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()
                        && !(stack.getItem() instanceof BowItem)
                        && !stack.isOf(Items.CHORUS_FLOWER)
                        && !stack.isOf(Items.ARROW)
                        && !stack.isOf(Items.CHORUS_FRUIT)) {
                    mc.player.getInventory().setSelectedSlot(i);
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private void stopBow() {
        if (drawingBow && mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        drawingBow = false;
        bowShots = 0;
        if (mc.player != null && mc.interactionManager != null
                && mc.player.isUsingItem()
                && mc.player.getActiveItem().getItem() instanceof BowItem) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
    }

    private void abortBlock(BlockPos pos) {
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
        breakingPos = null;
    }

    private void skipTarget(BlockPos pos, long ms) {
        skipped.put(pos.toImmutable(), System.currentTimeMillis() + ms);
        currentTarget = null;
        navGoal = null;
        breakingPos = null;
        plantAttempts = 0;
        bowShots = 0;
        stopBow();
        FarmNavigator.stop();
    }

    private boolean isSkipped(BlockPos pos) {
        Long until = skipped.get(pos);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            skipped.remove(pos);
            return false;
        }
        return true;
    }

    private boolean hasInventorySpace() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()
                    || ((stack.isOf(Items.CHORUS_FRUIT) || stack.isOf(Items.CHORUS_FLOWER))
                    && stack.getCount() < stack.getMaxCount())) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findNearestChest(BlockPos corner1, BlockPos corner2) {
        int[] bounds = clampBounds(corner1, corner2);
        Vec3d eyes = mc.player.getEyePos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])) {
            if (!isChestBlock(pos)) {
                continue;
            }
            double dist = eyes.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private boolean isChestBlock(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL);
    }

    private BlockPos standNear(BlockPos pos, BlockPos corner1, BlockPos corner2) {
        int y = mc.player.getBlockY();
        for (int offset : new int[]{0, -1, 1, -2, 2}) {
            BlockPos stand = new BlockPos(pos.getX(), y + offset, pos.getZ());
            if (inZone(stand, corner1, corner2) && isStandable(stand)) {
                return stand;
            }
        }
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private boolean isStandable(BlockPos pos) {
        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState floor = mc.world.getBlockState(pos.down());
        boolean feetFree = feet.isAir() || feet.getCollisionShape(mc.world, pos).isEmpty();
        boolean headFree = head.isAir() || head.getCollisionShape(mc.world, pos.up()).isEmpty();
        boolean solidFloor = !floor.isAir() && !floor.getCollisionShape(mc.world, pos.down()).isEmpty();
        return feetFree && headFree && solidFloor;
    }

    private int[] clampBounds(BlockPos corner1, BlockPos corner2) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY()) - 4;
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.min(corner1.getY(), corner2.getY()) + 32;
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        BlockPos player = mc.player.getBlockPos();
        minX = Math.max(minX, player.getX() - 40);
        minZ = Math.max(minZ, player.getZ() - 40);
        maxX = Math.min(maxX, player.getX() + 40);
        maxZ = Math.min(maxZ, player.getZ() + 40);
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private boolean inZone(BlockPos pos, BlockPos corner1, BlockPos corner2) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private Vec3d aimPoint(Target target) {
        return target.type == TargetType.PLANT
                ? new Vec3d(target.blockPos.getX() + 0.5, target.blockPos.getY() + 1.0, target.blockPos.getZ() + 0.5)
                : Vec3d.ofCenter(target.blockPos);
    }

    private double horizontalDistTo(ItemEntity item) {
        double dx = mc.player.getX() - item.getX();
        double dz = mc.player.getZ() - item.getZ();
        return dx * dx + dz * dz;
    }

    private void aimAt(Vec3d point) {
        FarmNavigator.lookAt(point);
    }

    private BlockHitResult raycastBlock(BlockPos pos) {
        Vec3d eyes = mc.player.getEyePos();
        for (double x : new double[]{0.5, 0.2, 0.8}) {
            for (double y : new double[]{0.5, 0.2, 0.8}) {
                for (double z : new double[]{0.5, 0.2, 0.8}) {
                    Vec3d target = new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    BlockHitResult hit = mc.world.raycast(new RaycastContext(
                            eyes, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                        return hit;
                    }
                }
            }
        }
        return null;
    }

    private BlockHitResult raycastFace(BlockPos block, Direction direction) {
        Vec3d eyes = mc.player.getEyePos();
        for (double h : new double[]{0.5, 0.3, 0.7}) {
            for (double v : new double[]{0.5, 0.3, 0.7}) {
                Vec3d target = switch (direction) {
                    case UP -> new Vec3d(block.getX() + h, block.getY() + 1.0, block.getZ() + v);
                    case DOWN -> new Vec3d(block.getX() + h, block.getY(), block.getZ() + v);
                    case NORTH -> new Vec3d(block.getX() + h, block.getY() + v, block.getZ());
                    case SOUTH -> new Vec3d(block.getX() + h, block.getY() + v, block.getZ() + 1.0);
                    case WEST -> new Vec3d(block.getX(), block.getY() + h, block.getZ() + v);
                    case EAST -> new Vec3d(block.getX() + 1.0, block.getY() + h, block.getZ() + v);
                };
                BlockHitResult hit = mc.world.raycast(new RaycastContext(
                        eyes, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(block)
                        && hit.getSide() == direction) {
                    return hit;
                }
            }
        }
        return null;
    }

    private BlockPos parsePos(TextSetting setting) {
        String[] parts = setting.getValue().trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum FarmPhase {
        FARM, TO_CHEST, OPEN_CHEST, DEPOSIT
    }

    private enum TargetType {
        SHOOT, PLANT, CLEAR
    }

    private static final class Target {
        private final TargetType type;
        private final BlockPos blockPos;
        private final Direction direction;

        private Target(TargetType type, BlockPos blockPos, Direction direction) {
            this.type = type;
            this.blockPos = blockPos;
            this.direction = direction;
        }
    }

    private record StemInfo(List<BlockPos> flowers) {
    }
}
