#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    vec2 Size;
    vec2 P0;
    vec2 P1;
    vec2 P2;
    vec2 P3;
    float Thickness;
} RockstarUniforms;

in vec2 FragCoord; // нормализованная координата фрагмента [0..1] по примитиву
in vec4 FragColor;




out vec4 OutColor;

vec2 cubic(float t) {
    float u = 1.0 - t;
    return u * u * u * RockstarUniforms.P0 + 3.0 * u * u * t * RockstarUniforms.P1 + 3.0 * u * t * t * RockstarUniforms.P2 + t * t * t * RockstarUniforms.P3;
}

// расстояние от точки p до отрезка a-b
float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = FragCoord * RockstarUniforms.Size; // px относительно квада — то же пространство, что RockstarUniforms.P0..RockstarUniforms.P3

    // мин. расстояние до кривой: семплим в полилинию и берём ближайший отрезок (distance field → плавный AA)
    const int N = 48;
    float best = 1e9;
    vec2 prev = RockstarUniforms.P0;
    for (int i = 1; i <= N; i++) {
        vec2 cur = cubic(float(i) / float(N));
        best = min(best, segDist(p, prev, cur));
        prev = cur;
    }

    float halfWidth = RockstarUniforms.Thickness * 0.5; // не half: зарезервированное слово GLSL, часть драйверов не компилирует
    float alpha = 1.0 - smoothstep(halfWidth - 0.6, halfWidth + 0.6, best); // 0.6px перо сглаживания

    vec4 col = vec4(FragColor.rgb, FragColor.a * alpha);
    if (col.a <= 0.0) { // alpha test
        discard;
    }
    OutColor = col * ColorModulator;
}
