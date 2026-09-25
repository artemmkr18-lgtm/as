#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform RockstarData {
    float Range;
    float Thickness;
    float Smoothness;
    int Outline;
    float OutlineThickness;
    vec4 OutlineColor;
    int EnableFadeout;
    float FadeoutStart;
    float FadeoutEnd;
    float FadeinStart;
    float FadeinEnd;
    float MaxWidth;
    float TextPosX;
} RockstarUniforms;

in vec2 TexCoord;
in vec4 FragColor;
in vec2 GlobalPos;

uniform sampler2D Sampler0;











out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + RockstarUniforms.Thickness;
    vec2 h = vec2(dFdx(TexCoord.x), dFdy(TexCoord.y)) * textureSize(Sampler0, 0);
    float pixels = RockstarUniforms.Range * inversesqrt(h.x * h.x + h.y * h.y);
    float alpha = smoothstep(-RockstarUniforms.Smoothness, RockstarUniforms.Smoothness, dist * pixels);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if (RockstarUniforms.Outline != 0) {
        color = mix(RockstarUniforms.OutlineColor, FragColor, alpha);
        color.a *= smoothstep(-RockstarUniforms.Smoothness, RockstarUniforms.Smoothness, (dist + RockstarUniforms.OutlineThickness) * pixels);
    }

    // Apply horizontal fade (right fade-out + optional left fade-in)
    if (RockstarUniforms.EnableFadeout != 0) {
        float fadeAlpha = 1.0;
        // Вычисляем позицию относительно начала текста
        float relativeX = GlobalPos.x - RockstarUniforms.TextPosX;
        // Нормализуем относительно ширины текста
        float normalizedX = relativeX / RockstarUniforms.MaxWidth;
        if (normalizedX > RockstarUniforms.FadeoutStart) {
            fadeAlpha *= 1.0 - smoothstep(RockstarUniforms.FadeoutStart, RockstarUniforms.FadeoutEnd, normalizedX);
        }
        if (RockstarUniforms.FadeinEnd > 0.0 && normalizedX < RockstarUniforms.FadeinEnd) {
            fadeAlpha *= smoothstep(RockstarUniforms.FadeinStart, RockstarUniforms.FadeinEnd, normalizedX);
        }
        color.a *= fadeAlpha;
    }

    OutColor = color * ColorModulator;
}
