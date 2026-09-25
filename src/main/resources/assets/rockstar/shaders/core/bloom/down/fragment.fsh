#version 150

layout(std140) uniform RockstarData {
    vec2 Resolution;
    float Offset;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;


out vec4 OutColor;

vec4 addmix(vec2 offset) {
    vec4 color = texture(Sampler0, TexCoord + offset);
    color = vec4(color.rgb, color.a < 0.01 ? 0.0 : 1.0);
    return color;
}

void main() {
    vec2 off = vec2((RockstarUniforms.Offset + 0.5) * 1.5 * RockstarUniforms.Resolution);

    vec4 color = texture(Sampler0, TexCoord);

    color += addmix(off);
    color += addmix(-off);
    color += addmix(vec2(off.x, -off.y));
    color += addmix(vec2(-off.x, off.y));

    OutColor = color / 4.0;
}