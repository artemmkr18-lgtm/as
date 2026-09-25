#version 330

// Ванильный core/terrain, переложенный на формат вершин предмета.
//
// Ванильный core/entity (на нём призрак жил раньше) домножает цвет на minecraft_mix_light —
// направленный свет сущностей. Для боковой грани это ~0.5, для нижней 0.4, и всё это поверх уже
// запечённого в вершину затенения стороны: призрак выходил заметно темнее блока, который подменял.
// Тут его нет вовсе, а карта света читается ровно как у меша чанка (minecraft_sample_lightmap,
// а не texelFetch: у второго выборка сдвинута на полтексела и блок оказывается светлее).

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
