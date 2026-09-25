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
    vec4 TopLeftColor;
    vec4 BottomLeftColor;
    vec4 TopRightColor;
    vec4 BottomRightColor;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec4 FragColor;




// Четыре цвета для углов градиента



out vec4 OutColor;

vec4 bilinearInterpolation(vec2 uv) {
    // Интерполяция по горизонтали для верхней строки
    vec4 topColor = mix(RockstarUniforms.TopLeftColor, RockstarUniforms.TopRightColor, uv.x);

    // Интерполяция по горизонтали для нижней строки
    vec4 bottomColor = mix(RockstarUniforms.BottomLeftColor, RockstarUniforms.BottomRightColor, uv.x);

    // Интерполяция по вертикали между верхней и нижней строками
    return mix(topColor, bottomColor, uv.y);
}

void main() {
    vec2 center = RockstarUniforms.Size * 0.5;
    vec2 uv = FragCoord; // UV координаты от 0 до 1

    // Вычисляем цвет градиента для текущего фрагмента
    vec4 gradientColor = bilinearInterpolation(uv);

    float distance = roundedBoxSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius);
    float alpha = 1.0 - smoothstep(1.0 - sdfAA(distance, RockstarUniforms.Smoothness), 1.0, distance);

    vec4 finalColor = vec4(gradientColor.rgb, gradientColor.a * alpha);

    if (finalColor.a == 0.0) { // alpha test
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
