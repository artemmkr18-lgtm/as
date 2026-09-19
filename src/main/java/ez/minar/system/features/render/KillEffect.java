package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.events.impl.EntityStatusEvent;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

@NewFunction(name = "KillEffect", desc = "Плавные glow-партиклы при убийстве сущности", category = Category.RENDER)
public class KillEffect extends Function {
    private static final Identifier GLOW_TEXTURE = Identifier.of("minar", "images/particles/glow.png");
    private static final byte DEATH_STATUS = 3;
    private static final long ATTACK_MEMORY_NANOS = 5_000_000_000L;
    private static final long LIFE_NANOS = 1_800_000_000L;
    private static final double GRAVITY = -5.2;
    private static final double AIR_DRAG = 0.972;
    private static final double FLOOR_BOUNCE = 0.18;
    private static final double FLOOR_FRICTION = 0.76;
    private static final List<KillParticle> PARTICLES = new ArrayList<>();
    private static final Map<Integer, Long> ATTACKED_ENTITIES = new HashMap<>();
    private static final Random RANDOM = new Random();

    public static KillEffect Instance;

    public final NumberSetting amount = new NumberSetting("Количество", 30, 5, 80, 1);
    public final NumberSetting speed = new NumberSetting("Скорость", 1.0, 0.2, 3.0, 0.1);
    public final NumberSetting size = new NumberSetting("Размер", 1.0, 0.4, 2.5, 0.1);
    public final BooleanSetting themeColor = new BooleanSetting("Цвет от темы", true);
    public final ColorSetting color = new ColorSetting("Цвет", new Color(255, 255, 255));

    public KillEffect() {
        Instance = this;
        addSettings(amount, speed, size, themeColor, color);
        themeColor.runnable(this::updateVisibility);
        updateVisibility();
    }

    @Override
    public void onDisable() {
        synchronized (PARTICLES) {
            PARTICLES.clear();
            ATTACKED_ENTITIES.clear();
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof LivingEntity target) || target == mc.player) return;

