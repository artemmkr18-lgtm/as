package ez.minar.system.neuro;

import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

public final class NeuroPredictor {
    private static final float MAX_ATTACK_TICKS = 20.0F;

    private float[] hidden;
    private float ticksSinceAttack = MAX_ATTACK_TICKS;
    private float freezeTicks;

    private float lastTargetYaw;
    private float lastTargetPitch;

    private float errorYaw;
    private float errorPitch;
    private float prevErrorYaw;
    private float prevErrorPitch;

    private float prevDeltaYaw;
    private float prevDeltaPitch;
    private float prevDeltaYaw2;
    private float prevDeltaPitch2;

    private float boxSizeYaw = 5.0F;
    private float boxSizePitch = 15.0F;
    private double distance = 3.0D;
    private boolean onTarget;

    public boolean isInitialized() {
        return this.hidden != null;
    }

    public void reset(NeuroModel model, float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        if (model == null) {
            this.hidden = null;
            return;
        }
        this.hidden = model.createHiddenState();
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;
        this.freezeTicks = 0.0F;
        this.prevDeltaYaw = 0.0F;
        this.prevDeltaPitch = 0.0F;
        this.prevDeltaYaw2 = 0.0F;
        this.prevDeltaPitch2 = 0.0F;
        this.errorYaw = this.prevErrorYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        this.errorPitch = this.prevErrorPitch = targetPitch - currentPitch;
    }

    public void clear() {
        this.hidden = null;
        this.freezeTicks = 0.0F;
    }

    public void onAttack() {
        this.ticksSinceAttack = 0.0F;
    }

    public void tick() {
        this.ticksSinceAttack = Math.min(this.ticksSinceAttack + 1.0F, MAX_ATTACK_TICKS);
    }

    public boolean isOnTarget() {
        return this.onTarget;
    }

    public boolean predict(NeuroModel model,
                           float currentYaw, float currentPitch,
                           float targetYaw, float targetPitch,
                           float boxSizeYaw, float boxSizePitch,
                           double distance, float errorFactor,
                           int maxFreezeTicks, float humanScale,
                           int candidateCount, float speedFactor,
                           float gcd, float[] resultDelta) {
        if (model == null) {
            return false;
        }
        if (this.hidden == null) {
            this.reset(model, currentYaw, currentPitch, targetYaw, targetPitch);
        }

        this.boxSizeYaw = boxSizeYaw;
        this.boxSizePitch = boxSizePitch;
        this.distance = distance;

        float targetVelYaw = MathHelper.wrapDegrees(targetYaw - this.lastTargetYaw);
        float targetVelPitch = targetPitch - this.lastTargetPitch;
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;

        float rawErrorYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float rawErrorPitch = targetPitch - currentPitch;

        float[] features = this.extractFeatures(model, targetVelYaw, targetVelPitch);
        float[] output = model.forward(features, this.hidden);

        float bestDeltaYaw = 0.0F;
        float bestDeltaPitch = 0.0F;

        boolean forceMove = this.freezeTicks >= (float) Math.min(maxFreezeTicks, model.getFreezeCut());
        float moveProb = NeuroModel.sigmoid(output[0]);

        if (forceMove || ThreadLocalRandom.current().nextFloat() >= moveProb) {
            float bestScore = Float.MAX_VALUE;
            for (int i = candidateCount; i > 0; i--) {
                int comp = model.sampleComponent(output, ThreadLocalRandom.current().nextFloat());
                int base = 1 + 6 * comp;

                float rho = NeuroModel.tanhClamp(output[base + 5]);
                float oneMinusRho2 = (float) Math.sqrt(Math.max(0.0F, 1.0F - rho * rho));
                float z1 = (float) ThreadLocalRandom.current().nextGaussian();
                float z2 = rho * z1 + oneMinusRho2 * (float) ThreadLocalRandom.current().nextGaussian();

                float candYaw = roundToGcd(speedFactor * model.sampleValue(output[base + 1], output[base + 3], true, z1, humanScale), gcd);
                float candPitch = roundToGcd(speedFactor * model.sampleValue(output[base + 2], output[base + 4], false, z2, humanScale), gcd);

                float score = Math.abs((float) Math.hypot(
                        MathHelper.wrapDegrees(rawErrorYaw - candYaw) / this.boxSizeYaw,
                        (rawErrorPitch - candPitch) / this.boxSizePitch
                ) - errorFactor);

                if (score < bestScore) {
                    bestScore = score;
                    bestDeltaYaw = candYaw;
                    bestDeltaPitch = candPitch;
                }
            }
        }

        if (forceMove && bestDeltaYaw == 0.0F && bestDeltaPitch == 0.0F) {
            if (Math.abs(rawErrorYaw) >= Math.abs(rawErrorPitch)) {
                bestDeltaYaw = Math.copySign(gcd, rawErrorYaw);
            } else {
                bestDeltaPitch = Math.copySign(gcd, rawErrorPitch);
            }
        }

        this.freezeTicks = (bestDeltaYaw == 0.0F && bestDeltaPitch == 0.0F) ? (this.freezeTicks + 1.0F) : 0.0F;

        float targetClampedPitch = MathHelper.clamp(currentPitch + bestDeltaPitch, -90.0F, 90.0F);
        bestDeltaPitch = targetClampedPitch - currentPitch;

        this.prevDeltaYaw2 = this.prevDeltaYaw;
        this.prevDeltaPitch2 = this.prevDeltaPitch;
        this.prevDeltaYaw = bestDeltaYaw;
        this.prevDeltaPitch = bestDeltaPitch;

        this.prevErrorYaw = this.errorYaw;
        this.prevErrorPitch = this.errorPitch;
        this.errorYaw = MathHelper.wrapDegrees(targetYaw - (currentYaw + bestDeltaYaw));
        this.errorPitch = targetPitch - targetClampedPitch;

        this.onTarget = Math.abs(this.errorYaw) <= this.boxSizeYaw && Math.abs(this.errorPitch) <= this.boxSizePitch;

        resultDelta[0] = bestDeltaYaw;
        resultDelta[1] = bestDeltaPitch;
        return true;
    }

    private float[] extractFeatures(NeuroModel model, float targetVelYaw, float targetVelPitch) {
        return new float[]{
                asinh(this.errorYaw),
                asinh(this.errorPitch),
                asinh(MathHelper.wrapDegrees(this.errorYaw - this.prevErrorYaw)),
                asinh(this.errorPitch - this.prevErrorPitch),
                asinh(targetVelYaw),
                asinh(targetVelPitch),
                asinh(this.prevDeltaYaw),
                asinh(this.prevDeltaPitch),
                asinh(this.prevDeltaYaw2),
                asinh(this.prevDeltaPitch2),
                asinh(this.errorYaw / this.boxSizeYaw),
                asinh(this.errorPitch / this.boxSizePitch),
                (float) Math.log(Math.max(this.distance, 0.05D) + 0.5D) / 2.0F,
                (float) Math.log(this.boxSizeYaw) / 3.0F,
                this.onTarget ? 1.0F : 0.0F,
                this.ticksSinceAttack / MAX_ATTACK_TICKS,
                this.freezeTicks / (float) model.getFreezeCut()
        };
    }

    private static float asinh(float val) {
        return (float) (Math.log(val + Math.sqrt(val * val + 1.0D)) / 3.0D);
    }

    private static float roundToGcd(float val, float gcd) {
        if (gcd <= 0.0F) {
            return val;
        }
        return (float) Math.round(val / gcd) * gcd;
    }
}
