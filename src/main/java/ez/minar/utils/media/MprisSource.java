package ez.minar.utils.media;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux now-playing over the MPRIS D-Bus spec, read with gdbus.
 *
 * A D-Bus client library would be the cleaner shape, but this project builds --offline and cannot
 * resolve a new dependency, so each read spawns a short-lived call. That cost is paid on the
 * poller thread once a second, never on the render thread, and the bus-name list is cached so a
 * steady state costs one call rather than one per player.
 */
public final class MprisSource implements MediaSource {

    private static final String PLAYER_IFACE = "org.mpris.MediaPlayer2.Player";
    private static final String NAME_PREFIX = "org.mpris.MediaPlayer2.";
    private static final long NAMES_TTL_MS = 5000L;
    private static final long CALL_TIMEOUT_MS = 1200L;

    private static final Pattern BUS_NAME = Pattern.compile("'(org\\.mpris\\.MediaPlayer2\\.[^']+)'");
    /** Covers both top-level props ('PlaybackStatus': <'Paused'>) and metadata entries. gdbus wraps
     *  strings containing an apostrophe ("I Was Made For Lovin' You") in double quotes, so both
     *  quote styles must parse or the title silently vanishes. */
    private static final Pattern STRING_PROP = Pattern.compile("'(?:[a-zA-Z:]+)':\\s*<(?:\"([^\"]*)\"|'([^']*)')>");
    private static final Pattern TYPED_PROP = Pattern.compile("'(xesam:title|xesam:album|xesam:url|mpris:artUrl|PlaybackStatus)':\\s*<(?:\"([^\"]*)\"|'([^']*)')>");
    private static final Pattern ARTIST_LIST = Pattern.compile("'xesam:artist':\\s*<\\[([^\\]]*)\\]>");
    private static final Pattern QUOTED = Pattern.compile("(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern LENGTH = Pattern.compile("'mpris:length':\\s*<int64\\s+(\\d+)>");
    private static final Pattern POSITION = Pattern.compile("'Position':\\s*<int64\\s+(\\d+)>");

    private final List<String> names = new ArrayList<>();
    private long namesReadAt;

    @Override
    public boolean available() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return !os.contains("win") && !os.contains("mac")
                && (System.getenv("DBUS_SESSION_BUS_ADDRESS") != null || new File("/run/user/" + uid() + "/bus").exists());
    }

    @Override
    public MediaSession read() {
        MediaSession paused = null;
        for (String busName : playerNames()) {
            String reply = gdbusCall(busName, "/org/mpris/MediaPlayer2",
                    "org.freedesktop.DBus.Properties.GetAll", PLAYER_IFACE);
            if (reply == null) continue;
            MediaSession session = parse(busName, reply);
            if (!session.hasTrack()) continue;
            lastPlayer = busName;
            // A playing session wins outright; the first paused one still carries the track, which
            // is what lets the island show it and offer play/pause again.
            if (session.playing()) return session;
            if (paused == null) paused = session;
        }
        return paused;
    }