        synchronized (PARTICLES) {
            ATTACKED_ENTITIES.put(target.getId(), System.nanoTime());
        }
    }

    @EventHandler
    private void onEntityStatus(EntityStatusEvent event) {
        if (event.getPacket().getStatus() != DEATH_STATUS) return;
        if (!(event.getEntity() instanceof LivingEntity entity) || entity == mc.player) return;

        long now = System.nanoTime();
        synchronized (PARTICLES) {
            Long attackedAt = ATTACKED_ENTITIES.remove(entity.getId());
            boolean attackedManuallyOrByAura =
                    attackedAt != null && now - attackedAt <= ATTACK_MEMORY_NANOS;

            AttackAura aura = FunctionManager.getFunction(AttackAura.class);
            boolean targetedByAura = aura != null
                    && aura.isEnabled()
                    && aura.getTarget() == entity;

            if (!attackedManuallyOrByAura && !targetedByAura) return;
            spawn(entity, now);
        }
    }

    private void spawn(LivingEntity entity, long now) {
        Vec3d center = entity.getEntityPos().add(0.0, entity.getHeight() * 0.5, 0.0);
        int count = (int) amount.getValue();
        double speedValue = speed.getValue();
        float sizeValue = (float) size.getValue();

        for (int i = 0; i < count; i++) {
            Vec3d direction = randomDirection();
            double impulse = (0.65 + RANDOM.nextDouble() * 0.75) * speedValue;
            Vec3d velocity = direction.multiply(impulse)
                    .add(0.0, -(0.2 + RANDOM.nextDouble() * 0.45) * speedValue, 0.0);
            Vec3d position = center.add(
                    (RANDOM.nextDouble() - 0.5) * entity.getWidth() * 0.75,
                    (RANDOM.nextDouble() - 0.5) * entity.getHeight() * 0.8,
                    (RANDOM.nextDouble() - 0.5) * entity.getWidth() * 0.75
            );

            float particleSize = (0.14f + RANDOM.nextFloat() * 0.1f) * sizeValue;
            float spinSpeed = -90f + RANDOM.nextFloat() * 180f;
            PARTICLES.add(new KillParticle(position, velocity, particleSize,
                    RANDOM.nextFloat() * 360f, spinSpeed, now));
        }
    }

    private Vec3d randomDirection() {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double horizontal = 0.55 + RANDOM.nextDouble() * 0.4;
        double y = -(0.3 + RANDOM.nextDouble() * 0.55);
        return new Vec3d(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal).normalize();
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;

        long now = System.nanoTime();
        List<KillParticle> particles;
        synchronized (PARTICLES) {
            ATTACKED_ENTITIES.entrySet().removeIf(entry -> now - entry.getValue() > ATTACK_MEMORY_NANOS);

            Iterator<KillParticle> iterator = PARTICLES.iterator();
            while (iterator.hasNext()) {
                KillParticle particle = iterator.next();
                if (now - particle.spawnedAt >= LIFE_NANOS) {
                    iterator.remove();
                } else {
                    particle.update(now);
                }
            }

            if (PARTICLES.isEmpty()) return;
            particles = new ArrayList<>(PARTICLES);
        }

        Color selectedColor = Instance.themeColor.isEnabled()
                ? ThemeManager.getThemeColor()
                : Instance.color.getColor();
        Render3DUtils.TexturedBillboardBatch batch =
                Render3DUtils.additiveTexturedBillboardBatch(context, GLOW_TEXTURE);

        for (KillParticle particle : particles) {
            float age = (now - particle.spawnedAt) / (float) LIFE_NANOS;
            float alpha = particleAlpha(age);
            if (alpha <= 0.01f) continue;

            float scale = smooth(Math.min(age / 0.16f, 1f)) * (1f - age * 0.3f);
            batch.render(particle.position, particle.size * scale, particle.rotation,
                    selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), alpha);
        }
    }

    private static float particleAlpha(float age) {
        float appear = smooth(Math.min(age / 0.1f, 1f));
        float disappear = smooth(Math.clamp((1f - age) / 0.42f, 0f, 1f));
        return appear * disappear;
    }

    private static float smooth(float value) {
        return value * value * (3f - 2f * value);
    }

    private void updateVisibility() {
        color.setVisible(!themeColor.isEnabled());
    }

    private static class KillParticle {
        private Vec3d position;
        private Vec3d velocity;
        private final float size;
        private float rotation;
        private final float spinSpeed;
        private final long spawnedAt;
        private long lastUpdate;

        private KillParticle(Vec3d position, Vec3d velocity, float size,
                             float rotation, float spinSpeed, long spawnedAt) {
            this.position = position;
            this.velocity = velocity;
            this.size = size;
            this.rotation = rotation;
            this.spinSpeed = spinSpeed;
            this.spawnedAt = spawnedAt;
            this.lastUpdate = spawnedAt;
        }

        private void update(long now) {
            double delta = Math.min((now - lastUpdate) / 1_000_000_000.0, 0.05);
            lastUpdate = now;

            double drag = Math.pow(AIR_DRAG, delta * 60.0);
            velocity = new Vec3d(velocity.x * drag, velocity.y, velocity.z * drag)
                    .add(0.0, GRAVITY * delta, 0.0);
            Vec3d next = position.add(velocity.multiply(delta));

            if (Instance != null && Instance.mc.world != null) {
                BlockPos floorPos = BlockPos.ofFloored(next.x, next.y - size * 0.45f, next.z);
                if (!Instance.mc.world.getBlockState(floorPos).isAir() && velocity.y < 0.0) {
                    next = new Vec3d(next.x, floorPos.getY() + 1.0 + size * 0.45f, next.z);
                    velocity = new Vec3d(
                            velocity.x * FLOOR_FRICTION,
                            Math.abs(velocity.y) < 0.45 ? 0.0 : -velocity.y * FLOOR_BOUNCE,
                            velocity.z * FLOOR_FRICTION
                    );
                }
            }

            if (Math.abs(velocity.y) < 0.02) {
                velocity = new Vec3d(velocity.x * 0.94, 0.0, velocity.z * 0.94);
            }

            position = next;
            rotation += spinSpeed * (float) delta;
        }
    }
}
