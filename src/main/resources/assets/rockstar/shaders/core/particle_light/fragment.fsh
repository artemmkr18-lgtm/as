#version 150

layout(std140) uniform RockstarData {
    mat4 InvViewProj;
    float Ambient;
    vec4 L0Pos;
    vec4 L0Col;
    vec4 L1Pos;
    vec4 L1Col;
    vec4 L2Pos;
    vec4 L2Col;
    vec4 L3Pos;
    vec4 L3Col;
    vec4 L4Pos;
    vec4 L4Col;
    vec4 L5Pos;
    vec4 L5Col;
    vec4 L6Pos;
    vec4 L6Col;
    vec4 L7Pos;
    vec4 L7Col;
    vec4 L8Pos;
    vec4 L8Col;
    vec4 L9Pos;
    vec4 L9Col;
    vec4 L10Pos;
    vec4 L10Col;
    vec4 L11Pos;
    vec4 L11Col;
    vec4 L12Pos;
    vec4 L12Col;
    vec4 L13Pos;
    vec4 L13Col;
    vec4 L14Pos;
    vec4 L14Col;
    vec4 L15Pos;
    vec4 L15Col;
    vec4 L16Pos;
    vec4 L16Col;
    vec4 L17Pos;
    vec4 L17Col;
    vec4 L18Pos;
    vec4 L18Col;
    vec4 L19Pos;
    vec4 L19Col;
    vec4 L20Pos;
    vec4 L20Col;
    vec4 L21Pos;
    vec4 L21Col;
    vec4 L22Pos;
    vec4 L22Col;
    vec4 L23Pos;
    vec4 L23Col;
    vec4 L24Pos;
    vec4 L24Col;
    vec4 L25Pos;
    vec4 L25Col;
    vec4 L26Pos;
    vec4 L26Col;
    vec4 L27Pos;
    vec4 L27Col;
    vec4 L28Pos;
    vec4 L28Col;
    vec4 L29Pos;
    vec4 L29Col;
    vec4 L30Pos;
    vec4 L30Col;
    vec4 L31Pos;
    vec4 L31Col;
    vec4 L32Pos;
    vec4 L32Col;
    vec4 L33Pos;
    vec4 L33Col;
    vec4 L34Pos;
    vec4 L34Col;
    vec4 L35Pos;
    vec4 L35Col;
    vec4 L36Pos;
    vec4 L36Col;
    vec4 L37Pos;
    vec4 L37Col;
    vec4 L38Pos;
    vec4 L38Col;
    vec4 L39Pos;
    vec4 L39Col;
    vec4 L40Pos;
    vec4 L40Col;
    vec4 L41Pos;
    vec4 L41Col;
    vec4 L42Pos;
    vec4 L42Col;
    vec4 L43Pos;
    vec4 L43Col;
    vec4 L44Pos;
    vec4 L44Col;
    vec4 L45Pos;
    vec4 L45Col;
    vec4 L46Pos;
    vec4 L46Col;
    vec4 L47Pos;
    vec4 L47Col;
} RockstarUniforms;

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0; // цвет сцены
uniform sampler2D Sampler1; // глубина сцены



































































out vec4 OutColor;

// Скорость выхода на насыщение. Свет ДОМНОЖАЕТ картинку, а не кладётся сверху: только так
// проявляется текстура блока — иначе получается цветная плёнка.
const float GAIN = 2.0;
// Во столько раз ярче исходного пикселя может стать полностью засвеченная поверхность.
const float BOOST = 1.7;
// Небольшая добавка сверху, чтобы источник читался и на совсем чёрной поверхности.
const float LIFT = 0.12;

vec3 worldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 pos = RockstarUniforms.InvViewProj * clip;
    return pos.xyz / pos.w;
}

/**
 * Нормаль грани из буфера глубины. Наивный cross(dFdx, dFdy) врёт на каждом силуэте: соседний
 * пиксель там принадлежит другой поверхности, и нормаль уезжает поперёк экрана — по краям
 * блоков это давало дрожащие светлые каёмки. Поэтому по каждой оси берём того соседа, у
 * которого скачок глубины меньше: он почти наверняка лежит на той же грани.
 */
