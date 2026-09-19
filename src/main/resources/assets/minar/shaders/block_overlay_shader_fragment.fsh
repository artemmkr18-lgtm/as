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
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
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

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += noise(p) * a;
        p = p * 2.02 + vec2(8.4, 5.7);
        a *= 0.5;
    }
    return v;
}

float ridged(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 4; i++) {
        float r = 1.0 - abs(noise(p) * 2.0 - 1.0);
        v += r * a;
        p = p * 2.18 + vec2(3.1, 9.2);
        a *= 0.52;
    }
    return v;
}

float edgeGlow(vec2 uv) {
    vec2 edge = min(uv, 1.0 - uv);
    float nearest = min(edge.x, edge.y);
    return 1.0 - smoothstep(0.0, 0.18, nearest);
}

float bwColormapRed(float x) {
    if (x < 0.0) return 54.0 / 255.0;
    if (x < 20049.0 / 82979.0) return (829.79 * x + 54.51) / 255.0;
    return 1.0;
}

float bwColormapGreen(float x) {
    if (x < 20049.0 / 82979.0) return 0.0;
    if (x < 327013.0 / 810990.0) return (8546482679670.0 / 10875673217.0 * x - 2064961390770.0 / 10875673217.0) / 255.0;
    if (x <= 1.0) return (103806720.0 / 483977.0 * x + 19607415.0 / 483977.0) / 255.0;
    return 1.0;
}

float bwColormapBlue(float x) {
    if (x < 0.0) return 54.0 / 255.0;
    if (x < 7249.0 / 82979.0) return (829.79 * x + 54.51) / 255.0;
    if (x < 20049.0 / 82979.0) return 127.0 / 255.0;
    if (x < 327013.0 / 810990.0) return (792.0224934136139 * x - 64.36479073560233) / 255.0;
    return 1.0;
}

vec3 bwColormap(float x) {
    return vec3(bwColormapRed(x), bwColormapGreen(x), bwColormapBlue(x));
}

float bwRand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float bwNoise(vec2 p) {
    vec2 ip = floor(p);
    vec2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float res = mix(
        mix(bwRand(ip), bwRand(ip + vec2(1.0, 0.0)), u.x),
        mix(bwRand(ip + vec2(0.0, 1.0)), bwRand(ip + vec2(1.0, 1.0)), u.x),
        u.y
    );
    return res * res;
}

const mat2 BW_MTX = mat2(0.80, 0.60, -0.60, 0.80);

float bwFbm(vec2 p, float t) {
    float f = 0.0;
    f += 0.500000 * bwNoise(p + vec2(t)); p = BW_MTX * p * 2.02;
    f += 0.031250 * bwNoise(p); p = BW_MTX * p * 2.01;
    f += 0.250000 * bwNoise(p); p = BW_MTX * p * 2.03;
    f += 0.125000 * bwNoise(p); p = BW_MTX * p * 2.01;
    f += 0.062500 * bwNoise(p); p = BW_MTX * p * 2.04;
    f += 0.015625 * bwNoise(p + vec2(sin(t)));
    return f / 0.96875;
}

float bwPattern(vec2 p, float t) {
    return bwFbm(p + vec2(bwFbm(p + vec2(bwFbm(p, t)), t)), t);
}

float polygonDistance(vec2 p, float radius, float angleOffset, int sideCount) {
    float a = atan(p.x, p.y) + angleOffset;
    float b = 6.2831853 / float(sideCount);
    return cos(floor(0.5 + a / b) * b - a) * length(p) - radius;
}

#define HASHSCALE1 443.8975
float hash11(float p) {
    vec3 p3  = fract(vec3(p) * HASHSCALE1);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}

