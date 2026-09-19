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
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@NewFunction(name = "Trails", desc = "Шлейф частиц позади игрока", category = Category.RENDER)
public class Trails extends Function {
    private static final Identifier BLOOM_TEXTURE = Identifier.of("minar", "images/trails/bloom.png");
    private static final Identifier CUBIC1_TEXTURE = Identifier.of("minar", "images/trails/cubic1.png");
    private static final Identifier CUBIC2_TEXTURE = Identifier.of("minar", "images/trails/cubic2.png");
    private static final Identifier CUBIC3_TEXTURE = Identifier.of("minar", "images/trails/cubic3.png");
    private static final Identifier CUBIC4_TEXTURE = Identifier.of("minar", "images/trails/cubic4.png");
    private static final Identifier CUBIC5_TEXTURE = Identifier.of("minar", "images/trails/cubic5.png");
    private static final Identifier[] ALL_TEXTURES = {BLOOM_TEXTURE, CUBIC1_TEXTURE, CUBIC2_TEXTURE, CUBIC3_TEXTURE, CUBIC4_TEXTURE, CUBIC5_TEXTURE};
    private static final long LIFE_NANOS = 1_200_000_000L;
    private static final double SPAWN_HEIGHT_FACTOR = 0.48;
    private static final float SCALE_IN_TIME = 0.18f;
    private static final float SCALE_OUT_TIME = 0.32f;
    private static final Random RANDOM = new Random();

    public static Trails Instance;
    public final ModeSetting texture = new ModeSetting("Текстура", "Bloom", "Cubic1", "Cubic2", "Cubic3", "Cubic4", "Cubic5", "Все");
    public final NumberSetting density = new NumberSetting("Плотность", 4, 1, 20, 1);
    public final NumberSetting size = new NumberSetting("Размер", 0.5, 0.1, 2.0, 0.05);
    public final NumberSetting spread = new NumberSetting("Разброс", 0.3, 0.0, 1.5, 0.05);
    public final BooleanSetting followLook = new BooleanSetting("По взгляду", false);
    public final BooleanSetting seeFirstPerson = new BooleanSetting("Видеть от первого лица", false);
    public final BooleanSetting themeColor = new BooleanSetting("Цвет темы", true);
    public final ColorSetting color = new ColorSetting("Цвет", new Color(255, 255, 255));
    public final BooleanSetting glow = new BooleanSetting("Свечение", true);
    public final ModeSetting glowMode = new ModeSetting("Тип свечения", "Normal", "Glow");
    public final NumberSetting glowWidth = new NumberSetting("Ширина свечения", 1.0, 0.5, 3.0, 0.1);
    public final NumberSetting glowStrength = new NumberSetting("Сила свечения", 1.0, 0.2, 2.0, 0.05);

    private final List<TrailParticle> particles = new ArrayList<>();
    private Vec3d lastPlayerPos;

    public Trails() {
        Instance = this;
        addSettings(texture, density, size, spread, followLook, seeFirstPerson, themeColor, color, glow, glowMode, glowWidth, glowStrength);
        themeColor.runnable(() -> color.setVisible(!themeColor.isEnabled()));
        glowMode.runnable(() -> glowWidth.setVisible(glow.isEnabled() && glowMode.isEnabled("Glow")));
        glow.runnable(() -> {
            boolean on = glow.isEnabled();
            glowMode.setVisible(on);
            glowWidth.setVisible(on && glowMode.isEnabled("Glow"));
            glowStrength.setVisible(on && glowMode.isEnabled("Glow"));
        });
        color.setVisible(!themeColor.isEnabled());
        glowMode.setVisible(glow.isEnabled());
        glowWidth.setVisible(glow.isEnabled() && glowMode.isEnabled("Glow"));
        glowStrength.setVisible(glow.isEnabled() && glowMode.isEnabled("Glow"));
    }

