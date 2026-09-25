#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);

    // отсечку считаем по текстуре, а не по итоговому цвету: альфа у призрака своя и как раз уезжает
    // к нулю у камеры — на общем пороге 0.1 он бы не таял, а гас скачком
    if (texel.a < 0.1) {
        discard;
    }

    vec4 color = texel * vertexColor * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
