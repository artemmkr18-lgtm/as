#version 330

layout(std140) uniform Uniforms {
    mat4 InvViewProj;
    vec4 CircleCount;       // x = count
    vec4 CircleCenter[12];  // xyz: center
    vec4 CircleParams[12];  // x: radius, y: thickness, z: strength
    vec4 CircleColor[12];   // rgba
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 vUV;
out vec4 OutColor;

const float REFRACT_BASE = 0.04;
const float GLOW = 1.0;
const float OCCLUDE_BIAS = 0.05;

void main() {
    vec2 uv = vUV;
    vec3 base = texture(Sampler0, uv).rgb;

    int count = int(CircleCount.x);
    if (count <= 0) {
        OutColor = vec4(base, 1.0);
        return;
    }

    vec2 ndc = uv * 2.0 - 1.0;
    vec4 farClip = vec4(ndc, 1.0, 1.0);
    vec4 farWorld = InvViewProj * farClip;
    farWorld.xyz /= farWorld.w;
    vec3 rayDir = normalize(farWorld.xyz);

    // If ray is parallel to the ground or looking straight up, no ground intersection
    if (abs(rayDir.y) < 1e-4) {
        OutColor = vec4(base, 1.0);
        return;
    }

    float depth = -1.0;
    float sceneDist = -1.0;
    bool depthChecked = false;

    vec2 totalDistortion = vec2(0.0);
    vec3 totalGlow = vec3(0.0);

    for (int i = 0; i < count; i++) {
        vec3 center = CircleCenter[i].xyz;
        float radius = CircleParams[i].x;
        float thickness = CircleParams[i].y;
        float strength = CircleParams[i].z;
        vec4 ringCol = CircleColor[i];

        if (thickness <= 0.0001 || ringCol.a <= 0.001) {
            continue;
        }

        float t = center.y / rayDir.y;
        if (t <= 0.0) {
            continue;
        }

        vec3 hit = rayDir * t;
        vec2 diff = hit.xz - center.xz;
        float distSq = dot(diff, diff);
        float maxR = radius + thickness;
        if (distSq > maxR * maxR) {
            continue;
        }
        if (radius > thickness) {
            float minR = radius - thickness;
            if (distSq < minR * minR) {
                continue;
            }
        }

        float d = sqrt(distSq);
        float ringDist = abs(d - radius);
        if (ringDist >= thickness) {
            continue;
        }

        // Lazy depth buffer test: only execute for pixels that actually hit a circle ring!
        if (!depthChecked) {
            depth = texture(Sampler1, uv).r;
            if (depth < 1.0) {
                vec4 clip = vec4(ndc, depth * 2.0 - 1.0, 1.0);
                vec4 sceneW = InvViewProj * clip;
                sceneW.xyz /= sceneW.w;
                sceneDist = length(sceneW.xyz);
            } else {
                sceneDist = 1e8;
            }
            depthChecked = true;
        }

        if (t > sceneDist + max(OCCLUDE_BIAS, sceneDist * 0.005)) {
            continue;
        }

        float band = 1.0 - (ringDist / thickness);
        float bandSq = band * band;
        float refrProfile = bandSq;
        float glowProfile = bandSq * bandSq;

        float refr = REFRACT_BASE * refrProfile * strength;
        vec2 grad = vec2(dFdx(d), dFdy(d));
        float glen = length(grad);
        if (glen > 1e-6) {
            vec2 dir = grad / glen;
            float signVal = sign(d - radius);
            totalDistortion += dir * refr * signVal;
        }

        totalGlow += ringCol.rgb * (glowProfile * GLOW * ringCol.a);
    }

    // Fast-path: if no circle affected this pixel, return base color without extra texture tap
    if (dot(totalDistortion, totalDistortion) < 1e-8 && dot(totalGlow, totalGlow) < 1e-8) {
        OutColor = vec4(base, 1.0);
        return;
    }

    vec2 finalUv = clamp(uv + totalDistortion, 0.0, 1.0);
    vec3 refracted = texture(Sampler0, finalUv).rgb;

    OutColor = vec4(refracted + totalGlow, 1.0);
}
