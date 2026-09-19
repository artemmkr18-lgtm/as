package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NewFunction(name = "CocoaFarm", desc = "Авто-ферма какао-бобов на тропических брёвнах", category = Category.MISC)
public class CocoaFarm extends Function {
    public final TextSetting pos1 = new TextSetting("Pos1", "0 64 0", 32);
    public final TextSetting pos2 = new TextSetting("Pos2", "0 64 0", 32);
    public final BooleanSetting autoPlant = new BooleanSetting("Авто-посадка", true);
    public final BooleanSetting chestDump = new BooleanSetting("Склад в сундук", true);

    private final FarmTimer scanTimer = new FarmTimer();
    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer navTimer = new FarmTimer();
    private final FarmTimer chestTimer = new FarmTimer();

    private final List<CocoaTarget> targets = new ArrayList<>();
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    private FarmPhase phase = FarmPhase.FARM;
    private CocoaTarget currentTarget;
    private BlockPos chestPos;
    private BlockPos navGoal;
    private int chestSyncId = -1;
    private int chestStableChecks;
    private int chestOpenAttempts;
    private int lastDepositBeanCount = -1;
    private long chestBlockedUntil;

    public CocoaFarm() {
        addSettings(pos1, pos2, autoPlant, chestDump);
    }

    @Override
    public void onEnable() {
        phase = FarmPhase.FARM;
        targets.clear();
        skipped.clear();
        currentTarget = null;
        chestPos = null;
        navGoal = null;
        chestSyncId = -1;
        chestStableChecks = 0;
        chestOpenAttempts = 0;
        lastDepositBeanCount = -1;
        chestBlockedUntil = 0L;
        scanTimer.reset();
        actionTimer.reset();
        navTimer.reset();
        chestTimer.reset();
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
                FarmNavigator.stop();
                return;
            }
        }

        if (pickupBeans(corner1, corner2)) {
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
            FarmNavigator.stop();
            return;
        }

        BlockPos standPos = standPosition(currentTarget, corner1, corner2);
        Vec3d aim = aimPoint(currentTarget);
        double eyeDist = mc.player.getEyePos().distanceTo(aim);
        double horizontalDistSq = horizontalDistSq(standPos);

        if (eyeDist > 4.6 || (horizontalDistSq > 1.44 && eyeDist > 3.6)) {
            if (!navigateTo(standPos)) {
                skipTarget(currentTarget.blockPos, 12000L);
                currentTarget = null;
            }
            return;
        }

        if (currentTarget.type == TargetType.HARVEST) {
            harvest(currentTarget);
        } else {
            plant(currentTarget);
        }
    }

    private void harvest(CocoaTarget target) {
        BlockHitResult hit = raycastBlock(target.blockPos);
        if (hit == null || mc.player.getEyePos().distanceTo(hit.getPos()) > 4.2) {
            navigateTo(standPosition(target, parsePos(pos1), parsePos(pos2)));
            return;
        }

        aimAt(hit.getPos());
        if (RotationManager.getCurrentRotation().differenceValue(RotationManager.getRotationTo(hit.getPos())) > 4.0F) {
            return;
        }

        if (!actionTimer.tryReset(300L)) {
            return;
        }

        mc.interactionManager.updateBlockBreakingProgress(target.blockPos, hit.getSide());
        mc.player.swingHand(Hand.MAIN_HAND);
        currentTarget = null;
    }

    private void plant(CocoaTarget target) {
        if (!ensureBeansInHand()) {
            currentTarget = null;
            return;
        }

        BlockHitResult hit = raycastLogFace(target.blockPos, target.direction);
        if (hit == null || mc.player.getEyePos().distanceTo(hit.getPos()) > 4.2) {
            navigateTo(standPosition(target, parsePos(pos1), parsePos(pos2)));
            return;
        }

        aimAt(hit.getPos());
        if (RotationManager.getCurrentRotation().differenceValue(RotationManager.getRotationTo(hit.getPos())) > 4.0F) {
            return;
        }

        if (!actionTimer.tryReset(300L)) {
            return;
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        currentTarget = null;
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

        int beans = countBeansInPlayerInventory(handler);
        if (lastDepositBeanCount >= 0 && beans >= lastDepositBeanCount) {
            chestStableChecks++;
        } else {
            chestStableChecks = 0;
        }
        lastDepositBeanCount = beans;

        if (chestStableChecks >= 3) {
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[CocoaFarm] ").formatted(Formatting.RED)
                        .append(Text.literal("Сундук заполнен, освободите место").formatted(Formatting.WHITE)));
            }
            FarmInventory.closeScreen();
            finishChestSession(true);
            return;
        }

        int playerStart = handler.getRows() * 9;
        int slot = findPlayerBeanSlot(handler, playerStart);
        if (slot == -1 || beans == 0) {
            FarmInventory.closeScreen();
            finishChestSession(false);
            return;
        }

        FarmInventory.click(chestSyncId, slot, 0, SlotActionType.QUICK_MOVE);
    }

    private int findPlayerBeanSlot(GenericContainerScreenHandler handler, int playerStart) {
        boolean reserveBeans = autoPlant.isEnabled();
        boolean reserved = false;
        for (int slot = playerStart; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isOf(Items.COCOA_BEANS)) {
                if (!reserveBeans || reserved) {
                    return slot;
                }
                reserved = true;
            }
        }
        return -1;
    }

    private int countBeansInPlayerInventory(GenericContainerScreenHandler handler) {
        int total = 0;
        int playerStart = handler.getRows() * 9;
        for (int slot = playerStart; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isOf(Items.COCOA_BEANS)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean shouldDeposit() {
        if (System.currentTimeMillis() < chestBlockedUntil && chestBlockedUntil != 0L) {
            return false;
        }
        return FarmInventory.freeSlots() == 0 || FarmInventory.count(Items.COCOA_BEANS) >= 256;
    }

    private void finishChestSession(boolean block) {
        if (block) {
            chestBlockedUntil = System.currentTimeMillis() + 30000L;
        } else {
            chestBlockedUntil = 0L;
        }
        chestPos = null;
        chestSyncId = -1;
        chestStableChecks = 0;
        chestOpenAttempts = 0;
        phase = FarmPhase.FARM;
        FarmNavigator.stop();
    }

    private boolean pickupBeans(BlockPos corner1, BlockPos corner2) {
        if (!hasInventorySpace()) {
            return false;
        }

        ItemEntity item = findBeanItem(corner1, corner2);
        if (item == null) {
            return false;
        }

        double distSq = horizontalDistTo(item);
        if (distSq > 36.0 && currentTarget != null) {
            return false;
        }
        if (distSq <= 1.7) {
            return false;
        }

        BlockPos goal = standNear(item.getBlockPos(), corner1, corner2);
        FarmNavigator.goTo(goal, 1.2);
        return true;
    }

    private ItemEntity findBeanItem(BlockPos corner1, BlockPos corner2) {
        Box box = Box.enclosing(corner1, corner2).expand(1.0);
        long now = System.currentTimeMillis();
        return mc.world.getEntitiesByClass(ItemEntity.class, box, entity -> entity.isAlive() && entity.getStack().isOf(Items.COCOA_BEANS)).stream()
                .filter(entity -> {
                    Long until = skipped.containsKey(entity.getBlockPos()) ? skipped.get(entity.getBlockPos()) : null;
                    return until == null || now > until;
                })
                .min(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .orElse(null);
    }

    private void rescan(BlockPos corner1, BlockPos corner2) {
        targets.clear();
        int[] bounds = clampBounds(corner1, corner2);

        for (BlockPos pos : BlockPos.iterate(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])) {
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isIn(BlockTags.JUNGLE_LOGS)) {
                continue;
            }

            BlockPos log = pos.toImmutable();
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos cocoaPos = log.offset(direction);
                if (isSkipped(cocoaPos)) {
                    continue;
                }

                BlockState cocoaState = mc.world.getBlockState(cocoaPos);
                if (cocoaState.isOf(Blocks.COCOA) && cocoaState.get(CocoaBlock.AGE) >= 2) {
                    targets.add(new CocoaTarget(TargetType.HARVEST, cocoaPos, direction));
                } else if (autoPlant.isEnabled()
                        && (cocoaState.isAir() || cocoaState.isReplaceable())
                        && FarmInventory.count(Items.COCOA_BEANS) > 0) {
                    targets.add(new CocoaTarget(TargetType.PLANT, log, direction));
                }
            }
        }
    }

    private CocoaTarget pickNearestTarget() {
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

    private boolean isTargetValid(CocoaTarget target) {
        if (target.type == TargetType.HARVEST) {
            BlockState state = mc.world.getBlockState(target.blockPos);
            return state.isOf(Blocks.COCOA) && state.get(CocoaBlock.AGE) >= 2;
        }
        if (!autoPlant.isEnabled() || FarmInventory.count(Items.COCOA_BEANS) <= 0) {
            return false;
        }
        BlockState log = mc.world.getBlockState(target.blockPos);
        if (!log.isIn(BlockTags.JUNGLE_LOGS)) {
            return false;
        }
        BlockState side = mc.world.getBlockState(target.blockPos.offset(target.direction));
        return side.isAir() || side.isReplaceable();
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

    private BlockPos standPosition(CocoaTarget target, BlockPos corner1, BlockPos corner2) {
        BlockPos primary = target.type == TargetType.HARVEST
                ? target.blockPos
                : target.blockPos.offset(target.direction);
        BlockPos secondary = target.type == TargetType.HARVEST
                ? target.blockPos.offset(target.direction)
                : target.blockPos.offset(target.direction, 2);

        int y = mc.player.getBlockY();
        for (BlockPos candidate : new BlockPos[]{primary, secondary}) {
            for (int offset : new int[]{0, -1, 1, -2, 2, -3, -4}) {
                BlockPos stand = new BlockPos(candidate.getX(), y + offset, candidate.getZ());
                if (inZone(stand, corner1, corner2) && isStandable(stand)) {
                    return stand;
                }
            }
        }
        return new BlockPos(primary.getX(), y, primary.getZ());
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
        boolean solidFloor = !floor.isAir() && !floor.isOf(Blocks.COCOA) && !floor.getCollisionShape(mc.world, pos.down()).isEmpty();
        return feetFree && headFree && solidFloor;
    }

    private int[] clampBounds(BlockPos corner1, BlockPos corner2) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockPos player = mc.player.getBlockPos();
        minX = Math.max(minX, player.getX() - 32);
        minZ = Math.max(minZ, player.getZ() - 32);
        maxX = Math.min(maxX, player.getX() + 32);
        maxZ = Math.min(maxZ, player.getZ() + 32);
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private boolean inZone(BlockPos pos, BlockPos corner1, BlockPos corner2) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
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

    private boolean ensureBeansInHand() {
        if (mc.player.getMainHandStack().isOf(Items.COCOA_BEANS)) {
            return true;
        }
        if (FarmInventory.selectHotbar(Items.COCOA_BEANS)) {
            return true;
        }
        int invSlot = FarmInventory.findInv(Items.COCOA_BEANS);
        if (invSlot == -1) {
            return false;
        }
        int screenSlot = invSlot < 9 ? invSlot + 36 : invSlot;
        FarmInventory.click(mc.player.currentScreenHandler.syncId, screenSlot, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP);
        return true;
    }

    private boolean hasInventorySpace() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || (stack.isOf(Items.COCOA_BEANS) && stack.getCount() < stack.getMaxCount())) {
                return true;
            }
        }
        return false;
    }

    private void skipTarget(BlockPos pos, long ms) {
        skipped.put(pos.toImmutable(), System.currentTimeMillis() + ms);
        currentTarget = null;
        navGoal = null;
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

    private Vec3d aimPoint(CocoaTarget target) {
        return target.type == TargetType.HARVEST
                ? Vec3d.ofCenter(target.blockPos)
                : faceCenter(target.blockPos, target.direction);
    }

    private Vec3d faceCenter(BlockPos log, Direction direction) {
        return new Vec3d(
                log.getX() + 0.5 + direction.getOffsetX() * 0.5,
                log.getY() + 0.5 + direction.getOffsetY() * 0.5,
                log.getZ() + 0.5 + direction.getOffsetZ() * 0.5
        );
    }

    private double horizontalDistSq(BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz;
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
                            eyes, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
                    ));
                    if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                        return hit;
                    }
                }
            }
        }
        return null;
    }

    private BlockHitResult raycastLogFace(BlockPos log, Direction direction) {
        Vec3d eyes = mc.player.getEyePos();
        for (double h : new double[]{0.5, 0.3, 0.7}) {
            for (double v : new double[]{0.5, 0.3, 0.7}) {
                Vec3d target = switch (direction) {
                    case NORTH -> new Vec3d(log.getX() + h, log.getY() + v, log.getZ());
                    case SOUTH -> new Vec3d(log.getX() + h, log.getY() + v, log.getZ() + 1.0);
                    case WEST -> new Vec3d(log.getX(), log.getY() + h, log.getZ() + v);
                    case EAST -> new Vec3d(log.getX() + 1.0, log.getY() + h, log.getZ() + v);
                    default -> Vec3d.ofCenter(log);
                };
                BlockHitResult hit = mc.world.raycast(new RaycastContext(
                        eyes, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
                ));
                if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(log)
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
        FARM,
        TO_CHEST,
        OPEN_CHEST,
        DEPOSIT
    }

    private enum TargetType {
        HARVEST,
        PLANT
    }

    private static final class CocoaTarget {
        private final TargetType type;
        private final BlockPos blockPos;
        private final Direction direction;

        private CocoaTarget(TargetType type, BlockPos blockPos, Direction direction) {
            this.type = type;
            this.blockPos = blockPos;
            this.direction = direction;
        }
    }
}
