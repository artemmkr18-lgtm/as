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
    vec2 Smoothness;
    float Thickness;
} RockstarUniforms;

#moj_import <rockstar:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec4 FragColor;





out vec4 OutColor;

void main() {
    vec2 center = RockstarUniforms.Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * RockstarUniforms.Size), center - 1.0, RockstarUniforms.Radius);

    float inner = sdfAA(distance, RockstarUniforms.Smoothness.x);
    float outer = sdfAA(distance, RockstarUniforms.Smoothness.y);

    float alpha = smoothstep(1.0 - RockstarUniforms.Thickness - inner - outer,
        1.0 - RockstarUniforms.Thickness - outer, distance); // internal edge
    alpha *= 1.0 - smoothstep(1.0 - outer, 1.0, distance); // external edge

    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * alpha);

    if (finalColor.a == 0.0) { // alpha test
        discard;
    }

    OutColor = finalColor * ColorModulator;
}