#version 330

layout(std140) uniform Uniforms {
    vec4 uScreen;       // x = width, y = height, z = time, w = mode (0: Pretty, 1: Smoke, 2: Glass, 3: Plasma)
    vec4 uColor;        // rgb = main color, a = alpha
    vec4 uColor2;       // rgb = secondary / flame tip color
    vec4 uParams;       // x = intensity, y = speed, z = height, w = glow
    vec4 uExtra;        // x = wind, y = wave, z = smoke density, w = activity
    vec4 uMotion;       // x = camShiftX, y = camShiftY, z = slash, w = reserved
};

uniform sampler2D Sampler0; // Свежая маска рук (жёсткая, привязана к рукам)
uniform sampler2D Sampler1; // Режим 2 — копия сцены; остальные режимы — накопленный шлейф

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

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amplitude;
        p = p * 2.02 + vec2(8.4, 5.7);
        amplitude *= 0.5;
    }
    return value;
}

float ridged(vec2 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 4; i++) {
        float r = 1.0 - abs(noise(p) * 2.0 - 1.0);
        value += r * amplitude;
        p = p * 2.18 + vec2(3.1, 9.2);
        amplitude *= 0.52;
    }
    return value;
}

float sampleMask(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0))).r;
}

// Накопленный шлейф: прошлые позиции рук с затуханием. Живёт только в Sampler1.
float sampleTrail(vec2 uv) {
    return texture(Sampler1, clamp(uv, vec2(0.0), vec2(1.0))).r;
}

float edgeMask(vec2 uv, vec2 texel) {
    float c = sampleMask(uv);
    float e = 0.0;
    e += abs(c - sampleMask(uv + vec2(texel.x, 0.0)));
    e += abs(c - sampleMask(uv - vec2(texel.x, 0.0)));
    e += abs(c - sampleMask(uv + vec2(0.0, texel.y)));
    e += abs(c - sampleMask(uv - vec2(0.0, texel.y)));
    return clamp(e * 0.9, 0.0, 1.0);
}

float sampleBlurredMask(vec2 uv, vec2 texel, float radius) {
    float sum = sampleMask(uv) * 4.0;
    vec2 offset = texel * radius;
    sum += sampleMask(uv + vec2(offset.x, 0.0));
    sum += sampleMask(uv - vec2(offset.x, 0.0));
    sum += sampleMask(uv + vec2(0.0, offset.y));
    sum += sampleMask(uv - vec2(0.0, offset.y));
    sum += sampleMask(uv + offset);
    sum += sampleMask(uv - offset);
    sum += sampleMask(uv + vec2(offset.x, -offset.y));
    sum += sampleMask(uv + vec2(-offset.x, offset.y));
    return sum / 12.0;
}

