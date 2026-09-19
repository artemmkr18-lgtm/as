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
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@NewFunction(name = "WardenFarm", desc = "Авто-фарм варден/медного данжа с лутом и складом", category = Category.MISC)
public class WardenFarm extends Function {
    private final ModeSetting mode = new ModeSetting("Mode", "Warden", "Warden", "Copper");
    private final BooleanSetting lootPotions = new BooleanSetting("Potions", false);
    private final BooleanSetting lootSpheres = new BooleanSetting("Spheres", true);
    private final BooleanSetting lootTalismans = new BooleanSetting("Talismans", true);
    private final BooleanSetting lootArrows = new BooleanSetting("Arrows", false);
    private final BooleanSetting lootWeapons = new BooleanSetting("Weapons", false);
    private final BooleanSetting lootArmor = new BooleanSetting("Armor", false);
    private final BooleanSetting lootValuables = new BooleanSetting("Valuables", true);
    private final BooleanSetting lootEggs = new BooleanSetting("Eggs", false);
    private final BooleanSetting storeLoot = new BooleanSetting("Store loot", true);
    private final ModeSetting storeMode = new ModeSetting("Store to", "Clan", "Clan", "Chest");
    private final BooleanSetting swapAnarchy = new BooleanSetting("Swap anarchy", true);
    private final TextSetting anarchyList = new TextSetting("Anarchy list", "903,102,504", 96);
    private final TextSetting baseAnarchy = new TextSetting("Base anarchy", "109", 16);
    private final TextSetting homeName = new TextSetting("Home name", "warden", 32);
    private final NumberSetting chestWaitSec = new NumberSetting("Chest wait sec", 240.0, 5.0, 600.0, 5.0);
    private final BooleanSetting stealth = new BooleanSetting("Stealth sneak", true);

    private final FarmTimer tickTimer = new FarmTimer();
    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer homeTimer = new FarmTimer();
    private final List<BlockPos> chestTargets = new ArrayList<>();
    private final List<String> anarchyCycle = new ArrayList<>();

    private FarmState state = FarmState.PREP;
    private BlockPos targetChest;
    private int anarchyIndex;
    private boolean waitingHub;
    private boolean homeSent;

    public WardenFarm() {
        addSettings(
                mode, lootPotions, lootSpheres, lootTalismans, lootArrows, lootWeapons, lootArmor, lootValuables, lootEggs,
                storeLoot, storeMode, swapAnarchy, anarchyList, baseAnarchy, homeName, chestWaitSec, stealth
        );
    }

    @Override
    public void onEnable() {
        reloadAnarchyList();
        reset(false);
    }

    @Override
    public void onDisable() {
        FarmNavigator.stop();
        if (mc.player != null) mc.player.setSneaking(false);
        reset(true);
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        String lower = AuctionHelper.strip(event.getPacket().content().getString()).toLowerCase(Locale.ROOT);
        if (lower.contains("успешно телепорт") || lower.contains("teleport")) homeSent = false;
        if (waitingHub && (lower.contains("хаб") || lower.contains("hub") || lower.contains("lobby"))) {
            waitingHub = false;
            actionTimer.reset();
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.interactionManager == null) return;
        if (!tickTimer.tryReset(50L)) return;

        applyStealth();
        reloadAnarchyListIfNeeded();

        switch (state) {
            case PREP -> tickPrep();
            case GO_HOME -> tickGoHome();
            case SWAP_AN -> tickSwapAn();
            case SCAN -> tickScan();
            case GOTO_CHEST -> tickGotoChest();
            case OPEN_CHEST -> tickOpenChest();
            case LOOT -> tickLoot();
            case STORE_OPEN -> tickStoreOpen();
            case STORE_DEPOSIT -> tickStoreDeposit();
            case STORE_CLOSE -> tickStoreClose();
        }
    }

    private void tickPrep() {
        if (swapAnarchy.isEnabled() && !inFarmZone()) {
            state = FarmState.SWAP_AN;
            actionTimer.reset();
            return;
        }
        if (actionTimer.tryReset(800L)) {
            FarmInventory.sendCommand("home " + homeName.getValue().trim());
            homeSent = true;
            homeTimer.reset();
            state = FarmState.GO_HOME;
        }
    }

    private void tickGoHome() {
        if (inFarmZone()) {
            state = FarmState.SCAN;
            actionTimer.reset();
            return;
        }
        if (homeTimer.passed(8000L) && actionTimer.tryReset(2500L)) {
            FarmInventory.sendCommand("home " + homeName.getValue().trim());
            homeTimer.reset();
        }
        if (homeTimer.passed(20000L)) state = FarmState.SCAN;
    }

