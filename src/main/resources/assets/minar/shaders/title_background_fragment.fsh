#version 330

layout(std140) uniform Uniforms {
    vec4 uScreen;
    vec4 uTime;
};

in vec2 vUV;
out vec4 fragColor;

const vec3 mixColor1 = vec3(0.8, 0.35, 0.12);
const vec3 mixColor2 = vec3(0.3, 0.75, 0.69);

#define NUM_OCTAVES 5

float random(in vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    
    float a = random(i + vec2(0.0, 0.0));
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    
    vec2 u = f * f * (3.0 - 2.0 * f);
    
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(in vec2 st) {
    float v = 0.0;
    float a = 0.5;
    
    for (int i = 0; i < NUM_OCTAVES; i++) {
        v += a * noise(st);
        st = st * 2.0;
        a *= 0.5;
    }
    
    return v;
}

void main() {
    vec2 st = vUV;
    float iTime = uTime.x;
    
    vec3 color = vec3(0.0);
        
    vec2 q = vec2(0.0);
    q.x = fbm(st + vec2(0.0));
    q.y = fbm(st + vec2(1.0));
    
    vec2 r = vec2(0.0);
    r.x = fbm(st + (4.0 * q) + vec2(1.7, 9.2) + (0.15 * iTime));
    r.y = fbm(st + (4.0 * q) + vec2(8.3, 2.8) + (0.12 * iTime));
    
    color = mix(color, mixColor1, clamp(length(q), 0.0, 1.0));
    color = mix(color, mixColor2, clamp(length(r), 0.0, 1.0));
    
    float f = fbm(st + 4.0 * r);
    
    float coef = (f * f * f + (0.6 * f * f) + (0.5 * f));
    color *= coef;
    
    fragColor = vec4(color, 1.0);
}

