package ez.minar.system.managers;

public class TimerManager {
    private static float timer = 1.0F;

    public static void setTimer(float timer) {
        TimerManager.timer = Math.max(0.05F, timer);
    }

    public static float getTimer() {
        return timer;
    }

    public static void reset() {
        timer = 1.0F;
    }
}
