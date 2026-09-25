#version 150

layout(std140) uniform RockstarData {
    mat4 InvViewProj;
    vec3 CamPos;
    float RockstarPadding_CamPos;
    vec3 FogTint;
    float RockstarPadding_FogTint;
    vec2 Flow;
    vec2 FlowPatch;
    float Density;
    float LayerY;
    float Thickness;
    float MaxDistance;
    float Coverage;
    float TileScale;
    float StepWorld;
} RockstarUniforms;

in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;









out vec4 OutColor;

const int MAX_STEPS = 40;
const int MIN_STEPS = 6;
const float SLAB_MARGIN = 4.0;
const float OPTICAL_LIMIT = 4.0;
const float PATCH_FACTOR = 0.25;
const float SLICE_HEIGHT = 18.0;
const vec2 SLICE_OFFSET = vec2(37.0, 61.0);
const vec2 PATCH_SHEAR = vec2(0.55, -0.4);
const float DETAIL_NEAR = 26.0;
const float DETAIL_FAR = 90.0;

float densityAt(vec3 world, float detail) {
    float above = max(world.y - RockstarUniforms.LayerY, 0.0);
    float height = exp(-above / max(RockstarUniforms.Thickness, 0.5));
    if (height < 0.01) return 0.0;

    float slice = world.y / SLICE_HEIGHT;
    float lower = floor(slice);
    float blend = fract(slice);
    blend = blend * blend * (3.0 - 2.0 * blend);

    vec2 ground = world.xz + RockstarUniforms.Flow;
    vec2 field = mix(texture(Sampler1, (ground + SLICE_OFFSET * lower) * RockstarUniforms.TileScale).rg,
                     texture(Sampler1, (ground + SLICE_OFFSET * (lower + 1.0)) * RockstarUniforms.TileScale).rg,
                     blend);

    float shape = smoothstep(RockstarUniforms.Coverage, min(RockstarUniforms.Coverage + 0.32, 1.0), mix(field.r, field.g, detail));
    if (shape <= 0.0) return 0.0;

    float patchNoise = texture(Sampler1, (world.xz + RockstarUniforms.FlowPatch + PATCH_SHEAR * world.y) * (RockstarUniforms.TileScale * PATCH_FACTOR)).b;
    return height * shape * (0.65 + 0.7 * patchNoise);
}

float interleavedGradient(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

vec3 viewFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 pos = RockstarUniforms.InvViewProj * clip;
    return pos.xyz / pos.w;
}

void main() {
    vec3 rayDir = normalize(viewFromDepth(TexCoord, 1.0));

    float depth = texture(Sampler0, TexCoord).r;
    float sceneDistance = RockstarUniforms.MaxDistance;
    if (depth < 1.0) sceneDistance = min(length(viewFromDepth(TexCoord, depth)), RockstarUniforms.MaxDistance);
    if (sceneDistance <= 0.05) { OutColor = vec4(0.0); return; }

    float slabTop = RockstarUniforms.LayerY + RockstarUniforms.Thickness * SLAB_MARGIN;
    float enter = 0.0;
    float exitAt = sceneDistance;

    if (rayDir.y > 1e-4) {
        if (RockstarUniforms.CamPos.y >= slabTop) { OutColor = vec4(0.0); return; }
        exitAt = min(exitAt, (slabTop - RockstarUniforms.CamPos.y) / rayDir.y);
    } else if (rayDir.y < -1e-4) {
        if (RockstarUniforms.CamPos.y >= slabTop) enter = max(enter, (slabTop - RockstarUniforms.CamPos.y) / rayDir.y);
    } else if (RockstarUniforms.CamPos.y >= slabTop) {
        OutColor = vec4(0.0);
        return;
    }

    if (enter >= exitAt) { OutColor = vec4(0.0); return; }

    float span = exitAt - enter;
    int steps = clamp(int(ceil(span / max(RockstarUniforms.StepWorld, 0.5))), MIN_STEPS, MAX_STEPS);
    float stepLength = span / float(steps);
    float jitter = interleavedGradient(gl_FragCoord.xy);
    float limit = OPTICAL_LIMIT / max(RockstarUniforms.Density, 1e-4);

    float optical = 0.0;

    for (int i = 0; i < MAX_STEPS; i++) {
        if (i >= steps) break;

        float at = enter + (float(i) + jitter) * stepLength;
        float density = densityAt(RockstarUniforms.CamPos + rayDir * at, smoothstep(DETAIL_NEAR, DETAIL_FAR, at));

        if (density > 1e-4) {
            optical += density * stepLength * (1.0 - smoothstep(RockstarUniforms.MaxDistance * 0.7, RockstarUniforms.MaxDistance, at));
            if (optical >= limit) break;
        }
    }

    float fade = 1.0 - exp(-optical * RockstarUniforms.Density);
    OutColor = vec4(RockstarUniforms.FogTint * fade, fade);
}
