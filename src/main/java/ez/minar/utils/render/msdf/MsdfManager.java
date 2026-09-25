package ez.minar.utils.render.msdf;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class MsdfManager {

    private static final Map<String, MsdfFont> fonts = new HashMap<>();
    private static MsdfFont defaultFont;
    private static boolean initialized = false;

    public static MsdfFont SF_REGULAR;
    public static MsdfFont SF_BOLD;
    public static MsdfFont MONTSERRAT;
    public static MsdfFont BOLD;
    public static MsdfFont WTMICO;
    public static MsdfFont ICONS;
    public static MsdfFont WILD;
    public static MsdfFont SUISSE;
    public static MsdfFont WILDICONS;
    public static MsdfFont SF_MEDIUM;
    public static MsdfFont SF_SEMIBOLD;
    public static MsdfFont SF_LIGHT;
    public static MsdfFont SF_ROUND;

    public static void init() {
        if (initialized)
            return;

        Msdf.SF_REGULAR = SF_REGULAR = register("sf_regular", "sf_regular.png", "sf_regular.json");
        Msdf.SF_BOLD = SF_BOLD = register("sf_bold", "sf_bold.png", "sf_bold.json");
        Msdf.MONTSERRAT = MONTSERRAT = register("montserrat", "montserrat.png", "montserrat.json");
        Msdf.BOLD = BOLD = register("bold", "bold.png", "bold.json");
        Msdf.WTMICO = WTMICO = register("wtmico", "wtmico.png", "wtmico.json");
        Msdf.ICONS = ICONS = register("icons", "icons.png", "icons.json");
        Msdf.WILD = WILD = register("wild", "wild.png", "wild.json");
        Msdf.SUISSE = SUISSE = register("suisse", "suisseintlmedium.png", "suisseintlmedium.json");
        Msdf.WILDICONS = WILDICONS = register("wildicons", "wildicons.png", "wildicons.json");
        Msdf.SF_MEDIUM = SF_MEDIUM = register("sf_medium", "sf_medium.png", "sf_medium.json");
        Msdf.SF_SEMIBOLD = SF_SEMIBOLD = register("sf_semibold", "sf_semibold.png", "sf_semibold.json");
        Msdf.SF_LIGHT = SF_LIGHT = register("sf_light", "sf_light.png", "sf_light.json");
        Msdf.SF_ROUND = SF_ROUND = register("sf_round", "sf_round.png", "sf_round.json");
        setDefault("montserrat");

        initialized = true;
    }

    public static MsdfFont register(String name, String atlasPath, String jsonPath) {
        MsdfFont font = new MsdfFont(name);
        font.load(Identifier.of("minar", atlasPath), Identifier.of("minar", jsonPath));
        fonts.put(name, font);
        if (defaultFont == null)
            defaultFont = font;
        return font;
    }

    public static MsdfFont get(String name) {
        return fonts.get(name);
    }

    public static MsdfFont getDefault() {
        return defaultFont;
    }

    public static void setDefault(String name) {
        MsdfFont font = fonts.get(name);
        if (font != null)
            defaultFont = font;
    }

    public static void shutdown() {
        fonts.values().forEach(MsdfFont::shutdown);
        fonts.clear();
        defaultFont = null;
        initialized = false;
    }
}
