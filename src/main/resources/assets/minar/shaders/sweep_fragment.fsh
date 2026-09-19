#version 410 core

layout(std140) uniform Uniforms {
    mat4 uProjection;
    vec4 uRect;
    vec4 uRadii; // x: top-right, y: bottom-right, z: top-left, w: bottom-left
    float uZ;
    float uProgress;
    float uAngle;
    float uOpacity;
    vec4 _pad;
};

in vec2 vUV;
in vec2 vSize;

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.y  : r.x;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 pixelPos = vUV * vSize;
    vec2 center = vSize * 0.5;

    float dist = sdRoundedBox(pixelPos - center, center, uRadii);
    float edge = fwidth(dist);
    float alpha = 1.0 - smoothstep(-edge, edge, dist);

    if (alpha <= 0.0) discard;

    float s = sin(uAngle);
    float c = cos(uAngle);
    
    // Project point onto the normal of the line
    float projectedDist = pixelPos.x * c + pixelPos.y * s;
    
    float p1 = 0.0;
    float p2 = vSize.x * c;
    float p3 = vSize.y * s;
    float p4 = vSize.x * c + vSize.y * s;
    
    float minP = min(min(p1, p2), min(p3, p4));
    float maxP = max(max(p1, p2), max(p3, p4));
    
    float thickness = max(30.0, min(vSize.x, vSize.y) * 0.3);
    float currentLinePos = minP - thickness + (maxP - minP + thickness * 2.0) * uProgress;
    
    float lineDist = abs(projectedDist - currentLinePos);
    
    float lineAlpha = max(0.0, 1.0 - lineDist / thickness);
    lineAlpha = lineAlpha * lineAlpha * (3.0 - 2.0 * lineAlpha);

    if (lineAlpha <= 0.0) discard;

    fragColor = vec4(1.0, 1.0, 1.0, lineAlpha * 0.3 * uOpacity * alpha);
}
