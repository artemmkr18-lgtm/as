package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
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
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;
import java.util.function.Predicate;

@NewFunction(name = "PotionCombiner", desc = "Автоматически объединяет зелья в наковальне", category = Category.MISC)
public class PotionCombiner extends Function {
    private static final String STRENGTH = "Сила";
    private static final String SPEED = "Скорость";
    private static final String SPEED3_STRENGTH3 = "Скорость 3 + Сила 3";
    private static final String STRENGTH3_SPEED3 = "Сила 3 + Скорость 3";

    public final ModeSetting potionType = new ModeSetting("Зелье", STRENGTH, STRENGTH, SPEED, SPEED3_STRENGTH3, STRENGTH3_SPEED3);
    public final NumberSetting level = new NumberSetting("Уровень", 5.0, 1.0, 30.0, 1.0);
    public final BooleanSetting saveXp = new BooleanSetting("Экономия опыта", true);

    private final FarmTimer actionTimer = new FarmTimer();
    private final FarmTimer anvilTimer = new FarmTimer();
    private final FarmTimer openTimer = new FarmTimer();
    private final FarmTimer chatTimer = new FarmTimer();
    private final Random random = new Random();

    private boolean drinkingXp;
    private int savedHotbarSlot = -1;
    private int swappedSlot = -1;
    private float savedPitch;
    private int openDelayMs = 8;
    private int anvilDelayMs = 100;
    private int drinkDelayMs = 60;
    private int renameCounter;
    private String lastChatMessage = "";

    public PotionCombiner() {
        addSettings(potionType, level, saveXp);
    }

    @Override
    public void onEnable() {
        lastChatMessage = "";
        chatTimer.reset();
        drinkingXp = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        cleanupXpDrink(true);
        FarmNavigator.stop();
        RotationManager.reset();
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (nullCheck.all() || mc.interactionManager == null) {
            cleanupXpDrink(false);
            return;
        }

        if (drinkingXp) {
            handleXpDrink();
            return;
        }

        if (mc.player.experienceLevel < requiredLevel()) {
            if (findXpBottleSlot() != -1) {
                startXpDrink();
            } else {
                fail("Нет пузырьков опыта. Нужно добить уровень до " + requiredLevel() + ".");
            }
            return;
        }

        if (mc.currentScreen instanceof AnvilScreen && mc.player.currentScreenHandler instanceof AnvilScreenHandler handler) {
            handleAnvil(handler);
            return;
        }

        if (mc.currentScreen == null) {
            openAnvil();
        }
    }

