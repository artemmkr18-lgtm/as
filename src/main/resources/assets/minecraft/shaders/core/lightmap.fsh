#version 330

layout(std140) uniform LightmapInfo {
    float AmbientLightFactor;
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float DarkenWorldFactor;
    float BrightnessFactor;
    vec3 SkyLightColor;
    vec3 AmbientColor;
} lightmapInfo;

in vec2 texCoord;

out vec4 fragColor;

float get_brightness(float level) {
    return level / (4.0 - 3.0 * level);
}

vec3 notGamma(vec3 color) {
    float maxComponent = max(max(color.x, color.y), color.z);
    float maxInverted = 1.0f - maxComponent;
    float maxScaled = 1.0f - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / maxComponent);
}

void main() {
    bool recolor = lightmapInfo.AmbientColor.r < 0.0;
    vec3 selectedColor = recolor
        ? vec3(-lightmapInfo.AmbientColor.r - 0.001, lightmapInfo.AmbientColor.gb)
        : vec3(1.0);
    vec3 ambientColor = recolor ? vec3(1.0) : lightmapInfo.AmbientColor;
    float block_level = floor(texCoord.x * 16);
    float sky_level = floor(texCoord.y * 16);
    bool recolorPixel = recolor && (block_level < 15.0 || sky_level < 15.0);

    float block_brightness = get_brightness(block_level / 15) * lightmapInfo.BlockFactor;
    float sky_brightness = get_brightness(sky_level / 15) * lightmapInfo.SkyFactor;

    vec3 color = vec3(
        block_brightness,
        block_brightness * ((block_brightness * 0.6 + 0.4) * 0.6 + 0.4),
        block_brightness * (block_brightness * block_brightness * 0.6 + 0.4)
    );

    color = mix(color, ambientColor, lightmapInfo.AmbientLightFactor);
    color += lightmapInfo.SkyLightColor * sky_brightness;
    color = mix(color, vec3(0.75), 0.04);

    if (lightmapInfo.AmbientLightFactor == 0.0f) {
        vec3 darkened_color = color * vec3(0.7, 0.6, 0.6);
        color = mix(color, darkened_color, lightmapInfo.DarkenWorldFactor);
    }

    if (lightmapInfo.NightVisionFactor > 0.0) {
        float max_component = max(color.r, max(color.g, color.b));
        if (max_component < 1.0) {
            vec3 bright_color = color / max_component;
            color = mix(color, bright_color, lightmapInfo.NightVisionFactor);
        }
    }

    if (lightmapInfo.AmbientLightFactor == 0.0f) {
        color = color - vec3(lightmapInfo.DarknessScale);
    }

    color = clamp(color, 0.0, 1.0);

    vec3 gammaColor = notGamma(color);
    color = mix(color, gammaColor, lightmapInfo.BrightnessFactor);
    color = mix(color, vec3(0.75), 0.04);

    if (recolorPixel) {
        color *= selectedColor;
    }

    fragColor = vec4(color, 1.0);
}
