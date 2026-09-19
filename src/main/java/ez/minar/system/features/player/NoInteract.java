package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

@NewFunction(name = "NoInteract", desc = "Блокирует взаимодействие с выбранными объектами", category = Category.PLAYER)
public class NoInteract extends Function {
    private final BooleanSetting stand = new BooleanSetting("Стойки для брони", true);
    private final MultiListSetting blocks = new MultiListSetting(
            "Блоки",
            "Сундуки",
            "Двери",
            "Воронки",
            "Кнопки",
            "Раздатчики",
            "Нотные блоки",
            "Верстаки",
            "Люки",
            "Печки",
            "Калитки",
            "Наковальни",
            "Рычаги"
    );
    private final BooleanSetting onlyWithAttackAura = new BooleanSetting("Только с AttackAura", false);
    private final Map<String, Predicate<Block>> blockPredicates = new LinkedHashMap<>();

    public NoInteract() {
        blockPredicates.put("Сундуки", block -> matchesBlock(block, "chest"));
        blockPredicates.put("Двери", block -> matchesBlock(block, "door"));
        blockPredicates.put("Воронки", block -> matchesBlock(block, "hopper"));
        blockPredicates.put("Кнопки", block -> matchesBlock(block, "button"));
        blockPredicates.put("Раздатчики", block -> matchesBlock(block, "dispenser"));
        blockPredicates.put("Нотные блоки", block -> matchesBlock(block, "note"));
        blockPredicates.put("Верстаки", block -> matchesBlock(block, "crafting_table"));
        blockPredicates.put("Люки", block -> matchesBlock(block, "trapdoor"));
        blockPredicates.put("Печки", block -> matchesBlock(block, "furnace"));
        blockPredicates.put("Калитки", block -> matchesBlock(block, "fence_gate"));
        blockPredicates.put("Наковальни", block -> matchesBlock(block, "anvil"));
        blockPredicates.put("Рычаги", block -> matchesBlock(block, "lever"));

        addSettings(stand, blocks, onlyWithAttackAura);
    }

    public boolean shouldCancelBlock(BlockPos pos) {
        if (!canWork()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null || pos == null) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        for (Map.Entry<String, Predicate<Block>> entry : blockPredicates.entrySet()) {
            if (blocks.isEnabled(entry.getKey()) && entry.getValue().test(block)) {
                return true;
            }
        }

        return false;
    }

    public boolean shouldCancelEntity(Entity entity) {
        return canWork()
                && stand.isEnabled()
                && entity instanceof ArmorStandEntity;
    }

    private boolean matchesBlock(Block block, String idPart) {
        String registryId = Registries.BLOCK.getId(block).getPath();
        String translationKey = block.getTranslationKey();
        return registryId.contains(idPart) || translationKey.contains(idPart);
    }

    private boolean canWork() {
        if (!isEnabled()) {
            return false;
        }

        if (!onlyWithAttackAura.isEnabled()) {
            return true;
        }

        AttackAura attackAura = FunctionManager.getFunction(AttackAura.class);
        return attackAura != null && attackAura.isEnabled();
    }
}
