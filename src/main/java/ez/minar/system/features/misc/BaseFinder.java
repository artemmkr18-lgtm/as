package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.helpers.FarmNavigator;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

@NewFunction(name = "BaseFinder", desc = "Ищет базы в соседних чанках", category = Category.MISC)
public class BaseFinder extends Function {
    public final MultiListSetting blocks = new MultiListSetting(
            "Блоки",
            "Сундуки",
            "Шалкера",
            "Бочки",
            "Печка",
            "Эндер сундук"
    );
    public final BooleanSetting minecarts = new BooleanSetting("Искать вагонетки", true);
    public final BooleanSetting walkToFind = new BooleanSetting("Идти к находке", true);
    public final BooleanSetting stopNearPlayers = new BooleanSetting("Выкл при игроке", true);
    public final BooleanSetting lightCheck = new BooleanSetting("Проверки на свет", false);
    public final NumberSetting chunkRadius = new NumberSetting("Радиус чанков", 4.0, 1.0, 8.0, 1.0);

    private final Set<BlockPos> foundBlocks = new HashSet<>();
    private final Set<Integer> foundMinecarts = new HashSet<>();

    private int scanTick;
    private BlockPos walkTarget;

    public BaseFinder() {
        addSettings(blocks, minecarts, walkToFind, stopNearPlayers, lightCheck, chunkRadius);
    }

    @Override
    public void onEnable() {
        foundBlocks.clear();
        foundMinecarts.clear();
        scanTick = 0;
        walkTarget = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        FarmNavigator.stop();
        walkTarget = null;
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (nullCheck.all()) {
            return;
        }

        if (stopNearPlayers.isEnabled() && checkNearbyPlayers()) {
            return;
        }

        if (walkTarget != null && walkToFind.isEnabled()) {
            if (FarmNavigator.goTo(Vec3d.ofCenter(walkTarget), 2.5)) {
                walkTarget = null;
            }
        }

        if (++scanTick < 10) {
            return;
        }
        scanTick = 0;

        scanChunks();
        if (minecarts.isEnabled()) {
            scanMinecarts();
        }
    }

    private void scanChunks() {
        ChunkPos playerChunk = mc.player.getChunkPos();
        int radius = (int) chunkRadius.getValue();

        for (int cx = playerChunk.x - radius; cx <= playerChunk.x + radius; cx++) {
            for (int cz = playerChunk.z - radius; cz <= playerChunk.z + radius; cz++) {
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!matchesSetting(blockEntity)) {
                        continue;
                    }

                    BlockPos pos = blockEntity.getPos();
                    if (foundBlocks.contains(pos)) {
                        continue;
                    }
                    if (lightCheck.isEnabled() && mc.world.getLightLevel(LightType.BLOCK, pos) < 8) {
                        continue;
                    }

                    foundBlocks.add(pos);
                    notifyFind(describe(blockEntity), pos);
                }
            }
        }
    }

    private void scanMinecarts() {
        double maxDist = chunkRadius.getValue() * 16.0;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ChestMinecartEntity) && !(entity instanceof HopperMinecartEntity)) {
                continue;
            }
            if (foundMinecarts.contains(entity.getId())) {
                continue;
            }
            if (entity.distanceTo(mc.player) > maxDist) {
                continue;
            }

            foundMinecarts.add(entity.getId());
            String label = entity instanceof ChestMinecartEntity ? "Грузовая вагонетка" : "Вагонетка с воронкой";
            BlockPos pos = entity.getBlockPos();
            notifyFind(label, pos);
        }
    }

    private boolean matchesSetting(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) {
            return blocks.isEnabled("Сундуки");
        }
        if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return blocks.isEnabled("Шалкера");
        }
        if (blockEntity instanceof BarrelBlockEntity) {
            return blocks.isEnabled("Бочки");
        }
        if (blockEntity instanceof FurnaceBlockEntity) {
            return blocks.isEnabled("Печка");
        }
        if (blockEntity instanceof EnderChestBlockEntity) {
            return blocks.isEnabled("Эндер сундук");
        }
        return false;
    }

    private String describe(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) {
            return "Сундук";
        }
        if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return "Шалкер";
        }
        if (blockEntity instanceof BarrelBlockEntity) {
            return "Бочка";
        }
        if (blockEntity instanceof FurnaceBlockEntity) {
            return "Печка";
        }
        if (blockEntity instanceof EnderChestBlockEntity) {
            return "Эндер сундук";
        }
        return "Неизвестный блок";
    }

    private void notifyFind(String type, BlockPos pos) {
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[BaseFinder] ").formatted(Formatting.DARK_PURPLE)
                    .append(Text.literal("Найден ").formatted(Formatting.GREEN))
                    .append(Text.literal(type).formatted(Formatting.WHITE))
                    .append(Text.literal(String.format(" на XYZ: %d %d %d", pos.getX(), pos.getY(), pos.getZ())).formatted(Formatting.GREEN)));
        }

        if (walkToFind.isEnabled()) {
            walkTarget = pos.toImmutable();
        }
    }

    private boolean checkNearbyPlayers() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[BaseFinder] ").formatted(Formatting.DARK_RED)
                        .append(Text.literal("ОБНАРУЖЕН ИГРОК: ").formatted(Formatting.RED))
                        .append(Text.literal(player.getName().getString()).formatted(Formatting.WHITE)));
            }
            toggle();
            return true;
        }
        return false;
    }
}
