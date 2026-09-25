#version 330

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D Sampler0;

layout(std140) uniform Uniforms {
    mat4 ProjMat;
    vec4 Params;
    vec4 Color;
};

const float TAU = 6.28318530718;

vec3 hsv2rgb(vec3 c) {
    vec4 k = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + k.xyz) * 6.0 - k.www);
    return c.z * mix(k.xxx, clamp(p - k.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float vaporRing(float angle, float scale, float v, float time) {
    vec2 ring = vec2(cos(angle), sin(angle)) * scale;
    float n = noise(ring + vec2(0.0, -time * 2.0 + v * 4.0));
    n += 0.5 * noise(ring * 2.1 + vec2(0.0, -time * 3.4 + v * 6.0));
    n += 0.25 * noise(ring * 4.3 + vec2(0.0, -time * 5.0 + v * 8.0));
    return n / 1.75;
}

void main() {
    float fade = Params.x;
    float time = Params.y;
    float rgbMode = Params.z;
    float ringProgress = Params.w;

    float u = vUV.x;
    float v = vUV.y;
    float angle = u * TAU;

    float tailLength = mix(0.35, 1.0, ringProgress);
    float bottomFade = smoothstep(0.0, 0.18, v);
    float trail = 1.0 - smoothstep(0.0, tailLength, v);
    float vertical = bottomFade * trail;

    float f1 = vaporRing(angle, 3.0, v, time);
    float f2 = vaporRing(angle + 1.7, 5.0, v, time);
    float steam = smoothstep(0.2, 0.92, mix(f1, f2, 0.5));

    float streak = mix(0.65, steam, smoothstep(0.0, 0.7, v));
    float intensity = vertical * streak * fade;
    if (intensity <= 0.003) {
        discard;
    }

    vec2 screenSize = vec2(textureSize(Sampler0, 0));
    vec2 screenUv = gl_FragCoord.xy / screenSize;

    vec2 warp = vec2((f2 - 0.5) * 1.4, -abs(f1 - 0.5) * 2.4 - steam * 0.8);
    vec2 offset = warp * vertical * fade * 0.10;
    vec3 refracted = texture(Sampler0, clamp(screenUv + offset, vec2(0.0), vec2(1.0))).rgb;

    vec3 baseColor;
    if (rgbMode > 0.5) {
        baseColor = hsv2rgb(vec3(fract(u + time * 0.15), 1.0, 1.0));
    } else {
        float lum = dot(Color.rgb, vec3(0.299, 0.587, 0.114));
        baseColor = clamp(mix(vec3(lum), Color.rgb, 2.0), 0.0, 1.0);
    }

    float core = mix(1.2, 3.2, smoothstep(0.0, 0.35, v) * (1.0 - v));
    vec3 color = refracted + baseColor * (intensity * core) * 5.0;

    fragColor = vec4(color, clamp(intensity * (0.8 + 0.4 * steam) * Color.a, 0.0, 1.0));
}
