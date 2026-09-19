package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.commands.CommandFeedback;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.KeybindSetting;
import ez.minar.system.settings.impl.TextSetting;
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
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@NewFunction(name = "AutoMine", desc = "Авто-шахта: добыча алмазов в зоне и сброс в сундук", category = Category.MISC)
public class AutoMine extends Function {

    private static final BlockPos DEFAULT_POS1 = new BlockPos(-55, 93, 30);
    private static final BlockPos DEFAULT_POS2 = new BlockPos(-73, 84, 48);
    private static final List<String> ANARCHY_ROTATION = Arrays.asList(
            "405", "503", "504", "505", "304", "902", "901", "404", "402", "401", "903",
            "201", "202", "203", "204", "205", "206", "207", "208", "209", "210"
    );

    private final TextSetting anarchyCommand = new TextSetting("Анархия для сброса", "903", 16);
    private final KeybindSetting chestBind = new KeybindSetting("Бинд на сундук", -1);
    private final BooleanSetting hideGui = new BooleanSetting("Не отображать экран", false);
    private final TextSetting layoutData = new TextSetting("AutoMineLayoutData", "", 4096);
    private final TextSetting dropChestData = new TextSetting("AutoMineDropChest", "", 64);
    private final TextSetting zonePos1 = new TextSetting("Зона pos1", formatPos(DEFAULT_POS1), 32);
    private final TextSetting zonePos2 = new TextSetting("Зона pos2", formatPos(DEFAULT_POS2), 32);

    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer navTimer = new FarmTimer();
    private final FarmTimer navFailTimer = new FarmTimer();
    private final FarmTimer breakTimer = new FarmTimer();
    private final FarmTimer dropTimer = new FarmTimer();
    private final Queue<Runnable> dropQueue = new ArrayDeque<>();
    private final Map<Integer, LayoutEntry> layout = new HashMap<>();

    private MineState state = MineState.IDLE;
    private int anarchyIndex;
    private BlockPos mineTarget;
    private BlockPos breakingPos;
    private BlockPos navPos;
    private BlockPos dropChest;
    private BlockPos dropNav;
    private boolean sneaking;

    public AutoMine() {
        layoutData.setVisible(false);
        dropChestData.setVisible(false);
        addSettings(anarchyCommand, chestBind, hideGui, zonePos1, zonePos2, layoutData, dropChestData);
    }

    @Override
    public void onEnable() {
        loadLayout();
        loadDropChest();
        state = MineState.IDLE;
        mineTarget = null;
        breakingPos = null;
        navPos = null;
        dropNav = null;
        dropQueue.clear();
        anarchyIndex = 0;
        stopBreaking();
        if (nullCheck.all()) return;
        if (inventoryNeedsDrop()) startDropCycle();
        else if (hasOreInZone()) {
            state = MineState.MINING;
            pickOre();
        } else if (inMineZone()) {
            state = MineState.GOING_TO_MINE;
            navPos = zoneCenter();
            navTimer.reset();
            navFailTimer.reset();
        } else {
            FarmInventory.sendCommand("warp mine");
            state = MineState.WAITING_FOR_TP;
            actionTimer.reset();
        }
    }

