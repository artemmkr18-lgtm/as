package ez.minar.utils.render;

import ez.minar.mixins.interfaces.IGameRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WorldToScreen {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private WorldToScreen() {}

    public static float[] project(Vec3d worldPos) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || mc.player == null) return null;

        Vec3d camPos = camera.getCameraPos();
        Vector3f pos = new Vector3f(
                (float) (worldPos.x - camPos.x),
                (float) (worldPos.y - camPos.y),
                (float) (worldPos.z - camPos.z)
        );

        Quaternionf rotation = new Quaternionf(camera.getRotation()).conjugate();
        pos.rotate(rotation);

        if (pos.z >= 0) return null;

        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        double fov = ((IGameRenderer) mc.gameRenderer).minar$getFov(camera, tickProgress, true);

        int fixedW = RenderUtil.getFixedScaledWidth();
        int fixedH = RenderUtil.getFixedScaledHeight();
        float halfW = fixedW * 0.5f;
        float halfH = fixedH * 0.5f;

        float factor = (float) (halfH / Math.tan(Math.toRadians(fov) * 0.5)) / -pos.z;

        float screenX = halfW + pos.x * factor;
        float screenY = halfH - pos.y * factor;

        return new float[]{screenX, screenY, factor};
    }

    public static Vec3d getInterpolatedPos(Entity entity, float tickDelta) {
        return entity.getLerpedPos(tickDelta);
    }
}