void main() {
    vec2 uv = vUV;
    vec2 texel = 1.0 / max(uScreen.xy, vec2(1.0));
    float time = uScreen.z;
    int mode = int(uScreen.w + 0.5);

    float mask = sampleMask(uv);
    float edge = edgeMask(uv, texel);

    float intensity = uParams.x;
    float speed = uParams.y;
    float height = uParams.z;
    float glow = uParams.w;
    float wind = uExtra.x;
    float wave = uExtra.y;
    float smoke = uExtra.z;
    float activity = uExtra.w;
    float slash = uMotion.z;
    vec2 camShift = uMotion.xy;

    float t = time * max(speed, 0.001);

    // MODE 0: "Красивый" (Pretty Animated Fire)
    if (mode == 0) {
        vec2 flow = uv * 2.5;
        vec2 drift = vec2(t * 0.35, -t * 0.25);

        vec2 warp = vec2(
            fbm(flow * 0.85 + drift * 0.85 + vec2(0.0, 4.1)),
            fbm(flow * 0.72 - drift * 0.55 + vec2(3.7, 1.8))
        );
        vec2 q = flow + (warp - 0.5) * 2.2;

        float mist = fbm(q * 0.75 - drift * 0.28 + vec2(4.2, 8.1));
        float veins = ridged(q * 1.75 + vec2(mist * 2.8, mist * 1.8) - drift * 0.58);
        veins = pow(clamp(veins, 0.0, 1.0), 2.4);

        float stripeA = 1.0 - abs(sin((q.x * 1.05 + q.y * 0.4) * 1.8 + time * 0.9 + mist * 4.5));
        float stripeB = 1.0 - abs(sin((q.x * -0.55 + q.y * 1.1) * 1.5 - time * 0.7 - mist * 3.0));
        stripeA = pow(clamp(stripeA, 0.0, 1.0), 5.0);
        stripeB = pow(clamp(stripeB, 0.0, 1.0), 5.5);

        float energy = clamp(mist * 0.2 + veins * 0.9 + stripeA * 0.6 + stripeB * 0.35, 0.0, 1.0);
        float core = smoothstep(0.15, 0.95, energy);

        float windForce = (sin(time * 1.8) * 0.8 + sin(time * 0.9 + 1.5) * 0.6) * wind;
        float gust = (fbm(vec2(uv.x * 3.5, uv.y * 3.5 - t * 1.2)) - 0.5) * wind;

        // 20-step upward raymarching of flame plume
        float plume = 0.0;
        const int STEPS = 20;
        for (int i = 1; i <= STEPS; i++) {
            float d = float(i) / float(STEPS);
            float wave1 = sin((d * 8.0 + time * 1.5)) * 0.03 * wave;
            float wave2 = sin((d * 12.0 + time * 2.0 + 2.5)) * 0.02 * wave;
            float wave3 = (warp.x - 0.5) * 0.04 * d * wave;

            float below = uv.y + d * height + camShift.y * d;
            vec2 samplePos = vec2(uv.x - wave1 - wave2 - wave3 + windForce * 0.03 * d + gust * 0.04 * d + camShift.x * d, below);

            float m = sampleTrail(samplePos);
            float falloff = 1.0 - d;
            plume += m * falloff * falloff;
        }
        plume = clamp(plume * 0.4, 0.0, 1.0);

        float blurred = sampleBlurredMask(uv, texel, 1.5);
        float flame = clamp(plume * 1.15 + blurred * 0.25 + mask * 0.3, 0.0, 1.0) * energy * intensity;

        vec3 colorHot = uColor.rgb;
        vec3 colorCold = uColor2.rgb;
        vec3 flameCol = mix(colorHot, colorCold, clamp(uv.y * 1.1, 0.0, 1.0));
        flameCol += vec3(core * 0.5);

        float outAlpha = clamp(flame * uColor.a, 0.0, 1.0);
        if (outAlpha <= 0.002) discard;

        fragColor = vec4(flameCol * outAlpha, outAlpha);
        return;
    }

    // MODE 1: "Дым" (Smoke / Swirling Trail)
    if (mode == 1) {
        float blurred = sampleBlurredMask(uv, texel, 1.5 + smoke * 1.5);
        float blurredWide = sampleBlurredMask(uv, texel, 3.0 + smoke * 3.0);
        float trailSmoke = sampleTrail(uv + vec2(0.0, 0.01));

        vec2 smokeDrift = vec2(sin(t * 0.7) * 0.04, -t * 0.15);
        float smokeNoise = fbm(uv * 3.5 + smokeDrift);
        float smokeTurbulence = ridged(uv * 5.0 - smokeDrift * 1.2);

        float aura = clamp(max(blurred * 0.55, blurredWide * 0.3) + trailSmoke * 0.25, 0.0, 1.0);
        float smokeAlpha = clamp(aura * (0.5 + intensity * 0.35 + smoke * 0.25 + activity * 0.15 + slash * 0.3), 0.0, 0.7);
        smokeAlpha *= (0.6 + smokeNoise * 0.55 + smokeTurbulence * 0.35);

        vec3 smokeColor = uColor.rgb * (1.15 + smoke * 0.3);
        smokeColor += edge * uColor.rgb * (0.35 + glow * 0.2);

        float outAlpha = clamp(smokeAlpha * uColor.a, 0.0, 1.0);
        if (outAlpha <= 0.002) discard;

        fragColor = vec4(smokeColor * outAlpha, outAlpha);
        return;
    }

    // MODE 2: "Стекло" (Refractive Glass Hands)
    if (mode == 2) {
        if (mask <= 0.001) discard;

        vec2 distort = (vec2(noise(uv * 12.0 + t * 0.5), noise(uv * 12.0 - t * 0.5)) - 0.5) * 0.02 * intensity;
        vec2 distortR = distort * 1.2;
        vec2 distortB = -distort * 1.2;

        float r = texture(Sampler1, uv + distortR).r;
        float g = texture(Sampler1, uv + distort).g;
        float b = texture(Sampler1, uv + distortB).b;

        vec3 glassScene = vec3(r, g, b);
        vec3 glassCol = mix(glassScene, uColor.rgb, 0.35) + vec3(edge * glow * 0.6);
        glassCol += pow(clamp(edge, 0.0, 1.0), 2.0) * uColor2.rgb * 0.5;

        float outAlpha = clamp(mask * uColor.a, 0.0, 1.0);
        fragColor = vec4(glassCol * outAlpha, outAlpha);
        return;
    }

    // MODE 3: "Плазма" (Pulsing Energy Arcs)
    if (mode == 3) {
        float blurred = sampleBlurredMask(uv, texel, 1.2);
        vec2 p = (uv - 0.5) * 3.0;
        float d = length(p);
        float a = atan(p.y, p.x);

        float arcs = abs(sin(6.0 * a + t * 4.0 + sin(d * 8.0 - t * 3.0)));
        arcs = pow(1.0 - clamp(arcs, 0.0, 1.0), 4.0);

        float plasma = (mask * 0.55 + blurred * 0.3) * (0.4 + arcs * 0.8) * intensity;
        vec3 plasmaCol = mix(uColor.rgb, uColor2.rgb, arcs) * (1.0 + arcs * glow * 0.6);

        float outAlpha = clamp(plasma * uColor.a, 0.0, 1.0);
        if (outAlpha <= 0.002) discard;

        fragColor = vec4(plasmaCol * outAlpha, outAlpha);
        return;
    }

    discard;
}
