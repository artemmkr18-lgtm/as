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
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord; // normalized fragment.fsh coord relative to the primitive
in vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D Sampler0;



out vec4 OutColor;

void main() {
    vec2 center = RockstarUniforms.Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius);

    float alpha = 1.0 - smoothstep(1.0 - sdfAA(distance, RockstarUniforms.Smoothness), 1.0, distance);
    vec4 whiteColor = vec4(1.0, 1.0, 1.0, alpha); // white color - no color modulation applied by default

    vec4 finalColor = whiteColor * texture(Sampler0, TexCoord) * FragColor;

    if (finalColor.a == 0.0) { // alpha test
        discard;
    }

    OutColor = finalColor * ColorModulator;
}