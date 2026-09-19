package ez.minar.system.autobuy.ui;

import ez.minar.system.autobuy.items.AutoBuyableItem;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PurchaseHistoryWindow {
    private static final List<PurchaseRecord> purchases = new CopyOnWriteArrayList<>();

    public static void addPurchase(AutoBuyableItem item, int price) {
        String cleanName = item.getDisplayName();
        long currentTime = System.currentTimeMillis();
        for (PurchaseRecord record : purchases) {
            if (record.getItemName().equals(cleanName) && record.getPrice() == price && (currentTime - record.getTimestamp()) < 1000) {
                return;
            }
        }
        ItemStack stackCopy = item.createItemStack();
        stackCopy.setCount(1);
        purchases.add(0, new PurchaseRecord(stackCopy, cleanName, price, item.getSettings().getMinQuantity(), currentTime));
        if (purchases.size() > 50) {
            purchases.remove(purchases.size() - 1);
        }
    }

    public static void addPurchase(String itemName, int price) {
        String cleanName = itemName;
        long currentTime = System.currentTimeMillis();
        for (PurchaseRecord record : purchases) {
            if (record.getItemName().equals(cleanName) && record.getPrice() == price && (currentTime - record.getTimestamp()) < 1000) {
                return;
            }
        }
        purchases.add(0, new PurchaseRecord(null, cleanName, price, 1, currentTime));
        if (purchases.size() > 50) {
            purchases.remove(purchases.size() - 1);
        }
    }

    public static List<PurchaseRecord> getPurchases() {
        return Collections.unmodifiableList(purchases);
    }

    public static void clear() {
        purchases.clear();
    }

    @Getter
    public static class PurchaseRecord {
        private final ItemStack itemStack;
        private final String itemName;
        private final int price;
        private final int quantity;
        private final long timestamp;

        public PurchaseRecord(ItemStack itemStack, String itemName, int price, int quantity, long timestamp) {
            this.itemStack = itemStack;
            this.itemName = itemName;
            this.price = price;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
    }
}
