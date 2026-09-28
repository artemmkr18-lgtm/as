package ez.minar.utils.anim;

/**
 * Port of Rockstar's easing table.
 *
 * <p>{@link #ease(float, float, float, float)} keeps Robert Penner's original
 * signature - {@code (time, start, change, duration)} - because every caller in the
 * ported UI stack is written against it. Curves are pure functions, so they are safe
 * to share as constants.
 */
public interface Easing {

    float ease(float time, float start, float change, float duration);

    default float ease(float progress) {
        return ease(progress, 0f, 1f, 1f);
    }

    // ------------------------------------------------------------------ cubic-bezier presets
    /** Rockstar's signature curve: quick start, ~9% overshoot, soft settle. */
    Easing SWING_OVERSHOOT = bezier(0.45f, 1.45f, 0.49f, 1.15f);
    /** Same attack as {@link #SWING_OVERSHOOT} but lands exactly on the target. */
    Easing SWING_LANDING = bezier(0.45f, 1.45f, 0.43f, 0.91f);
    Easing GLIDE_BOUNCE = bezier(0.1f, 1.07f, 0.34f, 1.04f);
    /** The card / visibility curve used across Rockstar's panels. */
    Easing CARD = bezier(0.27f, 1.09f, 0.49f, 1.06f);
    /** Anticipation curve - dips below zero before rising. */
    Easing ANTICIPATE = bezier(0.62f, -0.16f, 0.8f, 0.37f);
    Easing POP = bezier(0.25f, 1.07f, 0.11f, 1.1f);
    /** CSS {@code ease-in-out}; used for drags and blurs. */
    Easing EASE_IN_OUT = bezier(0.42f, 0f, 0.58f, 1f);

    // ------------------------------------------------------------------ polynomial family
    Easing LINEAR = (t, s, c, d) -> c * t / d + s;

    Easing IN_QUAD = (t, s, c, d) -> c * (t /= d) * t + s;
    Easing OUT_QUAD = (t, s, c, d) -> -c * (t /= d) * (t - 2f) + s;
    Easing IN_OUT_QUAD = (t, s, c, d) -> {
        t /= d / 2f;
        if (t < 1f) return c / 2f * t * t + s;
        return -c / 2f * ((t -= 1f) * (t - 2f) - 1f) + s;
    };

    Easing IN_CUBIC = (t, s, c, d) -> c * (t /= d) * t * t + s;
    Easing OUT_CUBIC = (t, s, c, d) -> {
        t = t / d - 1f;
        return c * (t * t * t + 1f) + s;
    };
    Easing IN_OUT_CUBIC = (t, s, c, d) -> {
        t /= d / 2f;
        if (t < 1f) return c / 2f * t * t * t + s;
        return c / 2f * ((t -= 2f) * t * t + 2f) + s;
    };

    Easing IN_QUART = (t, s, c, d) -> c * (t /= d) * t * t * t + s;
    Easing OUT_QUART = (t, s, c, d) -> {
        t = t / d - 1f;
        return -c * (t * t * t * t - 1f) + s;
    };
    Easing IN_OUT_QUART = (t, s, c, d) -> {
        t /= d / 2f;
        if (t < 1f) return c / 2f * t * t * t * t + s;
        return -c / 2f * ((t -= 2f) * t * t * t - 2f) + s;
    };

    Easing IN_QUINT = (t, s, c, d) -> c * (t /= d) * t * t * t * t + s;
    Easing OUT_QUINT = (t, s, c, d) -> {
        t = t / d - 1f;
        return c * (t * t * t * t * t + 1f) + s;
    };
    Easing IN_OUT_QUINT = (t, s, c, d) -> {
        t /= d / 2f;
        if (t < 1f) return c / 2f * t * t * t * t * t + s;
        return c / 2f * ((t -= 2f) * t * t * t * t + 2f) + s;
    };

    /** Smoothstep - the curve Rockstar drives its FPS counter with so digits never jitter. */
    Easing SMOOTHSTEP = (t, s, c, d) -> {
        float p = c * t / d + s;
        return -2f * p * p * p + 3f * p * p;
    };