    private void openAnvil() {
        BlockPos anvil = findAnvil(6);
        if (anvil == null) {
            notify("Наковальня не найдена в радиусе 6 блоков.");
            return;
        }

        Vec3d hit = Vec3d.ofCenter(anvil).add(0.0, 0.4, 0.0);
        FarmNavigator.lookAt(hit);

        RotationManager.Rotation target = RotationManager.getRotationTo(hit);
        if (RotationManager.getCurrentRotation().differenceValue(target) > 4.0F) {
            return;
        }

        if (!openTimer.tryReset(openDelayMs)) {
            return;
        }

        if (mc.player.getEyePos().squaredDistanceTo(hit) > 4.6 * 4.6) {
            return;
        }

        BlockHitResult ray = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                hit,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(anvil)) {
            return;
        }

        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, ray);
        openDelayMs = randomBetween(0, 1);
    }

    private void handleAnvil(AnvilScreenHandler handler) {
        if (splitStacks(handler)) {
            return;
        }

        if (!anvilTimer.passed(anvilDelayMs)) {
            return;
        }

        if (missingIngredients(handler)) {
            return;
        }

        if (canTakeResult(handler) && handler.getSlot(2).hasStack()) {
            if (saveXp.isEnabled()) {
                spamRename(handler);
            }
            FarmInventory.click(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE);
            anvilTimer.reset();
            anvilDelayMs = randomBetween(85, 120);
            return;
        }

        fillAnvilSlots(handler);
    }

    private void fillAnvilSlots(AnvilScreenHandler handler) {
        if (isComboMode()) {
            fillCombo(handler);
            return;
        }

        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && !matchesPotion(stack)) {
                FarmInventory.quickMove(handler.syncId, slot);
                bumpAnvilDelay();
                return;
            }
        }

        for (int slot = 0; slot < 2; slot++) {
            if (handler.getSlot(slot).getStack().isEmpty()) {
                int source = findPotionSlot(handler, this::matchesPotion);
                if (source != -1) {
                    moveToAnvilSlot(handler, source, slot);
                    bumpAnvilDelay();
                }
                return;
            }
        }
    }

    private void fillCombo(AnvilScreenHandler handler) {
        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && !matchesComboSlot(stack, slot)) {
                FarmInventory.quickMove(handler.syncId, slot);
                bumpAnvilDelay();
                return;
            }
        }

        for (int slot = 0; slot < 2; slot++) {
            if (handler.getSlot(slot).getStack().isEmpty()) {
                int source = findPotionSlot(handler, comboPredicate(slot));
                if (source != -1) {
                    moveToAnvilSlot(handler, source, slot);
                    bumpAnvilDelay();
                }
                return;
            }
        }
    }

    private boolean splitStacks(AnvilScreenHandler handler) {
        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && stack.getCount() > 1) {
                FarmInventory.quickMove(handler.syncId, slot);
                bumpAnvilDelay();
                return true;
            }
        }
        return false;
    }

    private boolean missingIngredients(AnvilScreenHandler handler) {
        if (isComboMode()) {
            if (countMatching(handler, this::isSpeed3Only) <= 0) {
                fail("Нет ингредиента: Скорость III.");
                return true;
            }
            if (countMatching(handler, this::isStrength3Only) <= 0) {
                fail("Нет ингредиента: Сила III.");
                return true;
            }
            return false;
        }

        int count = countMatching(handler, this::matchesPotion);
        if (count < 2) {
            fail("Нет ингредиента: " + ingredientName() + " x" + (2 - count) + ".");
            return true;
        }
        return false;
    }

    private boolean canTakeResult(AnvilScreenHandler handler) {
        ItemStack left = handler.getSlot(0).getStack();
        ItemStack right = handler.getSlot(1).getStack();
        if (isComboMode()) {
            return matchesComboSlot(left, 0) && matchesComboSlot(right, 1);
        }
        return matchesPotion(left) && matchesPotion(right);
    }

    private void moveToAnvilSlot(AnvilScreenHandler handler, int source, int target) {
        FarmInventory.click(handler.syncId, source, 0, SlotActionType.PICKUP);
        FarmInventory.click(handler.syncId, target, 1, SlotActionType.PICKUP);
        FarmInventory.click(handler.syncId, source, 0, SlotActionType.PICKUP);
    }

    private int findPotionSlot(AnvilScreenHandler handler, Predicate<ItemStack> predicate) {
        for (int i = 3; i < handler.slots.size(); i++) {
            if (predicate.test(handler.getSlot(i).getStack())) {
                return i;
            }
        }
        return -1;
    }

    private int countMatching(AnvilScreenHandler handler, Predicate<ItemStack> predicate) {
        int total = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (i == 2) continue;
            ItemStack stack = handler.getSlot(i).getStack();
            if (predicate.test(stack)) {
                total += Math.max(1, stack.getCount());
            }
        }
        return total;
    }

    private boolean matchesPotion(ItemStack stack) {
        if (potionType.isEnabled(STRENGTH)) {
            return hasEffect(stack, StatusEffects.STRENGTH, 2);
        }
        if (potionType.isEnabled(SPEED)) {
            return hasEffect(stack, StatusEffects.SPEED, 2);
        }
        return isSpeed3Only(stack) || isStrength3Only(stack);
    }

    private boolean matchesComboSlot(ItemStack stack, int slot) {
        return slot == 0 ? isSpeed3ComboFirst() ? isSpeed3Only(stack) : isStrength3Only(stack)
                : isSpeed3ComboFirst() ? isStrength3Only(stack) : isSpeed3Only(stack);
    }

    private Predicate<ItemStack> comboPredicate(int slot) {
        return stack -> matchesComboSlot(stack, slot);
    }

    private boolean isSpeed3ComboFirst() {
        return potionType.isEnabled(SPEED3_STRENGTH3);
    }

    private boolean isComboMode() {
        return potionType.isEnabled(SPEED3_STRENGTH3) || potionType.isEnabled(STRENGTH3_SPEED3);
    }

    private boolean isSpeed3Only(ItemStack stack) {
        return hasEffect(stack, StatusEffects.SPEED, 3) && !hasEffect(stack, StatusEffects.STRENGTH, 3);
    }

    private boolean isStrength3Only(ItemStack stack) {
        return hasEffect(stack, StatusEffects.STRENGTH, 3) && !hasEffect(stack, StatusEffects.SPEED, 3);
    }

    private boolean hasEffect(ItemStack stack, RegistryEntry<StatusEffect> effect, int level) {
        if (!isPotionItem(stack)) {
            return false;
        }
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType().equals(effect) && instance.getAmplifier() == level - 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isPotionItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION));
    }

    private void spamRename(AnvilScreenHandler handler) {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        String base = readRenameBase(handler);
        for (int i = 0; i < 10; i++) {
            String name = i % 2 == 0 ? base + randomSuffix() : base;
            handler.setNewItemName(name);
            mc.getNetworkHandler().sendPacket(new RenameItemC2SPacket(name));
        }
    }

    private String readRenameBase(AnvilScreenHandler handler) {
        ItemStack left = handler.getSlot(0).getStack();
        if (!left.isEmpty()) {
            return trimName(left.getName().getString());
        }
        ItemStack result = handler.getSlot(2).getStack();
        if (!result.isEmpty()) {
            return trimName(result.getName().getString());
        }
        return "Potion";
    }

    private String randomSuffix() {
        renameCounter++;
        return "_" + Integer.toString(renameCounter, 36) + Integer.toString(random.nextInt(1296), 36);
    }

    private String trimName(String name) {
        if (name == null || name.isBlank()) {
            return "Potion";
        }
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    private void startXpDrink() {
        drinkingXp = true;
        savedHotbarSlot = mc.player.getInventory().getSelectedSlot();
        savedPitch = mc.player.getPitch();
        actionTimer.reset();
    }

    private void handleXpDrink() {
        if (mc.player.experienceLevel >= requiredLevel()) {
            cleanupXpDrink(true);
            return;
        }

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            return;
        }

        mc.player.setPitch(clampPitch(87.0F + randomBetweenF(-0.7F, 0.7F)));

        if (!prepareXpBottle()) {
            cleanupXpDrink(true);
            fail("Нет пузырьков опыта. Нужно добить уровень до " + requiredLevel() + ".");
            return;
        }

        if (actionTimer.tryReset(drinkDelayMs)) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            drinkDelayMs = randomBetween(50, 70);
        }
    }

    private boolean prepareXpBottle() {
        if (mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            return true;
        }

        int slot = findXpBottleSlot();
        if (slot == -1) {
            return false;
        }

        if (slot >= 36 && slot <= 44) {
            mc.player.getInventory().setSelectedSlot(slot - 36);
            return true;
        }

        if (savedHotbarSlot < 0) {
            savedHotbarSlot = mc.player.getInventory().getSelectedSlot();
        }
        swappedSlot = slot;
        FarmInventory.click(mc.player.currentScreenHandler.syncId, slot, savedHotbarSlot, SlotActionType.SWAP);
        return true;
    }

    private int findXpBottleSlot() {
        for (int slot = 9; slot <= 44; slot++) {
            if (mc.player.currentScreenHandler.getSlot(slot).getStack().isOf(Items.EXPERIENCE_BOTTLE)) {
                return slot;
            }
        }
        return -1;
    }

    private void cleanupXpDrink(boolean restore) {
        if (restore && mc.player != null && mc.interactionManager != null) {
            if (swappedSlot != -1 && savedHotbarSlot >= 0) {
                FarmInventory.click(mc.player.currentScreenHandler.syncId, swappedSlot, savedHotbarSlot, SlotActionType.SWAP);
            }
            if (savedHotbarSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(savedHotbarSlot);
            }
            mc.player.setPitch(savedPitch);
        }
        drinkingXp = false;
        swappedSlot = -1;
        savedHotbarSlot = -1;
    }

    private BlockPos findAnvil(int radius) {
        BlockPos origin = mc.player.getBlockPos();
        Vec3d eyes = mc.player.getEyePos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (!isAnvil(block)) {
                        continue;
                    }
                    Vec3d center = Vec3d.ofCenter(pos).add(0.0, 0.4, 0.0);
                    double dist = eyes.squaredDistanceTo(center);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isAnvil(Block block) {
        return block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
    }

    private int requiredLevel() {
        return Math.max(1, (int) Math.round(level.getValue()));
    }

    private String ingredientName() {
        if (potionType.isEnabled(STRENGTH)) {
            return "Сила II";
        }
        if (potionType.isEnabled(SPEED)) {
            return "Скорость II";
        }
        return "зелье";
    }

    private void bumpAnvilDelay() {
        anvilTimer.reset();
        anvilDelayMs = randomBetween(85, 120);
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    private float randomBetweenF(float min, float max) {
        return min + (max - min) * random.nextFloat();
    }

    private float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private void notify(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (message.equals(lastChatMessage) && !chatTimer.passed(2500L)) {
            return;
        }
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[PotionCombiner] ").formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal(message).formatted(Formatting.WHITE)));
        }
        lastChatMessage = message;
        chatTimer.reset();
    }

    private void fail(String message) {
        notify(message);
        toggle();
    }
}