    /** Bus names of every MPRIS player on the session bus, refreshed at most every five seconds. */
    private List<String> playerNames() {
        long now = System.currentTimeMillis();
        if (now - namesReadAt < NAMES_TTL_MS && !names.isEmpty()) return List.copyOf(names);
        namesReadAt = now;
        String reply = gdbusCall("org.freedesktop.DBus", "/org/freedesktop/DBus",
                "org.freedesktop.DBus.ListNames", null);
        names.clear();
        if (reply == null) return List.of();
        Matcher matcher = BUS_NAME.matcher(reply);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!names.contains(name)) names.add(name);
        }
        return List.copyOf(names);
    }

    private MediaSession parse(String busName, String reply) {
        String status = null;
        String title = "";
        String artUrl = "";
        Matcher typed = TYPED_PROP.matcher(reply);
        while (typed.find()) {
            String value = typed.group(2) != null ? typed.group(2) : typed.group(3);
            switch (typed.group(1)) {
                case "PlaybackStatus" -> status = value;
                case "xesam:title" -> title = value;
                case "mpris:artUrl" -> artUrl = value;
            }
        }
        String artist = first(ARTIST_LIST.matcher(reply));
        if (artist.isBlank()) {
            // Single-value artists come back as a plain string, and podcasts/videos have no artist at
            // all, where the album is the next best thing to attribute the track to.
            Matcher strings = STRING_PROP.matcher(reply);
            while (strings.find()) {
                String value = strings.group(1) != null ? strings.group(1) : strings.group(2);
                if (!value.isBlank() && !value.equals(title) && !value.startsWith("http")
                        && !value.startsWith("file:") && !value.equals(status)) {
                    artist = value;
                    break;
                }
            }
        }
        long lengthUs = number(LENGTH.matcher(reply));
        long positionUs = number(POSITION.matcher(reply));
        boolean playing = "Playing".equals(status);
        if (!playing && !"Paused".equals(status)) {
            return new MediaSession(playerId(busName), "", "", false, 0f, 0L, "", false, false, false);
        }
        return new MediaSession(playerId(busName), title, artist, playing,
                positionUs / 1_000_000f, lengthUs / 1_000_000L, artworkFile(artUrl),
                bool("CanPause", reply) || bool("CanPlay", reply),
                bool("CanGoNext", reply), bool("CanGoPrevious", reply));
    }

    /** MPRIS players advertise which transport buttons actually work; a browser cannot skip. */
    private static boolean bool(String key, String reply) {
        return java.util.regex.Pattern.compile("'" + key + "':\\s*<true>").matcher(reply).find();
    }

    /** `org.mpris.MediaPlayer2.firefox.instance_1_232` becomes `firefox`. */
    private static String playerId(String busName) {
        String id = busName.substring(NAME_PREFIX.length());
        int instance = id.indexOf(".instance");
        if (instance > 0) id = id.substring(0, instance);
        return id.toLowerCase();
    }

    /** MPRIS hands browsers' artwork as a local file; remote URLs are left for a later pass. */
    private static String artworkFile(String artUrl) {
        if (artUrl.startsWith("file://")) {
            String path = artUrl.substring("file://".length());
            return new File(path).isFile() ? path : "";
        }
        return "";
    }

    private static String first(Matcher matcher) {
        if (!matcher.find()) return "";
        Matcher quoted = QUOTED.matcher(matcher.group(1));
        if (!quoted.find()) return "";
        return quoted.group(1) != null ? quoted.group(1) : quoted.group(2);
    }

    private static long number(Matcher matcher) {
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    private static String uid() {
        String uid = System.getenv("XDG_RUNTIME_DIR");
        return uid != null && uid.startsWith("/run/user/") ? uid.substring("/run/user/".length()) : "0";
    }

    /** One gdbus round trip; null on any failure so a closed player never breaks the poll. */
    static String gdbusCall(String dest, String objectPath, String method, String argument) {
        List<String> command = new ArrayList<>(List.of("gdbus", "call", "--session",
                "--dest", dest, "--object-path", objectPath, "--method", method));
        if (argument != null) command.add(argument);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            // Wait before draining: reading first would block forever on a bus that stops answering,
            // and these replies are a few kilobytes, far under the pipe buffer.
            if (!process.waitFor(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** The bus name of the player we last reported, so a control acts on that one. */
    private String lastPlayer = "";

    @Override
    public boolean control(MediaCommand command) {
        List<String> players = playerNames();
        String target = players.contains(lastPlayer) ? lastPlayer
                : players.isEmpty() ? "" : players.get(0);
        if (target.isEmpty()) return false;
        lastPlayer = target;
        String method = switch (command) {
            case PLAY -> "org.mpris.MediaPlayer2.Player.Play";
            case STOP -> "org.mpris.MediaPlayer2.Player.Stop";
            case PAUSE -> "org.mpris.MediaPlayer2.Player.Pause";
            case NEXT -> "org.mpris.MediaPlayer2.Player.Next";
            case PREVIOUS -> "org.mpris.MediaPlayer2.Player.Previous";
        };
        return gdbusCall(target, "/org/mpris/MediaPlayer2", method, null) != null;
    }
}
