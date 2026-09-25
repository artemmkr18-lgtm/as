#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    float Floor;
} RockstarUniforms;
// Лифт отражения для зеркального режима FillESP. Фикс-функция блендинга затем
// умножает этот выход на освещённый предмет (GL_DST_COLOR), сохраняя 3D-тень.
//
// RockstarUniforms.Floor поднимает тёмные участки отражения к 1.0, чтобы предмет не уходил в
// чёрный над тёмными блоками / листвой / землёй. RockstarUniforms.Floor=0 — чистое отражение
// (старое поведение), RockstarUniforms.Floor=1 — отражение игнорируется, остаётся только
// собственный свет предмета.

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;


out vec4 OutColor;

void main() {
    vec4 refl = texture(Sampler0, TexCoord);
    vec3 lifted = mix(refl.rgb, vec3(1.0), clamp(RockstarUniforms.Floor, 0.0, 1.0));
    OutColor = vec4(lifted, refl.a) * ColorModulator;
}
