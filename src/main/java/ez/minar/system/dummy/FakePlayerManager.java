package ez.minar.system.dummy;

import ez.minar.system.commands.CommandFeedback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FakePlayerManager {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Map<String, FakeDummyPlayer> DUMMIES = new LinkedHashMap<>();

    private FakePlayerManager() {
    }

    public static boolean addDummy(String name) {
        if (MC.world == null || MC.player == null) {
            CommandFeedback.message("Мир не загружен!", Formatting.RED);
            return false;
        }

        String cleanName = (name == null || name.isBlank()) ? "NeuroDummy" : name.trim();
        String lower = cleanName.toLowerCase(Locale.ROOT);

        if (DUMMIES.containsKey(lower)) {
            removeDummy(cleanName);
        }

        UUID uuid = UUID.nameUUIDFromBytes(("MinarDummy:" + lower).getBytes(StandardCharsets.UTF_8));
        FakeDummyPlayer dummy = new FakeDummyPlayer(MC.world, cleanName, uuid);

        dummy.copyPositionAndRotation(MC.player);
        dummy.setYaw(MC.player.getYaw());
        dummy.setPitch(MC.player.getPitch());
        dummy.headYaw = MC.player.headYaw;
        dummy.bodyYaw = MC.player.bodyYaw;

        if (!MC.player.getMainHandStack().isEmpty()) {
            dummy.setStackInHand(Hand.MAIN_HAND, MC.player.getMainHandStack().copy());
        }
        if (!MC.player.getOffHandStack().isEmpty()) {
            dummy.setStackInHand(Hand.OFF_HAND, MC.player.getOffHandStack().copy());
        }

        dummy.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 999999, 4, false, false));
        dummy.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 999999, 4, false, false));
        dummy.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 999999, 2, false, false));

        dummy.spawn();
        DUMMIES.put(lower, dummy);

        CommandFeedback.message("Нейро-дамми '" + cleanName + "' успешно создан на твоей позиции!", Formatting.GREEN);
        return true;
    }

    public static boolean removeDummy(String name) {
        if (name == null || name.isBlank()) {
            return clearAll();
        }

        String lower = name.trim().toLowerCase(Locale.ROOT);
        FakeDummyPlayer dummy = DUMMIES.remove(lower);
        if (dummy != null) {
            dummy.remove();
            CommandFeedback.message("Нейро-дамми '" + dummy.getName().getString() + "' удален!", Formatting.YELLOW);
            return true;
        } else {
            CommandFeedback.message("Нейро-дамми '" + name + "' не найден!", Formatting.RED);
            return false;
        }
    }

    public static boolean clearAll() {
        if (DUMMIES.isEmpty()) {
            CommandFeedback.message("Активных нейро-дамми нет.", Formatting.GRAY);
            return false;
        }
        int count = DUMMIES.size();
        for (FakeDummyPlayer dummy : DUMMIES.values()) {
            dummy.remove();
        }
        DUMMIES.clear();
        CommandFeedback.message("Удалено всех нейро-дамми (" + count + " шт.)", Formatting.YELLOW);
        return true;
    }

    public static List<String> getNames() {
        return new ArrayList<>(DUMMIES.values().stream().map(d -> d.getName().getString()).toList());
    }

    public static boolean isDummy(net.minecraft.entity.Entity entity) {
        return entity instanceof FakeDummyPlayer;
    }
}
