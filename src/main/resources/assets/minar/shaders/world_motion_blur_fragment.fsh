#version 330

layout(std140) uniform Uniforms {
    mat4 uInvProjection;     // inverse of current projection matrix (64 bytes)
    mat4 uProjection;        // current projection matrix (64 bytes)
    mat4 uViewRot;           // current camera rotation matrix (view -> world) (64 bytes)
    mat4 uPrevProjection;    // previous projection matrix (64 bytes)
    mat4 uPrevViewRot;       // previous camera rotation matrix (view -> world) (64 bytes)
    vec4 uCameraPos;         // xyz: camera pos in world, w: fov (16 bytes)
    vec4 uPrevCameraPos;     // xyz: prev camera pos in world, w: prev fov (16 bytes)
    vec4 uScreen;            // xy: screen resolution (px), zw: inverse resolution (16 bytes)
    vec4 uParams;            // x: blendFactor, y: samples, z: algorithm (0 = backwards, 1 = centered), w: maxVelocity (16 bytes)
};

uniform sampler2D Sampler0; // Color buffer
uniform sampler2D Sampler1; // Depth buffer

in vec2 vUV;
out vec4 fragColor;

// Interleaved Gradient Noise for artifact-free temporal dithering across blur steps
float getDither(vec2 coord) {
    return fract(52.9829189 * fract(dot(coord, vec2(0.06711056, 0.00583715))));
}

vec3 getViewPosFromRawDepth(vec2 uv, float d) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, d * 2.0 - 1.0, 1.0);
    vec4 v = uInvProjection * clip;
    return v.xyz / max(abs(v.w), 0.00001);
}

void main() {
    vec4 originalColor = texture(Sampler0, vUV);

    float blendFactor = uParams.x;
    if (blendFactor <= 0.001) {
        fragColor = originalColor;
        return;
    }

    float depthVal = texture(Sampler1, vUV).r;
    bool isSky = (depthVal >= 0.99999);

    // Reconstruct current view-space position
    // For sky, we treat depth as 1.0 to get a far direction
    vec3 viewPos = getViewPosFromRawDepth(vUV, isSky ? 1.0 : depthVal);

    // Transform view-space vector to world space using camera rotation
    vec3 worldOffset = (uViewRot * vec4(viewPos, 0.0)).xyz;

    // Relative offset to previous camera
    // If it's sky, camera translation should not affect sky blur (sky is at infinity)
    vec3 camDelta = isSky ? vec3(0.0) : (uCameraPos.xyz - uPrevCameraPos.xyz);
    vec3 prevWorldOffset = worldOffset + camDelta;

    // Transform from world to previous view space using transpose of previous rotation
    vec3 prevViewPos = (transpose(uPrevViewRot) * vec4(prevWorldOffset, 0.0)).xyz;

    // Project using previous projection matrix
    vec4 prevClip = uPrevProjection * vec4(prevViewPos, 1.0);
    if (abs(prevClip.w) <= 0.0001) {
        fragColor = originalColor;
        return;
    }

    vec2 prevUV = (prevClip.xy / prevClip.w) * 0.5 + 0.5;

    // Velocity in UV space
    vec2 velocity = (vUV - prevUV) * blendFactor;

    // Clamp velocity to avoid massive streaks on camera teleport / huge jumps
    float maxVel = max(uParams.w, 0.25);
    float velLen = length(velocity);
    if (velLen > maxVel && velLen > 0.00001) {
        velocity = (velocity / velLen) * maxVel;
    }

    // If velocity is negligible (< 0.2 px), don't blur
    vec2 velPx = velocity * uScreen.xy;
    if (dot(velPx, velPx) < 0.04) {
        fragColor = originalColor;
        return;
    }

    int sampleCount = int(clamp(uParams.y, 4.0, 32.0));
    float isCentered = uParams.z;
    float dither = getDither(gl_FragCoord.xy);

    vec3 accumColor = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < sampleCount; i++) {
        float stepFraction;
        float weight;
        if (isCentered > 0.5) {
            // Centered algorithm: distribution centered around current pixel
            stepFraction = ((float(i) + dither) / float(sampleCount)) - 0.5;
            weight = 1.0 - abs(stepFraction * 2.0);
        } else {
            // Backwards algorithm: samples from current pixel backwards along velocity
            stepFraction = -((float(i) + dither) / float(sampleCount));
            weight = 1.0 - abs(stepFraction);
        }

        vec2 sampleUV = clamp(vUV + velocity * stepFraction, vec2(0.0001), vec2(0.9999));
        weight = max(weight * weight, 0.001);

        accumColor += texture(Sampler0, sampleUV).rgb * weight;
        totalWeight += weight;
    }

    vec3 finalColor = accumColor / max(totalWeight, 0.0001);
    fragColor = vec4(finalColor, 1.0);
}
