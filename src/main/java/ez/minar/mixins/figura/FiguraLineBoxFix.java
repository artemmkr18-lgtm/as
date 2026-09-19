package ez.minar.mixins.figura;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.figuramc.figura.model.rendering.ImmediateFiguraRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Figura 0.1.5 does not provide the line-width vertex attribute added in 1.21.11.
 */
@Mixin(value = ImmediateFiguraRenderer.class, remap = false)
public class FiguraLineBoxFix {

    @Inject(method = "renderLineBox", at = @At("HEAD"), cancellable = true)
    private static void minar$renderLineBox(
            MatrixStack.Entry pose,
            VertexConsumer vertices,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a,
            CallbackInfo ci
    ) {
        line(pose, vertices, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(pose, vertices, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(pose, vertices, x1, y1, z1, x1, y1, z2, r, g, b, a);
        line(pose, vertices, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(pose, vertices, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(pose, vertices, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(pose, vertices, x1, y2, z1, x1, y2, z2, r, g, b, a);
        line(pose, vertices, x1, y1, z2, x2, y1, z2, r, g, b, a);
        line(pose, vertices, x1, y1, z2, x1, y2, z2, r, g, b, a);
        line(pose, vertices, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(pose, vertices, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(pose, vertices, x1, y2, z2, x2, y2, z2, r, g, b, a);
        ci.cancel();
    }

    private static void line(
            MatrixStack.Entry pose,
            VertexConsumer vertices,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a
    ) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;

        vertices.vertex(pose, (float) x1, (float) y1, (float) z1)
                .color(r, g, b, a).normal(pose, nx, ny, nz).lineWidth(1.0F);
        vertices.vertex(pose, (float) x2, (float) y2, (float) z2)
                .color(r, g, b, a).normal(pose, nx, ny, nz).lineWidth(1.0F);
    }
}
