package ez.minar.utils.render.chat;

public final class ChatAnimationRenderState {
    private static float alpha = 1.0f;
    private static float shift;

    private ChatAnimationRenderState() {
    }

    public static void set(float lineAlpha, float lineShift) {
        alpha = lineAlpha;
        shift = lineShift;
    }

    public static void reset() {
        alpha = 1.0f;
        shift = 0.0f;
    }

    public static float alpha() {
        return alpha;
    }

    public static float shift() {
        return shift;
    }
}
