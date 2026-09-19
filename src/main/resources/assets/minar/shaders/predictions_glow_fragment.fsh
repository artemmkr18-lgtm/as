#version 330

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

void main() {
    vec2 uv = texCoord0;
    vec4 tint = vertexColor;
    float time = GameTime * 2400.0;
    float sideDistance = abs(uv.y - 0.5) * 2.0;
    float core = 1.0 - smoothstep(0.0, 0.18, sideDistance);
    float inner = 1.0 - smoothstep(0.16, 0.48, sideDistance);
    float outer = 1.0 - smoothstep(0.30, 1.0, sideDistance);
    float flow = sin(uv.x * 7.0 - time * 3.2) * 0.5 + 0.5;
    float sparks = pow(noise(vec2(uv.x * 3.5 - time * 0.8, uv.y * 6.0 + time * 0.15)), 3.0);
    float energy = core * 1.25 + inner * 0.72 + outer * (0.34 + flow * 0.16) + sparks * outer * 0.22;
    vec3 hot = mix(tint.rgb, vec3(1.0), clamp(core * 0.78 + sparks * 0.35, 0.0, 1.0));
    vec3 color = mix(tint.rgb * 0.8, hot, clamp(inner + core, 0.0, 1.0));
    float alpha = tint.a * energy;

    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
