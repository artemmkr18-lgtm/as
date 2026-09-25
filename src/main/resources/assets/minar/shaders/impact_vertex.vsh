#version 330

layout(std140) uniform Uniforms {
    mat4 ProjMat;
    vec4 Params;
    vec4 Color;
};

in vec3 Position;
in vec2 UV0;

out vec2 vUV;

void main() {
    vUV = UV0;
    gl_Position = ProjMat * vec4(Position, 1.0);
}
