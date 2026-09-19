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

layout(std140) uniform Uniforms {
    vec4 uScreen;
    vec4 uColor;
    vec4 uParams;
    vec4 uShaderParams;
    vec4 uCameraRight;
    vec4 uCameraUp;
    vec4 uCameraForward;
};

in vec2 vUV;
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

void main() {
    vec2 uv = vUV;
    vec2 aspect = vec2(uScreen.x / max(uScreen.y, 1.0), 1.0);
    vec2 rayUv = (uv * 2.0 - 1.0) * aspect;
    vec3 ray = normalize(uCameraRight.xyz * rayUv.x + uCameraUp.xyz * rayUv.y + uCameraForward.xyz * 1.25);
    vec2 skyUv = ray.xz * (1.0 - abs(ray.y) * 0.35) + vec2(ray.y * 0.22, ray.y * -0.18);
    vec3 base = uColor.rgb;
    float alpha = clamp(uParams.x, 0.0, 1.0);
    float time = (GameTime * 1200.0 + uParams.w) * max(uParams.y, 0.01);
    int shaderMode = int(uShaderParams.x);
    float horizon = smoothstep(-0.45, 0.85, ray.y);
    vec3 color = mix(base * 0.16, base * 0.48, horizon);

    if (shaderMode == 0) {
        float pulse = 0.72 + 0.28 * sin(time * 1.5 + (skyUv.x + skyUv.y) * 8.0);
        float mist = fbm(skyUv * 3.0 + vec2(time * 0.12, -time * 0.08));
        color = mix(color, mix(base, vec3(1.0), 0.35), mist * pulse);
    } else if (shaderMode == 2) {
        vec2 flow = skyUv * 2.5;
        vec2 drift = vec2(time * 0.20, -time * 0.15);
        vec2 warp = vec2(fbm(flow * 0.90 + drift * 0.75 + vec2(0.0, 4.1)), fbm(flow * 0.78 - drift * 0.48 + vec2(3.7, 1.8)));
        vec2 q = flow + (warp - 0.5) * 1.8;
        float mist = fbm(q * 0.72 - drift * 0.24 + vec2(4.2, 8.1));
        float veins = pow(clamp(ridged(q * 1.85 + vec2(mist * 2.5, mist * 1.6) - drift * 0.55), 0.0, 1.0), 2.4);
        float strands = pow(clamp(1.0 - abs(sin((q.x * 1.08 + q.y * 0.42) * 1.7 + time * 0.85 + mist * 4.3)), 0.0, 1.0), 4.8);
        float energy = clamp(mist * 0.22 + veins * 0.88 + strands * 0.55, 0.0, 1.0);
        color = mix(color, mix(base, vec3(1.0), 0.45), energy);
    } else if (shaderMode == 4) {
        float p1 = sin(skyUv.x * 8.0 + time * 2.0);
        float p2 = sin(skyUv.y * 6.0 + time * 1.5);
        float p3 = sin((skyUv.x + skyUv.y) * 5.0 + time * 1.8);
        float n = (p1 + p2 + p3) * 0.16 + 0.5;
        color = mix(base * (0.28 + horizon * 0.25), mix(base, vec3(1.0), 0.7), n);
    } else if (shaderMode == 6) {
        float densityMix = 0.25;
        vec2 flow = skyUv * 1.35;
        vec2 drift = vec2(time * 0.22, time * 0.15);
        vec2 warp = vec2(fbm(flow * 0.85 + drift * 0.55 + vec2(0.0, 4.1)), fbm(flow * 0.80 - drift * 0.42 + vec2(3.7, 1.8)));
        vec2 q = flow + (warp - 0.5) * mix(1.3, 2.5, densityMix);
        float mist = fbm(q * 0.70 - drift * 0.18 + vec2(4.2, 8.1));
        float band1 = 1.0 - abs(sin((q.x * 1.02 + q.y * 0.38 + time * 0.21) * 1.85 + mist * 4.8));
        float band2 = 1.0 - abs(sin((q.x * -0.58 + q.y * 1.10 - time * 0.13) * 1.45 - mist * 3.2));
        band1 = pow(clamp(band1, 0.0, 1.0), 4.2);
        band2 = pow(clamp(band2, 0.0, 1.0), 4.8);
        float veins = pow(clamp(ridged(q * 1.90 + vec2(mist * 2.7, mist * 1.9) - drift * 0.55), 0.0, 1.0), 2.4);
        float energy = clamp(mist * 0.24 + band1 * 0.70 + band2 * 0.40 + veins * 0.84, 0.0, 1.0);
        color = base * (0.18 + horizon * 0.18 + smoothstep(0.16, 0.98, energy) * 1.1);
    } else if (shaderMode == 8) {
        float shade = bwPattern(skyUv * 2.15, time * 0.18);
        color = mix(bwColormap(shade), base, 0.22);
    } else if (shaderMode == 10) {
        vec2 waveUv = skyUv * 2.0;
        for (float i = 1.0; i < 10.0; i++) {
            waveUv.x += 0.6 / i * cos(i * 2.5 * waveUv.y + time);
            waveUv.y += 0.6 / i * cos(i * 1.5 * waveUv.x + time);
        }
        float wave = 0.1 / max(abs(sin(time - waveUv.y - waveUv.x)), 0.08);
        color = mix(base * (0.12 + horizon * 0.22), base, clamp(wave, 0.0, 1.8));
    } else if (shaderMode == 12) {
        // Water turbulence (Hoskins / joltz0r)
        const float TAU = 6.28318530718;
        float wTime = time * 0.5 + 23.0;
        vec2 uv = skyUv * 0.5 + 0.5;
        vec2 p = mod(uv * TAU, TAU) - 250.0;
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
        color = mix(base * (0.12 + horizon * 0.18), base * water, clamp(water.b * 1.15, 0.0, 1.0));
    }

    float zenith = smoothstep(-0.15, 0.85, ray.y);
    color *= 0.78 + zenith * 0.28;
    fragColor = vec4(color, alpha);
}
