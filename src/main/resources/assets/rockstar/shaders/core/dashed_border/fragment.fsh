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
    float DashLength;
    float GapLength;
    vec4 Dashed;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord; // нормализованная координата фрагмента [0..1] по примитиву (0,0 — ВЛ, 1,1 — ПН)
in vec4 FragColor;







out vec4 OutColor;

// 1 в штрихе, 0 в зазоре (с лёгким AA по обоим краям штриха)
float dashMask(float along) {
    float period = max(RockstarUniforms.DashLength + RockstarUniforms.GapLength, 0.001);
    float m = mod(along, period);
    float rise = smoothstep(-0.5, 0.5, m);                                // нарастание в начале штриха
    float fall = 1.0 - smoothstep(RockstarUniforms.DashLength - 0.5, RockstarUniforms.DashLength + 0.5, m); // спад в конце
    return rise * fall;
}

void main() {
    vec2 p = FragCoord * RockstarUniforms.Size; // px: (0,0) ВЛ → (RockstarUniforms.Size) ПН
    vec2 center = RockstarUniforms.Size * 0.5;

    // кольцо СКРУГЛЁННОЙ рамки (тот же SDF, что у core/border — округлые углы)
    float distance = roundedBoxSDF(center - p, center - 1.0, RockstarUniforms.Radius);
    float inner = sdfAA(distance, RockstarUniforms.Smoothness.x);
    float outer = sdfAA(distance, RockstarUniforms.Smoothness.y);

    float ring = smoothstep(1.0 - RockstarUniforms.Thickness - inner - outer, 1.0 - RockstarUniforms.Thickness - outer, distance); // внутр. край
    ring *= 1.0 - smoothstep(1.0 - outer, 1.0, distance);                                        // внешн. край

    // к какому ребру принадлежит фрагмент + перим. координата s (по часовой от верх-лево, непрерывна на углах
    // одинакового типа: верх→право сшиваются у ВП-угла, т.к. там p.x≈W и p.y≈0 дают s≈W)
    float distL = p.x, distT = p.y, distR = RockstarUniforms.Size.x - p.x, distB = RockstarUniforms.Size.y - p.y;
    float s;
    float dashed;
    if (distT <= distR && distT <= distB && distT <= distL) { s = p.x;                                  dashed = RockstarUniforms.Dashed.y; } // верх
    else if (distR <= distB && distR <= distL)              { s = RockstarUniforms.Size.x + p.y;                         dashed = RockstarUniforms.Dashed.z; } // право
    else if (distB <= distL)                                { s = RockstarUniforms.Size.x + RockstarUniforms.Size.y + (RockstarUniforms.Size.x - p.x);     dashed = RockstarUniforms.Dashed.w; } // низ
    else                                                    { s = 2.0 * RockstarUniforms.Size.x + RockstarUniforms.Size.y + (RockstarUniforms.Size.y - p.y); dashed = RockstarUniforms.Dashed.x; } // лево

    float alpha = ring * mix(1.0, dashMask(s), dashed);

    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * alpha);
    if (finalColor.a <= 0.0) { // alpha test
        discard;
    }
    OutColor = finalColor * ColorModulator;
}
