#version 330

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D Sampler0;

layout(std140) uniform Uniforms {
    mat4 ProjMat;
    vec4 Params;
    vec4 Color;
};

#define RING_WIDTH 0.30

void main() {
    float ringProgress = Params.x;
    float strength = Params.y;
    float fade = Params.z;

    vec2 centered = (vUV - 0.5) * 2.0;
    float dist = length(centered);
    if (dist > 1.0) {
        discard;
    }

    float edge = dist - ringProgress;
    float band = smoothstep(RING_WIDTH, 0.0, abs(edge));
    band *= band;

    float inside = smoothstep(1.0, 0.82, dist);
    float coverage = max(inside, band);
    if (coverage <= 0.001) {
        discard;
    }

    vec2 dir = dist > 0.0001 ? centered / dist : vec2(0.0);

    vec2 screenSize = vec2(textureSize(Sampler0, 0));
    vec2 screenUv = gl_FragCoord.xy / screenSize;

    float ripple = sin(dist * 38.0 - ringProgress * 26.0) * 0.5 + 0.5;
    float crest = band * mix(0.7, 1.3, ripple);

    float innerDistort = inside * mix(0.55, 1.0, dist);
    float offsetAmount = strength * fade * 0.05 * (innerDistort + crest * 1.6);

    vec3 refracted = texture(Sampler0, clamp(screenUv + dir * offsetAmount, vec2(0.0), vec2(1.0))).rgb;

    float crestHighlight = pow(band, 1.5) * fade * 0.5;
    float sheen = inside * fade * 0.06;
    vec3 crestTint = Color.rgb * pow(band, 1.2) * fade * 0.55;

    fragColor = vec4(refracted + vec3(crestHighlight + sheen) + crestTint, coverage * fade * Color.a);
}
