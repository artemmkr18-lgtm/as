package ez.minar.system.features.render;

import ez.minar.mixins.interfaces.IGameRenderer;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.JumpCirclePipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@NewFunction(name = "JumpCircles", desc = "Эффект ударных кругов под ногами при прыжке", category = Category.RENDER)
public class JumpCircles extends Function {
    public static JumpCircles Instance;

    private final NumberSetting radius = new NumberSetting("Радиус", 1.5, 0.5, 2.5, 0.1);
    private final NumberSetting width = new NumberSetting("Толщина", 1.0, 0.05, 1.5, 0.05);
    private final NumberSetting strength = new NumberSetting("Сила", 2.0, 0.1, 2.0, 0.1);
    private final NumberSetting expand = new NumberSetting("Расширение", 800.0, 100.0, 800.0, 25.0);
    private final NumberSetting fade = new NumberSetting("Затухание", 1500.0, 200.0, 1500.0, 25.0);
    private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", true);
    private final ColorSetting color = new ColorSetting("Цвет", new Color(255, 255, 255));
    private final BooleanSetting onlySelf = new BooleanSetting("Только себя", true);

    private final CopyOnWriteArrayList<Circle> circles = new CopyOnWriteArrayList<>();
    private final Map<UUID, PlayerTracker> trackers = new HashMap<>();

    public JumpCircles() {
        Instance = this;
        addSettings(radius, width, strength, expand, fade, themeColor, color, onlySelf);
        themeColor.runnable(this::updateVisibility);
        updateVisibility();
    }

    private void updateVisibility() {
        color.setVisible(!themeColor.isEnabled());
    }

    @Override
    public void onEnable() {
        circles.clear();
        trackers.clear();
    }

    @Override
    public void onDisable() {
        circles.clear();
        trackers.clear();
    }

    @EventHandler
    private void onUpdate(UpdateEvent event) {
        if (mc.world == null || mc.player == null) {
            trackers.clear();
            circles.clear();
            return;
        }

        HashSet<UUID> active = new HashSet<>();
        Iterable<? extends PlayerEntity> players = onlySelf.isEnabled() ? List.of(mc.player) : mc.world.getPlayers();

        for (PlayerEntity player : players) {
            UUID uuid = player.getUuid();
            active.add(uuid);

            boolean onGround = player.isOnGround();
            double y = player.getY();

            PlayerTracker tracker = trackers.get(uuid);
            if (tracker == null) {
                trackers.put(uuid, new PlayerTracker(onGround, player.getX(), y, player.getZ(), y));
                continue;
            }

            if (onGround) {
                tracker.lastGroundX = player.getX();
                tracker.lastGroundY = y;
                tracker.lastGroundZ = player.getZ();
            }

            boolean jumped = (y - tracker.lastY > 0.02 || player.getVelocity().y > 0.0);
            if (tracker.wasOnGround && !onGround && jumped) {
                double groundY = findGroundY(tracker.lastGroundX, tracker.lastGroundY, tracker.lastGroundZ);
                spawnCircle(player, tracker.lastGroundX, groundY, tracker.lastGroundZ);
            }

            tracker.wasOnGround = onGround;
            tracker.lastY = y;
        }

        trackers.keySet().retainAll(active);
    }

    private void spawnCircle(PlayerEntity player, double x, double y, double z) {
        Color c = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();
        float r = c.getRed() / 255.0f;
        float g = c.getGreen() / 255.0f;
        float b = c.getBlue() / 255.0f;
        float a = c.getAlpha() / 255.0f;

        if (circles.size() >= 10) {
            circles.remove(0);
        }
        circles.add(new Circle(
                x, y, z,
                (float) radius.getValue(),
                (float) width.getValue(),
                (long) expand.getValue(),
                (long) fade.getValue(),
                (float) strength.getValue(),
                r, g, b, a
        ));
    }