    private void tickSwapAn() {
        if (inFarmZone()) {
            state = FarmState.GO_HOME;
            return;
        }
        if (waitingHub) {
            if (actionTimer.passed(6000L)) waitingHub = false;
            return;
        }
        if (actionTimer.tryReset(1500L)) {
            FarmInventory.sendCommand("hub");
            waitingHub = true;
            actionTimer.reset();
            return;
        }
        if (actionTimer.tryReset(2200L)) {
            String next = nextAnarchy();
            if (!next.isBlank()) FarmInventory.sendCommand("an" + next);
            state = FarmState.GO_HOME;
            actionTimer.reset();
        }
    }

    private void tickScan() {
        if (!inFarmZone()) {
            state = FarmState.SWAP_AN;
            actionTimer.reset();
            return;
        }
        chestTargets.clear();
        if (mc.world == null || mc.player == null) return;
        BlockPos origin = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(origin.add(-32, -8, -32), origin.add(32, 8, 32))) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.CHEST)
                    && !mc.world.getBlockState(pos).isOf(Blocks.TRAPPED_CHEST)
                    && !mc.world.getBlockState(pos).isOf(Blocks.BARREL)) {
                continue;
            }
            BlockEntity be = mc.world.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity chest && chest.isEmpty()) continue;
            chestTargets.add(pos.toImmutable());
        }
        chestTargets.sort((a, b) -> Double.compare(distSq(a), distSq(b)));
        if (chestTargets.isEmpty()) {
            if (actionTimer.passed((long) (chestWaitSec.getValue() * 1000.0))) state = FarmState.PREP;
            return;
        }
        targetChest = chestTargets.get(0);
        state = FarmState.GOTO_CHEST;
        actionTimer.reset();
    }

    private void tickGotoChest() {
        if (targetChest == null) {
            state = FarmState.SCAN;
            return;
        }
        if (FarmNavigator.goTo(targetChest, 2.5)) {
            FarmNavigator.stop();
            state = FarmState.OPEN_CHEST;
            actionTimer.reset();
        }
    }

    private void tickOpenChest() {
        if (targetChest == null) {
            state = FarmState.SCAN;
            return;
        }
        FarmNavigator.lookAtBlock(targetChest);
        if (!actionTimer.tryReset(180L)) return;
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(targetChest), directionTo(targetChest), targetChest, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (mc.currentScreen instanceof GenericContainerScreen) {
            state = FarmState.LOOT;
            actionTimer.reset();
        } else if (actionTimer.passed(4000L)) {
            state = FarmState.SCAN;
        }
    }

    private void tickLoot() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            state = FarmState.SCAN;
            return;
        }
        if (FarmInventory.freeSlots() <= 0) {
            FarmInventory.closeScreen();
            if (storeLoot.isEnabled()) {
                state = FarmState.STORE_OPEN;
            } else {
                state = FarmState.PREP;
            }
            actionTimer.reset();
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        int chestSlots = AuctionHelper.containerSlots(screen);
        boolean moved = false;
        for (int i = 0; i < Math.min(chestSlots, handler.slots.size()); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty() || !shouldLoot(stack)) continue;
            if (actionTimer.tryReset(90L)) {
                FarmInventory.click(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
                moved = true;
                break;
            }
        }
        if (!moved) {
            FarmInventory.closeScreen();
            state = storeLoot.isEnabled() && hasLootInInventory() ? FarmState.STORE_OPEN : FarmState.SCAN;
            actionTimer.reset();
        }
    }

    private void tickStoreOpen() {
        if (!hasLootInInventory()) {
            state = FarmState.SCAN;
            return;
        }
        if (mc.currentScreen != null) FarmInventory.closeScreen();
        if (storeMode.isEnabled("Clan")) {
            if (actionTimer.tryReset(1200L)) {
                FarmInventory.sendCommand("clan storage");
                state = FarmState.STORE_DEPOSIT;
                actionTimer.reset();
            }
            return;
        }
        BlockPos stash = findNearbyChest();
        if (stash == null) {
            state = FarmState.SCAN;
            return;
        }
        targetChest = stash;
        if (FarmNavigator.goTo(stash, 2.5)) {
            FarmNavigator.stop();
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(stash), directionTo(stash), stash, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            state = FarmState.STORE_DEPOSIT;
            actionTimer.reset();
        }
    }

    private void tickStoreDeposit() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (actionTimer.passed(6000L)) state = FarmState.STORE_CLOSE;
            return;
        }
        ScreenHandler handler = screen.getScreenHandler();
        boolean deposited = false;
        for (int i = handler.slots.size() - 36; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty() || !shouldLoot(stack)) continue;
            if (actionTimer.tryReset(90L)) {
                FarmInventory.click(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
                deposited = true;
                break;
            }
        }
        if (!deposited) state = FarmState.STORE_CLOSE;
    }

    private void tickStoreClose() {
        FarmInventory.closeScreen();
        if (actionTimer.tryReset(400L)) {
            state = FarmState.PREP;
            actionTimer.reset();
        }
    }

    private boolean shouldLoot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String lower = AuctionHelper.strip(stack.getName().getString()).toLowerCase(Locale.ROOT);
        Item item = stack.getItem();

        if (lootEggs.isEnabled() && item instanceof SpawnEggItem) return true;
        if (lootSpheres.isEnabled() && lower.contains("сфер")) return true;
        if (lootTalismans.isEnabled() && lower.contains("талисман")) return true;
        if (lootPotions.isEnabled() && isPotion(stack)) return true;
        if (lootArrows.isEnabled() && (item == Items.ARROW || item == Items.SPECTRAL_ARROW || lower.contains("стрел"))) return true;
        if (lootWeapons.isEnabled() && isWeapon(item)) return true;
        if (lootArmor.isEnabled() && isArmor(item)) return true;
        if (lootValuables.isEnabled() && isValuable(stack, lower)) return true;
        return false;
    }

    private boolean isPotion(ItemStack stack) {
        if (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) return true;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        for (StatusEffectInstance effect : contents.customEffects()) {
            if (effect != null) return true;
        }
        return false;
    }

    private boolean isWeapon(net.minecraft.item.Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("sword") || path.contains("axe") || path.contains("bow")
                || path.contains("crossbow") || path.contains("trident") || path.contains("mace");
    }

    private boolean isArmor(net.minecraft.item.Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots");
    }

    private boolean isValuable(ItemStack stack, String lower) {
        if (stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isOf(Items.ELYTRA) || stack.isOf(Items.NETHERITE_INGOT)) return true;
        if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || lower.contains("shulker") || lower.contains("шалк")) return true;
        return lower.contains("крушит") || lower.contains("tier") || lower.contains("xxx");
    }

    private boolean hasLootInInventory() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (shouldLoot(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private boolean inFarmZone() {
        if (mc.player == null) return false;
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        if (mode.isEnabled("Copper")) {
            double dx = x - 2000.0;
            double dz = z - 2000.0;
            return dx * dx + dz * dz <= 62500.0;
        }
        return x >= -2068.0 && x <= -1932.0
                && y >= -60.0 && y <= -20.0
                && z >= -2066.0 && z <= -1934.0;
    }

    private void applyStealth() {
        if (!stealth.isEnabled() || mc.player == null || !mode.isEnabled("Warden")) return;
        if (!inFarmZone()) {
            mc.player.setSneaking(false);
            return;
        }
        boolean sculkNear = false;
        BlockPos origin = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(origin.add(-4, -2, -4), origin.add(4, 2, 4))) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (block == Blocks.SCULK || block == Blocks.SCULK_SENSOR || block == Blocks.SCULK_SHRIEKER) {
                sculkNear = true;
                break;
            }
        }
        mc.player.setSneaking(sculkNear);
    }

    private BlockPos findNearbyChest() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(origin.add(-16, -4, -16), origin.add(16, 4, 16))) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.CHEST)
                    && !mc.world.getBlockState(pos).isOf(Blocks.BARREL)
                    && !mc.world.getBlockState(pos).isOf(Blocks.TRAPPED_CHEST)) {
                continue;
            }
            double dist = distSq(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private double distSq(BlockPos pos) {
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
    }

    private Direction directionTo(BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private String nextAnarchy() {
        if (anarchyCycle.isEmpty()) return baseAnarchy.getValue().trim();
        String value = anarchyCycle.get(anarchyIndex % anarchyCycle.size()).trim();
        anarchyIndex = (anarchyIndex + 1) % anarchyCycle.size();
        return value;
    }

    private void reloadAnarchyList() {
        anarchyCycle.clear();
        for (String part : anarchyList.getValue().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) anarchyCycle.add(trimmed);
        }
        if (anarchyCycle.isEmpty() && !baseAnarchy.getValue().isBlank()) {
            anarchyCycle.add(baseAnarchy.getValue().trim());
        }
    }

    private void reloadAnarchyListIfNeeded() {
        if (anarchyCycle.isEmpty()) reloadAnarchyList();
    }

    private void reset(boolean full) {
        if (full) FarmNavigator.stop();
        state = FarmState.PREP;
        targetChest = null;
        waitingHub = false;
        homeSent = false;
        tickTimer.reset();
        actionTimer.reset();
        homeTimer.reset();
    }

    private enum FarmState {
        PREP, GO_HOME, SWAP_AN, SCAN, GOTO_CHEST, OPEN_CHEST, LOOT, STORE_OPEN, STORE_DEPOSIT, STORE_CLOSE
    }
}
