package ez.minar.utils.render;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.rendering.Vertex;

import java.util.List;

public final class FiguraBounds {
    private FiguraBounds() {
    }

    public static Box get(PlayerEntity player) {
        Avatar avatar = AvatarManager.getLoadedAvatar(player.getUuid());
        if (avatar == null || avatar.renderer == null || avatar.renderer.root == null) {
            return player.getBoundingBox();
        }

        double[] bounds = {
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        collect(avatar.renderer.root, true, bounds);
        if (!Double.isFinite(bounds[0]) || !Double.isFinite(bounds[3])) {
            return player.getBoundingBox();
        }

        Box vanilla = player.getBoundingBox();
        return new Box(
                Math.min(vanilla.minX, bounds[0]),
                Math.min(vanilla.minY, bounds[1]),
                Math.min(vanilla.minZ, bounds[2]),
                Math.max(vanilla.maxX, bounds[3]),
                Math.max(vanilla.maxY, bounds[4]),
                Math.max(vanilla.maxZ, bounds[5])
        );
    }

    private static void collect(FiguraModelPart part, boolean parentVisible, double[] bounds) {
        boolean visible = parentVisible && part.customization.visible;
        if (!visible || part.parentType.isSeparate) {
            return;
        }

        if (part.vertices != null) {
            for (List<Vertex> vertices : part.vertices.values()) {
                for (Vertex vertex : vertices) {
                    FiguraVec3 point = part.savedPartToWorldMat.apply(
                            (double) vertex.x, (double) vertex.y, (double) vertex.z
                    );
                    bounds[0] = Math.min(bounds[0], point.x);
                    bounds[1] = Math.min(bounds[1], point.y);
                    bounds[2] = Math.min(bounds[2], point.z);
                    bounds[3] = Math.max(bounds[3], point.x);
                    bounds[4] = Math.max(bounds[4], point.y);
                    bounds[5] = Math.max(bounds[5], point.z);
                }
            }
        }

        for (FiguraModelPart child : part.children) {
            collect(child, visible, bounds);
        }
    }
}
