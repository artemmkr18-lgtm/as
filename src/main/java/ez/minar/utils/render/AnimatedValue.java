package ez.minar.utils.render;

/**
 * Rockstar's HUD motion: a fixed-duration tween driven by a CSS-style cubic-bezier,
 * so cards and pills settle with the same slight overshoot as in the source client
 * instead of the exponential crawl that a per-frame lerp gives.
 */
public final class AnimatedValue {

    /** Maps linear progress in [0,1] to eased progress; may exceed 1 for overshoot curves. */
    public interface Curve {
        float ease(float progress);
    }

    /** cubic-bezier(x1, y1, x2, y2) solved with Newton-Raphson, as Rockstar's factory does. */
    public static Curve bezier(final float x1, final float y1, final float x2, final float y2) {
        return progress -> {
            if (progress <= 0f) return 0f;
            if (progress >= 1f) return 1f;
            float t = progress;
            for (int i = 0; i < 8; i++) {
                float dx = sample(t, x1, x2) - progress;
                float slope = derivative(t, x1, x2);
                if (Math.abs(dx) < 1e-5f || Math.abs(slope) < 1e-6f) break;
                t = Math.clamp(t - dx / slope, 0f, 1f);
            }
            return sample(t, y1, y2);
        };
    }

    private static float sample(float t, float a, float b) {
        float inv = 1f - t;
        return 3f * inv * inv * t * a + 3f * inv * t * t * b + t * t * t;
    }

    private static float derivative(float t, float a, float b) {
        return 3f * ((1f - t) * (1f - 3f * t) * a + (2f * t - 3f * t * t) * b) + 3f * t * t;
    }

    /** Rockstar's UiInternal021 card/visibility curve: 300ms with a 9% overshoot. */
    public static final Curve CARD = bezier(0.27f, 1.09f, 0.49f, 1.06f);
    /** Rockstar's drag and blur curve: symmetric ease-in-out. */
    public static final Curve DRAG = bezier(0.42f, 0f, 0.58f, 1f);
    /** Rockstar's FPS counter curve: smoothstep, so the number never jitters frame to frame. */
    public static final Curve SMOOTHSTEP = p -> -2f * p * p * p + 3f * p * p;
    /** Rockstar's UiNode.appear curve (Easing easeOutCubic), used for every list row. */
    public static final Curve EASE_OUT_CUBIC = p -> {
        float f = p - 1f;
        return f * f * f + 1f;
    };

    private final long durationMs;
    private Curve curve;
    private float value;
    private float from;
    private float target;
    private long startedAt;

    /** Rockstar swaps the curve per direction, so a card overshoots in and sucks out. */
    public void setCurve(Curve destination) {
        this.curve = destination;
    }

    public AnimatedValue(long durationMs, float initial, Curve curve) {
        this.durationMs = Math.max(durationMs, 1L);
        this.curve = curve;
        this.value = initial;
        this.from = initial;
        this.target = initial;
        this.startedAt = System.currentTimeMillis();
    }

    /** Retargets the tween when needed and returns the eased value for this frame. */
    public float to(float destination) {
        long now = System.currentTimeMillis();
        if (destination != target) {
            from = value;
            target = destination;
            startedAt = now;
        }
        long elapsed = now - startedAt;
        if (elapsed >= durationMs) {
            value = target;
        } else {
            value = from + (target - from) * curve.ease((float) elapsed / durationMs);
        }
        return value;
    }

    public float get() {
        return value;
    }

    public void snap(float instant) {
        value = instant;
        from = instant;
        target = instant;
        startedAt = System.currentTimeMillis();
    }
}
