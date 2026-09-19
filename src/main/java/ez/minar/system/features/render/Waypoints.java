package ez.minar.system.features.render;

import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.managers.WaypointManager;
import ez.minar.system.menu.ThemeManager;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.Render3DUtils;
import ez.minar.utils.render.WorldToScreen;
import ez.minar.utils.render.msdf.Msdf;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.List;

public final class Waypoints {

    public static final Waypoints Instance = new Waypoints();

    private static final Identifier ARROW_TEXTURE = Identifier.of("minar", "images/world/arrow.png");
    private static final float ARROW_SIZE = 0.9F;
    private static final double ARROW_HEIGHT = 1.9;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private Waypoints() {
    }

    public static void renderWorld(WorldRenderContext context) {
        Instance.render(context);
    }

    private void render(WorldRenderContext context) {
        if (mc.world == null || mc.player == null) return;
        List<WaypointManager.Waypoint> waypoints = WaypointManager.getWaypoints();
        if (waypoints.isEmpty()) return;

        float bob = (float) Math.sin(System.currentTimeMillis() / 500.0) * 0.1F;
        Color color = ThemeManager.getThemeColor();
        Render3DUtils.TexturedBillboardBatch batch =
                Render3DUtils.shaderGlowTexturedBillboardNoDepthBatch(context, ARROW_TEXTURE);

        for (WaypointManager.Waypoint waypoint : waypoints) {
            double bx = Math.floor(waypoint.x());
            double by = Math.floor(waypoint.y());
            double bz = Math.floor(waypoint.z());
            Vec3d position = new Vec3d(bx + 0.5, by + ARROW_HEIGHT + bob, bz + 0.5);
            batch.renderShaderGlow(position, ARROW_SIZE, 0F,
                    color.getRed(), color.getGreen(), color.getBlue(), 0.95F);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        DrawContext context = event.getContext();
        if (context == null || mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        List<WaypointManager.Waypoint> waypoints = WaypointManager.getWaypoints();
        if (waypoints.isEmpty()) return;

        int screenW = RenderUtil.getFixedScaledWidth();
        int screenH = RenderUtil.getFixedScaledHeight();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        for (WaypointManager.Waypoint waypoint : waypoints) {
            double bx = Math.floor(waypoint.x());
            double by = Math.floor(waypoint.y());
            double bz = Math.floor(waypoint.z());

            Vec3d anchor = new Vec3d(bx + 0.5, by + ARROW_HEIGHT + ARROW_SIZE + 0.55, bz + 0.5);
            float[] screen = WorldToScreen.project(anchor);
            if (screen == null) continue;
            if (screen[0] < -80 || screen[0] > screenW + 80 || screen[1] < -80 || screen[1] > screenH + 80) continue;

            double dist = cameraPos.distanceTo(new Vec3d(bx + 0.5, by + 0.5, bz + 0.5));
            float scaleFactor = (float) Math.max(1.0, 9.0 / Math.max(dist, 3.0));
            float size = 8F * 0.7F * scaleFactor;
            float distSize = size * 0.8F;

            String name = waypoint.name();
            String distText = Math.round(dist) + "m";

            float nameWidth = Msdf.width(Msdf.SF_BOLD, name, size);
            float distWidth = Msdf.width(Msdf.SF_BOLD, distText, distSize);
            float nameHeight = Msdf.height(Msdf.SF_BOLD, size);

            float nameX = screen[0] - nameWidth * 0.5F;
            float nameY = screen[1] - nameHeight;
            float distX = screen[0] - distWidth * 0.5F;
            float distY = nameY + nameHeight + 1F;

            RenderUtil.text(context, Msdf.SF_BOLD, nameX + 0.7F, nameY + 0.7F, name, size, new Color(0, 0, 0, 150));
            RenderUtil.text(context, Msdf.SF_BOLD, nameX, nameY, name, size, new Color(245, 245, 248));
            RenderUtil.text(context, Msdf.SF_BOLD, distX, distY, distText, distSize, new Color(200, 200, 205));
        }
    }
}
