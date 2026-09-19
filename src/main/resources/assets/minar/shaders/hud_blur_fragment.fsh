#version 150

layout(std140) uniform Uniforms {
    mat4 uProjection;
    vec4 uRect;
    vec4 uScreen;
    vec4 uRadii;
    vec4 uParams; // x: blurRadius, y: smoothness, z: alpha, w: tintAlpha
    vec4 uTint;
    vec4 uZ_Padding;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec2 pixelCoord;
in vec2 rectSize;
in vec4 cornerRadii;

out vec4 fragColor;

const float DPI = 6.28318530718;
const float STEP = DPI / 16.0;

float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.y  : r.x;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    float blurRadius = uParams.x;
    float smoothness = uParams.y;
    float alpha = uParams.z;
    float tintAlpha = uParams.w;

    vec2 multiplier = blurRadius / vec2(textureSize(Sampler0, 0));

    vec3 average = texture(Sampler0, texCoord).rgb;
    float samples = 128.0;
    float noise = fract(sin(dot(pixelCoord, vec2(12.9898, 78.233))) * 43758.5453);
    float startTheta = noise * DPI;
    
    for (float i = 1.0; i <= samples; i++) {
        float r = sqrt(i / samples);
        float theta = startTheta + i * 2.39996323; // Golden angle in radians
        vec2 offset = vec2(cos(theta), sin(theta)) * r * multiplier;
        average += texture(Sampler0, texCoord + offset).rgb;
    }
    average /= (samples + 1.0);

    vec2 center = rectSize * 0.5;
    float dist = sdRoundedBox(pixelCoord - center, center - 1.0, cornerRadii);
    float rAlpha = 1.0 - smoothstep(1.0 - smoothness, 1.0, dist);
    
    if (rAlpha <= 0.0 || alpha <= 0.0) {
        discard;
    }

    vec3 mixedColor = mix(average, uTint.rgb, tintAlpha);
    fragColor = vec4(mixedColor, alpha * rAlpha);
}
