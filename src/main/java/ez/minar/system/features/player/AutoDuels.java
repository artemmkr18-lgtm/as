package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.GameMessageEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NewFunction(name = "AutoDuels", desc = "Автоматически отправляет игрокам приглашения на дуэль", category = Category.PLAYER)
public class AutoDuels extends Function {
    private final Pattern pattern = Pattern.compile("^\\w{3,16}$");

    private final ModeSetting mode = new ModeSetting("Режим", "balls", "shield", "thorns", "bow", "totems", "nodebuff", "classic", "cheaterparadise", "netherite");
    private final NumberSetting slowTime = new NumberSetting("Скорость отправки", 500, 300, 1000, 100);
    private final BooleanSetting moneyDuel = new BooleanSetting("Играть на деньги", false);
    private final ModeSetting money = new ModeSetting("Монет", "10000", "100", "500", "1000", "5000", "50000", "100000");

    private double lastPosX, lastPosY, lastPosZ;
    private final List<String> sent = new ArrayList<>();
    private final Counter counter = new Counter();
    private final Counter clearCounter = new Counter();
    private final Counter choiceCounter = new Counter();
    private final Counter confirmCounter = new Counter();

    public AutoDuels() {
        addSettings(mode, slowTime, moneyDuel, money);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (nullCheck.all()) {
            toggle();
            return;
        }

        counter.reset();
        clearCounter.reset();
        choiceCounter.reset();
        confirmCounter.reset();
        sent.clear();
        lastPosX = mc.player.getX();
        lastPosY = mc.player.getY();
        lastPosZ = mc.player.getZ();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;
        handleUpdate();
    }

    @EventHandler
    public void onGameMessage(GameMessageEvent event) {
        String text = event.getPacket().content().getString();
        if ((text.contains("начало") && text.contains("через") && text.contains("секунд!"))
                || text.contains("дуэли » во время поединка запрещено использовать команды")) {
            toggle();
        }
    }

    private void handleUpdate() {
        List<String> players = getOnlinePlayers();

        double distance = Math.sqrt(
                Math.pow(lastPosX - mc.player.getX(), 2) +
                        Math.pow(lastPosY - mc.player.getY(), 2) +
                        Math.pow(lastPosZ - mc.player.getZ(), 2)
        );

        if (distance > 500) {
            toggle();
            return;
        }

        lastPosX = mc.player.getX();
        lastPosY = mc.player.getY();
        lastPosZ = mc.player.getZ();

        if (clearCounter.hasReached(800L * players.size())) {
            sent.clear();
            clearCounter.reset();
        }

        for (String player : players) {
            if (!sent.contains(player) && !player.equals(mc.player.getGameProfile().name()) && counter.hasReached((long) slowTime.getValue())) {
                if (moneyDuel.isEnabled()) {
                    mc.player.networkHandler.sendChatCommand("duel " + player + " " + money.getActiveMode());
                } else {
                    mc.player.networkHandler.sendChatCommand("duel " + player);
                }

                sent.add(player);
                counter.reset();
            }
        }

        if (mc.currentScreen != null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest) {
            String title = mc.currentScreen.getTitle().getString();

            if (title.contains("Выбор набора (1/1)") && choiceCounter.hasReached(150)) {
                mc.interactionManager.clickSlot(chest.syncId, getModeSlot(), 0, SlotActionType.QUICK_MOVE, mc.player);
                choiceCounter.reset();
            } else if (title.contains("Настройка поединка") && confirmCounter.hasReached(150)) {
                mc.interactionManager.clickSlot(chest.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                confirmCounter.reset();
            }
        }
    }

    private int getModeSlot() {
        return switch (mode.getActiveMode()) {
            case "shield" -> 0;
            case "thorns" -> 1;
            case "bow" -> 2;
            case "totems" -> 3;
            case "nodebuff" -> 4;
            case "classic" -> 6;
            case "cheaterparadise" -> 7;
            case "netherite" -> 8;
            default -> 5;
        };
    }

    private List<String> getOnlinePlayers() {
        return mc.player.networkHandler.getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(GameProfile::name)
                .filter(name -> pattern.matcher(name).matches())
                .collect(Collectors.toList());
    }

    private static class Counter {
        private long lastTime = System.currentTimeMillis();

        private boolean hasReached(long delay) {
            return System.currentTimeMillis() - lastTime >= delay;
        }

        private void reset() {
            lastTime = System.currentTimeMillis();
        }
    }
}
