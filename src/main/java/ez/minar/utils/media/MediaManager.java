package ez.minar.utils.media;

/**
 * Polls the desktop's now-playing session away from the render thread and keeps the last snapshot.
 *
 * The poll spawns a short-lived system call, so it runs on its own daemon thread and the HUD only
 * ever reads a volatile field. A reported track is held briefly after it stops being seen, otherwise
 * pausing or closing the player would blank the island mid-animation instead of letting it fade out.
 */
public final class MediaManager {

    private static final long POLL_MS = 300L;
    private static final long STICKY_MS = 6000L;

    private static volatile MediaSession snapshot = MediaSession.NONE;
    private static volatile long lastSeenAt;
    /**
     * The browser throttles its reported position to a crawl while it sits unfocused, which would
     * freeze the lyric timing while the audio keeps playing. So the last position that actually
     * advanced is kept with its wall-clock stamp and extrapolated forward while playing.
     */
    private static volatile double positionBase;
    private static volatile long positionBaseAt;
    /** Highest position the player has reported and when it last changed: freshness evidence. */
    private static volatile double lastReported;
    private static volatile long lastFreshAt;
    private static volatile long lastReadAt;
    private static volatile boolean running;
    private static MediaSource source;
    private static Thread thread;

    private MediaManager() {
    }

    public static void start() {
        if (running) return;
        source = forCurrentOs();
        if (source == null) return;
        running = true;
        thread = new Thread(MediaManager::poll, "Minar media session");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
        snapshot = MediaSession.NONE;
        positionBase = 0.0;
        positionBaseAt = System.currentTimeMillis();
        lastReported = 0.0;
        LyricsManager.reset();
    }

    /**
     * Track position interpolated with the wall clock between the 1s polls, so lyric lines can
     * change mid-second instead of one poll late.
     */
    public static double positionSeconds() {
        MediaSession session = snapshot;
        double position = positionBase;
        if (session.playing()) position += (System.currentTimeMillis() - positionBaseAt) / 1000.0;
        return Math.clamp(position, 0.0, Math.max(0.0, session.durationSeconds()));
    }

    /** The latest track, or {@link MediaSession#NONE} once the sticky window has passed. */
    public static MediaSession current() {
        MediaSession session = snapshot;
        if (session == MediaSession.NONE) return session;
        if (System.currentTimeMillis() - lastSeenAt > STICKY_MS) {
            snapshot = MediaSession.NONE;
            return MediaSession.NONE;
        }
        return session;
    }

    public static boolean control(MediaCommand command) {
        MediaSource active = source;
        if (active == null) return false;
        try {
            return active.control(command);
        } catch (Exception e) {
            return false;
        }
    }

    private static void poll() {
        while (running) {
            try {
                MediaSession session = source.read();
                if (session != null && session.hasTrack()) {
                    long now = System.currentTimeMillis();
                    MediaSession previous = snapshot;
                    if (!previous.hasTrack() || !previous.title().equals(session.title())
                            || !previous.artist().equals(session.artist())) {
                        positionBase = 0.0;
                        positionBaseAt = now;
                        lastReported = 0.0;
                        lastFreshAt = now;
                    }
                    snapshot = session;
                    lastSeenAt = now;
                    double reported = session.positionSeconds();
                    if (reported > lastReported + 0.05 || reported < lastReported - 2.0) {
                        // A fresh sample was taken somewhere between the last read and this one, so
                        // anchor the clock at the midpoint of that window: adopting it as "now" is
                        // what made the words lag half a poll behind, every poll.
                        lastReported = reported;
                        lastFreshAt = now;
                        positionBase = reported;
                        positionBaseAt = (lastReadAt + now) / 2L;
                    } else if (!session.playing()) {
                        positionBase = reported;
                        positionBaseAt = now;
                        lastReported = reported;
                    } else if (now - lastFreshAt > 3000) {
                        // Claims Playing but reports no progress for 3s: a stall or a throttled page.
                        // Re-anchor to the reported value instead of extrapolating ahead of the audio.
                        positionBase = reported;
                        positionBaseAt = now;
                    }
                    lastReadAt = now;
                    LyricsManager.request(session.artist(), session.title(), session.durationSeconds());
                }
            } catch (Exception ignored) {
                // A player that vanished mid-read is normal; keep the previous snapshot.
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static MediaSource forCurrentOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            // Windows SMTC lands in a later pass; until then the island simply shows no track.
            return null;
        }
        MprisSource mpris = new MprisSource();
        return mpris.available() ? mpris : null;
    }
}
