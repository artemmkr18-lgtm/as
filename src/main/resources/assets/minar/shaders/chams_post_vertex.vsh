#version 330

out vec2 vUV;

void main() {
    vec2 positions[3] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );

    vec2 pos = positions[gl_VertexID];
    vec2 uv = pos * 0.5 + 0.5;
    vUV = uv;
    gl_Position = vec4(pos, 0.0, 1.0);
}
