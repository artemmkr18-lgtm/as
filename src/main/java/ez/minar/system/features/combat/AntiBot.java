package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.ModeSetting;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NewFunction(name = "AntiBot", desc = "Обнаруживает ботов по паттернам брони и инвентаря", category = Category.COMBAT)
public class AntiBot extends Function {
    private final ModeSetting mode = new ModeSetting("Режим", "Matrix/Rw", "CakeWorld");
    private final List<PlayerEntity> bots = new ArrayList<>();

    public AntiBot() {
        addSettings(mode);
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            boolean isBot = false;

            if (mode.getActiveMode().equals("Matrix/Rw")) {
                isBot = isBotLike(player);
            } else if (mode.getActiveMode().equals("CakeWorld")) {
                isBot = isBotCakeWorld(player);
            }

            if (isBot) {
                if (!bots.contains(player)) bots.add(player);
                if (mode.getActiveMode().equals("CakeWorld")) {
                    mc.world.removeEntity(player.getId(), RemovalReason.DISCARDED);
                }
            } else {
                bots.remove(player);
            }
        }

        bots.removeIf(player -> player == null || !player.isAlive() || !mc.world.getPlayers().contains(player));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        bots.clear();
    }

    public boolean isBot(PlayerEntity player) {
        return bots.contains(player);
    }

    private boolean isBotLike(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack armorPiece = player.getEquippedStack(slot);

            if (!armorPiece.isEmpty()
                    && armorPiece.isEnchantable()
                    && !armorPiece.isDamaged()
                    && hasLeatherOrIronArmor(player)
                    && !player.getMainHandStack().isEmpty()
                    && player.getOffHandStack().isEmpty()
                    && player.getHungerManager().getFoodLevel() == 20) {
                return true;
            }
        }

        return false;
    }

    private boolean hasLeatherOrIronArmor(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.FEET).getItem() == Items.LEATHER_BOOTS
                || player.getEquippedStack(EquipmentSlot.LEGS).getItem() == Items.LEATHER_LEGGINGS
                || player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.LEATHER_CHESTPLATE
                || player.getEquippedStack(EquipmentSlot.HEAD).getItem() == Items.LEATHER_HELMET
                || player.getEquippedStack(EquipmentSlot.FEET).getItem() == Items.IRON_BOOTS
                || player.getEquippedStack(EquipmentSlot.LEGS).getItem() == Items.IRON_LEGGINGS
                || player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.IRON_CHESTPLATE
                || player.getEquippedStack(EquipmentSlot.HEAD).getItem() == Items.IRON_HELMET;
    }

    private boolean isBotCakeWorld(PlayerEntity player) {
        if (mc.player == null || mc.player.networkHandler == null) return false;
        PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(player.getUuid());
        if (entry == null) return true;

        String name = player.getGameProfile().name();
        if (name == null) return true;

        return !getOnlinePlayers().contains(name);
    }

    private final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\w{3,16}$");

    private List<String> getOnlinePlayers() {
        if (mc.player == null || mc.player.networkHandler == null) return new ArrayList<>();
        return mc.player.networkHandler.getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(GameProfile::name)
                .filter(name -> pattern.matcher(name).matches())
                .collect(Collectors.toList());
    }
}
