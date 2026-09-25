#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    float Weight;
    float Softness;
    int EnableFadeout;
    float FadeoutStart;
    float FadeoutEnd;
    float FadeinStart;
    float FadeinEnd;
    float MaxWidth;
    float TextPosX;
} RockstarUniforms;

#moj_import <rockstar:slug.glsl>

in vec2 TexCoord;
in vec4 FragColor;
in vec2 GlobalPos;
flat in int GlyphIndex;

uniform sampler2D Sampler0;   // контрольные точки кривых
uniform usampler2D Sampler1;  // полосы: заголовки и адреса кривых
uniform sampler2D Sampler2;   // таблица глифов









out vec4 OutColor;

void main() {
    if (GlyphIndex < 0) discard;

    vec2 pixelsPerEm;
    float coverage = slugWeight(
            slugCoverage(Sampler0, Sampler1, Sampler2, GlyphIndex, TexCoord, RockstarUniforms.Softness, pixelsPerEm), RockstarUniforms.Weight);

    vec4 color = vec4(FragColor.rgb, FragColor.a * coverage);

    if (RockstarUniforms.EnableFadeout != 0) {
        float fade = 1.0;
        float normalizedX = (GlobalPos.x - RockstarUniforms.TextPosX) / RockstarUniforms.MaxWidth;
        if (normalizedX > RockstarUniforms.FadeoutStart) fade *= 1.0 - smoothstep(RockstarUniforms.FadeoutStart, RockstarUniforms.FadeoutEnd, normalizedX);
        if (RockstarUniforms.FadeinEnd > 0.0 && normalizedX < RockstarUniforms.FadeinEnd) fade *= smoothstep(RockstarUniforms.FadeinStart, RockstarUniforms.FadeinEnd, normalizedX);
        color.a *= fade;
    }

    OutColor = color * ColorModulator;
}
