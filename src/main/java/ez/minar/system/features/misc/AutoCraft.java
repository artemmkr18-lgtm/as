package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.FarmInventory;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

@NewFunction(name = "AutoCraft", desc = "Автоматически крафтит выбранный рецепт", category = Category.MISC)
public class AutoCraft extends Function {
    public final TextSetting recipe = new TextSetting("Рецепт", "minecraft:oak_planks,minecraft:oak_planks,minecraft:oak_planks,minecraft:oak_planks", 256);
    public final TextSetting targetCount = new TextSetting("Кол-во предметов", "64", 8);
    public final NumberSetting delay = new NumberSetting("Задержка", 80.0, 20.0, 500.0, 10.0);

    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer pathTimer = new FarmTimer();
    private final Queue<Runnable> clickQueue = new ArrayDeque<>();

    private State state = State.IDLE;
    private BlockPos tablePos;
    private int craftedCount;
    private String missingItem = "";

    public AutoCraft() {
        addSettings(recipe, targetCount, delay);
    }

    @Override
    public void onEnable() {
        craftedCount = 0;
        missingItem = "";
        clickQueue.clear();
        tablePos = null;

        if (parseRecipe().length == 0) {
            fail("Рецепт пуст.");
            return;
        }
        if (getTargetCount() <= 0) {
            fail("Некорректное количество предметов.");
            return;
        }

        state = State.FINDING_TABLE;
        actionTimer.reset();
        pathTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        clickQueue.clear();
        tablePos = null;
        state = State.IDLE;
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

        if (processClickQueue()) {
            return;
        }

        if (!missingItem.isBlank()) {
            fail("Не хватает предмета: " + prettyItemId(missingItem));
            return;
        }

        switch (state) {
            case IDLE -> {
            }
            case FINDING_TABLE -> findTable();
            case GOING_TO_TABLE -> goToTable();
            case OPENING_TABLE -> openTable();
            case CLEARING_GRID -> clearGrid();
            case PLACING_RECIPE -> placeRecipe();
            case WAITING_RESULT -> waitResult();
            case TAKING_RESULT -> takeResult();
            case CLOSING -> closeCraft();
        }
    }

    private void findTable() {
        if (!actionTimer.tryReset(delayMs())) {
            return;
        }

        tablePos = findNearestTable();
        if (tablePos == null) {
            fail("Верстак рядом не найден.");
            return;
        }

        state = State.GOING_TO_TABLE;
        pathTimer.reset();
    }

