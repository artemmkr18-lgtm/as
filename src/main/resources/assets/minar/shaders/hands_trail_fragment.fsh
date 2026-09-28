#version 330

// Feedback-проход шлейфа: берёт предыдущий накопленный кадр, сдвигает его по
// вектору движения камеры, затушевляет и добавляет свежую маску рук.
layout(std140) uniform Uniforms {
    vec4 uScreen;   // x = width, y = height, z = time, w = decay (уже скорректировано на dt)
    vec4 uTrail;    // x = shiftX, y = shiftY (в UV), z = подъём, w = жёсткость следа
};

uniform sampler2D Sampler0; // предыдущий кадр шлейфа
uniform sampler2D Sampler1; // свежая маска рук

in vec2 vUV;
out vec4 fragColor;

void main() {
    vec2 uv = vUV;
    vec2 texel = 1.0 / max(uScreen.xy, vec2(1.0));

    vec2 shift = uTrail.xy;
    float decay = clamp(uScreen.w, 0.0, 1.0);
    float rise = uTrail.z;

    // Содержимое сдвигается вверх: чтобы картинка двигалась вверх, читаем ниже.
    vec2 advectUV = uv - shift - vec2(0.0, rise * texel.y);

    vec3 prev = vec3(0.0);
    if (advectUV.x > 0.0 && advectUV.x < 1.0 && advectUV.y > 0.0 && advectUV.y < 1.0) {
        // Небольшое размытие по ходу сдвига — край шлейфа не должен быть резким.
        vec3 c = texture(Sampler0, advectUV).rgb * 0.5;
        c += texture(Sampler0, advectUV + vec2(texel.x, 0.0)).rgb * 0.125;
        c += texture(Sampler0, advectUV - vec2(texel.x, 0.0)).rgb * 0.125;
        c += texture(Sampler0, advectUV + vec2(0.0, texel.y)).rgb * 0.125;
        c += texture(Sampler0, advectUV - vec2(0.0, texel.y)).rgb * 0.125;
        prev = c;
    }

    float mask = texture(Sampler1, uv).r;

    vec3 result = prev * decay + vec3(mask);
    fragColor = vec4(min(result, vec3(1.0)), 1.0);
}
