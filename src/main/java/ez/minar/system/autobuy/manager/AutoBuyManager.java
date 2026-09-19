package ez.minar.system.autobuy.manager;

import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.originalitems.ItemRegistry;
import java.util.List;

public class AutoBuyManager {
    private static AutoBuyManager instance;
    private boolean enabled = false;

    private AutoBuyManager() {}

    public static AutoBuyManager getInstance() {
        if (instance == null) {
            instance = new AutoBuyManager();
        }
        return instance;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<AutoBuyableItem> getAllItems() {
        return ItemRegistry.getAllItems();
    }

    public void toggleItem(AutoBuyableItem item) {
        item.setEnabled(!item.isEnabled());
    }
}
