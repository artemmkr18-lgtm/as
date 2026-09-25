package ez.minar.system.features.render;

import ez.minar.mixins.interfaces.IGameRenderer;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.ImpactRingPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@NewFunction(name = "ImpactRing", desc = "Ударное кольцо, преломляющее сцену", category = Category.RENDER)
public class ImpactRing extends Function {
    private static final int MAX_RINGS = 14;
    private static final long NANOS_PER_MS = 1_000_000L;
    private static final Vec3d WORLD_UP = new Vec3d(0.0, 1.0, 0.0);

    public static ImpactRing Instance;

    private final NumberSetting lifetime = new NumberSetting("Длительность", 320, 60, 1000, 10);
    private final NumberSetting radius = new NumberSetting("Радиус", 0.85, 0.2, 3.0, 0.05);
    private final NumberSetting waveStrength = new NumberSetting("Сила волны", 0.75, 0.02, 2.0, 0.05);
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Theme", "Custom", "Enemy");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", new Color(0x3B82F6));
    private final BooleanSetting glow = new BooleanSetting("Свечение", true);
    private final BooleanSetting onlyAir = new BooleanSetting("Только в прыжке", false);

    private final List<Ring> rings = new ArrayList<>();

    public ImpactRing() {
        Instance = this;
        addSettings(lifetime, radius, waveStrength, colorMode, customColor, glow, onlyAir);
        colorMode.runnable(() -> customColor.setVisible(colorMode.isEnabled("Custom")));
        customColor.setVisible(false);
    }

    @Override
    public void onDisable() {
        rings.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (onlyAir.isEnabled() && mc.player.isOnGround()) return;

        Vec3d center = target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0);
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d eye = camera != null ? camera.getCameraPos() : mc.player.getEyePos();

        Vec3d toCam = eye.subtract(center);
        if (toCam.lengthSquared() < 1e-6) toCam = mc.player.getRotationVec(1.0f);
        Vec3d normal = toCam.normalize();

        Vec3d right = normal.crossProduct(WORLD_UP);
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1.0, 0.0, 0.0);
        else right = right.normalize();
        Vec3d up = right.crossProduct(normal).normalize();

        float tilt = (float) Math.toRadians(0.35 + (Math.random() - 0.5) * 4.0);
        Vec3d axisU = up.multiply(Math.cos(tilt)).add(normal.multiply(Math.sin(tilt))).normalize();

        rings.add(new Ring(center, axisU, right, System.nanoTime(), (long) lifetime.getValue()));
        if (rings.size() > MAX_RINGS) rings.remove(0);
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled() || Instance.rings.isEmpty()) return;
        if (Instance.mc.player == null || Instance.mc.world == null) {
            Instance.rings.clear();
            return;
        }
        Instance.render();
    }

    private void render() {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;
        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        float fov = ((IGameRenderer) mc.gameRenderer).minar$getFov(camera, tickProgress, true);
        if (!ImpactRingPipeline.beginFrame(mc.gameRenderer.getBasicProjectionMatrix(fov))) return;

        long now = System.nanoTime();
        float time = ((now / NANOS_PER_MS) % 1_000_000L) / 1000f;
        Color color = resolveColor();
        float maxRadius = (float) radius.getValue() * 1.1f;
        float strength = (float) waveStrength.getValue();
        boolean drawGlow = glow.isEnabled();

        Iterator<Ring> iterator = rings.iterator();
        while (iterator.hasNext()) {
            Ring ring = iterator.next();
            long age = now - ring.born();
            long life = ring.lifetimeMs() * NANOS_PER_MS;
            if (age >= life) {
                iterator.remove();
                continue;
            }

            float raw = age / (float) life;
            float eased = 1f - raw;
            float progress = 1f - eased * eased * eased;
            float fade = eased * 0.8f;
            if (fade <= 0.02f) continue;

            ImpactRingPipeline.drawWave(ring.center(), ring.axisU(), ring.axisV(), maxRadius,
                    progress, strength, fade, color);
            if (drawGlow) {
                ImpactRingPipeline.drawGlow(ring.center(), ring.axisU(), ring.axisV(), maxRadius,
                        progress, fade * 0.62f, time, color);
            }
        }
    }

    private Color resolveColor() {
        if (colorMode.isEnabled("Custom")) return customColor.getColor();
        if (colorMode.isEnabled("Enemy")) return new Color(0xFF, 0x33, 0x44);
        return ThemeManager.getThemeColor();
    }

    private record Ring(Vec3d center, Vec3d axisU, Vec3d axisV, long born, long lifetimeMs) {}
}
