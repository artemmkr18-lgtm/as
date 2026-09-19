package ez.minar.utils.render.msdf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MsdfFont {

    private final String name;
    private final Map<Integer, MsdfGlyph> glyphs = new HashMap<>();
    private GpuTexture texture;
    private GpuTextureView textureView;
    private float lineHeight;
    private float ascender;
    private float descender;
    private float pxRange;
    private int atlasWidth;
    private int atlasHeight;
    private float emSize;
    private boolean loaded = false;

    public MsdfFont(String name) {
        this.name = name;
    }

    public void load(Identifier atlasId, Identifier jsonId) {
        if (loaded)
            return;
        try {
            loadAtlas(atlasId);
            loadMetrics(jsonId);
            loaded = true;
        } catch (Exception e) {
            System.err.println("[MSDF] Failed to load font '" + name + "':");
            e.printStackTrace();
        }
    }

    private void loadAtlas(Identifier id) throws Exception {
        Identifier resourceId = Identifier.of(id.getNamespace(), "font/" + id.getPath());
        try (InputStream is = MinecraftClient.getInstance().getResourceManager().getResource(resourceId).get()
                .getInputStream()) {
            NativeImage image = NativeImage.read(is);
            atlasWidth = image.getWidth();
            atlasHeight = image.getHeight();

            texture = RenderSystem.getDevice().createTexture(
                    () -> "msdf:" + name,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    TextureFormat.RGBA8,
                    atlasWidth, atlasHeight, 1, 1);

            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
            textureView = RenderSystem.getDevice().createTextureView(texture);
            image.close();
        }
    }

    private void loadMetrics(Identifier id) throws Exception {
        Identifier resourceId = Identifier.of(id.getNamespace(), "font/" + id.getPath());
        try (InputStream is = MinecraftClient.getInstance().getResourceManager().getResource(resourceId).get()
                .getInputStream()) {
            String json = new String(is.readAllBytes());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonObject atlas = root.getAsJsonObject("atlas");
            pxRange = atlas.get("distanceRange").getAsFloat();
            boolean yOriginBottom = atlas.has("yOrigin") && "bottom".equals(atlas.get("yOrigin").getAsString());

            JsonObject metrics = root.getAsJsonObject("metrics");
            emSize = metrics.get("emSize").getAsFloat();
            lineHeight = metrics.get("lineHeight").getAsFloat();
            ascender = metrics.get("ascender").getAsFloat();
            descender = metrics.get("descender").getAsFloat();

            for (JsonElement el : root.getAsJsonArray("glyphs")) {
                JsonObject g = el.getAsJsonObject();
                int unicode = g.get("unicode").getAsInt();
                float advance = g.has("advance") ? g.get("advance").getAsFloat() : 0;

                float pw = 0, ph = 0;
                float u0 = 0, v0 = 0, u1 = 0, v1 = 0;
                float bx = 0, by = 0;

                if (g.has("atlasBounds")) {
                    JsonObject ab = g.getAsJsonObject("atlasBounds");
                    float left = ab.get("left").getAsFloat();
                    float bottom = ab.get("bottom").getAsFloat();
                    float right = ab.get("right").getAsFloat();
                    float top = ab.get("top").getAsFloat();
                    u0 = left / atlasWidth;
                    u1 = right / atlasWidth;
                    if (yOriginBottom) {
                        v0 = 1.0f - top / atlasHeight;
                        v1 = 1.0f - bottom / atlasHeight;
                    } else {
                        v0 = top / atlasHeight;
                        v1 = bottom / atlasHeight;
                    }
                }

                if (g.has("planeBounds")) {
                    JsonObject pb = g.getAsJsonObject("planeBounds");
                    float left = pb.get("left").getAsFloat();
                    float bottom = pb.get("bottom").getAsFloat();
                    float right = pb.get("right").getAsFloat();
                    float top = pb.get("top").getAsFloat();
                    bx = left;
                    by = top;
                    pw = right - left;
                    ph = top - bottom;
                }

                glyphs.put(unicode, new MsdfGlyph(unicode, advance, 0, 0, pw, ph, u0, v0, u1, v1, bx, by));
            }
        }
    }

    public static int getFallbackCodepoint(int codepoint) {
        return switch (codepoint) {
            case 'ą', 'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a';
            case 'Ą', 'À', 'Á', 'Â', 'Ã', 'Ä', 'Å' -> 'A';
            case 'ć', 'ç', 'č' -> 'c';
            case 'Ć', 'Ç', 'Č' -> 'C';
            case 'ę', 'è', 'é', 'ê', 'ë' -> 'e';
            case 'Ę', 'È', 'É', 'Ê', 'Ë' -> 'E';
            case 'ł' -> 'l';
            case 'Ł' -> 'L';
            case 'ń', 'ñ' -> 'n';
            case 'Ń', 'Ñ' -> 'N';
            case 'ó', 'ò', 'ô', 'õ', 'ö' -> 'o';
            case 'Ó', 'Ò', 'Ô', 'Õ', 'Ö' -> 'O';
            case 'ś', 'š' -> 's';
            case 'Ś', 'Š' -> 'S';
            case 'ź', 'ż', 'ž' -> 'z';
            case 'Ź', 'Ż', 'Ž' -> 'Z';
            case 'ù', 'ú', 'û', 'ü' -> 'u';
            case 'Ù', 'Ú', 'Û', 'Ü' -> 'U';
            case 'ý', 'ÿ' -> 'y';
            case 'Ý' -> 'Y';
            case 'ř' -> 'r';
            case 'Ř' -> 'R';
            case 'ď' -> 'd';
            case 'Ď' -> 'D';
            case 'ť' -> 't';
            case 'Ť' -> 'T';
            default -> codepoint;
        };
    }

    public MsdfGlyph getGlyph(int codepoint) {
        MsdfGlyph glyph = glyphs.get(codepoint);
        if (glyph != null) {
            return glyph;
        }
        int fallback = getFallbackCodepoint(codepoint);
        if (fallback != codepoint) {
            return glyphs.get(fallback);
        }
        return null;
    }

    public GpuTextureView getTextureView() {
        return textureView;
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public float getAscender() {
        return ascender;
    }

    public float getDescender() {
        return descender;
    }

    public float getPxRange() {
        return pxRange;
    }

    public float getEmSize() {
        return emSize;
    }

    public String getName() {
        return name;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public float getStringWidth(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;
        float scale = size / emSize;
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            MsdfGlyph glyph = getGlyph((int) text.charAt(i));
            if (glyph == null) glyph = getGlyph((int) '?');
            if (glyph != null)
                width += glyph.advance * scale;
        }
        return width;
    }

    public void shutdown() {
        if (textureView != null) {
            textureView.close();
            textureView = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        glyphs.clear();
        loaded = false;
    }
}
