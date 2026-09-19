package ez.minar.utils.helpers;

public final class FarmTimer {
    private long last;

    public FarmTimer() {
        reset();
    }

    public void reset() {
        last = System.currentTimeMillis();
    }

    public boolean passed(long ms) {
        return System.currentTimeMillis() - last >= ms;
    }

    public boolean tryReset(long ms) {
        if (!passed(ms)) return false;
        reset();
        return true;
    }

    public long elapsed() {
        return System.currentTimeMillis() - last;
    }
}