    private void goToTable() {
        if (!isValidTable(tablePos)) {
            state = State.FINDING_TABLE;
            actionTimer.reset();
            return;
        }

        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(tablePos)) <= 4.0) {
            FarmNavigator.stop();
            state = State.OPENING_TABLE;
            actionTimer.reset();
            return;
        }

        FarmNavigator.goTo(tablePos, 2.0);

        if (pathTimer.passed(15000L)) {
            fail("Не удалось дойти до верстака.");
        }
    }

    private void openTable() {
        FarmNavigator.lookAt(Vec3d.ofCenter(tablePos));
        RotationManager.Rotation target = RotationManager.getRotationTo(Vec3d.ofCenter(tablePos));
        if (RotationManager.getCurrentRotation().differenceValue(target) > 4.0F) {
            return;
        }

        if (!actionTimer.tryReset(delayMs())) {
            return;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(tablePos), Direction.UP, tablePos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        state = State.CLEARING_GRID;
        actionTimer.reset();
    }

    private void clearGrid() {
        CraftingScreenHandler handler = getCraftingHandler();
        if (handler == null) {
            if (actionTimer.passed(5000L)) {
                fail("Верстак не открылся.");
            }
            return;
        }

        for (int slot = 1; slot <= 9; slot++) {
            if (handler.getSlot(slot).hasStack()) {
                int index = slot;
                clickQueue.add(() -> FarmInventory.quickMove(handler.syncId, index));
            }
        }

        state = State.PLACING_RECIPE;
        actionTimer.reset();
    }

    private void placeRecipe() {
        CraftingScreenHandler handler = getCraftingHandler();
        if (handler == null) {
            state = State.FINDING_TABLE;
            actionTimer.reset();
            return;
        }

        String missing = findMissingIngredient(handler);
        if (!missing.isBlank()) {
            missingItem = missing;
            return;
        }

        String[] items = parseRecipe();
        for (int i = 0; i < items.length && i < 9; i++) {
            String itemId = items[i];
            if (itemId.isBlank()) {
                continue;
            }
            int gridSlot = i + 1;
            clickQueue.add(() -> moveIngredient(handler, itemId, gridSlot));
        }

        state = State.WAITING_RESULT;
        actionTimer.reset();
    }

    private void waitResult() {
        CraftingScreenHandler handler = getCraftingHandler();
        if (handler == null) {
            state = State.FINDING_TABLE;
            actionTimer.reset();
            return;
        }

        if (!actionTimer.tryReset(Math.max(150L, delayMs() * 2L))) {
            return;
        }

        if (!handler.getSlot(0).hasStack()) {
            fail("Рецепт не даёт результат.");
            return;
        }

        state = State.TAKING_RESULT;
        actionTimer.reset();
    }

    private void takeResult() {
        CraftingScreenHandler handler = getCraftingHandler();
        if (handler == null) {
            state = State.FINDING_TABLE;
            actionTimer.reset();
            return;
        }

        if (!actionTimer.tryReset(delayMs())) {
            return;
        }

        ItemStack result = handler.getSlot(0).getStack().copy();
        int stackSize = Math.max(1, result.getCount());
        FarmInventory.quickMove(handler.syncId, 0);
        craftedCount += stackSize;

        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[AutoCraft] ").formatted(Formatting.GOLD)
                    .append(Text.literal("Скрафтил: " + Math.min(craftedCount, getTargetCount()) + "/" + getTargetCount()).formatted(Formatting.GREEN)));
        }

        state = State.CLOSING;
        actionTimer.reset();
    }

    private void closeCraft() {
        if (!actionTimer.tryReset(delayMs())) {
            return;
        }

        if (craftedCount >= getTargetCount()) {
            FarmInventory.closeScreen();
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[AutoCraft] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Готово.").formatted(Formatting.GREEN)));
            }
            toggle();
            return;
        }

        state = State.CLEARING_GRID;
        actionTimer.reset();
    }

    private boolean processClickQueue() {
        if (clickQueue.isEmpty()) {
            return false;
        }
        if (!actionTimer.tryReset(delayMs())) {
            return true;
        }
        Runnable task = clickQueue.poll();
        if (task != null) {
            task.run();
        }
        return true;
    }

    private CraftingScreenHandler getCraftingHandler() {
        if (mc.currentScreen instanceof CraftingScreen screen) {
            return screen.getScreenHandler();
        }
        return null;
    }

    private void moveIngredient(CraftingScreenHandler handler, String itemId, int gridSlot) {
        int source = findInventorySlot(handler, itemId);
        if (source == -1) {
            missingItem = itemId;
            return;
        }
        FarmInventory.click(handler.syncId, source, 0, SlotActionType.PICKUP);
        FarmInventory.click(handler.syncId, gridSlot, 1, SlotActionType.PICKUP);
        FarmInventory.click(handler.syncId, source, 0, SlotActionType.PICKUP);
    }

    private int findInventorySlot(CraftingScreenHandler handler, String itemId) {
        for (int slot = 10; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (matchesItemId(stack, itemId)) {
                return slot;
            }
        }
        return -1;
    }

    private String findMissingIngredient(CraftingScreenHandler handler) {
        Map<String, Integer> required = new HashMap<>();
        for (String itemId : parseRecipe()) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            required.merge(itemId, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int available = countIngredient(handler, entry.getKey());
            if (available < entry.getValue()) {
                return entry.getKey();
            }
        }
        return "";
    }

    private int countIngredient(CraftingScreenHandler handler, String itemId) {
        int total = 0;
        for (int slot = 10; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (matchesItemId(stack, itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean matchesItemId(ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null && id.toString().equals(itemId);
    }

    private String[] parseRecipe() {
        String raw = recipe.getValue().trim();
        if (raw.isEmpty()) {
            return new String[0];
        }
        String[] parts = raw.split(",");
        String[] grid = new String[9];
        for (int i = 0; i < 9; i++) {
            if (i < parts.length) {
                grid[i] = parts[i].trim();
            } else {
                grid[i] = "";
            }
        }
        return grid;
    }

    private BlockPos findNearestTable() {
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int radius = 16;

        for (BlockPos pos : BlockPos.iterate(origin.add(-radius, -5, -radius), origin.add(radius, 5, radius))) {
            if (!isValidTable(pos)) {
                continue;
            }
            double dist = origin.getSquaredDistance(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private boolean isValidTable(BlockPos pos) {
        return pos != null && mc.world != null && mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE);
    }

    private int getTargetCount() {
        try {
            return Math.max(0, Math.min(999999, Integer.parseInt(targetCount.getValue().trim())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long delayMs() {
        return Math.max(20L, (long) delay.getValue());
    }

    private String prettyItemId(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return itemId;
        }
        Item item = Registries.ITEM.get(id);
        return item == Items.AIR ? itemId : item.getName().getString();
    }

    private void fail(String message) {
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[AutoCraft] ").formatted(Formatting.GOLD)
                    .append(Text.literal(message).formatted(Formatting.RED)));
        }
        toggle();
    }

    private enum State {
        IDLE,
        FINDING_TABLE,
        GOING_TO_TABLE,
        OPENING_TABLE,
        CLEARING_GRID,
        PLACING_RECIPE,
        WAITING_RESULT,
        TAKING_RESULT,
        CLOSING
    }
}
