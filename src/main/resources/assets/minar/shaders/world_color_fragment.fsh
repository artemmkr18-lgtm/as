#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform Uniforms {
    vec4 Tint;
    vec4 Params;
    vec4 Snow;
};

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + vec3(33.33));
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec4 scene = texture(Sampler0, uv);
    vec3 c = scene.rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, Params.x);
    float peak = max(max(Tint.r, Tint.g), max(Tint.b, 0.08));
    c *= mix(vec3(1.0), Tint.rgb / peak, Params.z);
    c = (c - 0.5) * Params.w + 0.5;
    c *= Params.y;

    float depth = texture(Sampler1, uv).r;
    float sky = 1.0 - step(0.000001, depth);
    float viewZ = 0.05 / max(depth, 0.000001);
    float noise = hash12(gl_FragCoord.xy * 0.31);
    float winter = Snow.x;

    float coldGrade = winter * (0.08 + noise * 0.025);
    vec3 winterTint = vec3(0.86, 0.94, 1.0);
    c = mix(c, winterTint, coldGrade);

    float hazeDistance = max(Snow.z, 1.0);
    float distanceHaze = (1.0 - sky) * winter * Snow.y
            * clamp(1.0 - exp(-viewZ / hazeDistance), 0.0, 1.0);
    c = mix(c, vec3(0.82, 0.89, 0.96), distanceHaze * 0.72);

    float overcast = sky * winter * (0.24 + noise * 0.10);
    c = mix(c, vec3(0.84, 0.90, 0.96), overcast);

    fragColor = vec4(clamp(c, 0.0, 1.0), scene.a);
}
