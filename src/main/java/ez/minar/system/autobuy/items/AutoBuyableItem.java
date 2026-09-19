package ez.minar.system.autobuy.items;

import ez.minar.system.autobuy.settings.AutoBuyItemSettings;
import net.minecraft.item.ItemStack;

public interface AutoBuyableItem {
    String getDisplayName();
    ItemStack createItemStack();
    int getPrice();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    AutoBuyItemSettings getSettings();
}
