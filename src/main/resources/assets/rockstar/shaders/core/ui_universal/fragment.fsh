#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    vec4 Radius;
    float RectSmoothness;
    vec2 BorderSmoothness;
    float BorderThickness;
    float CornerSmoothness;
    float TextThickness;
    vec2 HeadSize;
    vec4 HeadRadius;
    float HeadSmoothness;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>
#moj_import <rockstar:slug.glsl>

// Универсальный UI-шейдер: в одном дроколле рисует закруглённые прямоугольники, текст и
// головы игроков. Буква узнаётся по номеру глифа в вершине (у остальных примитивов он пуст),
// остальные — по UV:
//   - рамка: UV.x хранит отрицательный размер с маркером (-width - 4)
//   - голова: UV смещён в отрицательную зону ([-2..-1]) -> сэмплит Sampler1 (атлас голов)
//   - прямоугольник: UV = размер примитива в пикселях
// Текст раньше отличали по диапазону UV ([0..1] — координаты в атласе шрифта), но у Slug на
// месте UV лежат em-координаты глифа, а они спокойно выходят и за единицу, и в минус.

in vec2 FragCoord; // нормализованная координата угла (rvertexcoord)
in vec2 TexCoord;
in vec4 FragColor;
flat in int GlyphIndex;

uniform sampler2D Sampler0;  // контрольные точки кривых
uniform sampler2D Sampler1;  // атлас голов
uniform usampler2D Sampler2; // полосы глифов
uniform sampler2D Sampler3;  // таблица глифов










out vec4 OutColor;

float uiBoxSDF(vec2 p, vec2 b, vec4 r, float s) {
    r = min(r, vec4(min(b.x, b.y) * s * 0.5)); // максимум — круг (порог r=b·s/2), дальше не ромб
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 qc = max(q, 0.0);
    float len = pow(pow(qc.x, s) + pow(qc.y, s), 1.0 / s);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    vec4 color;

    if (GlyphIndex >= 0) {
        vec2 pixelsPerEm;
        float coverage = slugWeight(
                slugCoverage(Sampler0, Sampler2, Sampler3, GlyphIndex, TexCoord, 1.0, pixelsPerEm), RockstarUniforms.TextThickness);
        color = vec4(FragColor.rgb, FragColor.a * coverage);
    } else if (TexCoord.x < -3.0) {
        // рамка: x хранит отрицательный размер с маркером (-width - 4), y хранит height
        vec2 size = vec2(-TexCoord.x - 4.0, TexCoord.y);
        vec2 center = size * 0.5;
        float dist = uiBoxSDF(center - (FragCoord * size), center - 1.0, RockstarUniforms.Radius, RockstarUniforms.CornerSmoothness);
        float inner = sdfAA(dist, RockstarUniforms.BorderSmoothness.x);
        float outer = sdfAA(dist, RockstarUniforms.BorderSmoothness.y);
        float alpha = smoothstep(1.0 - RockstarUniforms.BorderThickness - inner - outer,
            1.0 - RockstarUniforms.BorderThickness - outer, dist);
        alpha *= 1.0 - smoothstep(1.0 - outer, 1.0, dist);
        color = vec4(FragColor.rgb, FragColor.a * alpha);
    } else if (TexCoord.x < -0.5) {
        // голова: реальные координаты атласа = TexCoord + 2, форма — закруглённый квадрат
        vec2 center = RockstarUniforms.HeadSize * 0.5;
        float dist = roundedBoxSDF(center - (FragCoord * RockstarUniforms.HeadSize), center - 1.0, RockstarUniforms.HeadRadius);
        float alpha = 1.0 - smoothstep(1.0 - sdfAA(dist, RockstarUniforms.HeadSmoothness), 1.0, dist);
        vec4 tex = texture(Sampler1, TexCoord + vec2(2.0));
        color = vec4(tex.rgb, tex.a * alpha) * FragColor;
    } else {
        // закруглённый прямоугольник, размер в UV
        vec2 size = TexCoord;
        vec2 center = size * 0.5;
        float dist = uiBoxSDF(center - (FragCoord * size), center - 1.0, RockstarUniforms.Radius, RockstarUniforms.CornerSmoothness);
        float alpha = 1.0 - smoothstep(1.0 - sdfAA(dist, RockstarUniforms.RectSmoothness), 1.0, dist);
        color = vec4(FragColor.rgb, FragColor.a * alpha);
    }

    if (color.a == 0.0) {
        discard;
    }

    OutColor = color * ColorModulator;
}