#define HASHSCALE3 vec3(0.1031, 0.1030, 0.0973)
vec2 hash21(float p) {
    vec3 p3 = fract(vec3(p) * HASHSCALE3);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract(vec2((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y));
}

void main() {
    int mode = int(floor(texCoord0.y * 0.5 + 0.0001));
    vec2 uv = vec2(texCoord0.x, texCoord0.y - float(mode) * 2.0);
    vec4 tint = vertexColor;
    float time = GameTime * 2400.0;
    float glow = edgeGlow(uv);
    vec2 patternUv = uv + vec2(time * 0.035, -time * 0.025);
    vec3 color = tint.rgb;
    float alpha = tint.a;

    if (mode == 0) {
        float pulse = 0.72 + 0.28 * sin(time * 1.5 + (patternUv.x + patternUv.y) * 8.0);
        float mist = fbm(patternUv * 3.0 + vec2(time * 0.12, -time * 0.08));
        color = mix(color * 0.55, mix(color, vec3(1.0), 0.35), mist * pulse + glow * 0.45);
        alpha *= 0.55 + mist * 0.3 + glow * 0.25;
    } else if (mode == 2) {
        vec2 flow = patternUv * 2.5;
        vec2 drift = vec2(time * 0.20, -time * 0.15);
        vec2 warp = vec2(fbm(flow * 0.90 + drift * 0.75 + vec2(0.0, 4.1)), fbm(flow * 0.78 - drift * 0.48 + vec2(3.7, 1.8)));
        vec2 q = flow + (warp - 0.5) * 1.8;
        float mist = fbm(q * 0.72 - drift * 0.24 + vec2(4.2, 8.1));
        float veins = pow(clamp(ridged(q * 1.85 + vec2(mist * 2.5, mist * 1.6) - drift * 0.55), 0.0, 1.0), 2.4);
        float strands = pow(clamp(1.0 - abs(sin((q.x * 1.08 + q.y * 0.42) * 1.7 + time * 0.85 + mist * 4.3)), 0.0, 1.0), 4.8);
        float energy = clamp(mist * 0.22 + veins * 0.88 + strands * 0.55, 0.0, 1.0);
        color = mix(color * 0.5, mix(color, vec3(1.0), 0.45), energy);
        alpha *= 0.35 + energy * 0.65;
    } else if (mode == 4) {
        float p1 = sin(patternUv.x * 8.0 + time * 2.0);
        float p2 = sin(patternUv.y * 6.0 + time * 1.5);
        float p3 = sin((patternUv.x + patternUv.y) * 5.0 + time * 1.8);
        float n = (p1 + p2 + p3) * 0.16 + 0.5;
        color = mix(color, mix(color, vec3(1.0), 0.7), n);
        alpha *= 0.45 + n * 0.55;
    } else if (mode == 4) {
        vec2 st = vec2(0.5) - uv;
        float t = time * 0.01;
        float accum = 0.0;
        for(int i = 0; i < 83; i++) {
            float fi = float(i);
            float thisYOffset = mod(hash11(fi * 0.017) * (t + 19.0) * 0.2, 4.0) - 2.0;
            vec2 center = (hash21(fi) * 2.0 - 1.0) * vec2(1.1, 1.0) - vec2(0.0, thisYOffset);
            float radius = 0.5;
            vec2 offset = st - center;
            float twistFactor = (hash11(fi * 0.0347) * 2.0 - 1.0) * 1.9;
            float rotation = 0.1 + t * 0.2 + sin(t * 0.1) * 0.9 + (length(offset) / radius) * twistFactor;
            accum += pow(smoothstep(radius, 0.0, polygonDistance(st - center, 0.1 + hash11(fi * 2.3) * 0.2, rotation, 5) + 0.1), 3.0);
        }
        vec3 subColor = vec3(0.4, 0.8, 0.2);
        vec3 addColor = vec3(0.3, 0.2, 0.1);
        vec3 nebulaCol = vec3(1.0) - accum * subColor + addColor;
        color = mix(color, color * nebulaCol, 0.8);
        alpha *= clamp(accum * 0.5 + 0.2, 0.0, 1.0);
    } else if (mode == 8) {
        float shade = bwPattern(patternUv * 2.15, time * 0.18);
        color = bwColormap(shade);
        alpha *= clamp(0.35 + shade * 0.85, 0.0, 1.0);
    } else if (mode == 12) {
        // Water turbulence (Hoskins / joltz0r)
        const float TAU = 6.28318530718;
        float wTime = time * 0.5 + 23.0;
        vec2 p = mod(patternUv * TAU, TAU) - 250.0;
        vec2 ii = vec2(p);
        float c = 1.0;
        float inten = 0.005;
        for (int n = 0; n < 5; n++) {
            float t = wTime * (1.0 - (3.5 / float(n + 1)));
            ii = p + vec2(cos(t - ii.x) + sin(t + ii.y), sin(t - ii.y) + cos(t + ii.x));
            c += 1.0 / length(vec2(p.x / (sin(ii.x + t) / inten), p.y / (cos(ii.y + t) / inten)));
        }
        c /= 5.0;
        c = 1.17 - pow(c, 1.4);
        vec3 water = vec3(pow(abs(c), 8.0));
        water = clamp(water + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
        color = mix(color * 0.18, color * water, clamp(water.b * 1.2, 0.0, 1.0));
        alpha *= clamp(0.40 + water.b * 0.70, 0.0, 1.0);
    }

    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
