package ez.minar.system.autobuy.originalitems;

import ez.minar.system.autobuy.items.AutoBuyableItem;
import ez.minar.system.autobuy.items.list.DonatorProvider;
import ez.minar.system.autobuy.settings.AutoBuySettingsManager;
import ez.minar.system.autobuy.util.krushprovider.KrushProvider;
import ez.minar.system.autobuy.items.list.MiscProvider;
import ez.minar.system.autobuy.items.list.PotionProvider;
import ez.minar.system.autobuy.items.list.SphereProvider;
import ez.minar.system.autobuy.items.list.TalismanProvider;

import java.util.ArrayList;
import java.util.List;

public class ItemRegistry {
    private static List<AutoBuyableItem> allItems = null;
    private static boolean settingsLoaded = false;

    public static void ensureSettingsLoaded() {
        if (!settingsLoaded) {
            settingsLoaded = true;
        }
    }

    public static List<AutoBuyableItem> getAllItems() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            allItems.addAll(getKrush());
            allItems.addAll(getTalismans());
            allItems.addAll(getSpheres());
            allItems.addAll(getMisc());
            allItems.addAll(getDonator());
            allItems.addAll(getPotions());

            for (AutoBuyableItem item : allItems) {
                AutoBuySettingsManager.getInstance().loadSettings(item.getDisplayName(), item.getSettings());
            }
        }
        return allItems;
    }

    public static void reloadSettings() {
        if (allItems != null) {
            for (AutoBuyableItem item : allItems) {
                AutoBuySettingsManager.getInstance().loadSettings(item.getDisplayName(), item.getSettings());
            }
        }
    }

    public static List<AutoBuyableItem> getKrush() {
        return KrushProvider.getKrush();
    }

    public static List<AutoBuyableItem> getTalismans() {
        return TalismanProvider.getTalismans();
    }

    public static List<AutoBuyableItem> getSpheres() {
        return SphereProvider.getSpheres();
    }

    public static List<AutoBuyableItem> getMisc() {
        return MiscProvider.getMisc();
    }

    public static List<AutoBuyableItem> getDonator() {
        return DonatorProvider.getDonator();
    }

    public static List<AutoBuyableItem> getPotions() {
        return PotionProvider.getPotions();
    }
}
