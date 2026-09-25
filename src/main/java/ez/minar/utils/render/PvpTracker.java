package ez.minar.utils.render;

public class PvpTracker {
    public static volatile int pvpSeconds = 0;
    public static volatile boolean inPvp = false;

    public static int getPvpSeconds() {
        return pvpSeconds;
    }

    public static boolean isInPvp() {
        return inPvp;
    }

    public static void update(boolean pvp, int seconds) {
        inPvp = pvp;
        pvpSeconds = seconds;
    }
}