    private double findGroundY(double x, double y, double z) {
        if (mc.world == null) {
            return y + 0.01;
        }

        BlockPos blockPos = BlockPos.ofFloored(x, y, z);
        double relX = x - blockPos.getX();
        double relZ = z - blockPos.getZ();
        double highest = Double.NEGATIVE_INFINITY;
        BlockPos.Mutable checkPos = new BlockPos.Mutable();

        for (int checkY = blockPos.getY() + 1; checkY >= blockPos.getY() - 2; checkY--) {
            checkPos.set(blockPos.getX(), checkY, blockPos.getZ());
            BlockState state = mc.world.getBlockState(checkPos);
            VoxelShape shape = state.getOutlineShape(mc.world, checkPos);

            for (Box box : shape.getBoundingBoxes()) {
                if (relX < box.minX || relX > box.maxX || relZ < box.minZ || relZ > box.maxZ) {
                    continue;
                }
                double surfaceY = (double) checkPos.getY() + box.maxY;
                boolean matchesY = Math.abs(surfaceY - y) <= 0.01;
                boolean isSnow = state.isOf(Blocks.SNOW) && surfaceY >= y - 0.01 && surfaceY <= y + 0.13;
                if ((matchesY || isSnow) && surfaceY > highest) {
                    highest = surfaceY;
                }
            }
        }

        return (highest == Double.NEGATIVE_INFINITY ? y : highest) + 0.01;
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled() || Instance.circles.isEmpty()) {
            return;
        }
        Instance.render(context);
    }

    private void render(WorldRenderContext context) {
        if (mc.world == null || mc.gameRenderer == null) {
            circles.clear();
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) {
            return;
        }

        List<JumpCirclePipeline.CircleInstance> list = prepareCircles(camera);
        if (list.isEmpty()) {
            return;
        }

        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        float fov = ((IGameRenderer) mc.gameRenderer).minar$getFov(camera, tickProgress, true);
        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(fov);
        if (proj == null) {
            return;
        }

        Quaternionf quat = camera.getRotation().conjugate(new Quaternionf());
        Matrix4f pos = new Matrix4f().rotation(quat);

        Matrix4f invViewProj = new Matrix4f(proj).mul(pos).invert();
        JumpCirclePipeline.render(invViewProj, list);
    }

    private List<JumpCirclePipeline.CircleInstance> prepareCircles(Camera camera) {
        ArrayList<JumpCirclePipeline.CircleInstance> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        Vec3d camPos = camera.getCameraPos();

        circles.removeIf(c -> (now - c.spawnedAt) > (c.expandMs + c.fadeMs));

        for (Circle c : circles) {
            if (list.size() >= JumpCirclePipeline.MAX_CIRCLES) {
                break;
            }

            long age = now - c.spawnedAt;
            long totalLife = c.expandMs + c.fadeMs;
            if (totalLife <= 0L) {
                continue;
            }

            float currentRadius;
            float alphaMult;

            if (age < c.expandMs) {
                float progress = (float) age / (float) c.expandMs;
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress);
                currentRadius = c.radius * eased;
                alphaMult = eased;
            } else {
                float fadeFraction = MathHelper.clamp((float) (age - c.expandMs) / (float) c.fadeMs, 0.0f, 1.0f);
                currentRadius = c.radius * (1.0f + fadeFraction * 0.18f);
                float fadeRem = 1.0f - fadeFraction;
                alphaMult = fadeRem * fadeRem;
            }

            if (currentRadius <= 0.001f || alphaMult <= 0.001f) {
                continue;
            }

            float lifeFraction = MathHelper.clamp((float) age / (float) totalLife, 0.0f, 1.0f);
            float currentThickness = c.thickness * (1.0f - 0.5f * lifeFraction);
            float currentAlpha = c.alpha * alphaMult;
            float currentStrength = c.strength * alphaMult;

            float cx = (float) (c.x - camPos.x);
            float cy = (float) (c.y - camPos.y);
            float cz = (float) (c.z - camPos.z);

            list.add(new JumpCirclePipeline.CircleInstance(
                    cx, cy, cz,
                    currentRadius, currentThickness, currentStrength,
                    c.r, c.g, c.b, currentAlpha
            ));
        }

        return list;
    }

    private static final class Circle {
        final double x;
        final double y;
        final double z;
        final float radius;
        final float thickness;
        final long expandMs;
        final long fadeMs;
        final float strength;
        final float r;
        final float g;
        final float b;
        final float alpha;
        final long spawnedAt;

        Circle(double x, double y, double z, float radius, float thickness, long expandMs, long fadeMs, float strength, float r, float g, float b, float alpha) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.thickness = thickness;
            this.expandMs = expandMs;
            this.fadeMs = fadeMs;
            this.strength = strength;
            this.r = r;
            this.g = g;
            this.b = b;
            this.alpha = alpha;
            this.spawnedAt = System.currentTimeMillis();
        }
    }

    private static final class PlayerTracker {
        boolean wasOnGround;
        double lastY;
        double lastGroundX;
        double lastGroundY;
        double lastGroundZ;

        PlayerTracker(boolean onGround, double x, double y, double z, double lastY) {
            this.wasOnGround = onGround;
            this.lastGroundX = x;
            this.lastGroundY = y;
            this.lastGroundZ = z;
            this.lastY = lastY;
        }
    }
}
