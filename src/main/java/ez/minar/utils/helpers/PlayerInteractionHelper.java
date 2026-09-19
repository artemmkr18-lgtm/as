package ez.minar.utils.helpers;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class PlayerInteractionHelper {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private PlayerInteractionHelper() {
    }

    public static boolean isPlayerInBlock(Block block) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        return isBoxInBlock(
                mc.player.getBoundingBox().contract(1.0E-3),
                block
        );
    }

    public static boolean isBoxInBlock(Box box, Block block) {
        return isBox(box, pos -> mc.world.getBlockState(pos).isOf(block));
    }

    public static boolean isBox(Box box, Predicate<BlockPos> predicate) {
        return streamBlockPos(box).anyMatch(predicate);
    }

    private static Stream<BlockPos> streamBlockPos(Box box) {
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);

        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);

        Iterable<BlockPos> iterable = () -> BlockPos.iterate(
                minX, minY, minZ,
                maxX, maxY, maxZ
        ).iterator();

        return StreamSupport.stream(iterable.spliterator(), false);
    }
}