    @Override
    public void onDisable() {
        synchronized (particles) {
            particles.clear();
        }
        lastPlayerPos = null;
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (shouldHideInFirstPerson()) {
            synchronized (particles) {
                particles.clear();
            }
            lastPlayerPos = null;
            return;
        }

        Vec3d pos = mc.player.getEntityPos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        long now = System.nanoTime();

        synchronized (particles) {
            Iterator<TrailParticle> it = particles.iterator();
            while (it.hasNext()) {
                TrailParticle p = it.next();
                float age = (now - p.spawnedAt) / (float) LIFE_NANOS;
                if (age >= 1f) {
                    it.remove();
                }
            }

            boolean inWater = mc.player.isTouchingWater() || mc.player.isSubmergedInWater();

            for (TrailParticle p : particles) {
                p.position = p.position.add(p.velocity);
                if (inWater) {
                    p.velocity = p.velocity.add(0, -0.003, 0);
                }
            }

            if (lastPlayerPos != null && pos.squaredDistanceTo(lastPlayerPos) < 1e-8) return;

            int count = (int) density.getValue();
            float spreadVal = (float) spread.getValue();
            float sizeVal = (float) size.getValue();
            double spawnY = mc.player.getHeight() * SPAWN_HEIGHT_FACTOR;
            boolean allTex = texture.isEnabled("Все");

            Vec3d offset = followLook.isEnabled()
                    ? look.multiply(-0.5)
                    : new Vec3d(0, 0, 0);

            for (int i = 0; i < count; i++) {
                Vec3d trailPos = pos.add(
                        (RANDOM.nextDouble() - 0.5) * spreadVal,
                        (RANDOM.nextDouble() - 0.5) * spreadVal + spawnY,
                        (RANDOM.nextDouble() - 0.5) * spreadVal
                ).add(offset);

                Identifier tex = allTex ? ALL_TEXTURES[RANDOM.nextInt(ALL_TEXTURES.length)] : getSelectedTexture();
                Vec3d particleVelocity = inWater
                        ? new Vec3d(0, -0.015 - RANDOM.nextFloat() * 0.01f, 0)
                        : Vec3d.ZERO;

                particles.add(new TrailParticle(
                        trailPos,
                        particleVelocity,
                        0.08f + RANDOM.nextFloat() * 0.06f,
                        RANDOM.nextFloat() * 360f,
                        now,
                        sizeVal,
                        tex
                ));
            }
        }

        lastPlayerPos = pos;
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        if (Instance.shouldHideInFirstPerson()) return;

        long now = System.nanoTime();

        List<TrailParticle> snapshot;
        synchronized (Instance.particles) {
            if (Instance.particles.isEmpty()) return;
            snapshot = new ArrayList<>(Instance.particles);
        }

        Color selectedColor = Instance.themeColor.isEnabled() ? ThemeManager.getThemeColor() : Instance.color.getColor();
        int red = selectedColor.getRed();
        int green = selectedColor.getGreen();
        int blue = selectedColor.getBlue();
        float glowW = (float) Instance.glowWidth.getValue();
        float glowS = (float) Instance.glowStrength.getValue();
        boolean hasGlow = Instance.glow.isEnabled();
        boolean isGlowType = hasGlow && Instance.glowMode.isEnabled("Glow");
        boolean isBloom = Instance.texture.isEnabled("Bloom");
        boolean isAll = Instance.texture.isEnabled("Все");

        Identifier renderTex = isBloom || isAll ? BLOOM_TEXTURE : Instance.getSelectedTexture();

        if (hasGlow && isGlowType) {
            Render3DUtils.TexturedBillboardBatch batch = Render3DUtils.shaderGlowTexturedBillboardBatch(context, renderTex);
            for (TrailParticle p : snapshot) {
                renderShaderGlow(batch, p, now, red, green, blue, glowW, glowS);
            }
        } else {
            Render3DUtils.TexturedBillboardBatch batch = Render3DUtils.additiveTexturedBillboardBatch(context, renderTex);
            for (TrailParticle p : snapshot) {
                renderCompact(batch, p, now, red, green, blue, hasGlow, glowW);
            }
        }
    }

    private static void renderShaderGlow(Render3DUtils.TexturedBillboardBatch batch, TrailParticle p, long now,
                                         int red, int green, int blue, float glowW, float glowS) {
        float age = (now - p.spawnedAt) / (float) LIFE_NANOS;
        float alpha = 1f - age;
        if (alpha <= 0.01f) return;

        float fade = alpha * alpha * (3f - 2f * alpha);
        float s = particleSize(p, age) * glowW;

        batch.renderShaderGlow(p.position, s, p.rotation,
                red, green, blue, fade * glowS);
    }

    private static void renderCompact(Render3DUtils.TexturedBillboardBatch batch, TrailParticle p, long now,
                                      int red, int green, int blue, boolean hasGlow, float glowW) {
        float age = (now - p.spawnedAt) / (float) LIFE_NANOS;
        float alpha = 1f - age;
        if (alpha <= 0.01f) return;

        float fade = alpha * alpha * (3f - 2f * alpha);
        float scale = hasGlow ? glowW : 1f;
        float s = particleSize(p, age) * scale;

        batch.render(p.position, s, p.rotation,
                red, green, blue, fade);
    }

    private static float particleSize(TrailParticle p, float age) {
        float base = p.baseSize * p.sizeMultiplier;
        float appear = smooth(Math.clamp(age / SCALE_IN_TIME, 0f, 1f));
        float disappear = smooth(Math.clamp((1f - age) / SCALE_OUT_TIME, 0f, 1f));
        float scale = Math.min(appear, disappear);
        return base * scale;
    }

    private static float smooth(float value) {
        return value * value * (3f - 2f * value);
    }

    private boolean shouldHideInFirstPerson() {
        return !seeFirstPerson.isEnabled()
                && mc.options != null
                && mc.options.getPerspective().isFirstPerson();
    }

    private Identifier getSelectedTexture() {
        if (texture.isEnabled("Cubic1")) return CUBIC1_TEXTURE;
        if (texture.isEnabled("Cubic2")) return CUBIC2_TEXTURE;
        if (texture.isEnabled("Cubic3")) return CUBIC3_TEXTURE;
        if (texture.isEnabled("Cubic4")) return CUBIC4_TEXTURE;
        if (texture.isEnabled("Cubic5")) return CUBIC5_TEXTURE;
        return BLOOM_TEXTURE;
    }

    private static class TrailParticle {
        private Vec3d position;
        private Vec3d velocity;
        private final float baseSize;
        private final float rotation;
        private final long spawnedAt;
        private final float sizeMultiplier;
        private final Identifier texture;

        private TrailParticle(Vec3d position, Vec3d velocity, float baseSize, float rotation, long spawnedAt, float sizeMultiplier, Identifier texture) {
            this.position = position;
            this.velocity = velocity;
            this.baseSize = baseSize;
            this.rotation = rotation;
            this.spawnedAt = spawnedAt;
            this.sizeMultiplier = sizeMultiplier;
            this.texture = texture;
        }
    }
}
