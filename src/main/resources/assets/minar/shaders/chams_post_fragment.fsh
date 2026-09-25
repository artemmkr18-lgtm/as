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

float edgeGlow(vec2 uv) {
    vec2 texel = 1.0 / max(uScreen.xy, vec2(1.0));
    float center = texture(Sampler0, uv).a;
    float edge = 0.0;
    for (int i = 0; i < 8; i++) {
        float ang = 6.2831853 * (float(i) / 8.0);
        vec2 off = vec2(cos(ang), sin(ang)) * texel * 3.0;
        edge += abs(center - texture(Sampler0, uv + off).a);
    }
    return clamp(edge / 4.0, 0.0, 1.0);
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

    // Standard fill (without shader effect)
    if (useShader < 0.5) {
        vec4 fill = vec4(uColor.rgb, mask * fillAlpha);
        fragColor = over(fill, vec4(outColor, outAlpha));
        return;
    }

    // Aspect-ratio-corrected, high-frequency UV
    vec2 aspect = vec2(uScreen.x / max(uScreen.y, 1.0), 1.0);
    vec2 patternUv = vUV * aspect * 6.5;

    vec3 color = uColor.rgb;
    float alpha = fillAlpha;
    float time = uParams.w * 0.85;
    float rimGlow = edgeGlow(vUV);

    if (shaderMode == 0) {
        // Mode 0: Solid (flat saturated clean silhouette)
        color = uColor.rgb;
        alpha = fillAlpha;
    } else if (shaderMode == 1) {
        // Mode 1: Web (cyberpunk spiderweb grid)
        vec2 w = patternUv * 3.5;
        vec2 grid = abs(fract(w + vec2(time * 0.35, -time * 0.2)) - 0.5);
        float d = min(grid.x, grid.y);
        float web1 = 1.0 - smoothstep(0.0, 0.12, d);
        float d2 = abs(fract((w.x + w.y) * 0.707 + time * 0.25) - 0.5);
        float web2 = 1.0 - smoothstep(0.0, 0.09, d2);
        float totalWeb = max(web1, web2);
        color = mix(uColor.rgb * 0.20, mix(uColor.rgb, vec3(1.0), 0.85), totalWeb);
        alpha = fillAlpha * (0.35 + totalWeb * 0.65);
    } else if (shaderMode == 2) {
        // Mode 2: Plasma (swirling electric multi-colored vortex)
        vec2 p = patternUv * 2.5;
        float p1 = sin(p.x * 1.8 + time * 2.8);
        float p2 = sin(p.y * 2.2 - time * 2.2);
        float p3 = sin((p.x + p.y) * 1.5 + time * 2.0);
        float p4 = sin(length(p) * 2.4 - time * 3.2);
        float plasma = (p1 + p2 + p3 + p4) * 0.25 * 0.5 + 0.5;
        vec3 c1 = vec3(1.0, 0.05, 0.65);
        vec3 c2 = vec3(0.0, 0.95, 1.0);
        vec3 c3 = vec3(0.55, 0.0, 1.0);
        vec3 plasmaCol = mix(c1, c2, plasma);
        plasmaCol = mix(plasmaCol, c3, sin(plasma * 6.28 + time * 1.5) * 0.5 + 0.5);
        color = mix(uColor.rgb, plasmaCol, 0.85);
        alpha = fillAlpha * (0.60 + 0.40 * plasma);
    } else if (shaderMode == 3) {
        // Mode 3: Waves (horizontal pulsating energy waves)
        float wave = sin(patternUv.y * 7.0 - time * 5.0) * 0.5 + 0.5;
        wave = pow(wave, 4.0);
        float subWave = pow(sin(patternUv.y * 14.0 - time * 10.0) * 0.5 + 0.5, 6.0) * 0.5;
        float totalWave = clamp(wave + subWave, 0.0, 1.0);
        vec3 crestColor = mix(uColor.rgb, vec3(1.0), 0.85);
        color = mix(uColor.rgb * 0.25, crestColor, totalWave);
        alpha = fillAlpha * (0.35 + 0.65 * totalWave);
    } else if (shaderMode == 4) {
        // Mode 4: Water (shimmering ocean caustics & aquatic ripple)
        vec2 wUv = patternUv * 3.5;
        float c1 = sin(wUv.x * 1.6 + time * 2.2 + cos(wUv.y * 1.4 + time * 1.6));
        float c2 = cos(wUv.y * 1.6 - time * 2.0 + sin(wUv.x * 1.4 - time * 1.4));
        float caustic = pow(clamp((c1 * c2 + 1.0) * 0.5, 0.0, 1.0), 2.2);
        vec3 oceanBlue = vec3(0.04, 0.22, 0.75);
        vec3 tropicalCyan = vec3(0.10, 0.88, 0.98);
        vec3 sunShimmer = vec3(0.95, 1.0, 1.0);
        vec3 water = mix(oceanBlue, tropicalCyan, caustic * 0.7);
        water = mix(water, sunShimmer, pow(caustic, 3.0) * 0.85);
        color = mix(uColor.rgb * 0.25, water, 0.88);
        alpha = fillAlpha * (0.45 + 0.55 * caustic);
    } else if (shaderMode == 5) {
        // Mode 5: Energy (crackling lightning arcs & electric sparks)
        vec2 eUv = patternUv * 3.0;
        float n = fbm(eUv * 1.5 + vec2(time * 1.8, -time * 2.5));
        float bolt1 = pow(clamp(1.0 - abs(sin(eUv.x * 3.2 + n * 5.0 + time * 8.0)), 0.0, 1.0), 12.0);
        float bolt2 = pow(clamp(1.0 - abs(sin((eUv.x + eUv.y) * 2.8 - n * 4.5 - time * 9.0)), 0.0, 1.0), 10.0);
        float spark = clamp(bolt1 + bolt2, 0.0, 1.0);
        vec3 elecDeep = vec3(0.12, 0.35, 1.0);
        vec3 elecGlow = vec3(0.90, 0.95, 1.0);
        color = mix(uColor.rgb * 0.25, mix(elecDeep, elecGlow, spark), clamp(n * 0.35 + spark * 0.85, 0.0, 1.0));
        alpha = fillAlpha * (0.35 + 0.65 * spark);
    } else if (shaderMode == 6) {
        // Mode 6: Rainbow (flowing chromatic RGB spectrum)
        float hue = fract(patternUv.y * 0.85 - time * 0.35 + patternUv.x * 0.4);
        vec3 rgb = clamp(abs(mod(hue * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
        color = rgb;
        alpha = fillAlpha;
    } else if (shaderMode == 7) {
        // Mode 7: Fire (rising flame tongues & embers)
        vec2 fUv = patternUv * vec2(2.5, 4.0);
        float fNoise = fbm(fUv + vec2(0.0, -time * 4.5));
        float flame = clamp(fNoise * 1.4 - (fUv.y * 0.25 - 0.2), 0.0, 1.0);
        float flicker = 0.88 + 0.12 * sin(time * 24.0);
        flame = clamp(flame * flicker, 0.0, 1.0);
        vec3 fYellow = vec3(1.0, 0.92, 0.2);
        vec3 fOrange = vec3(1.0, 0.45, 0.04);
        vec3 fRed = vec3(0.85, 0.08, 0.02);
        vec3 fDark = vec3(0.15, 0.03, 0.02);
        vec3 fCol;
        if (flame > 0.65) fCol = mix(fOrange, fYellow, (flame - 0.65) / 0.35);
        else if (flame > 0.30) fCol = mix(fRed, fOrange, (flame - 0.30) / 0.35);
        else fCol = mix(fDark, fRed, flame / 0.30);
        color = fCol;
        alpha = fillAlpha * clamp(flame * 1.35, 0.25, 1.0);
    } else if (shaderMode == 8) {
        // Mode 8: Smoke (dark ghostly swirling mist)
        vec2 sUv = patternUv * 2.2;
        float s1 = fbm(sUv * 1.2 + vec2(time * 0.3, -time * 0.5));
        float s2 = fbm(sUv * 1.6 - vec2(time * 0.35, time * 0.25));
        float smoke = clamp(s1 * 0.6 + s2 * 0.4, 0.0, 1.0);
        vec3 sDark = vec3(0.06, 0.06, 0.10);
        vec3 sLight = vec3(0.40, 0.36, 0.52);
        color = mix(sDark, sLight, smoke);
        alpha = fillAlpha * (0.28 + 0.62 * smoke);
    } else if (shaderMode == 9) {
        // Mode 9: Metal (chrome reflection & specular shine)
        vec2 mUv = patternUv * 3.0;
        float chrome = sin(mUv.y * 3.0 + mUv.x * 2.0 + time * 0.8) * 0.5 + 0.5;
        chrome = pow(chrome, 4.0);
        vec3 metalBase = uColor.rgb * 0.45;
        vec3 chromeShine = vec3(1.0);
        color = mix(metalBase, chromeShine, clamp(chrome * 0.85 + rimGlow * 0.55, 0.0, 1.0));
        alpha = fillAlpha * (0.70 + 0.30 * chrome);
    } else if (shaderMode == 10) {
        // Mode 10: Blur (neon aura & frosted glass)
        float pulse = 0.85 + 0.15 * sin(time * 3.5);
        vec3 neonColor = mix(uColor.rgb, vec3(1.0), 0.5);
        color = mix(uColor.rgb * 0.35, neonColor, rimGlow * pulse);
        alpha = fillAlpha * (0.35 + 0.65 * rimGlow * pulse);
    }

    if (alpha <= 0.001) {
        discard;
    }

    vec4 fill = vec4(color, clamp(mask * alpha, 0.0, 1.0));
    fragColor = over(fill, vec4(outColor, outAlpha));
}
