package ez.minar.utils.render.msdf;

public class MsdfGlyph {

    public final int codepoint;
    public final float advance;
    public final float x, y, width, height;
    public final float u0, v0, u1, v1;
    public final float bearingX, bearingY;

    public MsdfGlyph(int codepoint, float advance, float x, float y, float width, float height,
            float u0, float v0, float u1, float v1, float bearingX, float bearingY) {
        this.codepoint = codepoint;
        this.advance = advance;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
    }
}
