#version 150

layout(std140) uniform RockstarData {
    vec4 Roof;
    float RoofSpan;
    mat4 ViewProj;
    vec3 CamPos;
    float RockstarPadding_CamPos;
    vec3 CamRight;
    float RockstarPadding_CamRight;
    vec3 CamUp;
    float RockstarPadding_CamUp;
    vec3 Motion;
    float RockstarPadding_Motion;
    vec3 Cell;
    float RockstarPadding_Cell;
    vec4 Tint;
    float Time;
    float Size;
    float Drift;
    float Flicker;
    float Stretch;
} RockstarUniforms;

in vec3 Position;
in vec2 UV0;
in vec4 Color;









uniform sampler2D Sampler0;


out vec2 Uv;
out vec4 Col;

const float TAU = 6.2831853;
const float EDGE_FADE_START = 0.34;
const float EDGE_FADE_END = 0.5;
const float ROOF_RANGE = 128.0;

void main() {
    float phase = Color.r * TAU;
    float speedVar = 0.55 + Color.g * 0.9;
    float sizeVar = 0.5 + Color.b * 1.1;

    vec3 seeded = Position + RockstarUniforms.Motion * (RockstarUniforms.Time * speedVar);
    seeded.x += sin(RockstarUniforms.Time * 0.71 * speedVar + phase) * RockstarUniforms.Drift;
    seeded.z += cos(RockstarUniforms.Time * 0.53 * speedVar + phase * 1.37) * RockstarUniforms.Drift;
    seeded.y += sin(RockstarUniforms.Time * 0.31 * speedVar + phase * 0.79) * RockstarUniforms.Drift * 0.35;

    vec3 rel = mod(seeded - RockstarUniforms.CamPos + RockstarUniforms.Cell * 0.5, RockstarUniforms.Cell) - RockstarUniforms.Cell * 0.5;

    float size = RockstarUniforms.Size * sizeVar;

    vec3 flow = RockstarUniforms.Motion;
    float flowLen = length(flow);
    vec2 axis = vec2(0.0, 1.0);
    float stretch = 1.0;
    if (flowLen > 1e-4 && RockstarUniforms.Stretch > 1.001) {
        vec3 dir = flow / flowLen;
        vec2 screenDir = vec2(dot(dir, RockstarUniforms.CamRight), dot(dir, RockstarUniforms.CamUp));
        float screenLen = length(screenDir);
        if (screenLen > 1e-3) {
            axis = screenDir / screenLen;
            float across = pow(clamp(screenLen, 0.0, 1.0), 2.5);
            stretch = mix(1.0, RockstarUniforms.Stretch, across);
        }
    }
    vec2 perp = vec2(-axis.y, axis.x);

    vec2 local = UV0 - 0.5;
    vec2 planar = axis * (local.y * size * stretch) + perp * (local.x * size);
    vec3 offset = RockstarUniforms.CamRight * planar.x + RockstarUniforms.CamUp * planar.y;

    gl_Position = RockstarUniforms.ViewProj * vec4(rel + offset, 1.0);

    float twinkle = mix(1.0, 0.3 + 0.7 * (0.5 + 0.5 * sin(RockstarUniforms.Time * 3.3 * speedVar + phase * 2.1)), RockstarUniforms.Flicker);

    vec3 norm = abs(rel) / (RockstarUniforms.Cell * 0.5);
    float edge = 1.0 - smoothstep(EDGE_FADE_START, EDGE_FADE_END, max(max(norm.x, norm.y), norm.z) * 0.5);

    float roofCut = 1.0;
    if (RockstarUniforms.Roof.w > 0.5) {
        vec3 world = RockstarUniforms.CamPos + rel;
        vec2 roofUv = (world.xz - RockstarUniforms.Roof.xy) / RockstarUniforms.RoofSpan;
        if (roofUv.x >= 0.0 && roofUv.x <= 1.0 && roofUv.y >= 0.0 && roofUv.y <= 1.0) {
            float stored = texture(Sampler0, roofUv).r * 255.0;
            float top = stored - ROOF_RANGE + RockstarUniforms.Roof.z;
            roofCut = smoothstep(top - 1.5, top + 0.5, world.y);
        }
    }

    Uv = UV0;
    Col = vec4(RockstarUniforms.Tint.rgb, RockstarUniforms.Tint.a * Color.a * twinkle * edge * roofCut);
}
