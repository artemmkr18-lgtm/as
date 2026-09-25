package ez.minar.utils.media;

/**
 * One read of the desktop's now-playing session. Immutable so the render thread can hold a
 * reference without synchronising on the poller that produces it.
 */
public record MediaSession(String player, String title, String artist, boolean playing,
                           float positionSeconds, long durationSeconds, String artworkPath,
                           boolean canPlayPause, boolean canNext, boolean canPrevious) {

    public static final MediaSession NONE =
            new MediaSession("", "", "", false, 0f, 0L, "", false, false, false);

    public boolean hasTrack() {
        return !title.isBlank() || !artist.isBlank();
    }

    /** "Artist — Title", or whichever half exists; empty when nothing is known. */
    public String label() {
        if (artist.isBlank()) return title;
        if (title.isBlank()) return artist;
        return artist + " — " + title;
    }

    /** Progress in [0,1]; zero when the player reports no length (live streams, radios). */
    public float progress() {
        if (durationSeconds <= 0L) return 0f;
        return Math.clamp(positionSeconds / (float) durationSeconds, 0f, 1f);
    }
}