    // ------------------------------------------------------------------ exponential / circular
    Easing IN_EXPO = (t, s, c, d) -> t == 0f ? s : c * (float) Math.pow(2f, 10f * (t / d - 1f)) + s;
    Easing OUT_EXPO = (t, s, c, d) -> t == d ? s + c : c * (1f - (float) Math.pow(2f, -10f * t / d)) + s;
    Easing IN_OUT_EXPO = (t, s, c, d) -> {
        if (t == 0f) return s;
        if (t == d) return s + c;
        t /= d / 2f;
        if (t < 1f) return c / 2f * (float) Math.pow(2f, 10f * (t - 1f)) + s;
        return c / 2f * (2f - (float) Math.pow(2f, -10f * (t -= 1f))) + s;
    };

    Easing IN_CIRC = (t, s, c, d) -> -c * ((float) Math.sqrt(1f - (t /= d) * t) - 1f) + s;
    Easing OUT_CIRC = (t, s, c, d) -> {
        t = t / d - 1f;
        return c * (float) Math.sqrt(1f - t * t) + s;
    };
    Easing IN_OUT_CIRC = (t, s, c, d) -> {
        t /= d / 2f;
        if (t < 1f) return -c / 2f * ((float) Math.sqrt(1f - t * t) - 1f) + s;
        return c / 2f * ((float) Math.sqrt(1f - (t -= 2f) * t) + 1f) + s;
    };

    // ------------------------------------------------------------------ sinusoidal
    Easing IN_SINE = (t, s, c, d) -> c * (float) Math.sin(t / d * Math.PI / 2) + s;
    Easing OUT_SINE = (t, s, c, d) -> -c * (float) Math.cos(t / d * Math.PI / 2) + c + s;
    Easing IN_OUT_SINE = (t, s, c, d) -> -c / 2f * ((float) Math.cos(Math.PI * t / d) - 1f) + s;

    // ------------------------------------------------------------------ elastic / back / bounce
    Easing IN_ELASTIC = new Elastic(1f, 0.3f) {
        @Override
        public float ease(float t, float s, float c, float d) {
            if (t == 0f) return s;
            if ((t /= d) == 1f) return s + c;
            return elastic(t, s, c, d, true);
        }
    };
    Easing OUT_ELASTIC = new Elastic(1f, 0.3f) {
        @Override
        public float ease(float t, float s, float c, float d) {
            if (t == 0f) return s;
            if ((t /= d) == 1f) return s + c;
            return elastic(t, s, c, d, false);
        }
    };
    Easing IN_OUT_ELASTIC = new Elastic(1f, 0.3f) {
        @Override
        public float ease(float t, float s, float c, float d) {
            if (t == 0f) return s;
            if ((t /= d / 2f) == 2f) return s + c;
            return t < 1f ? elastic(t, s, c, d, true) : elastic(t, s, c, d, false);
        }
    };

    Easing IN_BACK = new Back(1.70158f) {
        @Override
        public float ease(float t, float s, float c, float d) {
            float k = overshoot();
            return c * (t /= d) * t * ((k + 1f) * t - k) + s;
        }
    };
    Easing OUT_BACK = new Back(1.70158f) {
        @Override
        public float ease(float t, float s, float c, float d) {
            float k = overshoot();
            t = t / d - 1f;
            return c * (t * t * ((k + 1f) * t + k) + 1f) + s;
        }
    };
    Easing IN_OUT_BACK = new Back(1.70158f) {
        @Override
        public float ease(float t, float s, float c, float d) {
            float k = overshoot() * 1.525f;
            t /= d / 2f;
            if (t < 1f) return c / 2f * (t * t * ((k + 1f) * t - k)) + s;
            return c / 2f * ((t -= 2f) * t * ((k + 1f) * t + k) + 2f) + s;
        }
    };

