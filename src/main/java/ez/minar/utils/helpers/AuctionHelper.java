package ez.minar.utils.helpers;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

public final class AuctionHelper {
    public static volatile PurchaseInfo lastPurchase;
    public static volatile boolean buyingActive;

    private AuctionHelper() {
    }

    public static PurchaseInfo getLastPurchase() {
        return lastPurchase;
    }

    public static String strip(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)[§&].", "").replace('\u00A0', ' ').trim();
    }

    public static String normalize(String text) {
        return strip(text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static final java.util.regex.Pattern PRICE_KEYWORD_PATTERN = java.util.regex.Pattern.compile(
            "(?iu)(?:цен[аa](?:\\s+за(?:\\s+\\d+)?\\s*шт\\.?)?|price|cost|buy|стоим(?:ость)?)[^0-9\\$¤]*([\\$¤]?\\s*[0-9]+(?:[\\s.,][0-9]+)*(?:\\s*[.,][0-9]+)?\\s*(?:монет|тыс|млн|млрд|ккк|kkk|кк|kk|[kkmмкbб\\$¤])?)"
    );

    private static final java.util.regex.Pattern CURRENCY_PATTERN = java.util.regex.Pattern.compile(
            "(?iu)(?:[\\$¤]\\s*([0-9]+(?:[\\s.,][0-9]+)*(?:[.,][0-9]+)?\\s*(?:монет|тыс|млн|млрд|ккк|kkk|кк|kk|[kkmмкbб])?)|([0-9]+(?:[\\s.,][0-9]+)*(?:[.,][0-9]+)?\\s*(?:монет|тыс|млн|млрд|ккк|kkk|кк|kk|[kkmмкbб])?)\\s*(?:[\\$¤]|монет))"
    );

    public static long parseMoney(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        String text = raw.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)[§&].", "")
                .replace('\u00A0', ' ')
                .replace("$", "")
                .replace("¤", "")
                .replace("монет", "")
                .trim();

        long mul = 1L;
        boolean hasSuffix = false;
        if (text.contains("млрд") || text.contains("kkk") || text.contains("ккк") || text.contains("b") || text.contains("б")) {
            mul = 1_000_000_000L;
            text = text.replaceAll("(млрд|kkk|ккк|b|б)", "").trim();
            hasSuffix = true;
        } else if (text.contains("млн") || text.contains("kk") || text.contains("кк") || text.contains("m") || text.contains("м")) {
            mul = 1_000_000L;
            text = text.replaceAll("(млн|kk|кк|m|м)", "").trim();
            hasSuffix = true;
        } else if (text.contains("тыс") || text.contains("k") || text.contains("к")) {
            mul = 1_000L;
            text = text.replaceAll("(тыс|k|к)", "").trim();
            hasSuffix = true;
        }

        if (hasSuffix) {
            text = text.replace(" ", "").replace(",", ".");
            try {
                double val = Double.parseDouble(text);
                return (long) (val * mul);
            } catch (NumberFormatException e) {
                return 0L;
            }
        } else {
            String digits = text.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0L;
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
    }

    public static long parseLotPriceFromLine(String line) {
        if (line == null || line.isBlank()) return 0L;
        String stripped = strip(line);

        if (stripped.contains("{") && stripped.contains("\"")) {
            try {
                com.google.gson.JsonElement elem = com.google.gson.JsonParser.parseString(stripped);
                StringBuilder sb = new StringBuilder();
                extractTextFromJson(elem, sb);
                if (!sb.isEmpty()) {
                    stripped = strip(sb.toString());
                }
            } catch (Exception ignored) {}
        }

        java.util.regex.Matcher m = PRICE_KEYWORD_PATTERN.matcher(stripped);
        if (m.find()) {
            long p = parseMoney(m.group(1));
            if (p > 0) return p;
        }

        java.util.regex.Matcher c = CURRENCY_PATTERN.matcher(stripped);
        if (c.find()) {
            String match = c.group(1) != null ? c.group(1) : c.group(2);
            long p = parseMoney(match);
            if (p > 0) return p;
        }

        return 0L;
    }

    private static void extractTextFromJson(com.google.gson.JsonElement elem, StringBuilder sb) {
        if (elem == null) return;
        if (elem.isJsonObject()) {
            com.google.gson.JsonObject obj = elem.getAsJsonObject();
            if (obj.has("text")) {
                sb.append(obj.get("text").getAsString());
            }
            if (obj.has("extra")) {
                for (com.google.gson.JsonElement child : obj.getAsJsonArray("extra")) {
                    extractTextFromJson(child, sb);
                }
            }
        } else if (elem.isJsonArray()) {
            for (com.google.gson.JsonElement child : elem.getAsJsonArray()) {
                extractTextFromJson(child, sb);
            }
        } else if (elem.isJsonPrimitive()) {
            sb.append(elem.getAsString());
        }
    }

    public static long readLotPrice(Slot slot) {
        if (slot == null || !slot.hasStack()) return 0L;
        return readLotPrice(slot.getStack());
    }

    public static long readLotPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;

        java.util.List<String> loreLines = new java.util.ArrayList<>();

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                loreLines.add(line.getString());
            }
        }

        net.minecraft.component.type.NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            net.minecraft.nbt.NbtCompound nbt = customData.copyNbt();
            if (nbt.contains("display")) {
                net.minecraft.nbt.NbtCompound display = nbt.getCompoundOrEmpty("display");
                if (display.contains("Lore")) {
                    net.minecraft.nbt.NbtList list = display.getListOrEmpty("Lore");
                    for (int i = 0; i < list.size(); i++) {
                        list.getString(i).ifPresent(loreLines::add);
                    }
                }
            }
        }

        if (loreLines.isEmpty()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc != null && mc.player != null) {
                try {
                    java.util.List<Text> tooltip = stack.getTooltip(
                            net.minecraft.item.Item.TooltipContext.DEFAULT,
                            mc.player,
                            net.minecraft.item.tooltip.TooltipType.BASIC
                    );
                    if (tooltip != null) {
                        for (Text t : tooltip) {
                            loreLines.add(t.getString());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (loreLines.isEmpty()) return 0L;

        long fallbackPrice = 0L;
        for (int i = 0; i < loreLines.size(); i++) {
            String line = loreLines.get(i);
            long price = parseLotPriceFromLine(line);
            if (price > 0) {
                String stripped = strip(line).toLowerCase(Locale.ROOT);
                if (stripped.contains("цена") && !stripped.contains("за")) {
                    return price;
                }
                if (fallbackPrice == 0L) {
                    fallbackPrice = price;
                }
            } else {
                String stripped = strip(line).toLowerCase(Locale.ROOT);
                if ((stripped.endsWith("цена:") || stripped.endsWith("price:") || stripped.endsWith("стоимость:"))
                        && i + 1 < loreLines.size()) {
                    long nextPrice = parseLotPriceFromLine(loreLines.get(i + 1));
                    if (nextPrice > 0) return nextPrice;
                }
            }
        }

        return fallbackPrice;
    }

    public static boolean isValidLot(Slot slot) {
        if (slot == null || !slot.hasStack()) return false;
        ItemStack stack = slot.getStack();
        if (stack.isOf(Items.GREEN_STAINED_GLASS_PANE)
                || stack.isOf(Items.RED_STAINED_GLASS_PANE)
                || stack.isOf(Items.GRAY_STAINED_GLASS_PANE)
                || stack.isOf(Items.BLACK_STAINED_GLASS_PANE)
                || stack.isOf(Items.LIME_STAINED_GLASS_PANE)) {
            return false;
        }
        return readLotPrice(slot) > 0L || !stack.getName().getString().isBlank();
    }

    public static boolean matchesName(ItemStack stack, String query) {
        if (stack == null || stack.isEmpty() || query == null || query.isBlank()) return false;
        String q = normalize(query);
        String name = normalize(stack.getName().getString());
        return name.contains(q) || q.contains(name);
    }

    public static boolean isAuctionScreen(GenericContainerScreen screen) {
        if (screen == null) return false;
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (title.contains("аукцион") || title.contains("auction") || title.contains("поиск")) return true;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int limit = Math.min(45, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            if (readLotPrice(handler.getSlot(i)) > 0L) return true;
        }
        return false;
    }

    public static boolean isConfirmScreen(GenericContainerScreen screen) {
        if (screen == null) return false;
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (title.contains("подтверждение") || title.contains("confirm")) return true;
        return findConfirmSlot(screen.getScreenHandler()) >= 0;
    }

    public static boolean isSellGui(GenericContainerScreen screen) {
        if (screen == null) return false;
        String title = strip(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        return title.contains("продажа") || title.contains("sellgui") || title.contains("sell gui");
    }

    public static int containerSlots(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        return Math.max(0, Math.min(handler.getRows() * 9, handler.slots.size()));
    }

    public static int findConfirmSlot(ScreenHandler handler) {
        if (handler == null) return -1;
        int limit = Math.min(handler.slots.size(), Math.max(0, handler.slots.size() - 36));
        for (int i = limit - 1; i >= 0; i--) {
            ItemStack stack = handler.getSlot(i).getStack();
            String name = strip(stack.getName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains("купить")
                    || stack.isOf(Items.LIME_STAINED_GLASS_PANE)
                    || stack.isOf(Items.GREEN_STAINED_GLASS_PANE)
                    || stack.isOf(Items.LIME_DYE)
                    || stack.isOf(Items.GREEN_DYE)) {
                return i;
            }
        }
        return -1;
    }

    public static int findSellButton(GenericContainerScreen screen) {
        int slots = containerSlots(screen);
        int fallback = -1;
        for (int i = slots - 1; i >= 0; i--) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (!stack.isEmpty() && (stack.isOf(Items.LIME_DYE) || stack.isOf(Items.GREEN_DYE)
                    || stack.isOf(Items.LIME_STAINED_GLASS_PANE) || stack.isOf(Items.GREEN_STAINED_GLASS_PANE))) {
                return i;
            }
            String name = strip(stack.getName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains("выстав") || name.contains("продать") || name.contains("подтверд")) {
                fallback = i;
            }
        }
        return fallback;
    }

    public static int findEmptySellSlot(GenericContainerScreen screen) {
        int limit = Math.min(9, containerSlots(screen));
        for (int i = 0; i < limit; i++) {
            if (!screen.getScreenHandler().getSlot(i).hasStack()) return i;
        }
        return -1;
    }

    public static int findMatchingSlot(ScreenHandler handler, int start, Item item, String searchName) {
        for (int i = start; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            if (item != Items.AIR && !stack.isOf(item)) continue;
            if (!matchesName(stack, searchName)) continue;
            return i;
        }
        return -1;
    }

    public record PurchaseInfo(String searchName, String displayName, Item item) {
    }
}
