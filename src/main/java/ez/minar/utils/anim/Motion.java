package ez.minar.utils.anim;

/**
 * Port of Rockstar's motion model: either a fixed-duration tween driven by an
 * {@link Easing}, or a critically-damped-ish spring. Rockstar uses springs for things that
 * are dragged (they react to retargeting mid-flight) and tweens for one-shot reveals.
 */
public abstract class Motion {

    /** Panels and pop-ups: 300ms with Rockstar's overshoot. */
    public static final Motion PANEL = timed(300L, Easing.SWING_OVERSHOOT);
    /** Slower, landing exactly on target - used for window open/close. */
    public static final Motion WINDOW = timed(450L, Easing.SWING_LANDING);
    /** Snappy state changes such as toggles and chip selection. */
    public static final Motion SNAP = timed(250L, Easing.EASE_IN_OUT);
    public static final Motion FAST_FADE = timed(150L, Easing.OUT_QUART);
    public static final Motion FADE = timed(200L, Easing.OUT_QUART);
    /** The spring behind setting rows and scroll offsets. */
    public static final Motion ROW = spring(220f, 24f);
    /** Stiffer, slower spring for large surfaces that must not wobble. */
    public static final Motion SURFACE = spring(380f, 30f);

    /** Captures the current value as the animation's start point. */
    public abstract void begin(AnimatedFloat value);

    /** Advances the animation by {@code deltaMs} of wall-clock time. */
    public abstract void step(AnimatedFloat value, float deltaMs);

    /** True once the value has come to rest and can stop being ticked. */
    public boolean settled(AnimatedFloat value) {
        return Math.abs(value.value - value.target) < 0.01f && Math.abs(value.velocity) < 0.01f;
    }

    public static Motion timed(long durationMs, Easing easing) {
        return new Timed(Math.max(1L, durationMs), easing);
    }

    public static Motion linear(long durationMs) {
        return timed(durationMs, Easing.LINEAR);
    }

    /**
     * @param stiffness how hard the spring pulls toward the target
     * @param damping how quickly the motion is bled off; higher means less ringing
     */
    public static Motion spring(float stiffness, float damping) {
        return new Spring(stiffness, damping);
    }

    static final class Timed extends Motion {
        private final long durationMs;
        private final Easing easing;

        Timed(long durationMs, Easing easing) {
            this.durationMs = durationMs;
            this.easing = easing;
        }

        @Override
        public void begin(AnimatedFloat value) {
            value.from = value.value;
            value.elapsed = 0f;
        }

        @Override
        public void step(AnimatedFloat value, float deltaMs) {
            if (value.value == value.target) return;
            value.elapsed += deltaMs;
            float progress = Math.min(1f, value.elapsed / (float) durationMs);
            value.value = value.from + (value.target - value.from) * easing.ease(progress, 0f, 1f, 1f);
            if (progress >= 1f) {
                value.value = value.target;
                value.velocity = 0f;
            }
        }

        @Override
        public boolean settled(AnimatedFloat value) {
            return value.value == value.target;
        }
    }

    static final class Spring extends Motion {
        private final float stiffness;
        private final float damping;

        Spring(float stiffness, float damping) {
            this.stiffness = stiffness;
            this.damping = damping;
        }

        @Override
        public void begin(AnimatedFloat value) {
            // Springs are defined by position and velocity alone, so there is nothing to capture.
        }

        @Override
        public void step(AnimatedFloat value, float deltaMs) {
            float dt = Math.min(0.05f, deltaMs / 1000f);
            if (dt <= 0f) return;
            float displacement = value.value - value.target;
            float acceleration = -stiffness * displacement - damping * value.velocity;
            value.velocity += acceleration * dt;
            value.value += value.velocity * dt;
            if (Math.abs(displacement) < 0.05f && Math.abs(value.velocity) < 0.05f) {
                value.value = value.target;
                value.velocity = 0f;
            }
        }
    }
}
