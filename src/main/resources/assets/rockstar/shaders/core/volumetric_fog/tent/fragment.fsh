#version 150

layout(std140) uniform RockstarData {
    vec2 TexelSize;
} RockstarUniforms;

in vec2 TexCoord;

uniform sampler2D Sampler0;

out vec4 OutColor;

void main() {
    vec2 offset = RockstarUniforms.TexelSize * 0.5;

    OutColor = 0.25 * (texture(Sampler0, TexCoord + vec2( offset.x,  offset.y))
                     + texture(Sampler0, TexCoord + vec2(-offset.x,  offset.y))
                     + texture(Sampler0, TexCoord + vec2( offset.x, -offset.y))
                     + texture(Sampler0, TexCoord + vec2(-offset.x, -offset.y)));
}
