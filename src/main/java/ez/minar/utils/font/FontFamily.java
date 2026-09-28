package ez.minar.utils.font;

import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.msdf.MsdfManager;

import java.util.HashMap;
import java.util.Map;

/**
 * One font face, from which fixed-size {@link SizedFont} handles are carved.
 *
 * <p>Mirrors Rockstar's {@code FontFamily}, but is backed by Minar's pre-baked MSDF atlases
 * instead of rasterising the {@code .otf} files at runtime: the six faces the source client
 * ships are the same typefaces Minar already has atlases for, so re-rasterising them would
 * only trade a crisp MSDF distance field for a blurry bitmap.
 */
public final class FontFamily {

    private final MsdfFont font;
    private final Map<Float, SizedFont> cache = new HashMap<>();

    FontFamily(MsdfFont font) {
        this.font = font;
    }

    /** Returns the handle for {@code size}, creating and caching it on first use. */
    public SizedFont of(float size) {
        return cache.computeIfAbsent(size, s -> new SizedFont(font, s));
    }

    public MsdfFont msdf() {
        return font;
    }

    public float width(String text, float size) {
        return Msdf.width(font, text, size);
    }

    public float height(float size) {
        return Msdf.height(font, size);
    }

    /**
     * The MSDF atlases are loaded on demand, so a family touched before the client finished
     * booting still resolves instead of silently rendering nothing.
     */
    static MsdfFont require(MsdfFont font) {
        if (font == null || !font.isLoaded()) {
            if (!Msdf.hasFonts()) MsdfManager.init();
        }
        return font;
    }
}