vec3 faceNormal(vec2 uv, float depth, vec3 world) {
    vec2 texel = 1.0 / vec2(textureSize(Sampler1, 0));

    vec2 uvL = uv - vec2(texel.x, 0.0);
    vec2 uvR = uv + vec2(texel.x, 0.0);
    vec2 uvD = uv - vec2(0.0, texel.y);
    vec2 uvU = uv + vec2(0.0, texel.y);

    float dL = texture(Sampler1, uvL).r;
    float dR = texture(Sampler1, uvR).r;
    float dD = texture(Sampler1, uvD).r;
    float dU = texture(Sampler1, uvU).r;

    vec3 ddx = abs(dL - depth) < abs(dR - depth)
        ? world - worldFromDepth(uvL, dL)
        : worldFromDepth(uvR, dR) - world;

    vec3 ddy = abs(dD - depth) < abs(dU - depth)
        ? world - worldFromDepth(uvD, dD)
        : worldFromDepth(uvU, dU) - world;

    vec3 n = cross(ddx, ddy);
    float len = length(n);
    // Вырожденный случай (плоскость строго вдоль луча, дыры в глубине) — смотрим в камеру.
    if (len < 1e-8) return normalize(-world);

    n /= len;
    // Камера в начале координат, поэтому разворачиваем нормаль к ней: знак векторного
    // произведения зависит от обхода.
    return dot(n, -world) < 0.0 ? -n : n;
}

// Точечный источник как факел: квадратичное затухание по расстоянию, умноженное на N·L —
// грань, отвёрнутая от партикла, остаётся тёмной, обращённая к нему светится.
vec3 contribution(vec3 world, vec3 normal, vec4 posRad, vec4 col) {
    float radius = posRad.w;
    if (radius <= 0.0001 || col.a <= 0.0001) return vec3(0.0);

    vec3 delta = posRad.xyz - world;
    float d = length(delta);
    if (d >= radius) return vec3(0.0);

    // Квадрат тут врал вдвое: при радиусе 10 на пяти блоках оставалось 25 %, и радиус читался
    // как «половина от выставленного». smoothstep держит середину на 0.5 и всё так же приходит
    // в ноль с нулевой производной — на границе сферы канта не видно.
    float falloff = smoothstep(0.0, 1.0, 1.0 - d / radius);

    float ndotl = d > 0.0001 ? dot(normal, delta / d) : 1.0;
    // Wrapped diffuse вместо mix(RockstarUniforms.Ambient, 1, ndotl): у mix даже полностью отвёрнутая грань
    // получала долю RockstarUniforms.Ambient, из-за чего источник за стеной подсвечивал её лицевую сторону.
    // Здесь RockstarUniforms.Ambient лишь «заворачивает» терминатор за край — при ndotl = -1 вклад строго ноль.
    float lambert = clamp((ndotl + RockstarUniforms.Ambient) / (1.0 + RockstarUniforms.Ambient), 0.0, 1.0);

    return col.rgb * (col.a * falloff * lambert);
}

