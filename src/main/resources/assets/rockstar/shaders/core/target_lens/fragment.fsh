#version 150

layout(std140) uniform RockstarData {
    float Count;
    float Aspect;
    float Strength;
    vec4 Lens0;
    vec4 Lens1;
    vec4 Lens2;
    vec4 Lens3;
    vec4 Lens4;
    vec4 Lens5;
    vec4 Lens6;
    vec4 Lens7;
    vec4 Lens8;
    vec4 Lens9;
    vec4 Lens10;
    vec4 Lens11;
    vec4 Lens12;
    vec4 Lens13;
    vec4 Lens14;
    vec4 Lens15;
    vec4 Lens16;
    vec4 Lens17;
} RockstarUniforms;

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0; // цвет (снимок кадра)
uniform sampler2D Sampler1; // глубина сцены



// xy = центр в uv, z = глубина точки (0..1), w = радиус линзы в uv по вертикали













out vec4 OutColor;

const int MAX_LENSES = 18;
const float PULL = 0.85;   // базовая сила «стеклянной» линзы
const float CHROMA = 0.35; // разнос каналов (хроматическая аберрация)

void main() {
    vec4 ls[MAX_LENSES] = vec4[MAX_LENSES](
        RockstarUniforms.Lens0, RockstarUniforms.Lens1, RockstarUniforms.Lens2, RockstarUniforms.Lens3, RockstarUniforms.Lens4, RockstarUniforms.Lens5,
        RockstarUniforms.Lens6, RockstarUniforms.Lens7, RockstarUniforms.Lens8, RockstarUniforms.Lens9, RockstarUniforms.Lens10, RockstarUniforms.Lens11,
        RockstarUniforms.Lens12, RockstarUniforms.Lens13, RockstarUniforms.Lens14, RockstarUniforms.Lens15, RockstarUniforms.Lens16, RockstarUniforms.Lens17
    );

    vec2 uv = TexCoord;
    float sceneDepth = texture(Sampler1, uv).r;

    int count = int(RockstarUniforms.Count + 0.5);
    vec2 offset = vec2(0.0);

    for (int i = 0; i < MAX_LENSES; i++) {
        if (i >= count) break;
        vec4 c = ls[i];
        if (c.w <= 0.0) continue;

        // расстояние в aspect-корректном пространстве — линза круглая, не эллипс
        vec2 d = (uv - c.xy) * vec2(RockstarUniforms.Aspect, 1.0);
        float len = length(d);
        float t = len / c.w;
        if (t >= 1.0 || len < 1e-5) continue;

        // геометрия перед точкой линзой не искажается
        if (sceneDepth < c.z - 0.0005) continue;

        // профиль: ноль в центре и на краю, пик у трети радиуса — край «стеклянного шарика»
        float p = t * (1.0 - t) * (1.0 - t) * 6.75;

        vec2 dir = d / len;
        offset -= (dir / vec2(RockstarUniforms.Aspect, 1.0)) * (p * c.w * PULL * RockstarUniforms.Strength);
    }

    if (dot(offset, offset) < 1e-12) {
        OutColor = vec4(texture(Sampler0, uv).rgb, 1.0);
        return;
    }

    // лёгкий разнос каналов вдоль смещения — стеклянная дисперсия
    vec2 uvR = clamp(uv + offset * (1.0 - CHROMA), vec2(0.0), vec2(1.0));
    vec2 uvG = clamp(uv + offset, vec2(0.0), vec2(1.0));
    vec2 uvB = clamp(uv + offset * (1.0 + CHROMA), vec2(0.0), vec2(1.0));

    OutColor = vec4(texture(Sampler0, uvR).r, texture(Sampler0, uvG).g, texture(Sampler0, uvB).b, 1.0);
}