    @Override
    public void onDisable() {
        stopBreaking();
        FarmNavigator.stop();
        RotationManager.reset();
        if (sneaking && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            sneaking = false;
        }
        dropQueue.clear();
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (nullCheck.all() || mc.currentScreen != null) return;
        int key = chestBind.getKeybind();
        if (key <= 0) return;
        boolean pressed = KeybindSetting.isMouseKey(key)
                ? chestBind.consumeMouseClick(mc.getWindow().getHandle())
                : false;
        if (!pressed || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        if (!isStorage(pos)) return;
        dropChest = pos.toImmutable();
        dropChestData.setValue(formatPos(dropChest));
        dropNav = standNear(dropChest);
        msg("Сундук для сброса: " + pos.toShortString(), Formatting.GREEN);
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        String text = event.getPacket().content().getString().toLowerCase();
        if (!text.contains("телепорт") && !text.contains("teleport")) return;
        if (state == MineState.WAITING_FOR_TP || state == MineState.TELEPORTING_TO_DROP || state == MineState.CHANGING_ANARCHY) {
            actionTimer.reset();
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        if (state == MineState.MINING && mc.player.isClimbing() && mc.options.attackKey.isPressed()) {
            mc.options.sneakKey.setPressed(true);
            sneaking = true;
        } else if (sneaking) {
            mc.options.sneakKey.setPressed(false);
            sneaking = false;
        }

        if (inventoryNeedsDrop() && state == MineState.MINING) {
            stopBreaking();
            startDropCycle();
            return;
        }

        GenericContainerScreen screen = mc.currentScreen instanceof GenericContainerScreen g ? g : null;
        if (state == MineState.DROPPING && screen != null) {
            processDrop(screen.getScreenHandler());
            return;
        }

        switch (state) {
            case IDLE -> {
                if (hasOreInZone()) {
                    state = MineState.MINING;
                    pickOre();
                }
            }
            case WAITING_FOR_TP -> {
                if (actionTimer.passed(5500L)) enterMineArea();
            }
            case GOING_TO_MINE -> goToMine();
            case MINING -> mineTick();
            case TELEPORTING_TO_DROP -> {
                if (actionTimer.passed(6000L)) beginDropWalk();
            }
            case GOING_TO_DROP_CHEST -> goToDropChest();
            case ROTATING_DROP_CHEST -> rotateDropChest();
            case OPENING_DROP_CHEST -> openDropChest();
            case WAITING_FOR_DROP_GUI -> {
                if (mc.currentScreen instanceof GenericContainerScreen) state = MineState.DROPPING;
                else if (dropTimer.passed(3000L)) state = MineState.OPENING_DROP_CHEST;
            }
            case CHANGING_ANARCHY -> {
                if (actionTimer.passed(2000L)) resumeAfterAnarchy();
            }
            default -> {}
        }
    }

    private void enterMineArea() {
        stopBreaking();
        navPos = standNear(zoneCenter());
        state = MineState.GOING_TO_MINE;
        navTimer.reset();
        navFailTimer.reset();
    }

    private void goToMine() {
        if (inMineZone()) {
            FarmNavigator.stop();
            navPos = null;
            state = MineState.MINING;
            pickOre();
            return;
        }
        if (navPos == null || navFailTimer.passed(10_000L)) navPos = standNear(zoneCenter());
        if (navTimer.tryReset(1500L)) FarmNavigator.goTo(navPos != null ? navPos : zoneCenter(), 2.0);
        if (navFailTimer.passed(45_000L)) switchAnarchy();
    }

    private void mineTick() {
        if (mineTarget == null || isAir(mineTarget)) pickOre();
        if (mineTarget == null) return;
        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(mineTarget)) > 36.0) {
            stopBreaking();
            return;
        }
        BlockHitResult hit = raycast(mineTarget);
        if (hit == null) {
            stopBreaking();
            return;
        }
        RotationManager.Rotation rot = RotationManager.getRotationTo(hit.getPos());
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 65.0F, 65.0F, 65.0F);
        if (playerRot().differenceValue(rot) > 6.0F) {
            mc.options.attackKey.setPressed(false);
            return;
        }
        mc.options.attackKey.setPressed(true);
        if (!mineTarget.equals(breakingPos)) {
            mc.interactionManager.attackBlock(mineTarget, hit.getSide());
            breakingPos = mineTarget;
            breakTimer.reset();
        } else if (breakTimer.tryReset(45L)) {
            mc.interactionManager.updateBlockBreakingProgress(mineTarget, hit.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (actionTimer.tryReset(2000L) && !hasOreInZone()) pickOre();
    }

    private void startDropCycle() {
        String an = parseAnarchy();
        if (an == null) {
            fail("Укажите анархию для сброса");
            return;
        }
        if (dropChest == null) {
            fail("Установите сундук для сброса через бинд");
            return;
        }
        stopBreaking();
        dropQueue.clear();
        FarmInventory.sendCommand("an" + an);
        state = MineState.TELEPORTING_TO_DROP;
        actionTimer.reset();
    }

    private void beginDropWalk() {
        if (dropChest == null) {
            fail("Сундук для сброса не установлен");
            return;
        }
        stopBreaking();
        dropQueue.clear();
        dropNav = standNear(dropChest);
        state = MineState.GOING_TO_DROP_CHEST;
        navTimer.reset();
        navFailTimer.reset();
    }

    private void goToDropChest() {
        if (dropChest == null) {
            fail("Сундук для сброса потерян");
            return;
        }
        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(dropChest)) <= 3.5 && canSeeChest(dropChest)) {
            FarmNavigator.stop();
            state = MineState.ROTATING_DROP_CHEST;
            dropTimer.reset();
            return;
        }
        if (dropNav == null || navFailTimer.passed(10_000L)) dropNav = standNear(dropChest);
        if (navTimer.tryReset(1500L)) FarmNavigator.goTo(dropNav != null ? dropNav : dropChest, 1.5);
        if (navFailTimer.passed(45_000L)) fail("Не удалось дойти до сундука");
    }

    private void rotateDropChest() {
        if (dropChest == null) return;
        RotationManager.Rotation rot = RotationManager.getRotationTo(Vec3d.ofCenter(dropChest));
        RotationManager.rotate(rot, RotationManager.MoveCorrection.SILENT, 35.0F, 35.0F, 35.0F);
        if (playerRot().differenceValue(rot) < 5.0F && dropTimer.tryReset(150L)) state = MineState.OPENING_DROP_CHEST;
    }

    private void openDropChest() {
        swapOffMenuItem();
        if (dropTimer.tryReset(150L)) {
            interactChest(dropChest);
            state = MineState.WAITING_FOR_DROP_GUI;
            dropTimer.reset();
        }
    }

    private void processDrop(GenericContainerScreenHandler handler) {
        if (!dropQueue.isEmpty()) {
            if (dropTimer.tryReset(50L)) dropQueue.poll().run();
            return;
        }
        int chestSlots = handler.slots.size() - 36;
        if (chestSlots <= 0) {
            fail("Открыт не контейнер для сброса");
            return;
        }
        if (layout.size() >= 36) restoreLayout(handler, chestSlots);
        else dumpExcess(handler, chestSlots);
    }

    private void restoreLayout(GenericContainerScreenHandler handler, int chestSlots) {
        for (int i = chestSlots; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            int invIndex = invIndex(i, chestSlots);
            LayoutEntry entry = layout.getOrDefault(invIndex, LayoutEntry.EMPTY);
            if (!slot.hasStack()) {
                if (entry.count > 0) {
                    if (!restoreFromChest(handler, chestSlots, entry, i, slot.getStack().getCount())) {
                        fail("Не удалось восстановить раскладку");
                    }
                    return;
                }
                continue;
            }
            ItemStack stack = slot.getStack();
            if (entry.count <= 0 || !entry.matches(stack)) {
                if (!hasChestSpace(handler, chestSlots, stack)) {
                    fail("Сундук для сброса заполнен");
                    return;
                }
                final int slotId = i; enqueue(() -> FarmInventory.quickMove(handler.syncId, slotId));
                return;
            }
            if (stack.getCount() > entry.count) {
                int move = stack.getCount() - entry.count;
                int dest = mergeSlot(handler, chestSlots, stack, move);
                if (dest < 0) {
                    fail("Сундук для сброса заполнен");
                    return;
                }
                splitStack(handler.syncId, i, dest, entry.count, move);
                return;
            }
            if (stack.getCount() < entry.count) {
                if (!restoreFromChest(handler, chestSlots, entry, i, stack.getCount())) {
                    fail("Не удалось восстановить раскладку");
                }
                return;
            }
        }
        finishDrop();
    }

    private void dumpExcess(GenericContainerScreenHandler handler, int chestSlots) {
        for (int i = chestSlots; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            int keep = keepCount(i - chestSlots, stack);
            int extra = stack.getCount() - keep;
            if (extra <= 0) continue;
            if (keep <= 0) {
                if (!hasChestSpace(handler, chestSlots, stack)) {
                    fail("Сундук для сброса заполнен");
                    return;
                }
                final int slotId = i; enqueue(() -> FarmInventory.quickMove(handler.syncId, slotId));
                return;
            }
            int dest = mergeSlot(handler, chestSlots, stack, extra);
            if (dest < 0) {
                fail("Сундук для сброса заполнен");
                return;
            }
            splitStack(handler.syncId, i, dest, keep, extra);
            return;
        }
        finishDrop();
    }

    private void finishDrop() {
        dropQueue.clear();
        FarmInventory.closeScreen();
        state = MineState.IDLE;
        dropNav = null;
        switchAnarchy();
    }

    private void switchAnarchy() {
        if (anarchyIndex >= ANARCHY_ROTATION.size()) anarchyIndex = 0;
        String current = currentAnarchy();
        String next = ANARCHY_ROTATION.get(anarchyIndex);
        if (current != null && next.equals(current)) {
            anarchyIndex++;
            if (anarchyIndex >= ANARCHY_ROTATION.size()) anarchyIndex = 0;
            next = ANARCHY_ROTATION.get(anarchyIndex);
        }
        FarmInventory.sendCommand("an" + next);
        anarchyIndex++;
        state = MineState.CHANGING_ANARCHY;
        actionTimer.reset();
    }

    private void resumeAfterAnarchy() {
        if (hasOreInZone()) {
            state = MineState.MINING;
            pickOre();
        } else if (inMineZone()) enterMineArea();
        else {
            FarmInventory.sendCommand("warp mine");
            state = MineState.WAITING_FOR_TP;
            actionTimer.reset();
        }
    }

    private void pickOre() {
        BlockPos best = null;
        int minX = Math.min(parsePos(zonePos1, DEFAULT_POS1).getX(), parsePos(zonePos2, DEFAULT_POS2).getX());
        int maxX = Math.max(parsePos(zonePos1, DEFAULT_POS1).getX(), parsePos(zonePos2, DEFAULT_POS2).getX());
        int minY = Math.min(parsePos(zonePos1, DEFAULT_POS1).getY(), parsePos(zonePos2, DEFAULT_POS2).getY());
        int maxY = Math.max(parsePos(zonePos1, DEFAULT_POS1).getY(), parsePos(zonePos2, DEFAULT_POS2).getY());
        int minZ = Math.min(parsePos(zonePos1, DEFAULT_POS1).getZ(), parsePos(zonePos2, DEFAULT_POS2).getZ());
        int maxZ = Math.max(parsePos(zonePos1, DEFAULT_POS1).getZ(), parsePos(zonePos2, DEFAULT_POS2).getZ());
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block b = mc.world.getBlockState(pos).getBlock();
                    if (b != Blocks.DIAMOND_ORE && b != Blocks.DEEPSLATE_DIAMOND_ORE) continue;
                    if (best == null || mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) < mc.player.squaredDistanceTo(Vec3d.ofCenter(best))) {
                        best = pos;
                    }
                }
            }
            if (best != null) break;
        }
        mineTarget = best;
        breakingPos = null;
    }

    private boolean hasOreInZone() {
        return pickOreTarget() != null;
    }

    private BlockPos pickOreTarget() {
        BlockPos prev = mineTarget;
        pickOre();
        BlockPos result = mineTarget;
        mineTarget = prev;
        return result;
    }

    private boolean inMineZone() {
        BlockPos p = mc.player.getBlockPos();
        int minX = Math.min(parsePos(zonePos1, DEFAULT_POS1).getX(), parsePos(zonePos2, DEFAULT_POS2).getX()) - 4;
        int maxX = Math.max(parsePos(zonePos1, DEFAULT_POS1).getX(), parsePos(zonePos2, DEFAULT_POS2).getX()) + 4;
        int minY = Math.min(parsePos(zonePos1, DEFAULT_POS1).getY(), parsePos(zonePos2, DEFAULT_POS2).getY()) - 6;
        int maxY = Math.max(parsePos(zonePos1, DEFAULT_POS1).getY(), parsePos(zonePos2, DEFAULT_POS2).getY()) + 8;
        int minZ = Math.min(parsePos(zonePos1, DEFAULT_POS1).getZ(), parsePos(zonePos2, DEFAULT_POS2).getZ()) - 4;
        int maxZ = Math.max(parsePos(zonePos1, DEFAULT_POS1).getZ(), parsePos(zonePos2, DEFAULT_POS2).getZ()) + 4;
        return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    private BlockPos zoneCenter() {
        BlockPos a = parsePos(zonePos1, DEFAULT_POS1);
        BlockPos b = parsePos(zonePos2, DEFAULT_POS2);
        return new BlockPos((a.getX() + b.getX()) / 2, Math.min(a.getY(), b.getY()), (a.getZ() + b.getZ()) / 2);
    }

    private BlockPos standNear(BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int y = -1; y <= 1; y++) {
            for (int r = 1; r <= 3; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) == r) candidates.add(target.add(x, y, z));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(p))));
        for (BlockPos pos : candidates) {
            if (isStandable(pos) && canSeeChestFrom(pos, target)) return pos;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean inventoryNeedsDrop() {
        if (mc.player.getInventory().getEmptySlot() != -1) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getCount() > keepCount(i, stack)) return true;
        }
        return false;
    }

    private int keepCount(int slot, ItemStack stack) {
        LayoutEntry entry = layout.get(slot);
        if (entry != null) return entry.matches(stack) ? Math.min(entry.count, stack.getCount()) : 0;
        return 1;
    }

    private boolean restoreFromChest(GenericContainerScreenHandler handler, int chestSlots, LayoutEntry entry, int targetSlot, int have) {
        int src = chestSlot(handler, chestSlots, entry);
        if (src < 0) return false;
        int take = Math.min(entry.count - have, handler.getSlot(src).getStack().getCount());
        transferPieces(handler.syncId, src, targetSlot, handler.getSlot(src).getStack().getCount(), take);
        return true;
    }

    private int chestSlot(GenericContainerScreenHandler handler, int chestSlots, LayoutEntry entry) {
        for (int i = 0; i < chestSlots; i++) {
            if (entry.matches(handler.getSlot(i).getStack())) return i;
        }
        return -1;
    }

    private int mergeSlot(GenericContainerScreenHandler handler, int chestSlots, ItemStack stack, int amount) {
        int empty = -1;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (other.isEmpty()) {
                if (empty < 0) empty = i;
            } else if (ItemStack.areItemsAndComponentsEqual(stack, other) && other.getCount() + amount <= other.getMaxCount()) {
                return i;
            }
        }
        return empty;
    }

    private boolean hasChestSpace(GenericContainerScreenHandler handler, int chestSlots, ItemStack stack) {
        for (int i = 0; i < chestSlots; i++) {
            ItemStack other = handler.getSlot(i).getStack();
            if (other.isEmpty()) return true;
            if (ItemStack.areItemsAndComponentsEqual(stack, other) && other.getCount() < other.getMaxCount()) return true;
        }
        return false;
    }

    private void splitStack(int syncId, int from, int to, int keep, int move) {
        enqueue(() -> FarmInventory.pickup(syncId, from));
        if (keep <= move) {
            for (int i = 0; i < keep; i++) enqueue(() -> FarmInventory.click(syncId, from, 1, SlotActionType.PICKUP));
            enqueue(() -> FarmInventory.pickup(syncId, to));
        } else {
            for (int i = 0; i < move; i++) enqueue(() -> FarmInventory.click(syncId, to, 1, SlotActionType.PICKUP));
            enqueue(() -> FarmInventory.pickup(syncId, from));
        }
    }

    private void transferPieces(int syncId, int from, int to, int srcCount, int take) {
        enqueue(() -> FarmInventory.pickup(syncId, from));
        if (take >= srcCount) enqueue(() -> FarmInventory.pickup(syncId, to));
        else if (take <= srcCount / 2) {
            for (int i = 0; i < take; i++) enqueue(() -> FarmInventory.click(syncId, to, 1, SlotActionType.PICKUP));
            enqueue(() -> FarmInventory.pickup(syncId, from));
        } else {
            int leave = srcCount - take;
            for (int i = 0; i < leave; i++) enqueue(() -> FarmInventory.click(syncId, from, 1, SlotActionType.PICKUP));
            enqueue(() -> FarmInventory.pickup(syncId, to));
        }
    }

    private int invIndex(int slot, int chestSlots) {
        int rel = slot - chestSlots;
        return rel >= 27 ? rel - 27 : rel + 9;
    }

    private void loadLayout() {
        layout.clear();
        String raw = layoutData.getValue();
        if (raw == null || raw.isBlank()) return;
        Base64.Decoder decoder = Base64.getDecoder();
        String[] parts = raw.split(";", -1);
        for (int i = 0; i < Math.min(36, parts.length); i++) {
            String[] pair = parts[i].split(",", 2);
            if (pair.length != 2) continue;
            try {
                int count = Integer.parseInt(pair[0]);
                String key = new String(decoder.decode(pair[1]), StandardCharsets.UTF_8);
                layout.put(i, new LayoutEntry(key, count));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void loadDropChest() {
        dropChest = parsePosOptional(dropChestData.getValue());
        if (dropChest != null) dropNav = standNear(dropChest);
    }

    private String parseAnarchy() {
        if (anarchyCommand.getValue() == null) return null;
        String s = anarchyCommand.getValue().trim().toLowerCase()
                .replace("/", "").replace("anarchy", "").replace("анархия", "")
                .replace("an", "").replace("аn", "").replace(" ", "");
        return s.isBlank() ? null : s;
    }

    private String currentAnarchy() {
        return parseAnarchy();
    }

    private void swapOffMenuItem() {
        int hot = mc.player.getInventory().getSelectedSlot();
        ItemStack hand = mc.player.getInventory().getStack(hot);
        if (hand.getItem() == Items.TRIPWIRE_HOOK || hand.getName().getString().contains("[★]")) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty() || (s.getItem() != Items.TRIPWIRE_HOOK && !s.getName().getString().contains("[★]"))) {
                    mc.player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
        }
    }

    private void interactChest(BlockPos pos) {
        mc.player.swingHand(Hand.MAIN_HAND);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
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
        return hasAdjacentAir(pos) ? new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false) : null;
    }

    private boolean hasAdjacentAir(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState st = mc.world.getBlockState(pos.offset(dir));
            if (st.isAir() || st.getBlock() == Blocks.CAVE_AIR || st.getBlock() == Blocks.VOID_AIR) return true;
        }
        return false;
    }

    private boolean isAir(BlockPos pos) {
        Block b = mc.world.getBlockState(pos).getBlock();
        return b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR;
    }

    private boolean isStandable(BlockPos pos) {
        BlockState floor = mc.world.getBlockState(pos);
        BlockState body = mc.world.getBlockState(pos.up());
        BlockState head = mc.world.getBlockState(pos.up(2));
        return floor.getCollisionShape(mc.world, pos).isEmpty()
                && body.getCollisionShape(mc.world, pos.up()).isEmpty()
                && !mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty()
                && head.getCollisionShape(mc.world, pos.up(2)).isEmpty();
    }

    private boolean canSeeChest(BlockPos chest) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, Vec3d.ofCenter(chest), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(chest);
    }

    private boolean canSeeChestFrom(BlockPos stand, BlockPos chest) {
        Vec3d from = Vec3d.ofCenter(stand).add(0, 1.2, 0);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                from, Vec3d.ofCenter(chest), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(chest);
    }

    private boolean isStorage(BlockPos pos) {
        BlockEntity be = mc.world.getBlockEntity(pos);
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private void stopBreaking() {
        if (mc.options != null) mc.options.attackKey.setPressed(false);
        breakingPos = null;
    }

    private void enqueue(Runnable task) {
        dropQueue.add(task);
    }

    private RotationManager.Rotation playerRot() {
        return new RotationManager.Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private static BlockPos parsePos(TextSetting setting, BlockPos fallback) {
        return parsePosOptional(setting.getValue(), fallback);
    }

    private static BlockPos parsePosOptional(String raw) {
        return parsePosOptional(raw, null);
    }

    private static BlockPos parsePosOptional(String raw, BlockPos fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String[] p = raw.split(",");
        if (p.length != 3) return fallback;
        try {
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void fail(String text) {
        msg(text, Formatting.RED);
        dropQueue.clear();
        stopBreaking();
        state = MineState.IDLE;
        FarmInventory.closeScreen();
        if (isEnabled()) toggle();
    }

    private void msg(String text, Formatting color) {
        CommandFeedback.message("[AutoMine] " + text, color);
    }

    private record LayoutEntry(String key, int count) {
        static final LayoutEntry EMPTY = new LayoutEntry("", 0);

        boolean matches(ItemStack stack) {
            return !stack.isEmpty() && count > 0 && key.equals(stackKey(stack));
        }
    }

    private static String stackKey(ItemStack stack) {
        return stack.getItem().toString() + "|" + stack.getName().getString();
    }

    private enum MineState {
        IDLE, WAITING_FOR_TP, GOING_TO_MINE, MINING, TELEPORTING_TO_DROP, GOING_TO_DROP_CHEST,
        ROTATING_DROP_CHEST, OPENING_DROP_CHEST, WAITING_FOR_DROP_GUI, DROPPING, CHANGING_ANARCHY
    }
}
