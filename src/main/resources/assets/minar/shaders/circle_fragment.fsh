#version 410 core

layout(std140) uniform Uniforms {
    mat4 uProjection;
    vec4 uParams;
    float uThickness;
    float uStartAngle;
    float uProgress;
    float _pad2;
    vec4 uColor;
};

in vec2 vPixelPos;

out vec4 fragColor;

void main() {
    float cx = uParams.x;
    float cy = uParams.y;
    float radius = uParams.z;

    vec2 center = vec2(cx, cy);
    float dist = length(vPixelPos - center) - radius;

    float ringDist = abs(dist) - uThickness * 0.5;

    float edge = fwidth(dist);
    float alpha = 1.0 - smoothstep(-edge, edge, ringDist);

    if (alpha <= 0.0) discard;
    if (uProgress < 0.999) {
        vec2 dir = vPixelPos - center;
        float angle = degrees(atan(dir.y, dir.x));
        float normalized = mod(angle - uStartAngle + 360.0, 360.0);
        if (normalized > uProgress * 360.0) discard;
    }

    fragColor = vec4(uColor.rgb, uColor.a * alpha);
}
