#version 410 core
precision highp float;

in vec2 v_TexCoord;
in vec2 v_RectSize;

layout(std140) uniform Uniforms {
    mat4 ProjMat;
    vec4 RectPosSize;
    vec4 Params;
    vec4 Color;
    vec4 Misc;
};

out vec4 fragColor;

float sdRoundedRect(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + vec2(r);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

void main() {
    float radius = Params.x;
    float thickness = max(Params.y, 0.001);
    float glowIntensity = max(Params.z, 0.0);
    float baseAlpha = Params.w;

    float glowSize = Misc.y > 0.0 ? Misc.y : max(thickness * 3.0, 8.0);
    float glowSoftness = Misc.z > 0.0 ? Misc.z : 4.0;

    vec2 p = (v_TexCoord - 0.5) * (v_RectSize + vec2(glowSize * 2.0));
    vec2 halfRectSize = v_RectSize * 0.5;

    float dist = sdRoundedRect(p, halfRectSize, radius);
    float aa = max(fwidth(dist), 1e-4);
    if (dist > glowSize + aa) discard;

    float halfT = thickness * 0.5;
    float d2 = abs(dist + halfT) - halfT;
    float outline = 1.0 - smoothstep(-aa, aa, d2);

    float glow = 0.0;
    if (glowIntensity > 0.0 && dist > -aa) {
        float outsideMask = smoothstep(-aa, aa, dist);
        float normDist = clamp(dist / glowSize, 0.0, 1.0);
        float glowFade = exp(-normDist * normDist * glowSoftness);
        glow = outsideMask * glowFade * glowIntensity;
    }

    float combined = clamp(outline + glow, 0.0, 1.0);
    float finalAlpha = combined * Color.a * baseAlpha;

    if (finalAlpha < (1.0 / 255.0)) discard;

    fragColor = vec4(Color.rgb, finalAlpha);
}
