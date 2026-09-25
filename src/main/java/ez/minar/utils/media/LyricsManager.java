package ez.minar.utils.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synced lyrics for the now-playing track, fetched from lrclib.net (no key, no account).
 *
 * The lookup runs on one daemon request away from the render thread; the HUD only ever reads the
 * volatile line list. A miss is cached per track so a song without lyrics never re-hammers the API
 * from the 1s media poll.
 */
public final class LyricsManager {

    /** One LRC timestamp line: when it starts singing and what it sings. */
    public record Line(long startMs, String text) {
    }

    /** The line being sung right now, with the window it owns - words are revealed across it. */
    public record Active(String text, long startMs, long endMs) {
    }

    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?](.*)");
    private static final String BASE = "https://lrclib.net/api/";
    private static final String USER_AGENT = "MinarClient/1.0 (Fabric HUD)";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static volatile List<Line> lines = List.of();
    /** artist|title of the loaded track; the render thread compares against this. */
    private static volatile String loadedKey = "";
    private static volatile String requestedKey = "";
    /** A miss is only provisional: mid-switch polls mix new titles with old durations, so retry later. */
    private static volatile String failedKey = "";
    private static volatile long failedAt;
    private static final long RETRY_MS = 20_000L;

    private LyricsManager() {
    }

    /** Kicks off a fetch when the media poll moves to a new track. Safe to call every poll. */
    public static void request(String artist, String title, long durationSec) {
        String key = artist + "|" + title;
        boolean staleFail = key.equals(failedKey) && System.currentTimeMillis() - failedAt > RETRY_MS;
        if ((key.equals(requestedKey) && !staleFail) || (key.equals(failedKey) && !staleFail)) return;
        requestedKey = key;
        Thread worker = new Thread(() -> fetch(key, artist, title, durationSec), "Minar lyrics");
        worker.setDaemon(true);
        worker.start();
    }

    public static void reset() {
        lines = List.of();
        loadedKey = "";
        requestedKey = "";
        failedKey = "";
    }

    /** True when {@link #currentLine} can be trusted for this track. */
    public static boolean hasLyrics(String artist, String title) {
        return !lines.isEmpty() && loadedKey.equals(artist + "|" + title);
    }

    /**
     * The line being sung at {@code positionMs}, or null outside its window. A line lives from its
     * timestamp until the next one starts, but at most ~2.5s plus its reading time: an instrumental
     * gap must blank the row, not leave last verse hanging for a minute.
     */
    public static Active lineAt(String artist, String title, double positionMs) {
        List<Line> snapshot = lines;
        if (snapshot.isEmpty() || !loadedKey.equals(artist + "|" + title)) return null;
        Line active = null;
        long nextStart = Long.MAX_VALUE;
        for (int i = 0; i < snapshot.size(); i++) {
            Line line = snapshot.get(i);
            if (line.startMs() > positionMs) break;
            if (line.text().isEmpty()) continue;
            active = line;
            nextStart = i + 1 < snapshot.size() ? snapshot.get(i + 1).startMs() : Long.MAX_VALUE;
        }
        if (active == null) return null;
        long end = Math.min(nextStart, active.startMs() + Math.max(3000L, active.text().length() * 80L));
        return positionMs < end ? new Active(active.text(), active.startMs(), end) : null;
    }

    /**
     * True while the playhead sits inside the song's vocal span (a second and a half before the
     * first line until the last line's window closes). The panel band keys off this, not off the
     * active line, so dense lyrics do not make it pump open and shut between every verse.
     */
    public static boolean inLyricSpan(String artist, String title, double positionMs) {
        List<Line> snapshot = lines;
        if (snapshot.isEmpty() || !loadedKey.equals(artist + "|" + title)) return false;
        Line first = null;
        Line last = null;
        for (Line line : snapshot) {
            if (line.text().isEmpty()) continue;
            if (first == null) first = line;
            last = line;
        }
        if (first == null) return false;
        long end = last.startMs() + Math.max(3000L, last.text().length() * 80L);
        return positionMs >= first.startMs() - 1500 && positionMs <= end;
    }

    private static void fetch(String key, String artist, String title, long durationSec) {
        try {
            String body = get("get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                    + "&duration=" + durationSec);
            String synced = body == null ? null : field(body, "syncedLyrics");
            if (synced == null || synced.isBlank()) {
                // /get 404s for anything not stored under its exact title; /search ranks close matches.
                String found = get("search?q=" + enc(artist + " " + title));
                if (found == null) {
                    // A 503/timeout is not "no lyrics": reopen the slot so the next poll retries.
                    requestedKey = "";
                    return;
                }
                synced = firstSynced(found);
            }
            List<Line> parsed = parseLrc(synced);
            if (parsed.isEmpty() && !strippedTitle(artist, title).isEmpty()) {
                // SoundCloud-style "Artist - Track" titles never match the real track name as-is.
                String bare = strippedTitle(artist, title);
                String retry = get("search?q=" + enc(artist + " " + bare));
                if (retry == null) {
                    requestedKey = "";
                    return;
                }
                parsed = parseLrc(firstSynced(retry));
            }
            if (!parsed.isEmpty()) {
                lines = parsed;
                loadedKey = key;
            } else if (durationSec <= 0) {
                // No length yet (mid-switch): a miss now means nothing, retry freely.
                requestedKey = "";
            } else {
                failedKey = key;
                failedAt = System.currentTimeMillis();
            }
        } catch (Exception e) {
            // A network hiccup is not "this song has no lyrics": reopen the slot so the next poll retries.
            requestedKey = "";
        }
    }

    /** "Screwly G - 3 a.m." with artist "Screwly G" yields "3 a.m."; anything else yields "". */
    private static String strippedTitle(String artist, String title) {
        int dash = title.indexOf(" - ");
        if (dash <= 0) return "";
        String prefix = title.substring(0, dash).trim();
        return prefix.equalsIgnoreCase(artist) ? title.substring(dash + 3).trim() : "";
    }

    private static String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        return response.body();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String field(String json, String name) {
        var element = JsonParser.parseString(json).getAsJsonObject().get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    /** /search returns an array; the first entry that carries synced lyrics wins. */
    private static String firstSynced(String json) {
        var root = JsonParser.parseString(json);
        if (!root.isJsonArray()) return null;
        for (var entry : root.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            JsonObject object = entry.getAsJsonObject();
            var synced = object.get("syncedLyrics");
            if (synced != null && !synced.isJsonNull() && !synced.getAsString().isBlank()) {
                return synced.getAsString();
            }
        }
        return null;
    }

    private static List<Line> parseLrc(String lrc) {
        List<Line> parsed = new ArrayList<>();
        if (lrc == null) return parsed;
        for (String raw : lrc.split("\n")) {
            Matcher matcher = LRC_TIME.matcher(raw);
            if (!matcher.find()) continue;
            long minutes = Long.parseLong(matcher.group(1));
            long seconds = Long.parseLong(matcher.group(2));
            long fraction = matcher.group(3) == null ? 0
                    : Long.parseLong(matcher.group(3)) * 1000L / (long) Math.pow(10, matcher.group(3).length());
            String text = matcher.group(4).trim();
            parsed.add(new Line((minutes * 60 + seconds) * 1000 + fraction, text));
        }
        parsed.sort((a, b) -> Long.compare(a.startMs(), b.startMs()));
        return parsed;
    }
}
