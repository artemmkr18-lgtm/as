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
    mat4 uInvViewProj;
};

uniform sampler2D Sampler0; // Mask
uniform sampler2D Sampler1; // Depth

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

float sampleMask(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

float outlineMask(vec2 uv, float mask, float widthPx) {
    vec2 texel = 1.0 / max(uScreen.xy, vec2(1.0));
    float width = max(widthPx, 0.5);
    float outer = 0.0;
    float inner = mask;

    for (int i = 0; i < 16; i++) {
        float a = 6.2831853 * (float(i) / 16.0);
        vec2 dir = vec2(cos(a), sin(a));
        float nearMask = sampleMask(uv + dir * texel * width);
        float farMask = sampleMask(uv + dir * texel * (width + 1.25));
        outer = max(outer, nearMask);
        inner = min(inner, farMask);
    }

    float outsideLine = outer * (1.0 - smoothstep(0.02, 0.18, mask));
    float edgeLine = smoothstep(0.08, 0.35, outer - inner) * (1.0 - smoothstep(0.2, 0.98, mask));
    return clamp(max(outsideLine, edgeLine), 0.0, 1.0);
}

float glowMask(vec2 uv, float mask, float widthPx) {
    vec2 texel = 1.0 / max(uScreen.xy, vec2(1.0));
    float radius = max(widthPx * 6.5, 8.0);
    float glow = 0.0;
    float weightSum = 0.0;

    for (int ring = 1; ring <= 7; ring++) {
        float r = radius * (float(ring) / 7.0);
        float weight = exp(-float(ring - 1) * 0.36);
        for (int i = 0; i < 20; i++) {
            float a = 6.2831853 * ((float(i) + float(ring) * 0.37) / 20.0);
            vec2 dir = vec2(cos(a), sin(a));
            glow += sampleMask(uv + dir * texel * r) * weight;
            weightSum += weight;
        }
    }

    glow = glow / max(weightSum, 0.001);
    glow *= 1.0 - smoothstep(0.02, 0.42, mask);
    return clamp(pow(glow, 0.72), 0.0, 1.0);
}

vec4 over(vec4 top, vec4 bottom) {
    float alpha = top.a + bottom.a * (1.0 - top.a);
    if (alpha <= 0.001) {
        return vec4(0.0);
    }
    vec3 color = (top.rgb * top.a + bottom.rgb * bottom.a * (1.0 - top.a)) / alpha;
    return vec4(color, alpha);
}

void main() {
    float mask = texture(Sampler0, vUV).a;
    float fillAlpha = uParams.x;
    float useShader = uParams.z;
    int shaderMode = int(uShaderParams.x);
    int outlineType = int(uShaderParams.y);
    float outlineWidth = uShaderParams.z;
    float outlineStrength = uShaderParams.w;

    float line = outlineType > 0 ? outlineMask(vUV, mask, outlineWidth) : 0.0;
    float outlineGlow = outlineType == 2 ? glowMask(vUV, mask, outlineWidth) * max(outlineStrength, 0.0) : 0.0;

    vec3 outlineColor = uColor.rgb;
    vec3 outColor = outlineColor;
    float outAlpha = 0.0;

    if (outlineGlow > 0.001) {
        float glowAlpha = clamp(outlineGlow * 1.65, 0.0, 1.0);
        outColor = outlineColor;
        outAlpha = max(outAlpha, glowAlpha);
    }

    if (line > 0.001) {
        float lineAlpha = outlineType == 2 ? 0.88 : 1.0;
        outColor = mix(outColor, outlineColor, clamp(line * lineAlpha, 0.0, 1.0));
        outAlpha = max(outAlpha, clamp(line * lineAlpha, 0.0, 1.0));
    }

    bool drawFill = fillAlpha > 0.001 && mask > 0.001;

    if (!drawFill) {
        if (outAlpha <= 0.001) {
            discard;
        }
        fragColor = vec4(outColor, outAlpha);
        return;
    }

    // Обычная заливка (без шейдера)
    if (useShader < 0.5) {
        vec4 fill = vec4(uColor.rgb, mask * fillAlpha);
        fragColor = over(fill, vec4(outColor, outAlpha));
        return;
    }

    // Шейдерные режимы
    vec2 uv = vUV;
    vec2 patternUv = uv;

    float useOffsets = uParams.y;
    if (useOffsets > 0.5) {
        vec3 partData = texture(Sampler0, uv).rgb;
        if (partData.b > 0.5) {
            patternUv = partData.rg * 2.0; // Local UV from mask (0.0 to 1.0) scaled up slightly
        }
    }

    vec3 color = uColor.rgb;
    float alpha = fillAlpha;
    float time = uParams.w * 0.85;
    float glow = edgeGlow(uv);
    patternUv += vec2(time * 0.035, -time * 0.025);

    if (shaderMode == 0) {
        // Full - туман с пульсацией
        float pulse = 0.72 + 0.28 * sin(time * 1.5 + (patternUv.x + patternUv.y) * 8.0);
        float mist = fbm(patternUv * 3.0 + vec2(time * 0.12, -time * 0.08));
        color = mix(color * 0.55, mix(color, vec3(1.0), 0.35), mist * pulse + glow * 0.45);
        alpha *= 0.55 + mist * 0.3 + glow * 0.25;
    } else if (shaderMode == 2) {
        // WebShader - паутина
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
    } else if (shaderMode == 4) {
        // Plasma - плазма
        float p1 = sin(patternUv.x * 8.0 + time * 2.0);
        float p2 = sin(patternUv.y * 6.0 + time * 1.5);
        float p3 = sin((patternUv.x + patternUv.y) * 5.0 + time * 1.8);
        float n = (p1 + p2 + p3) * 0.16 + 0.5;
        color = mix(color, mix(color, vec3(1.0), 0.7), n);
        alpha *= 0.45 + n * 0.55;
    } else if (shaderMode == 6) {
        // ChamsFill - энергетический поток
        float speedX = 0.22;
        float speedY = 0.15;
        float shaderScale = 1.35;
        float density = 1.15;
        float glowStrength = 1.0;

        float densityMix = clamp((density - 0.5) / 2.5, 0.0, 1.0);
        vec2 flow = patternUv * shaderScale;
        vec2 drift = vec2(time * speedX, time * speedY);

        vec2 warp = vec2(
            fbm(flow * 0.85 + drift * 0.55 + vec2(0.0, 4.1)),
            fbm(flow * 0.80 - drift * 0.42 + vec2(3.7, 1.8))
        );
        vec2 q = flow + (warp - 0.5) * mix(1.3, 2.5, densityMix);

        float mist = fbm(q * 0.70 - drift * 0.18 + vec2(4.2, 8.1));

        float diagonal1 = q.x * 1.02 + q.y * 0.38 + time * (speedX * 0.80 + speedY * 0.25);
        float diagonal2 = q.x * -0.58 + q.y * 1.10 - time * (speedY * 0.90);

        float band1 = 1.0 - abs(sin(diagonal1 * 1.85 + mist * 4.8));
        float band2 = 1.0 - abs(sin(diagonal2 * 1.45 - mist * 3.2));
        band1 = pow(clamp(band1, 0.0, 1.0), mix(3.3, 7.0, densityMix));
        band2 = pow(clamp(band2, 0.0, 1.0), mix(3.8, 7.8, densityMix));

        float veins = ridged(q * 1.90 + vec2(mist * 2.7, mist * 1.9) - drift * 0.55);
        veins = pow(clamp(veins, 0.0, 1.0), mix(2.0, 3.8, densityMix));

        float micro = ridged(q * 3.6 - vec2(7.1, 2.6) + drift * 0.35);
        micro = pow(clamp(micro, 0.0, 1.0), 5.8);

        float energy = clamp(
            mist * 0.24 +
            band1 * 0.70 +
            band2 * 0.40 +
            veins * 0.84 +
            micro * 0.28,
            0.0, 1.0
        );

        float core = smoothstep(0.16, 0.98, energy);
        float brightVeins = pow(clamp(max(veins, band1), 0.0, 1.0), 1.35);
        float glowVal = (brightVeins * 0.95 + micro * 0.55) * glowStrength;

        color = color * (0.24 + mist * 0.20 + core * 0.88 + glowVal * 0.52);
        alpha *= clamp(0.24 + core * 0.74 + glowVal * 0.20, 0.0, 1.0);
    } else if (shaderMode == 8) {
        float shade = bwPattern(patternUv * 2.15, time * 0.18);
        color = bwColormap(shade);
        alpha *= clamp(0.35 + shade * 0.85, 0.0, 1.0);
    } else if (shaderMode == 10) {
        vec2 waveUv = (2.0 * patternUv - 1.0) * vec2(uScreen.x / max(uScreen.y, 1.0), 1.0);
        for (float i = 1.0; i < 10.0; i++) {
            waveUv.x += 0.6 / i * cos(i * 2.5 * waveUv.y + time);
            waveUv.y += 0.6 / i * cos(i * 1.5 * waveUv.x + time);
        }
        float wave = 0.1 / max(abs(sin(time - waveUv.y - waveUv.x)), 0.08);
        color = mix(color * 0.18, color, clamp(wave, 0.0, 1.7));
        alpha *= clamp(0.38 + wave * 0.45, 0.0, 1.0);
    } else if (shaderMode == 12) {
        // Water turbulence (Hoskins / joltz0r)
        const float TAU = 6.28318530718;
        float wTime = time * 0.5 + 23.0;
        vec2 p = mod(patternUv * TAU, TAU) - 250.0;
        vec2 ii = vec2(p);
        float cval = 1.0;
        float inten = 0.005;
        for (int n = 0; n < 5; n++) {
            float t = wTime * (1.0 - (3.5 / float(n + 1)));
            ii = p + vec2(cos(t - ii.x) + sin(t + ii.y), sin(t - ii.y) + cos(t + ii.x));
            cval += 1.0 / length(vec2(p.x / (sin(ii.x + t) / inten), p.y / (cos(ii.y + t) / inten)));
        }
        cval /= 5.0;
        cval = 1.17 - pow(cval, 1.4);
        vec3 waterCol = vec3(pow(abs(cval), 8.0));
        waterCol = clamp(waterCol + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
        color = mix(color * 0.18, color * waterCol, clamp(waterCol.b * 1.2, 0.0, 1.0));
        alpha *= clamp(0.40 + waterCol.b * 0.70, 0.0, 1.0);
    }


    if (alpha <= 0.001) {
        discard;
    }

    vec4 fill = vec4(color, clamp(mask * alpha, 0.0, 1.0));
    fragColor = over(fill, vec4(outColor, outAlpha));
}
