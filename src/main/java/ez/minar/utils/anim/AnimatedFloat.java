package ez.minar.utils.anim;

/**
 * Port of Rockstar's {@code AnimatedFloat}: a scalar driven by a pluggable {@link Motion}.
 *
 * <p>The deliberately quirky part of the original - and it must be preserved - is that a freshly
 * constructed float is <em>not</em> resting, so its very first {@link #target(float)} snaps
 * instead of animating. That is why a freshly built row appears at its real size instead of
 * sliding up from zero, while every later change does animate.
 */
public final class AnimatedFloat {

    float value;
    float target;
    float velocity;
    float from;
    float elapsed;
    /** {@code false} until the first assignment has brought the float into a defined state. */
    boolean resting;
    private Motion motion;

    public AnimatedFloat(Motion motion) {
        this.motion = motion;
    }

    public AnimatedFloat(float initial, Motion motion) {
        this.motion = motion;
        snap(initial);
    }

    /** Swaps the motion driver in place; handy for making one element temporarily snappier. */
    public AnimatedFloat motion(Motion motion) {
        this.motion = motion;
        return this;
    }

    public Motion motion() {
        return motion;
    }

    /** Retargets the animation, recording the current value as the start point. */
    public void target(float destination) {
        if (!resting) {
            snap(destination);
            return;
        }
        if (destination == target) return;
        target = destination;
        if (motion != null) motion.begin(this);
    }

    /** Jumps to {@code instant} with no animation and clears any stored momentum. */
    public void snap(float instant) {
        target = from = instant;
        value = from;
        velocity = 0f;
        elapsed = 0f;
        resting = true;
    }

    /** Shifts value, target and start point together - used when the layout beneath moves. */
    public void offset(float amount) {
        if (!resting || amount == 0f) return;
        value += amount;
        target += amount;
        from += amount;
    }

    private long lastTime = -1L;

    /** Advances the animation; call once per frame with the frame time in milliseconds. */
    public void tick(float deltaMs) {
        if (motion != null) motion.step(this, deltaMs);
    }

    public void setTarget(float destination) {
        target(destination);
    }

    public float getValue() {
        long now = System.currentTimeMillis();
        if (lastTime > 0) {
            float deltaMs = Math.min(50f, now - lastTime);
            tick(deltaMs);
        }
        lastTime = now;
        return value;
    }

    public float get() {
        return value;
    }

    public float target() {
        return target;
    }

    public boolean settled() {
        return motion == null || motion.settled(this);
    }
}
