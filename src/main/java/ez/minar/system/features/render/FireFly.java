package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@NewFunction(name = "Fire Fly", desc = "Партиклы и летающие личинки вокруг игрока", category = Category.RENDER)
public class FireFly extends Function {
    private static final Identifier GLOW_TEXTURE = Identifier.of("minar", "images/particles/glow.png");
    private static final Identifier FIREFLY_TEXTURE = Identifier.of("minar", "images/particles/firefly.png");
    private static final Identifier CUBIC2_TEXTURE = Identifier.of("minar", "images/trails/cubic2.png");

    private static final String TYPE_PARTICLES = "Партиклы";
    private static final String TYPE_LARVAE = "Личинки";
    private static final String TYPE_GHOSTS = "Призраки";

    private static final double MIN_SPAWN_RADIUS = 3.0;
    private static final double MAX_SPAWN_RADIUS = 28.0;
    private static final double SPAWN_HEIGHT = 11.0;
    private static final long MAX_LIFETIME_MILLIS = 8_000L;
    private static final int LARVA_TRAIL_LENGTH = 10;

    public static FireFly Instance;

    public final MultiListSetting types = new MultiListSetting("Тип", TYPE_PARTICLES, TYPE_LARVAE, TYPE_GHOSTS);
    public final ModeSetting glowMode = new ModeSetting("Тип свечения", "Normal", "Glow");
    public final ModeSetting texture = new ModeSetting("Текстура", "Bloom", "Heart", "Куб");

    public final NumberSetting count = new NumberSetting("Количество", 55, 10, 120, 1);
    public final NumberSetting size = new NumberSetting("Размер", 1.0, 0.4, 3.0, 0.1);
    public final BooleanSetting themeColor = new BooleanSetting("Цвет от темы", true);
    public final ColorSetting color = new ColorSetting("Цвет", new Color(245, 248, 255));

    private final List<SnowParticle> particles = new ArrayList<>();
    private final List<Larva> larvae = new ArrayList<>();
    private final List<SnowParticle> ghosts = new ArrayList<>();
    private final Random random = new Random();

    public FireFly() {
        Instance = this;
        addSettings(types, count, size, texture, glowMode, themeColor, color);
        types.runnable(this::updateVisibility);
        themeColor.runnable(this::updateVisibility);
        updateVisibility();
    }

    @Override
    public void onDisable() {
        particles.clear();
        larvae.clear();
        ghosts.clear();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d playerPos = mc.player.getEntityPos();
        boolean particlesEnabled = types.isEnabled(TYPE_PARTICLES);
        boolean larvaeEnabled = types.isEnabled(TYPE_LARVAE);
        boolean ghostsEnabled = types.isEnabled(TYPE_GHOSTS);
        int targetCount = (int) count.getValue();

        if (particlesEnabled) {
            updateParticles(playerPos, targetCount);
        } else {
            particles.clear();
        }

        if (larvaeEnabled) {
            updateLarvae(playerPos, targetCount);
        } else {
            larvae.clear();
        }

        if (ghostsEnabled) {
            updateGhosts(playerPos, Math.max(5, targetCount / 3));
        } else {
            ghosts.clear();
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.render(context);
    }

    private void updateParticles(Vec3d playerPos, int targetCount) {
        Iterator<SnowParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            SnowParticle particle = iterator.next();
            particle.update();
            if (particle.isDead(playerPos)) {
                iterator.remove();
            }
        }

        while (particles.size() > targetCount) {
            particles.remove(particles.size() - 1);
        }
        while (particles.size() < targetCount) {
            spawnParticle(playerPos);
        }
    }

    private void updateLarvae(Vec3d playerPos, int targetCount) {
        Iterator<Larva> iterator = larvae.iterator();
        while (iterator.hasNext()) {
            Larva larva = iterator.next();
            larva.update();
            if (larva.isDead(playerPos)) {
                iterator.remove();
            }
        }

        while (larvae.size() > targetCount) {
            larvae.remove(larvae.size() - 1);
        }
        while (larvae.size() < targetCount) {
            spawnLarva(playerPos);
        }
    }

    private void spawnParticle(Vec3d playerPos) {
        Vec3d position = randomSpawnPosition(playerPos);
        Vec3d velocity = new Vec3d(
                (random.nextDouble() - 0.5) * 0.055,
                -0.035 - random.nextDouble() * 0.055,
                (random.nextDouble() - 0.5) * 0.055
        );

        particles.add(new SnowParticle(
                position,
                velocity,
                0.055f + random.nextFloat() * 0.075f,
                random.nextFloat() * 360f,
                -1.4f + random.nextFloat() * 2.8f,
                0.55f + random.nextFloat() * 0.35f
        ));
    }

