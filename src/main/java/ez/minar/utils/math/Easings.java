package ez.minar.utils.math;

public class Easings {
    public static float OutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return (float) (1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0));
    }

    public static float OutCubic(float x) {
        return (float) (1.0 - Math.pow(1.0 - x, 3.0));
    }

    public static float OutQuint(float x) {
        return (float) (1.0 - Math.pow(1.0 - x, 5.0));
    }

    public static float InOutQuad(float x) {
        return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
    }

    public static float InOutCubic(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    public static float OutQuart(float x) {
        return (float) (1.0 - Math.pow(1.0 - x, 4.0));
    }
}
