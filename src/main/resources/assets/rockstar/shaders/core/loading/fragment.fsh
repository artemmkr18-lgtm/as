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
    float Progress;
    float Fade;
    float StripeWidth;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;






out vec4 OutColor;

void main() {
    vec2 center = RockstarUniforms.Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius);
    float alpha = 1.0 - smoothstep(1.0 - sdfAA(distance, RockstarUniforms.Smoothness), 1.0, distance);

    float diag = (FragCoord.x + FragCoord.y) * 0.5;
    float stripeWidth = RockstarUniforms.StripeWidth;
    float fade = RockstarUniforms.Fade;

    float stripeMask = smoothstep(RockstarUniforms.Progress - stripeWidth * 0.5 - fade, RockstarUniforms.Progress - stripeWidth * 0.5, diag)
                     * (1.0 - smoothstep(RockstarUniforms.Progress + stripeWidth * 0.5, RockstarUniforms.Progress + stripeWidth * 0.5 + fade, diag));

    vec4 stripeColor = FragColor * ColorModulator;

    vec4 finalColor = vec4(stripeColor.rgb, stripeColor.a * alpha * stripeMask);

    if (finalColor.a < 0.01) discard;
    OutColor = finalColor;
}
