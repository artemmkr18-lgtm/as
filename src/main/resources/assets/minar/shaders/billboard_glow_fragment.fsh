#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord0;
    vec2 centered = uv - 0.5;
    vec2 coreUv = centered * 1.45 + 0.5;

    float coreMask = step(0.0, coreUv.x) * step(coreUv.x, 1.0) *
                     step(0.0, coreUv.y) * step(coreUv.y, 1.0);
    vec4 texColor = texture(Sampler0, clamp(coreUv, vec2(0.0), vec2(1.0)));
    float coreAlpha = texColor.a * coreMask;

    float dist = length(centered) * 2.0;
    float halo = 1.0 - smoothstep(0.28, 1.0, dist);
    halo = pow(clamp(halo, 0.0, 1.0), 2.2) * 0.24;

    float finalAlpha = vertexColor.a * clamp(coreAlpha + halo * (1.0 - coreAlpha), 0.0, 1.0);

    if (finalAlpha <= 0.002) {
        discard;
    }

    vec3 coreColor = mix(vertexColor.rgb * texColor.rgb, vec3(1.0), clamp(coreAlpha * 0.58, 0.0, 1.0));
    vec3 glowColor = vertexColor.rgb * (0.78 + halo * 0.55);
    vec3 color = mix(glowColor, coreColor, clamp(coreAlpha, 0.0, 1.0));

    fragColor = vec4(color, finalAlpha);
}
