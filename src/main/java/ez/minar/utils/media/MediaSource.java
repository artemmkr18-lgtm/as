package ez.minar.utils.media;

/**
 * Reads the desktop's now-playing session the way each OS publishes it: MPRIS over D-Bus on Linux,
 * SMTC on Windows. Implementations are polled from a background thread only.
 */
public interface MediaSource {
    /** False on an OS this backend cannot serve, so the manager can skip it without trying. */
    boolean available();

    /** The session that is playing right now, or null when nothing is. Must never throw. */
    MediaSession read();

    /** Sends a transport command to the player that was last reported. False if there is none. */
    boolean control(MediaCommand command);
}
