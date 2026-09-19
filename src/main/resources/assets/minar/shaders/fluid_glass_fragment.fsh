#version 150

layout(std140) uniform Uniforms {
    mat4 uProjection;
    vec4 uRect;
    vec4 uScreen;
    vec4 uRadii;
    vec4 uParams;
    vec4 uTint;
    vec4 uZ_Padding;
};

in vec2 texCoord;
in vec2 pixelCoord;
in vec2 rectSize;
in vec4 cornerRadii;

out vec4 fragColor;

uniform sampler2D Sampler0;

vec3 getTextureColorAt(vec2 coord) {
    vec2 sampleUv = vec2(coord.x / uScreen.x, 1.0 - coord.y / uScreen.y);
    return texture(Sampler0, clamp(sampleUv, vec2(0.0), vec2(1.0))).rgb;
}

float sdf(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + vec2(r);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

vec3 getBlurredColor(vec2 coord, float blurRadius) {
    vec3 color = vec3(0.0);
    float totalWeight = 0.0;

    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * blurRadius;
            float weight = exp(-0.5 * (float(x * x + y * y)) / 2.0);

            color += getTextureColorAt(coord + offset) * weight;
            totalWeight += weight;
        }
    }

    return color / totalWeight;
}

void main() {
    vec2 glassSize = rectSize;
    vec2 glassCenter = uRect.xy + glassSize * 0.5;
    vec2 fragCoord = uRect.xy + pixelCoord;
    vec2 glassCoord = fragCoord - glassCenter;

    float radius = min(min(cornerRadii.x, cornerRadii.y), min(cornerRadii.z, cornerRadii.w));
    float size = max(1.0, min(glassSize.x, glassSize.y));
    float dist = sdf(glassCoord, glassSize * 0.5, radius);
    float inversedSDF = -dist / size;
    float alphaMask = 1.0 - smoothstep(-max(fwidth(dist), 0.6), max(fwidth(dist), 0.6), dist);

    if (alphaMask < 0.01) {
        discard;
    }

    vec2 normalizedGlassCoord = glassCoord / max(length(glassCoord), 0.0001);
    float distFromCenter = 1.0 - clamp(inversedSDF / 0.3, 0.0, 1.0);
    float distortion = 1.0 - sqrt(max(0.0, 1.0 - pow(distFromCenter, 2.0)));
    vec2 offset = distortion * normalizedGlassCoord * glassSize * 0.5 * uParams.y;
    vec2 glassColorCoord = fragCoord - offset;

    float blurIntensity = 1.2 + uParams.y * 0.55;
    float blurRadius = blurIntensity * (1.0 - distFromCenter * 0.5);

    float edge = smoothstep(0.0, 0.02, inversedSDF);
    vec2 shift = normalizedGlassCoord * edge * 3.0;
    vec3 glassColor = vec3(
        getBlurredColor(glassColorCoord - shift, blurRadius).r,
        getBlurredColor(glassColorCoord, blurRadius).g,
        getBlurredColor(glassColorCoord + shift, blurRadius).b
    );

    glassColor *= vec3(0.90);
    glassColor = mix(glassColor, uTint.rgb, 0.12 * uParams.w);

    float edgeLight = smoothstep(0.02, 0.0, inversedSDF) * 0.24;
    float topLight = smoothstep(1.0, 0.0, pixelCoord.y / max(glassSize.y, 1.0)) * 0.13;
    glassColor += vec3(edgeLight + topLight);

    fragColor = vec4(glassColor, uParams.z * alphaMask);
}
