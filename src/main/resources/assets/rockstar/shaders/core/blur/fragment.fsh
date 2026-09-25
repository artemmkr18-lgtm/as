#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    vec2 Size;
    vec4 Radius;
    float Smoothness;
    float BlurRadius;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;




out vec4 OutColor;

const float DPI = 6.28318530718;
const float STEP = DPI / 16.0;

void main() {
    vec2 multiplier = RockstarUniforms.BlurRadius / textureSize(Sampler0, 0);

    vec3 average = texture(Sampler0, TexCoord).rgb;
    for (float d = 0.0; d < DPI; d += STEP) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            average += texture(Sampler0, TexCoord + vec2(cos(d), sin(d)) * multiplier * i).rgb;
        }
    }
    average /= 80.0;

    vec2 center = RockstarUniforms.Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius);

    float alpha = 1.0 - smoothstep(1.0 - sdfAA(distance, RockstarUniforms.Smoothness), 1.0, distance);
    vec4 finalColor = vec4(average.rgb, alpha) * FragColor;

    if (finalColor.a == 0.0) { // alpha test
        discard;
    }

    OutColor = finalColor;
}