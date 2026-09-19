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

out vec2 vPixelPos;

void main() {
    float cx = uParams.x;
    float cy = uParams.y;
    float radius = uParams.z;
    float z = uParams.w;

    float expand = radius + uThickness;

    vec2 positions[6];
    positions[0] = vec2(0.0, 0.0);
    positions[1] = vec2(1.0, 0.0);
    positions[2] = vec2(1.0, 1.0);
    positions[3] = vec2(0.0, 0.0);
    positions[4] = vec2(1.0, 1.0);
    positions[5] = vec2(0.0, 1.0);

    vec2 pos = positions[gl_VertexID];
    vec2 pixelPos = vec2(cx - expand, cy - expand) + pos * expand * 2.0;

    vPixelPos = pixelPos;

    gl_Position = uProjection * vec4(pixelPos, z, 1.0);
}
