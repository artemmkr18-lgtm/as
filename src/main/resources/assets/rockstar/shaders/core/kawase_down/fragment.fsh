#version 150

layout(std140) uniform RockstarData {
    vec2 Resolution;
    float Offset;
    float Saturation;
    float TintIntensity;
    vec3 TintColor;
    float RockstarPadding_TintColor;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;




out vec4 OutColor;

vec3 adjustSaturation(vec3 color, float saturation) {
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    return mix(vec3(gray), color, saturation);
}

void main() {
    vec2 texel = RockstarUniforms.Resolution;
    float off = RockstarUniforms.Offset + 0.5;

    vec2 d = texel * off;
    vec2 o1 = vec2( d.x,  d.y);
    vec2 o2 = vec2(-d.x,  d.y);
    vec2 o3 = vec2( d.x, -d.y);
    vec2 o4 = vec2(-d.x, -d.y);

    vec3 sum = texture(Sampler0, TexCoord + o1).rgb;
    sum += texture(Sampler0, TexCoord + o2).rgb;
    sum += texture(Sampler0, TexCoord + o3).rgb;
    sum += texture(Sampler0, TexCoord + o4).rgb;

    vec3 color = sum * 0.25;
    color = adjustSaturation(color, RockstarUniforms.Saturation);
    color = mix(color, RockstarUniforms.TintColor, RockstarUniforms.TintIntensity);

    OutColor = vec4(color, 1.0) * FragColor;
}