    private void spawnLarva(Vec3d playerPos) {
        Vec3d position = randomSpawnPosition(playerPos);
        Vec3d direction = randomFlyDirection();
        double speed = 0.045 + random.nextDouble() * 0.055;

        larvae.add(new Larva(
                position,
                direction.multiply(speed),
                0.07f + random.nextFloat() * 0.06f,
                0.65f + random.nextFloat() * 0.3f,
                0.035 + random.nextDouble() * 0.04,
                random.nextDouble() * Math.PI * 2.0,
                0.04 + random.nextDouble() * 0.06
        ));
    }

    private Vec3d randomSpawnPosition(Vec3d playerPos) {
        double angle = Math.toRadians(random.nextDouble() * 360.0);
        double distance = MIN_SPAWN_RADIUS + random.nextDouble() * (MAX_SPAWN_RADIUS - MIN_SPAWN_RADIUS);
        double height = 1.0 + random.nextDouble() * SPAWN_HEIGHT;

        return playerPos
                .add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance)
                .add(0.0, height, 0.0);
    }

    private Vec3d randomFlyDirection() {
        double x = random.nextDouble() * 2.0 - 1.0;
        double y = random.nextDouble() * 1.6 - 0.6;
        double z = random.nextDouble() * 2.0 - 1.0;
        Vec3d direction = new Vec3d(x, y, z);
        if (direction.lengthSquared() < 0.001) {
            return new Vec3d(1.0, 0.2, 0.0).normalize();
        }
        return direction.normalize();
    }

    private void updateGhosts(Vec3d playerPos, int targetCount) {
        Iterator<SnowParticle> iterator = ghosts.iterator();
        while (iterator.hasNext()) {
            SnowParticle ghost = iterator.next();
            ghost.update();
            if (ghost.isDead(playerPos)) {
                iterator.remove();
            }
        }

        while (ghosts.size() > targetCount) {
            ghosts.remove(ghosts.size() - 1);
        }
        while (ghosts.size() < targetCount) {
            spawnGhost(playerPos);
        }
    }

    private void spawnGhost(Vec3d playerPos) {
        Vec3d position = randomSpawnPosition(playerPos);
        Vec3d velocity = new Vec3d(
                (random.nextDouble() - 0.5) * 0.03,
                0.01 + random.nextDouble() * 0.035,
                (random.nextDouble() - 0.5) * 0.03
        );

        ghosts.add(new SnowParticle(
                position,
                velocity,
                0.25f + random.nextFloat() * 0.35f,
                random.nextFloat() * 360f,
                -0.5f + random.nextFloat() * 1.0f,
                0.35f + random.nextFloat() * 0.25f
        ));
    }

    private void render(WorldRenderContext context) {
        if (mc.player == null || mc.world == null) return;

        Color selectedColor = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();
        float sizeMultiplier = (float) size.getValue();
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        int red = selectedColor.getRed();
        int green = selectedColor.getGreen();
        int blue = selectedColor.getBlue();

        if (types.isEnabled(TYPE_PARTICLES) && !particles.isEmpty()) {
            renderParticles(context, red, green, blue, sizeMultiplier, tickDelta);
        }

        if (types.isEnabled(TYPE_LARVAE) && !larvae.isEmpty()) {
            renderLarvae(context, red, green, blue, sizeMultiplier, tickDelta);
        }

        if (types.isEnabled(TYPE_GHOSTS) && !ghosts.isEmpty()) {
            renderGhosts(context, red, green, blue, sizeMultiplier, tickDelta);
        }
    }

    private void renderGhosts(WorldRenderContext context, int red, int green, int blue,
                              float sizeMultiplier, float tickDelta) {
        Identifier particleTexture = CUBIC2_TEXTURE;
        boolean isGlowType = glowMode.isEnabled("Glow");
        Render3DUtils.TexturedBillboardBatch batch =
                isGlowType
                        ? Render3DUtils.shaderGlowTexturedBillboardBatch(context, particleTexture)
                        : Render3DUtils.additiveTexturedBillboardBatch(context, particleTexture);

        for (SnowParticle ghost : ghosts) {
            float alpha = ghost.getAlpha();
            if (alpha <= 0.01f) continue;

            Vec3d pos = ghost.getInterpolatedPosition(tickDelta);
            float renderSize = ghost.size * sizeMultiplier;
            if (isGlowType) {
                batch.renderShaderGlow(pos, renderSize, ghost.rotation, red, green, blue, alpha);
            } else {
                batch.render(pos, renderSize, ghost.rotation, red, green, blue, alpha);
            }
        }
    }

    private void renderParticles(WorldRenderContext context, int red, int green, int blue,
                                 float sizeMultiplier, float tickDelta) {
        Identifier particleTexture = getSelectedTexture();
        boolean isGlowType = glowMode.isEnabled("Glow");
        Render3DUtils.TexturedBillboardBatch batch =
                isGlowType
                        ? Render3DUtils.shaderGlowTexturedBillboardBatch(context, particleTexture)
                        : Render3DUtils.additiveTexturedBillboardBatch(context, particleTexture);

        for (SnowParticle particle : particles) {
            float alpha = particle.getAlpha();
            if (alpha <= 0.01f) continue;

            Vec3d position = particle.getInterpolatedPosition(tickDelta);
            float particleSize = particle.size * sizeMultiplier;
            if (isGlowType) {
                batch.renderShaderGlow(position, particleSize, particle.rotation, red, green, blue, alpha);
            } else {
                batch.render(position, particleSize, particle.rotation, red, green, blue, alpha);
            }
        }
    }

    private void renderLarvae(WorldRenderContext context, int red, int green, int blue,
                              float sizeMultiplier, float tickDelta) {
        Render3DUtils.TexturedBillboardBatch batch =
                Render3DUtils.shaderGlowTexturedBillboardBatch(context, GLOW_TEXTURE);

        for (Larva larva : larvae) {
            float alpha = larva.getAlpha();
            if (alpha <= 0.01f) continue;

            Deque<Vec3d> trail = larva.trail;
            int trailIndex = 0;
            int trailSize = trail.size();
            for (Vec3d trailPos : trail) {
                float t = trailSize <= 1 ? 1f : trailIndex / (float) (trailSize - 1);
                float trailAlpha = alpha * (0.12f + t * 0.45f);
                float trailSizeValue = larva.size * sizeMultiplier * (0.35f + t * 0.55f);
                if (trailAlpha > 0.01f) {
                    batch.renderShaderGlow(trailPos, trailSizeValue, 0f, red, green, blue, trailAlpha);
                }
                trailIndex++;
            }

            Vec3d position = larva.getInterpolatedPosition(tickDelta);
            float bodySize = larva.size * sizeMultiplier;
            batch.renderShaderGlow(position, bodySize, 0f, red, green, blue, alpha);
        }
    }

    private Identifier getSelectedTexture() {
        if (texture.isEnabled("Heart")) return FIREFLY_TEXTURE;
        if (texture.isEnabled("Куб")) return CUBIC2_TEXTURE;
        return GLOW_TEXTURE;
    }

    private void updateVisibility() {
        boolean particlesOn = types.isEnabled(TYPE_PARTICLES);
        texture.setVisible(particlesOn);
        glowMode.setVisible(particlesOn);
        color.setVisible(!themeColor.isEnabled());
    }

    private static class SnowParticle {
        private double x;
        private double y;
        private double z;
        private double prevX;
        private double prevY;
        private double prevZ;
        private final Vec3d velocity;
        private final float size;
        private float rotation;
        private final float spinSpeed;
        private final float maxAlpha;
        private final float wobbleOffset;
        private final long spawnTime;

        private SnowParticle(Vec3d position, Vec3d velocity, float size, float rotation, float spinSpeed, float maxAlpha) {
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
            this.prevX = position.x;
            this.prevY = position.y;
            this.prevZ = position.z;
            this.velocity = velocity;
            this.size = size;
            this.rotation = rotation;
            this.spinSpeed = spinSpeed;
            this.maxAlpha = maxAlpha;
            this.wobbleOffset = (float) (Math.random() * Math.PI * 2.0);
            this.spawnTime = System.currentTimeMillis();
        }

        private void update() {
            prevX = x;
            prevY = y;
            prevZ = z;

            long age = System.currentTimeMillis() - spawnTime;
            double wobble = Math.sin(age / 420.0 + wobbleOffset) * 0.018;

            x += velocity.x + wobble;
            y += velocity.y;
            z += velocity.z + Math.cos(age / 510.0 + wobbleOffset) * 0.018;
            rotation += spinSpeed;
        }

        private boolean isDead(Vec3d playerPos) {
            long age = System.currentTimeMillis() - spawnTime;
            if (age > MAX_LIFETIME_MILLIS || y < playerPos.y - 3.5) {
                return true;
            }

            double dx = x - playerPos.x;
            double dy = y - playerPos.y;
            double dz = z - playerPos.z;
            return dx * dx + dy * dy + dz * dz > 45.0 * 45.0;
        }

        private Vec3d getInterpolatedPosition(float tickDelta) {
            return new Vec3d(
                    MathHelper.lerp(tickDelta, prevX, x),
                    MathHelper.lerp(tickDelta, prevY, y),
                    MathHelper.lerp(tickDelta, prevZ, z)
            );
        }

        private float getAlpha() {
            long age = System.currentTimeMillis() - spawnTime;
            float fadeIn = Math.clamp(age / 650f, 0f, 1f);
            float fadeOut = Math.clamp((MAX_LIFETIME_MILLIS - age) / 1300f, 0f, 1f);
            return smooth(Math.min(fadeIn, fadeOut)) * maxAlpha;
        }

        private float smooth(float value) {
            value = Math.clamp(value, 0f, 1f);
            return value * value * (3f - 2f * value);
        }
    }

    private static class Larva {
        private double x;
        private double y;
        private double z;
        private double prevX;
        private double prevY;
        private double prevZ;
        private double vx;
        private double vy;
        private double vz;
        private final float size;
        private final float maxAlpha;
        private final double cruiseSpeed;
        private double wanderAngle;
        private final double wanderSpeed;
        private final double phase;
        private final long spawnTime;
        private final Deque<Vec3d> trail = new ArrayDeque<>(LARVA_TRAIL_LENGTH + 1);

        private Larva(Vec3d position, Vec3d velocity, float size, float maxAlpha,
                      double cruiseSpeed, double wanderAngle, double wanderSpeed) {
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
            this.prevX = position.x;
            this.prevY = position.y;
            this.prevZ = position.z;
            this.vx = velocity.x;
            this.vy = velocity.y;
            this.vz = velocity.z;
            this.size = size;
            this.maxAlpha = maxAlpha;
            this.cruiseSpeed = cruiseSpeed;
            this.wanderAngle = wanderAngle;
            this.wanderSpeed = wanderSpeed;
            this.phase = Math.random() * Math.PI * 2.0;
            this.spawnTime = System.currentTimeMillis();
            this.trail.addLast(position);
        }

        private void update() {
            prevX = x;
            prevY = y;
            prevZ = z;

            wanderAngle += wanderSpeed;

            double targetVx = Math.sin(wanderAngle) * cruiseSpeed
                    + Math.cos(wanderAngle * 0.73 + phase) * cruiseSpeed * 0.35;
            double targetVy = Math.sin(wanderAngle * 0.55 + phase) * cruiseSpeed * 0.45
                    + Math.cos(wanderAngle * 1.1) * cruiseSpeed * 0.15;
            double targetVz = Math.cos(wanderAngle) * cruiseSpeed
                    + Math.sin(wanderAngle * 0.91 + phase) * cruiseSpeed * 0.35;

            // Плавная физика: скорость мягко тянется к целевой
            double smoothing = 0.085;
            vx += (targetVx - vx) * smoothing;
            vy += (targetVy - vy) * smoothing;
            vz += (targetVz - vz) * smoothing;

            // Лёгкое демпфирование, чтобы движение оставалось стабильным
            vx *= 0.985;
            vy *= 0.985;
            vz *= 0.985;

            x += vx;
            y += vy;
            z += vz;

            trail.addLast(new Vec3d(x, y, z));
            while (trail.size() > LARVA_TRAIL_LENGTH) {
                trail.removeFirst();
            }
        }

        private boolean isDead(Vec3d playerPos) {
            long age = System.currentTimeMillis() - spawnTime;
            if (age > MAX_LIFETIME_MILLIS) {
                return true;
            }

            double dx = x - playerPos.x;
            double dy = y - playerPos.y;
            double dz = z - playerPos.z;
            return dx * dx + dy * dy + dz * dz > 45.0 * 45.0;
        }

        private Vec3d getInterpolatedPosition(float tickDelta) {
            return new Vec3d(
                    MathHelper.lerp(tickDelta, prevX, x),
                    MathHelper.lerp(tickDelta, prevY, y),
                    MathHelper.lerp(tickDelta, prevZ, z)
            );
        }

        private float getAlpha() {
            long age = System.currentTimeMillis() - spawnTime;
            float fadeIn = Math.clamp(age / 650f, 0f, 1f);
            float fadeOut = Math.clamp((MAX_LIFETIME_MILLIS - age) / 1300f, 0f, 1f);
            float value = Math.min(fadeIn, fadeOut);
            value = Math.clamp(value, 0f, 1f);
            return value * value * (3f - 2f * value) * maxAlpha;
        }
    }
}