void main() {
    vec4 src = texture(Sampler0, TexCoord);

    float depth = texture(Sampler1, TexCoord).r;
    if (depth >= 1.0) {
        // Небо: отражать свет нечему.
        OutColor = src;
        return;
    }

    vec3 world = worldFromDepth(TexCoord, depth);
    vec3 normal = faceNormal(TexCoord, depth, world);

    vec3 sum = vec3(0.0);
    sum += contribution(world, normal, RockstarUniforms.L0Pos, RockstarUniforms.L0Col);
    sum += contribution(world, normal, RockstarUniforms.L1Pos, RockstarUniforms.L1Col);
    sum += contribution(world, normal, RockstarUniforms.L2Pos, RockstarUniforms.L2Col);
    sum += contribution(world, normal, RockstarUniforms.L3Pos, RockstarUniforms.L3Col);
    sum += contribution(world, normal, RockstarUniforms.L4Pos, RockstarUniforms.L4Col);
    sum += contribution(world, normal, RockstarUniforms.L5Pos, RockstarUniforms.L5Col);
    sum += contribution(world, normal, RockstarUniforms.L6Pos, RockstarUniforms.L6Col);
    sum += contribution(world, normal, RockstarUniforms.L7Pos, RockstarUniforms.L7Col);
    sum += contribution(world, normal, RockstarUniforms.L8Pos, RockstarUniforms.L8Col);
    sum += contribution(world, normal, RockstarUniforms.L9Pos, RockstarUniforms.L9Col);
    sum += contribution(world, normal, RockstarUniforms.L10Pos, RockstarUniforms.L10Col);
    sum += contribution(world, normal, RockstarUniforms.L11Pos, RockstarUniforms.L11Col);
    sum += contribution(world, normal, RockstarUniforms.L12Pos, RockstarUniforms.L12Col);
    sum += contribution(world, normal, RockstarUniforms.L13Pos, RockstarUniforms.L13Col);
    sum += contribution(world, normal, RockstarUniforms.L14Pos, RockstarUniforms.L14Col);
    sum += contribution(world, normal, RockstarUniforms.L15Pos, RockstarUniforms.L15Col);
    sum += contribution(world, normal, RockstarUniforms.L16Pos, RockstarUniforms.L16Col);
    sum += contribution(world, normal, RockstarUniforms.L17Pos, RockstarUniforms.L17Col);
    sum += contribution(world, normal, RockstarUniforms.L18Pos, RockstarUniforms.L18Col);
    sum += contribution(world, normal, RockstarUniforms.L19Pos, RockstarUniforms.L19Col);
    sum += contribution(world, normal, RockstarUniforms.L20Pos, RockstarUniforms.L20Col);
    sum += contribution(world, normal, RockstarUniforms.L21Pos, RockstarUniforms.L21Col);
    sum += contribution(world, normal, RockstarUniforms.L22Pos, RockstarUniforms.L22Col);
    sum += contribution(world, normal, RockstarUniforms.L23Pos, RockstarUniforms.L23Col);
    sum += contribution(world, normal, RockstarUniforms.L24Pos, RockstarUniforms.L24Col);
    sum += contribution(world, normal, RockstarUniforms.L25Pos, RockstarUniforms.L25Col);
    sum += contribution(world, normal, RockstarUniforms.L26Pos, RockstarUniforms.L26Col);
    sum += contribution(world, normal, RockstarUniforms.L27Pos, RockstarUniforms.L27Col);
    sum += contribution(world, normal, RockstarUniforms.L28Pos, RockstarUniforms.L28Col);
    sum += contribution(world, normal, RockstarUniforms.L29Pos, RockstarUniforms.L29Col);
    sum += contribution(world, normal, RockstarUniforms.L30Pos, RockstarUniforms.L30Col);
    sum += contribution(world, normal, RockstarUniforms.L31Pos, RockstarUniforms.L31Col);
    sum += contribution(world, normal, RockstarUniforms.L32Pos, RockstarUniforms.L32Col);
    sum += contribution(world, normal, RockstarUniforms.L33Pos, RockstarUniforms.L33Col);
    sum += contribution(world, normal, RockstarUniforms.L34Pos, RockstarUniforms.L34Col);
    sum += contribution(world, normal, RockstarUniforms.L35Pos, RockstarUniforms.L35Col);
    sum += contribution(world, normal, RockstarUniforms.L36Pos, RockstarUniforms.L36Col);
    sum += contribution(world, normal, RockstarUniforms.L37Pos, RockstarUniforms.L37Col);
    sum += contribution(world, normal, RockstarUniforms.L38Pos, RockstarUniforms.L38Col);
    sum += contribution(world, normal, RockstarUniforms.L39Pos, RockstarUniforms.L39Col);
    sum += contribution(world, normal, RockstarUniforms.L40Pos, RockstarUniforms.L40Col);
    sum += contribution(world, normal, RockstarUniforms.L41Pos, RockstarUniforms.L41Col);
    sum += contribution(world, normal, RockstarUniforms.L42Pos, RockstarUniforms.L42Col);
    sum += contribution(world, normal, RockstarUniforms.L43Pos, RockstarUniforms.L43Col);
    sum += contribution(world, normal, RockstarUniforms.L44Pos, RockstarUniforms.L44Col);
    sum += contribution(world, normal, RockstarUniforms.L45Pos, RockstarUniforms.L45Col);
    sum += contribution(world, normal, RockstarUniforms.L46Pos, RockstarUniforms.L46Col);
    sum += contribution(world, normal, RockstarUniforms.L47Pos, RockstarUniforms.L47Col);

    // Вклад источников копится линейно, поэтому в гуще партиклов сумма легко уходила за десятку
    // и сцена выбеливалась в молоко. Экспонента насыщает её: сколько бы источников ни сошлось,
    // засветка упирается в BOOST, а разница между «один факел» и «два» остаётся видимой.
    vec3 lightAmount = vec3(1.0) - exp(-sum * GAIN);

    // Ключевое отличие от «цветного тумана»: основной вклад — множитель к уже отрисованному
    // пикселю. Тёмный булыжник становится ярким булыжником своего цвета, подкрашенным светом,
    // а не однородным пятном.
    vec3 lit = src.rgb * (vec3(1.0) + lightAmount * BOOST) + lightAmount * LIFT;

    OutColor = vec4(clamp(lit, 0.0, 1.0), src.a);
}
