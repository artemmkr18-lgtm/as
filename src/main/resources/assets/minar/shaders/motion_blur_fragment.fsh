#version 150

layout(std140) uniform Uniforms {
    mat4 uProjection;
    vec4 uRect;
    vec4 uScreen;
    vec4 uRadii;
    vec4 uParams; // x: blurAmount (ui px), y: dirX, z: dirY, w: alpha
    vec4 uTint;
    vec4 uZ_Padding;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec2 pixelCoord;
in vec2 rectSize;
in vec4 cornerRadii;

out vec4 fragColor;

const int SAMPLES = 12;

float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.y  : r.x;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    float blurAmount = uParams.x;
    vec2 direction = vec2(uParams.y, uParams.z);
    float alpha = uParams.w;

    vec2 center = rectSize * 0.5;
    float dist = sdRoundedBox(pixelCoord - center, center - 1.0, cornerRadii);
    float rAlpha = 1.0 - smoothstep(0.0, 1.0, dist);

    if (rAlpha <= 0.0 || alpha <= 0.0) {
        discard;
    }

    if (blurAmount < 0.35 || length(direction) < 0.001) {
        fragColor = vec4(texture(Sampler0, texCoord).rgb, alpha * rAlpha);
        return;
    }

    vec2 dir = normalize(direction);
    vec2 stepUV = vec2(
        dir.x * blurAmount / max(uScreen.x, 1.0),
        dir.y * blurAmount / max(uScreen.y, 1.0)
    );

    vec3 color = vec3(0.0);
    float totalWeight = 0.0;
    for (int i = -SAMPLES; i <= SAMPLES; i++) {
        float t = float(i) / float(SAMPLES);
        float weight = 1.0 - abs(t);
        weight *= weight;
        color += texture(Sampler0, texCoord + stepUV * t).rgb * weight;
        totalWeight += weight;
    }
    color /= max(totalWeight, 0.0001);

    fragColor = vec4(color, alpha * rAlpha);
}
