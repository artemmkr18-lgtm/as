#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    vec2 Size;
    vec4 Radius;
    float Smoothness;
    float FadeStart;
    float FadeEnd;
    vec2 ClampMin;
    vec2 ClampMax;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;






out vec4 OutColor;

void main() {
    // Мягкий (AA) край рамки — как в squircle_texture. RockstarUniforms.Radius=0 → просто прямоугольник.
    vec2 center = RockstarUniforms.Size * 0.5;
    float dist = roundedBoxSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius);
    float edge = 1.0 - smoothstep(1.0 - sdfAA(dist, RockstarUniforms.Smoothness), 1.0, dist);

    // Плавный вертикальный фейд блюра. FragCoord.y: 0 (верх) → 1 (низ).
    float vfade = 1.0 - smoothstep(RockstarUniforms.FadeStart, RockstarUniforms.FadeEnd, FragCoord.y);

    float alpha = edge * vfade;
    if (alpha <= 0.001) discard;

    // Клип семплинга к области окна (с запасом на размах блюра): пиксели у/за краем окна берут
    // ближайший ВНУТРЕННИЙ (без неба) столбец блюра, а не мир за окном.
    vec2 uv = clamp(TexCoord, RockstarUniforms.ClampMin, RockstarUniforms.ClampMax);

    // Цвет = размытый фон (RGB), альфа = маска. FragColor/ColorModulator — тинт/модулятор (обычно белые).
    OutColor = vec4(texture(Sampler0, uv).rgb, alpha) * FragColor * ColorModulator;
}
