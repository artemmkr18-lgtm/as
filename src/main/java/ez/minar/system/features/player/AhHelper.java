package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.utils.helpers.AuctionHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@NewFunction(name = "AH Helper", desc = "Подсвечивает лучшие лоты и шиповку на аукционе", category = Category.PLAYER)
public class AhHelper extends Function {
    private static final int FILL_ALPHA = 75;
    private static final Color BEST = new Color(0, 255, 100);
    private static final Color SECOND = new Color(255, 220, 0);
    private static final Color THIRD = new Color(255, 130, 0);
    private static final Color PLAIN_ARMOR = new Color(80, 170, 255);
    private static final Color SPIKED_ARMOR = new Color(200, 90, 255);

    private final BooleanSetting three = new BooleanSetting("Подсвечивать 3 слота", true);
    private final BooleanSetting not_ships = new BooleanSetting("Подсвечивать безшиповку", true);
    private final BooleanSetting shipi = new BooleanSetting("Подсвечивать шиповку", true);

    private HandledScreen<?> screen;
    private Slot best, second, third, plain, spiked;
    private long lastUpdate = 0;

    public AhHelper() {
        addSettings(three, not_ships, shipi);
    }

    @Override
    public void onEnable() {
        System.out.println("[AH] enabled, waiting for auction screen");
    }

    @Override
    public void onDisable() {
        clear();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (mc.currentScreen instanceof HandledScreen<?> handled) {
            boolean auction = isAuction(handled);
            if (System.currentTimeMillis() - lastUpdate > 1000) {
                lastUpdate = System.currentTimeMillis();
                System.out.println("[AH] screen=" + handled.getClass().getSimpleName()
                        + " title='" + handled.getTitle().getString() + "' auction=" + auction);
            }
            if (auction) {
                if (screen != handled || System.currentTimeMillis() - lastUpdate > 100) {
                    rescan(handled);
                }
            } else if (screen != null) {
                clear();
            }
        } else if (screen != null) {
            clear();
        }
    }

