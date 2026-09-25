package ez.minar.utils.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rockstar's per-element motion, ported from UiInternal021.update() and its draw path.
 *
 * `animation` follows the user's show/hide toggle, `visible` the contextual gate (HUD hidden,
 * another screen). Alpha is min(1, animation * visible) but the drawn size is 0.5 + 0.5 * that same
 * product about the element's centre - only opacity is allowed to saturate, because clamping the
 * product threw the curve's overshoot away and the card looked like it never moved at all.
 */
public final class HudMotion {
    /** UiInternal021: the curve swapped in while showing. */
    public static final AnimatedValue.Curve POP = AnimatedValue.bezier(0.45f, 1.45f, 0.49f, 1.15f);
    /** UiInternal021: the curve swapped in while hiding; it dips below zero first. */
    public static final AnimatedValue.Curve SUCK = AnimatedValue.bezier(0.62f, -0.16f, 0.8f, 0.37f);
    /** UiInternal021's dragAnim / blurAnim curve. */
    public static final AnimatedValue.Curve GLIDE = AnimatedValue.bezier(0.42f, 0f, 0.58f, 1f);
    /** UiNode.appear: the curve every list row uses, in and out. */
    public static final AnimatedValue.Curve ROW = AnimatedValue.EASE_OUT_CUBIC;

    /** UiNode: row entrance duration. */
    public static final long ROW_MS = 240L;
    /** ScriptInternal107: a row travels 6px while it fades. */
    public static final float ROW_SLIDE = 6.0f;

    private static final float EPSILON = 0.004f;

    private final AnimatedValue animation = new AnimatedValue(300L, 0f, POP);
    private final AnimatedValue visible = new AnimatedValue(300L, 0f, POP);
    private final AnimatedValue dragAnim = new AnimatedValue(300L, 0f, GLIDE);
    private final AnimatedValue blurAnim = new AnimatedValue(300L, 0f, GLIDE);

    /** Stacked-card rows keyed by their label; the caller keeps the row's payload. */
    private final Map<String, AnimatedValue> rows = new LinkedHashMap<>();
    private final Set<String> rowsLive = new HashSet<>();
    private final List<String> rowsLingering = new ArrayList<>();

    /** Advances every tween for this frame, exactly as UiInternal021.update() does. */
    public void update(boolean showing, boolean contextVisible, boolean dragging) {
        animation.setCurve(showing ? POP : SUCK);
        animation.to(showing ? 1f : 0f);
        visible.setCurve(contextVisible ? POP : SUCK);
        visible.to(contextVisible ? 1f : 0f);
        dragAnim.to(dragging ? 1f : 0f);
        blurAnim.to(animation.get() >= 0.6f ? 1f : 0f);
    }

    /** Drawn opacity. */
    public float alpha() {
        return Math.clamp(animation.get() * visible.get(), 0f, 1f);
    }

    /**
     * Drawn size multiplier, deliberately unclamped: UiInternal021 scales from half to full and lets
     * the appear curve carry it past 1 before it settles.
     */
    public float scale() {
        return scale(0.5f);
    }

    /**
     * Same curve from a different starting size. Tall stacked cards read as a zoom at Rockstar's
     * half scale, so they settle from closer in while a single card keeps the full pop.
     */
    public float scale(float from) {
        return from + (1f - from) * animation.get() * visible.get();
    }

    /** Blur strength multiplier: zero until the card is mostly on screen. */
    public float blur() {
        return blurAnim.get();
    }

    public float drag() {
        return dragAnim.get();
    }

    public boolean settled() {
        return animation.get() >= 1f && visible.get() >= 1f;
    }

    public void snap(boolean showing) {
        animation.snap(showing ? 1f : 0f);
        visible.snap(showing ? 1f : 0f);
        blurAnim.snap(showing ? 1f : 0f);
        rows.clear();
        rowsLive.clear();
    }

    /**
     * Alpha of one row. The caller advances every live row and then the lingering ones once per
     * frame; the value doubles as the row's share of the container height, which is how a leaving
     * row collapses the stack instead of punching a hole in it.
     */
    public float rowAlpha(String key, boolean live) {
        if (live) rowsLive.add(key);
        AnimatedValue value = rows.get(key);
        if (value == null) {
            value = new AnimatedValue(ROW_MS, 0f, ROW);
            rows.put(key, value);
        }
        value.setCurve(ROW);
        return Math.clamp(value.to(live ? 1f : 0f), 0f, 1f);
    }

    /** How far a row still has to travel at this alpha, in Rockstar's 6px. */
    public static float rowSlide(float alpha) {
        return ROW_SLIDE * (1f - alpha);
    }

    /**
     * Rows that left the list but have not finished fading out. Call after every live row has been
     * asked for its alpha; the returned list is only valid until the next call.
     */
    public List<String> lingeringRows() {
        rowsLingering.clear();
        for (Map.Entry<String, AnimatedValue> row : rows.entrySet()) {
            if (!rowsLive.contains(row.getKey()) && row.getValue().get() > EPSILON) {
                rowsLingering.add(row.getKey());
            }
        }
        return rowsLingering;
    }

    /**
     * Same, minus the rows the caller still draws from its live list. While a card is fading out as
     * a whole ({@code showing == false}) {@code rowAlpha} never marks its rows live, so the plain
     * lingering list also returns them - and they would be drawn a second time below the originals.
     */
    public List<String> lingeringRows(List<String> liveKeys) {
        rowsLingering.clear();
        for (Map.Entry<String, AnimatedValue> row : rows.entrySet()) {
            String key = row.getKey();
            if (!rowsLive.contains(key) && !liveKeys.contains(key) && row.getValue().get() > EPSILON) {
                rowsLingering.add(key);
            }
        }
        return rowsLingering;
    }

    /** Rows still worth keeping a cached payload for. */
    public Set<String> rowKeys() {
        return rows.keySet();
    }

    /** Drops fully faded rows and reopens the live set for the next frame. */
    public void endRows() {
        rows.entrySet().removeIf(row -> !rowsLive.contains(row.getKey()) && row.getValue().get() <= EPSILON);
        rowsLive.clear();
    }
}
