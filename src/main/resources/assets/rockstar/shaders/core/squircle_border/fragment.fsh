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
    vec2 Smoothness;
    float Thickness;
    float CornerSmoothness;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec4 FragColor;





out vec4 OutColor;

// SDF суперэллипса (squircle): тот же, что в squircle/fragment, но используется для рамки
float squircleSDF(vec2 p, vec2 b, vec4 r, float s) {
    r = min(r, vec4(min(b.x, b.y) * s * 0.5)); // максимум — круг (порог r=b·s/2), дальше не ромб
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 qc = max(q, 0.0);
    float len = pow(pow(qc.x, s) + pow(qc.y, s), 1.0 / s);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    vec2 center = RockstarUniforms.Size * 0.5;
    float distance = squircleSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius, RockstarUniforms.CornerSmoothness);

    float inner = sdfAA(distance, RockstarUniforms.Smoothness.x);
    float outer = sdfAA(distance, RockstarUniforms.Smoothness.y);

    float alpha = smoothstep(1.0 - RockstarUniforms.Thickness - inner - outer,
        1.0 - RockstarUniforms.Thickness - outer, distance); // внутренняя кромка
    alpha *= 1.0 - smoothstep(1.0 - outer, 1.0, distance); // внешняя кромка

    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * alpha);

    if (finalColor.a == 0.0) { // alpha test
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
