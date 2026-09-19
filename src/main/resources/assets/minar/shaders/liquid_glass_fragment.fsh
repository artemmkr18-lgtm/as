#version 150

layout(std140) uniform Uniforms {
    mat4 uProjection;
    vec4 uRect;
    vec4 uScreen;
    vec4 uRadii;
    vec4 uParams; // x: time, y: strength, z: alpha, w: tint alpha
    vec4 uTint;
    vec4 uZ_Padding;
};

in vec2 texCoord;
in vec2 pixelCoord;
in vec2 rectSize;
in vec4 cornerRadii;

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

float noise(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = noise(i);
    float b = noise(i + vec2(1.0, 0.0));
    float c = noise(i + vec2(0.0, 1.0));
    float d = noise(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 glassSample(vec2 uv, vec2 texel) {
    vec3 color = texture(Sampler0, uv).rgb * 0.28;
    color += texture(Sampler0, uv + texel * vec2(1.5, 0.0)).rgb * 0.12;
    color += texture(Sampler0, uv - texel * vec2(1.5, 0.0)).rgb * 0.12;
    color += texture(Sampler0, uv + texel * vec2(0.0, 1.5)).rgb * 0.12;
    color += texture(Sampler0, uv - texel * vec2(0.0, 1.5)).rgb * 0.12;
    color += texture(Sampler0, uv + texel * vec2(2.0, 2.0)).rgb * 0.06;
    color += texture(Sampler0, uv + texel * vec2(-2.0, 2.0)).rgb * 0.06;
    color += texture(Sampler0, uv + texel * vec2(2.0, -2.0)).rgb * 0.06;
    color += texture(Sampler0, uv + texel * vec2(-2.0, -2.0)).rgb * 0.06;
    return color;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;
    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 radii = min(cornerRadii, vec4(maxRadius));
    float dist = roundedBoxSDF(center, halfSize, radii);
    float smoothing = max(fwidth(dist), 0.6);
    float alphaMask = 1.0 - smoothstep(-smoothing, smoothing, dist);
    if (alphaMask < 0.01) discard;

    float strength = uParams.y;
    float time = uParams.x;
    vec2 uv = pixelCoord / max(rectSize, vec2(1.0));
    vec2 texel = 1.0 / max(uScreen.xy, vec2(1.0));

    float waveA = sin((uv.x * 3.4 + uv.y * 2.2 + time * 0.22) * 6.28318);
    float waveB = sin((uv.x * -2.6 + uv.y * 4.8 - time * 0.18) * 6.28318);
    float liquid = smoothNoise(uv * vec2(4.5, 3.2) + vec2(time * 0.08, -time * 0.05));
    vec2 ripple = vec2(waveA + liquid - 0.5, waveB - liquid + 0.5);

    vec2 lensDir = normalize(center / max(halfSize, vec2(1.0)) + vec2(0.0001));
    float edge = 1.0 - smoothstep(0.0, 11.0, -dist);
    float cornerLens = smoothstep(0.48, 1.05, length(center / max(halfSize, vec2(1.0))));
    vec2 lensBend = lensDir * (edge * 14.0 + cornerLens * 7.0);
    vec2 liquidBend = ripple * (7.5 + edge * 10.0);
    vec2 refractUv = texCoord + (lensBend + liquidBend) * texel * strength;

    vec3 base = glassSample(refractUv, texel * (1.0 + strength * 1.8));
    vec3 ca;
    ca.r = texture(Sampler0, refractUv + (ripple + lensDir) * texel * 2.4).r;
    ca.g = base.g;
    ca.b = texture(Sampler0, refractUv - (ripple + lensDir) * texel * 2.4).b;
    base = mix(base, ca, 0.48 * strength);

    float topGlow = smoothstep(1.0, 0.0, uv.y) * 0.34;
    float leftGlow = smoothstep(1.0, 0.0, uv.x) * 0.16;
    float diagonal = smoothstep(0.08, 0.42, uv.x - uv.y + 0.18) * smoothstep(0.84, 0.38, uv.x - uv.y + 0.18);
    float edgeLight = smoothstep(7.5, 0.0, abs(dist)) * (0.45 + topGlow);
    float innerShadow = smoothstep(0.0, 16.0, -dist) * (1.0 - smoothstep(16.0, 28.0, -dist));

    vec3 tint = uTint.rgb;
    vec3 color = mix(base, tint, 0.22 * uParams.w + 0.08);
    color += vec3(1.0) * (topGlow + leftGlow + diagonal * 0.22 + edgeLight * 0.32);
    color -= vec3(0.10, 0.11, 0.13) * innerShadow;
    color = mix(color, vec3(dot(color, vec3(0.299, 0.587, 0.114))), 0.10);
    color = pow(max(color, vec3(0.0)), vec3(0.92));

    float alpha = (0.62 + edgeLight * 0.28 + topGlow * 0.16) * uParams.z * alphaMask;
    fragColor = vec4(color, alpha);
}