    Easing IN_BOUNCE = (t, s, c, d) -> {
        t /= d;
        if (t < 0.36363637f) return c * (7.5625f * t * t) + s;
        if (t < 0.72727275f) return c * (7.5625f * (t -= 0.54545456f) * t + 0.75f) + s;
        if (t < 0.90909094f) return c * (7.5625f * (t -= 0.8181818f) * t + 0.9375f) + s;
        return c * (7.5625f * (t -= 0.95454544f) * t + 0.984375f) + s;
    };
    Easing OUT_BOUNCE = (t, s, c, d) -> c - IN_BOUNCE.ease(d - t, 0f, c, d) + s;
    Easing IN_OUT_BOUNCE = (t, s, c, d) -> {
        if (t < d / 2f) return OUT_BOUNCE.ease(t * 2f, 0f, c, d) * 0.5f + s;
        return IN_BOUNCE.ease(t * 2f - d, 0f, c, d) * 0.5f + c * 0.5f + s;
    };

    /**
     * Builds a CSS-style {@code cubic-bezier(x1, y1, x2, y2)} curve, solved for x with
     * Newton-Raphson exactly as Rockstar's factory does - this is what gives their UI the
     * overshoot that a plain per-frame lerp can never reproduce.
     */
    static Easing bezier(double x1, double y1, double x2, double y2) {
        float fx1 = (float) x1, fy1 = (float) y1, fx2 = (float) x2, fy2 = (float) y2;
        return new Easing() {
            @Override
            public float ease(float time, float start, float change, float duration) {
                if (duration <= 0f || time <= 0f) return start;
                if (time >= duration) return start + change;
                return start + change * evaluate(solveX(fx1, fx2, time / duration), fy1, fy2);
            }

            private float solveX(float a, float b, float x) {
                float t = x;
                for (int i = 0; i < 8; i++) {
                    float dx = evaluate(t, a, b) - x;
                    float slope = derivative(t, a, b);
                    if (Math.abs(dx) < 1e-5f || Math.abs(slope) < 1e-6f) break;
                    t = Math.max(0f, Math.min(1f, t - dx / slope));
                }
                return t;
            }

            private float derivative(float t, float a, float b) {
                return 3f * ((1f - t) * (1f - 3f * t) * a + (2f * t - 3f * t * t) * b) + 3f * t * t;
            }
        };
    }

    /** Evaluates one axis of a cubic bezier whose endpoints are pinned to 0 and 1. */
    private static float evaluate(float t, float a, float b) {
        float inv = 1f - t;
        return 3f * inv * inv * t * a + 3f * inv * t * t * b + t * t * t;
    }

    /**
     * Base for the three elastic curves, which share Penner's amplitude/period bookkeeping:
     * when the requested change exceeds the amplitude the wave is re-scaled and phase-shifted,
     * otherwise the curve would visibly cut through its own endpoints.
     */
    abstract class Elastic implements Easing {
        private final float amplitude;
        private final float period;

        protected Elastic(float amplitude, float period) {
            this.amplitude = amplitude;
            this.period = period;
        }

        /**
         * @param t normalized and already shifted into the [0,1) range
         * @param inward {@code true} for the ease-in half, {@code false} for ease-out
         */
        protected float elastic(float t, float s, float c, float d, boolean inward) {
            float a = amplitude;
            float p = period == 0f ? d * 0.45f : period;
            float skew;
            if (a < Math.abs(c)) {
                a = c;
                skew = p / 4f;
            } else {
                skew = p / ((float) Math.PI * 2f) * (float) Math.asin(c / a);
            }
            float wave = (float) Math.sin((t * d - skew) * Math.PI * 2 / p);
            if (inward) {
                return -0.5f * a * (float) Math.pow(2f, 10f * (t -= 1f)) * wave + s;
            }
            return a * (float) Math.pow(2f, -10f * (t -= 1f)) * wave * 0.5f + c + s;
        }
    }

    /** Base for the three back curves, which share a single overshoot constant. */
    abstract class Back implements Easing {
        private final float overshoot;

        protected Back(float overshoot) {
            this.overshoot = overshoot;
        }

        protected float overshoot() {
            return overshoot;
        }
    }
}