    public boolean isAuction(HandledScreen<?> handled) {
        if (handled == null) return false;
        String title = AuctionHelper.strip(handled.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (title.contains("аукцион") || title.contains("auction") || title.contains("поиск")
                || title.contains("рынок") || title.contains("ah") || title.contains("купить")) {
            return true;
        }
        var handler = handled.getScreenHandler();
        if (handler == null) return false;
        int limit = Math.min(45, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            if (AuctionHelper.readLotPrice(handler.getSlot(i)) > 0L) return true;
        }
        return false;
    }

    private static class ScannedLot {
        final Slot slot;
        final long price;
        final boolean thorns;
        final boolean armor;

        ScannedLot(Slot slot, long price, boolean thorns, boolean armor) {
            this.slot = slot;
            this.price = price;
            this.thorns = thorns;
            this.armor = armor;
        }
    }

    public void rescan(HandledScreen<?> container) {
        if (container == null || container.getScreenHandler() == null) {
            clear();
            return;
        }

        int slots = Math.min(45, container.getScreenHandler().slots.size());
        List<ScannedLot> lots = new ArrayList<>();
        ScannedLot cheapestPlain = null;
        ScannedLot cheapestSpiked = null;

        for (int i = 0; i < slots; i++) {
            Slot slot = container.getScreenHandler().getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            long price = AuctionHelper.readLotPrice(slot);
            if (price <= 0L) continue;

            boolean armor = isArmor(stack);
            boolean thorns = armor && hasThorns(stack);

            ScannedLot lot = new ScannedLot(slot, price, thorns, armor);
            lots.add(lot);

            if (armor) {
                if (!thorns) {
                    if (cheapestPlain == null || price < cheapestPlain.price) {
                        cheapestPlain = lot;
                    }
                } else {
                    if (cheapestSpiked == null || price < cheapestSpiked.price) {
                        cheapestSpiked = lot;
                    }
                }
            }
        }

        lots.sort(Comparator.comparingLong(l -> l.price));

        Slot newBest = !lots.isEmpty() ? lots.get(0).slot : null;
        Slot newSecond = null;
        Slot newThird = null;

        if (three.isEnabled()) {
            if (lots.size() > 1) newSecond = lots.get(1).slot;
            if (lots.size() > 2) newThird = lots.get(2).slot;
        }

        Slot newPlain = (not_ships.isEnabled() && cheapestPlain != null) ? cheapestPlain.slot : null;
        Slot newSpiked = (shipi.isEnabled() && cheapestSpiked != null) ? cheapestSpiked.slot : null;

        this.screen = container;
        this.best = newBest;
        this.second = newSecond;
        this.third = newThird;
        this.plain = newPlain;
        this.spiked = newSpiked;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void clear() {
        screen = null;
        best = second = third = plain = spiked = null;
    }

    public static void renderOverlay(HandledScreen<?> open, DrawContext context, int guiX, int guiY) {
        AhHelper helper = FunctionManager.getFunction(AhHelper.class);
        if (helper == null || !helper.isEnabled() || open == null) return;

        if (!helper.isAuction(open)) {
            if (helper.screen != null) {
                helper.clear();
            }
            return;
        }

        if (helper.screen != open || System.currentTimeMillis() - helper.lastUpdate > 100) {
            helper.rescan(open);
        }

        highlight(context, guiX, guiY, helper.best, BEST);
        if (helper.second != null && helper.second != helper.best) {
            highlight(context, guiX, guiY, helper.second, SECOND);
        }
        if (helper.third != null && helper.third != helper.best && helper.third != helper.second) {
            highlight(context, guiX, guiY, helper.third, THIRD);
        }
        if (helper.plain != null && helper.plain != helper.best && helper.plain != helper.second && helper.plain != helper.third) {
            highlight(context, guiX, guiY, helper.plain, PLAIN_ARMOR);
        }
        if (helper.spiked != null && helper.spiked != helper.best && helper.spiked != helper.second && helper.spiked != helper.third) {
            highlight(context, guiX, guiY, helper.spiked, SPIKED_ARMOR);
        }
    }

    private static void highlight(DrawContext context, int guiX, int guiY, Slot slot, Color color) {
        if (slot == null) return;
        int argb = color.getRGB();
        int fill = (FILL_ALPHA << 24) | (argb & 0xFFFFFF);
        int x = guiX + slot.x, y = guiY + slot.y;
        context.fill(x, y, x + 16, y + 16, fill);
        context.fill(x, y, x + 16, y + 1, argb);
        context.fill(x, y + 15, x + 16, y + 16, argb);
        context.fill(x, y, x + 1, y + 16, argb);
        context.fill(x + 15, y, x + 16, y + 16, argb);
    }

    public static boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            return switch (equippable.slot()) {
                case HEAD, CHEST, LEGS, FEET -> true;
                default -> false;
            };
        }
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots")
                || id.contains("шлем") || id.contains("нагрудник") || id.contains("поножи") || id.contains("ботинки")) {
            return true;
        }
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        return name.contains("helmet") || name.contains("chestplate") || name.contains("leggings") || name.contains("boots")
                || name.contains("шлем") || name.contains("нагрудник") || name.contains("поножи") || name.contains("ботинки");
    }

    public static boolean hasThorns(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // 1. DataComponentTypes.ENCHANTMENTS (1.21)
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.getEnchantments()) {
                if (entry.matchesKey(Enchantments.THORNS)) {
                    return true;
                }
            }
        }

        // 2. DataComponentTypes.CUSTOM_DATA (NBT Enchantments from 1.16-1.20)
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            if (nbt.contains("Enchantments")) {
                NbtList list = nbt.getListOrEmpty("Enchantments");
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound enchant = list.getCompoundOrEmpty(i);
                    String id = enchant.getString("id", "");
                    if ("minecraft:thorns".equals(id) || "thorns".equals(id)) {
                        return true;
                    }
                }
            }
        }

        // 3. Lore-based thorns (custom server enchants)
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String text = line.getString().toLowerCase(Locale.ROOT);
                if (text.contains("шипы") || text.contains("шип ") || text.contains("thorns")) {
                    return true;
                }
            }
        }

        // 4. Tooltip fallback
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            try {
                var tooltip = stack.getTooltip(net.minecraft.item.Item.TooltipContext.DEFAULT, mc.player, net.minecraft.item.tooltip.TooltipType.BASIC);
                if (tooltip != null) {
                    for (Text line : tooltip) {
                        String text = line.getString().toLowerCase(Locale.ROOT);
                        if (text.contains("шипы") || text.contains("шип ") || text.contains("thorns")) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }
}
