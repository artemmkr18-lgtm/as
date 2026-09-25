#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform RockstarData {
    vec2 Anchor;
    vec4 LightColor;
    vec4 DarkColor;
    vec4 Radius;
    float RectSmoothness;
    float Thickness;
} RockstarUniforms;

#moj_import <rockstar:rect.glsl>

in vec3 Position; // POSITION_TEXTURE_COLOR_LIGHT vertex attributes
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;




out vec2 FragCoord;
out vec2 TexCoord;
out vec4 FragColor;
out vec2 AnchorUv;
// Номер глифа, увеличенный на единицу: ноль означает, что примитив — фигура, а не буква.
flat out int GlyphIndex;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    FragCoord = rvertexcoord(gl_VertexID);
    TexCoord = UV0;
    FragColor = Color;
    GlyphIndex = UV2.x + UV2.y * 32768 - 1;

    // Якорь гоним теми же матрицами, что и позицию вершины: gui-масштаб, сдвиг окна и матрицу
    // стека пересчитывать руками не надо, а UV выходит ровно тот, что раньше давал
    // gl_FragCoord.xy / ScreenSize — только для одной точки, а не для каждого пикселя.
    vec4 anchor = ProjMat * ModelViewMat * vec4(RockstarUniforms.Anchor, 0.0, 1.0);
    AnchorUv = anchor.xy / anchor.w * 0.5 + 0.5;
}
