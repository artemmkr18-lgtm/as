package ez.minar.utils.font;

import ez.minar.utils.render.msdf.Msdf;
import ez.minar.utils.render.msdf.MsdfFont;
import ez.minar.utils.render.msdf.MsdfManager;

/**
 * Registry of font families matching Rockstar's {@code Fonts} class:
 * BOLD, MEDIUM, REGULAR, SEMIBOLD, LIGHT, ROUND.
 * Backed by Minar's MSDF font engine.
 */
public final class Fonts {

    private static FontFamily bold;
    private static FontFamily medium;
    private static FontFamily regular;
    private static FontFamily semiBold;
    private static FontFamily light;
    private static FontFamily round;

    private Fonts() {}

    private static void ensureInit() {
        if (!Msdf.hasFonts()) {
            MsdfManager.init();
        }
    }

    public static FontFamily bold() {
        ensureInit();
        if (bold == null) bold = new FontFamily(Msdf.SF_BOLD != null ? Msdf.SF_BOLD : Msdf.BOLD);
        return bold;
    }

    public static FontFamily medium() {
        ensureInit();
        if (medium == null) medium = new FontFamily(Msdf.SF_MEDIUM != null ? Msdf.SF_MEDIUM : Msdf.SF_REGULAR);
        return medium;
    }

    public static FontFamily regular() {
        ensureInit();
        if (regular == null) regular = new FontFamily(Msdf.SF_REGULAR != null ? Msdf.SF_REGULAR : MsdfManager.getDefault());
        return regular;
    }

    public static FontFamily semiBold() {
        ensureInit();
        if (semiBold == null) semiBold = new FontFamily(Msdf.SF_SEMIBOLD != null ? Msdf.SF_SEMIBOLD : Msdf.SF_BOLD);
        return semiBold;
    }

    public static FontFamily light() {
        ensureInit();
        if (light == null) light = new FontFamily(Msdf.SF_LIGHT != null ? Msdf.SF_LIGHT : Msdf.SF_REGULAR);
        return light;
    }

    public static FontFamily round() {
        ensureInit();
        if (round == null) round = new FontFamily(Msdf.SF_ROUND != null ? Msdf.SF_ROUND : Msdf.SF_REGULAR);
        return round;
    }

    public static FontFamily byName(String name) {
        if (name == null) return regular();
        return switch (name.toLowerCase()) {
            case "bold" -> bold();
            case "medium" -> medium();
            case "semibold", "semi_bold" -> semiBold();
            case "light" -> light();
            case "round", "roundbold" -> round();
            default -> regular();
        };
    }
}
