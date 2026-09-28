package ez.minar.utils.anim;

/**
 * Port of Rockstar's time-based tween: unlike {@link AnimatedFloat} it owns its clock, so a
 * caller can just ask {@link #to(float)} for the current value once per frame without threading
 * a delta through. This is the class Rockstar's setting components use for hover, press and
 * reveal opacity.
 *
 * <p>{@link ez.minar.utils.render.AnimatedValue} covers the same ground for the HUD; this one
 * exists so the ported UI stack keeps its original call sites.
 */
public class AnimatedValue {

    private final long durationMs;
    private Easing easing;
    private float value;
    private float from;
    private float target;
    private long startedAt;
    private boolean resting;
    /** Backing flag for {@link #flip()}, so a toggle can reverse mid-flight. */
    private boolean latched;

    public AnimatedValue(long durationMs, float initial, Easing easing) {
        this.durationMs = durationMs;
        this.easing = easing;
        this.value = initial;
        this.target = initial;
        this.from = initial;
        this.resting = true;
    }

    public AnimatedValue(long durationMs, Easing easing) {
        this(durationMs, 0f, easing);
    }

    public void setActive(boolean active) {
        to(active ? 1f : 0f);
    }

    /** Retargets when needed and returns the eased value for this frame. */
    public float to(float destination) {
        long now = System.currentTimeMillis();
        if (destination != target) {
            from = value;
            target = destination;
            startedAt = now;
            resting = false;
        }
        long elapsed = now - startedAt;
        if (elapsed >= durationMs) {
            value = target;
            resting = true;
            return value;
        }
        value = from + (target - from) * easing.ease(elapsed, 0f, 1f, (float) durationMs);
        return value;
    }

    /** Whether the tween has come to rest, or is hidden, depending on {@code atTarget}. */
    public boolean settled(boolean atTarget) {
        return atTarget ? value == target : value == 0f;
    }

    public void snap(float instant) {
        value = instant;
        target = instant;
        from = instant;
        resting = true;
    }

    /** Toggles between 0 and 1, remembering where the tween was if it is interrupted. */
    public void flip() {
        to(latched ? 0f : 1f);
        if (value == 1f) {
            latched = false;
        } else if (value == 0f) {
            latched = true;
        }
    }

    public long durationMs() {
        return durationMs;
    }

    public Easing easing() {
        return easing;
    }

    public void setEasing(Easing easing) {
        this.easing = easing;
    }

    public boolean latched() {
        return latched;
    }

    public boolean resting() {
        return resting;
    }
}
