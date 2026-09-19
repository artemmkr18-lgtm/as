package ez.minar.system.features.combat.rotation;

import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.settings.Setting;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public final class CustomRotation {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public final ListSetting aimPoint = new ListSetting(
            "Точка наводки",
            "Глаза",
            "Центр",
            "Ноги",
            "Случайная"
    );

    public final NumberSetting yawSpeed = new NumberSetting("Скорость Yaw", 180.0, 1.0, 360.0, 1.0);
    public final NumberSetting pitchSpeed = new NumberSetting("Скорость Pitch", 180.0, 1.0, 360.0, 1.0);
    public final NumberSetting returnSpeed = new NumberSetting("Скорость возврата", 60.0, 1.0, 360.0, 1.0);
    public final NumberSetting maxStep = new NumberSetting("Макс. шаг", 360.0, 1.0, 360.0, 1.0);

    public final NumberSetting smoothAttack = new NumberSetting("Плавность атаки", 1.0, 0.05, 1.0, 0.05);
    public final NumberSetting smoothIdle = new NumberSetting("Плавность ожидания", 0.5, 0.0, 1.0, 0.05);

    public final NumberSetting yawCapAttack = new NumberSetting("Лимит Yaw атака", 360.0, 1.0, 360.0, 1.0);
    public final NumberSetting yawCapIdle = new NumberSetting("Лимит Yaw ожидание", 100.0, 1.0, 360.0, 1.0);
    public final NumberSetting pitchCapAttack = new NumberSetting("Лимит Pitch атака", 180.0, 1.0, 180.0, 1.0);
    public final NumberSetting pitchCapIdle = new NumberSetting("Лимит Pitch ожидание", 180.0, 1.0, 180.0, 1.0);

    public final BooleanSetting jitter = new BooleanSetting("Jitter", false);
    public final NumberSetting jitterYaw = new NumberSetting("Jitter Yaw", 6.0, 0.0, 45.0, 0.5);
    public final NumberSetting jitterPitch = new NumberSetting("Jitter Pitch", 3.0, 0.0, 45.0, 0.5);
    public final NumberSetting jitterYawFreq = new NumberSetting("Частота Jitter Yaw", 60.0, 5.0, 200.0, 1.0);
    public final NumberSetting jitterPitchFreq = new NumberSetting("Частота Jitter Pitch", 80.0, 5.0, 200.0, 1.0);
    public final NumberSetting jitterFreqInterval = new NumberSetting("Интервал частот", 2000.0, 200.0, 5000.0, 100.0);
    public final BooleanSetting jitterOnlyIdle = new BooleanSetting("Jitter вне атаки", true);
    public final BooleanSetting jitterRandomizeFreq = new BooleanSetting("Случайные частоты", true);

    public final BooleanSetting randomSpeed = new BooleanSetting("Случайная скорость", false);
    public final NumberSetting speedMin = new NumberSetting("Мин. множитель", 0.8, 0.1, 2.0, 0.05);
    public final NumberSetting speedMax = new NumberSetting("Макс. множитель", 1.2, 0.1, 2.0, 0.05);

    public final BooleanSetting flick = new BooleanSetting("Flick", false);
    public final NumberSetting flickHits = new NumberSetting("Flick каждые", 50.0, 1.0, 200.0, 1.0);
    public final NumberSetting flickDuration = new NumberSetting("Длительность Flick", 200.0, 50.0, 1500.0, 25.0);
    public final NumberSetting flickStrength = new NumberSetting("Сила Flick", 90.0, 10.0, 90.0, 1.0);

    public final BooleanSetting skipGcd = new BooleanSetting("Пропуск GCD", false);
    public final NumberSetting yawOffset = new NumberSetting("Смещение Yaw", 0.0, -45.0, 45.0, 0.5);
    public final NumberSetting pitchOffset = new NumberSetting("Смещение Pitch", 0.0, -45.0, 45.0, 0.5);

    private float runtimeJitterYawFreq = 60.0F;
    private float runtimeJitterPitchFreq = 80.0F;
    private long jitterFreqUpdateTime;

    public Setting[] settings() {
        return new Setting[]{
                aimPoint,
                yawSpeed, pitchSpeed, returnSpeed, maxStep,
                smoothAttack, smoothIdle,
                yawCapAttack, yawCapIdle, pitchCapAttack, pitchCapIdle,
                jitter, jitterYaw, jitterPitch, jitterYawFreq, jitterPitchFreq, jitterFreqInterval, jitterOnlyIdle, jitterRandomizeFreq,
                randomSpeed, speedMin, speedMax,
                flick, flickHits, flickDuration, flickStrength,
                skipGcd, yawOffset, pitchOffset
        };
    }

    public void bindVisibility(Runnable update) {
        jitter.runnable(update);
        randomSpeed.runnable(update);
        flick.runnable(update);
        jitterRandomizeFreq.runnable(update);
    }

    public void updateVisibility(boolean enabled) {
        aimPoint.setVisible(enabled);
        yawSpeed.setVisible(enabled);
        pitchSpeed.setVisible(enabled);
        returnSpeed.setVisible(enabled);
        maxStep.setVisible(enabled);
        smoothAttack.setVisible(enabled);
        smoothIdle.setVisible(enabled);
        yawCapAttack.setVisible(enabled);
        yawCapIdle.setVisible(enabled);
        pitchCapAttack.setVisible(enabled);
        pitchCapIdle.setVisible(enabled);

        jitter.setVisible(enabled);
        boolean jitterOn = enabled && jitter.isEnabled();
        jitterYaw.setVisible(jitterOn);
        jitterPitch.setVisible(jitterOn);
        jitterOnlyIdle.setVisible(jitterOn);
        jitterRandomizeFreq.setVisible(jitterOn);
        boolean randomFreq = jitterOn && jitterRandomizeFreq.isEnabled();
        jitterFreqInterval.setVisible(randomFreq);
        jitterYawFreq.setVisible(jitterOn && !jitterRandomizeFreq.isEnabled());
        jitterPitchFreq.setVisible(jitterOn && !jitterRandomizeFreq.isEnabled());

        randomSpeed.setVisible(enabled);
        boolean randomOn = enabled && randomSpeed.isEnabled();
        speedMin.setVisible(randomOn);
        speedMax.setVisible(randomOn);

        flick.setVisible(enabled);
        boolean flickOn = enabled && flick.isEnabled();
        flickHits.setVisible(flickOn);
        flickDuration.setVisible(flickOn);
        flickStrength.setVisible(flickOn);

        skipGcd.setVisible(enabled);
        yawOffset.setVisible(enabled);
        pitchOffset.setVisible(enabled);
    }

    public void resetRuntime() {
        runtimeJitterYawFreq = (float) jitterYawFreq.getValue();
        runtimeJitterPitchFreq = (float) jitterPitchFreq.getValue();
        jitterFreqUpdateTime = 0L;
    }

    public Rotation compute(LivingEntity entity, Rotation current, boolean attackWindow, Random random, long flickUntil) {
        if (entity == null || mc.player == null) {
            return current;
        }

        long now = System.currentTimeMillis();
        if (flick.isEnabled() && now < flickUntil) {
            float strength = (float) flickStrength.getValue();
            float nextPitch = (float) lerp(current.pitch(), current.pitch() - strength, 0.55);
            return new Rotation(current.yaw(), MathHelper.clamp(nextPitch, -90.0F, 90.0F));
        }

        Rotation targetRot = RotationManager.getRotationTo(resolveAimPoint(entity, random));
        float yawDelta = MathHelper.wrapDegrees(targetRot.yaw() - current.yaw());
        float pitchDelta = targetRot.pitch() - current.pitch();

        float yawCap = (float) (attackWindow ? yawCapAttack.getValue() : yawCapIdle.getValue());
        float pitchCap = (float) (attackWindow ? pitchCapAttack.getValue() : pitchCapIdle.getValue());
        float clampedYaw = MathHelper.clamp(yawDelta, -yawCap, yawCap);
        float clampedPitch = MathHelper.clamp(pitchDelta, -pitchCap, pitchCap);

        float speed = (float) (attackWindow ? smoothAttack.getValue() : smoothIdle.getValue());
        if (randomSpeed.isEnabled()) {
            float min = (float) speedMin.getValue();
            float max = (float) speedMax.getValue();
            if (min > max) {
                float tmp = min;
                min = max;
                max = tmp;
            }
            speed *= randomBetween(random, min, max);
            speed = MathHelper.clamp(speed, 0.0F, 2.0F);
        }

        float nextYaw = (float) lerp(current.yaw(), current.yaw() + clampedYaw, MathHelper.clamp(speed, 0.0F, 1.0F));
        float nextPitch = (float) lerp(current.pitch(), current.pitch() + clampedPitch, MathHelper.clamp(speed, 0.0F, 1.0F));

        nextYaw += (float) yawOffset.getValue();
        nextPitch += (float) pitchOffset.getValue();

        if (jitter.isEnabled() && (!jitterOnlyIdle.isEnabled() || !attackWindow)) {
            updateJitterFrequencies(now, random);
            float yawAmp = (float) jitterYaw.getValue();
            float pitchAmp = (float) jitterPitch.getValue();
            nextYaw += yawAmp * (float) Math.sin(now / (double) runtimeJitterYawFreq);
            nextPitch += pitchAmp * (float) Math.sin(now / (double) runtimeJitterPitchFreq);
        }

        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    public Speeds speeds() {
        return new Speeds(
                (float) yawSpeed.getValue(),
                (float) pitchSpeed.getValue(),
                (float) returnSpeed.getValue()
        );
    }

    public float maxStepValue() {
        return (float) maxStep.getValue();
    }

    public boolean skipGcd() {
        return skipGcd.isEnabled();
    }

    public boolean flickEnabled() {
        return flick.isEnabled();
    }

    public boolean shouldTriggerFlick(int hitCounter) {
        if (!flick.isEnabled()) return false;
        int interval = Math.max(1, (int) Math.round(flickHits.getValue()));
        return hitCounter > 0 && hitCounter % interval == 0;
    }

    public long flickDurationMs() {
        return Math.max(1L, Math.round(flickDuration.getValue()));
    }

    public Vec3d resolveAimPoint(LivingEntity entity, Random random) {
        Box box = entity.getBoundingBox();
        if (aimPoint.isEnabled("Центр")) {
            return box.getCenter();
        }
        if (aimPoint.isEnabled("Ноги")) {
            return new Vec3d(
                    (box.minX + box.maxX) * 0.5D,
                    box.minY + (box.maxY - box.minY) * 0.15D,
                    (box.minZ + box.maxZ) * 0.5D
            );
        }
        if (aimPoint.isEnabled("Случайная")) {
            return new Vec3d(
                    box.minX + random.nextDouble() * (box.maxX - box.minX),
                    box.minY + random.nextDouble() * (box.maxY - box.minY),
                    box.minZ + random.nextDouble() * (box.maxZ - box.minZ)
            );
        }
        return new Vec3d(entity.getX(), entity.getEyeY() - 0.1, entity.getZ());
    }

    private void updateJitterFrequencies(long now, Random random) {
        if (!jitterRandomizeFreq.isEnabled()) {
            runtimeJitterYawFreq = Math.max(1.0F, (float) jitterYawFreq.getValue());
            runtimeJitterPitchFreq = Math.max(1.0F, (float) jitterPitchFreq.getValue());
            return;
        }

        long interval = Math.max(100L, Math.round(jitterFreqInterval.getValue()));
        if (now - jitterFreqUpdateTime > interval) {
            runtimeJitterYawFreq = randomBetween(random, 15.0F, 145.0F);
            runtimeJitterPitchFreq = randomBetween(random, 15.0F, 145.0F);
            jitterFreqUpdateTime = now;
        }
    }

    private static float randomBetween(Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private static double lerp(double from, double to, double delta) {
        return from + (to - from) * delta;
    }

    public record Speeds(float yaw, float pitch, float returnSpeed) {
    }
}
