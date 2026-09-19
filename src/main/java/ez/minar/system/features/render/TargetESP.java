package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.features.combat.AttackAura;
import ez.minar.system.features.combat.TriggerBot;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

@NewFunction(name = "TargetESP", desc = "Ghost glow around aimed target", category = Category.RENDER)
public class TargetESP extends Function {
    private static final Identifier GLOW_TEXTURE = Identifier.of("minar", "images/particles/glow.png");
    private static final Identifier DIAMOND_TEXTURE = Identifier.of("minar", "images/targetesp/target.png");
    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private static final Identifier SKULL_TEXTURE_0 = Identifier.of("minar", "images/targetesp/skull_state_0.png");
    private static final Identifier SKULL_TEXTURE_1 = Identifier.of("minar", "images/targetesp/skull_state_1.png");
    private static final Identifier SKULL_TEXTURE_2 = Identifier.of("minar", "images/targetesp/skull_state_2.png");
    private static final Identifier CAPTURE2_TEXTURE = Identifier.of("minar", "images/targetesp/capture2.png");
    private static final Identifier TARGETPRO_TEXTURE = Identifier.of("minar", "images/targetesp/targetpro.png");

    private static final RenderPipeline CUBES_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cubes_lines"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .build()
    );
    public static final RenderLayer CUBES_LINES = RenderLayer.of("minar_targetesp_cubes_lines",
            RenderSetup.builder(CUBES_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private static final RenderPipeline CUBES_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cubes_fill"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    private static final RenderLayer CUBES_FILL = RenderLayer.of("minar_targetesp_cubes_fill",
            RenderSetup.builder(CUBES_FILL_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline CUBES_LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cubes_lines_nodepth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );
    public static final RenderLayer CUBES_LINES_NO_DEPTH = RenderLayer.of("minar_targetesp_cubes_lines_nodepth",
            RenderSetup.builder(CUBES_LINES_NO_DEPTH_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private static final RenderPipeline CUBES_FILL_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cubes_fill_nodepth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );
    private static final RenderLayer CUBES_FILL_NO_DEPTH = RenderLayer.of("minar_targetesp_cubes_fill_nodepth",
            RenderSetup.builder(CUBES_FILL_NO_DEPTH_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline CYLINDER_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cylinder_fill"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    private static final RenderLayer CYLINDER_FILL = RenderLayer.of("minar_targetesp_cylinder_fill",
            RenderSetup.builder(CYLINDER_FILL_PIPELINE)
                    .translucent()
                    .expectedBufferSize(32768)
                    .build());

    private static final RenderPipeline CYLINDER_FILL_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cylinder_fill_nodepth"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );
    private static final RenderLayer CYLINDER_FILL_NO_DEPTH = RenderLayer.of("minar_targetesp_cylinder_fill_nodepth",
            RenderSetup.builder(CYLINDER_FILL_NO_DEPTH_PIPELINE)
                    .translucent()
                    .expectedBufferSize(32768)
                    .build());

    private static final RenderPipeline CYLINDER_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cylinder_lines"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthWrite(false)
                    .build()
    );
    private static final RenderLayer CYLINDER_LINES = RenderLayer.of("minar_targetesp_cylinder_lines",
            RenderSetup.builder(CYLINDER_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private static final RenderPipeline CYLINDER_LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "targetesp_cylinder_lines_nodepth"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );
    private static final RenderLayer CYLINDER_LINES_NO_DEPTH = RenderLayer.of("minar_targetesp_cylinder_lines_nodepth",
            RenderSetup.builder(CYLINDER_LINES_NO_DEPTH_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    public static TargetESP Instance;
    public final ModeSetting glowMode = new ModeSetting("\u0422\u0438\u043f \u0441\u0432\u0435\u0447\u0435\u043d\u0438\u044f", "Normal", "Glow");

    public final ModeSetting mode = new ModeSetting("Режим", "Призраки", "Ромб", "Cylinder", "Кубы", "Череп", "Кресты");
    public final ModeSetting ghostModel = new ModeSetting("Модель", "Обычные", "Nursultan", "Rock");

    public final BooleanSetting hitColor = new BooleanSetting("Краснеть", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Через стены", true);

    public final NumberSetting radius = new NumberSetting("Радиус", 0.72, 0.35, 1.6, 0.05);
    public final NumberSetting size = new NumberSetting("Размер", 0.48, 0.15, 1.1, 0.05);
    public final NumberSetting speed = new NumberSetting("Скорость", 120.0, 30.0, 300.0, 5.0);
    public final NumberSetting tailLength = new NumberSetting("Длина хвоста", 7.0, 1.0, 20.0, 1.0);

    public final NumberSetting diamondSize = new NumberSetting("Размер ромба", 0.48, 0.15, 1.1, 0.05);
    public final NumberSetting diamondSpeed = new NumberSetting("Скорость ромба", 120.0, 30.0, 300.0, 5.0);
    public final ez.minar.system.settings.impl.ModeSetting diamondTexture = new ez.minar.system.settings.impl.ModeSetting("Текстура ромба", "Target", "Capture2", "TargetPro");

    public final NumberSetting skullSize = new NumberSetting("Размер черепа", 0.6, 0.1, 1.5, 0.05);

    public final NumberSetting chainSize = new NumberSetting("Размер цепей", 0.18, 0.08, 0.6, 0.01);
    public final NumberSetting chainSpeed = new NumberSetting("Скорость цепей", 55.0, 5.0, 120.0, 1.0);
    public final NumberSetting chainRadius = new NumberSetting("Радиус цепей", 1.15, 0.6, 2.5, 0.05);

    public final NumberSetting cubesFillAlpha = new NumberSetting("Прозрачность кубов", 0.5, 0.1, 1.0, 0.05);

    private Entity target;
    private float animation;
    private long lastFrameTime;

    private float skullHitAnimation = 0f;
    private int lastHurtTime = 0;

    public TargetESP() {
        Instance = this;
        addSettings(
                mode, ghostModel, glowMode, hitColor, throughWalls,
                radius, size, speed, tailLength,
                diamondSize, diamondSpeed, diamondTexture,
                skullSize,
                chainSize, chainSpeed, chainRadius,
                cubesFillAlpha
        );
        mode.runnable(this::updateVisibility);
        ghostModel.runnable(this::updateVisibility);
        updateVisibility();
    }

    @Override
    public void onDisable() {
        target = null;
        animation = 0f;
        lastFrameTime = 0L;
        skullHitAnimation = 0f;
        lastHurtTime = 0;
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.render(context);
    }

    private void render(WorldRenderContext context) {
        updateVisibility();
        long now = System.nanoTime();
        float delta = lastFrameTime == 0L ? 0f : Math.min((now - lastFrameTime) / 1_000_000_000f, 0.05f);
        lastFrameTime = now;

        Entity auraTarget = getAuraTarget();
        if (auraTarget != null) target = auraTarget;
        else if (!isValidTarget(target)) target = null;

        animation = approach(animation, auraTarget != null ? 1f : 0f, delta * 6.5f);
        if (target == null || animation <= 0.01f) return;

        if (target instanceof net.minecraft.entity.LivingEntity living) {
            if (living.hurtTime > lastHurtTime && living.hurtTime > 0) {
                skullHitAnimation = 1.0f;
            }
            lastHurtTime = living.hurtTime;
        }
        skullHitAnimation = Math.max(0f, skullHitAnimation - delta * 3.5f);

        Color theme = ThemeManager.getThemeColor();
        if (hitColor.isEnabled() && skullHitAnimation > 0f) {
            theme = new Color(ez.minar.utils.render.utils.ColorHelper.lerpColor(theme.getRGB(), Color.RED.getRGB(), skullHitAnimation));
        }
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d targetPos = target.getLerpedPos(tickDelta);
        float alpha = smooth(animation);

        if (mode.isEnabled("Cylinder")) renderCylinder(context, targetPos, target.getHeight(), theme, alpha);
        else if (mode.isEnabled("Кубы")) {
            renderCubes(context, targetPos, target.getHeight(), theme, alpha, now);
        } else if (mode.isEnabled("Кресты")) {
            renderCrosses(context, targetPos, target.getHeight(), theme, alpha, now);
        } else if (mode.isEnabled("Череп")) {
            renderSkull(context, targetPos, target.getHeight(),
                    brightenLikeParticles(theme.getRed()),
                    brightenLikeParticles(theme.getGreen()),
                    brightenLikeParticles(theme.getBlue()),
                    alpha);
        } else if (mode.isEnabled("Ромб")) {
            renderDiamond(context, targetPos, target.getHeight(),
                    timeAngle(now, diamondSpeed.getValue()),
                    (float) diamondSize.getValue(),
                    theme.getRed(),
                    theme.getGreen(),
                    theme.getBlue(),
                    alpha);
        } else {
            if (ghostModel.isEnabled("Nursultan")) {
                renderGhostNursultan(context, targetPos, target.getHeight(), theme, alpha);
            } else if (ghostModel.isEnabled("Rock")) {
                renderGhostRock(context, targetPos, target.getHeight(), theme, alpha);
            } else {
                int red = brighten(theme.getRed()), green = brighten(theme.getGreen()), blue = brighten(theme.getBlue());
                double baseRadius = radius.getValue();
                float baseSize = (float) size.getValue();
                float angle = timeAngle(now, speed.getValue());

                Render3DUtils.TexturedBillboardBatch batch = createGlowBatch(context, GLOW_TEXTURE);
                for (int ghost = 0; ghost < 3; ghost++) {
                    renderGhost(batch, targetPos, target.getHeight(),
                            angle + ghost * (360f / 3), baseRadius, baseSize, red, green, blue, alpha);
                }
            }
        }
    }

    private void renderCylinder(WorldRenderContext context, Vec3d targetPos, double targetHeight,
                                Color color, float alpha) {
        double duration = 2000.0;
        double elapsed = (double) System.currentTimeMillis() % duration;
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);

        if (side) {
            progress -= 1.0;
        } else {
            progress = 1.0 - progress;
        }

        progress = progress < 0.5
                ? 2.0 * progress * progress
                : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;

        double eased = (targetHeight / 2.0)
                * (progress > 0.5 ? 1.0 - progress : progress)
                * (side ? -1.0 : 1.0);
        double ribbonHeight = eased * 1.5;
        double ringY = targetHeight * progress;
        double radius = target.getWidth() * 0.85;
        int segments = 360;
        float leadingAlpha = alpha * (145f / 255f);
        float trailingAlpha = alpha * (17.1f / 255f);

        Vec3d camera = context.worldState().cameraRenderState.pos;
        MatrixStack stack = context.matrices();
        stack.push();
        Vec3d relative = targetPos.subtract(camera);
        stack.translate(relative.x, relative.y, relative.z);
        MatrixStack.Entry entry = stack.peek();

        VertexConsumer fill = context.consumers().getBuffer(
                throughWalls.isEnabled() ? CYLINDER_FILL_NO_DEPTH : CYLINDER_FILL);
        for (int i = 0; i < segments; i++) {
            double angle1 = Math.toRadians(i);
            double angle2 = Math.toRadians(i + 1);
            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            vertex(fill, entry, color, x1, (float) ringY, z1, leadingAlpha);
            vertex(fill, entry, color, x2, (float) ringY, z2, leadingAlpha);
            vertex(fill, entry, color, x2, (float) (ringY + ribbonHeight), z2, trailingAlpha);
            vertex(fill, entry, color, x1, (float) (ringY + ribbonHeight), z1, trailingAlpha);
        }

        VertexConsumer outline = context.consumers().getBuffer(
                throughWalls.isEnabled() ? CYLINDER_LINES_NO_DEPTH : CYLINDER_LINES);
        for (int i = 0; i < segments; i++) {
            double angle1 = Math.toRadians(i);
            double angle2 = Math.toRadians(i + 1);
            line(outline, entry, color,
                    Math.cos(angle1) * radius, ringY, Math.sin(angle1) * radius,
                    Math.cos(angle2) * radius, ringY, Math.sin(angle2) * radius,
                    1.5f, trailingAlpha);
        }

        stack.pop();
    }

    private Entity getAuraTarget() {
        if (mc.player == null || mc.world == null) return null;
        AttackAura aura = FunctionManager.getFunction(AttackAura.class);
        if (aura != null && aura.isEnabled() && isValidTarget(aura.getTarget())) return aura.getTarget();
        TriggerBot trigger = FunctionManager.getFunction(TriggerBot.class);
        if (trigger != null && trigger.isEnabled() && isValidTarget(trigger.getTarget())) return trigger.getTarget();
        return null;
    }

    private boolean isValidTarget(Entity entity) {
        return entity != null && entity != mc.player && entity.isAlive();
    }

    private void updateVisibility() {
        boolean diamond = mode.isEnabled("Ромб");
        boolean cylinder = mode.isEnabled("Cylinder");
        boolean chain = mode.isEnabled("Chain");
        boolean cubes = mode.isEnabled("Кубы");
        boolean skull = mode.isEnabled("Череп");
        boolean crosses = mode.isEnabled("Кресты");
        boolean ghostRock = mode.isEnabled("Ghost Rock");
        boolean isOther = !diamond && !cylinder && !chain && !cubes && !skull && !crosses && !ghostRock;
        boolean isGhost = mode.isEnabled("Призраки");
        ghostModel.setVisible(isGhost);
        boolean showNormal = isOther && (!isGhost || ghostModel.isEnabled("Обычные"));

        glowMode.setVisible(showNormal || diamond || cubes || skull || crosses);
        radius.setVisible(showNormal);
        size.setVisible(showNormal);
        speed.setVisible(isOther);
        tailLength.setVisible(showNormal);

        diamondSize.setVisible(diamond);
        diamondSpeed.setVisible(diamond);
        diamondTexture.setVisible(diamond);

        skullSize.setVisible(skull);

        chainSize.setVisible(chain);
        chainSpeed.setVisible(chain);
        chainRadius.setVisible(chain);

        cubesFillAlpha.setVisible(cubes || crosses);
    }

    private void renderGhost(Render3DUtils.TexturedBillboardBatch batch, Vec3d targetPos, double targetHeight,
                             float angleDegrees, double radius, float baseSize, int red, int green, int blue, float alpha) {
        int trailParts = Math.max(1, (int) Math.round(tailLength.getValue()));
        for (int part = trailParts - 1; part >= 0; part--) {
            float trail = trailParts <= 1 ? 0f : part / (float) (trailParts - 1);
            float trailAngle = angleDegrees - part * 7.0f;
            Vec3d position = orbitPosition(targetPos, targetHeight, trailAngle, radius, trail);
            float partSize = baseSize * (1f - trail * 0.48f) * (0.45f + alpha * 0.55f);
            float partAlpha = alpha * (1f - trail * 0.74f);
            if (partAlpha <= 0.01f) continue;
            renderGlowTyped(batch, position, partSize, trailAngle * 1.35f, red, green, blue, partAlpha);
        }
    }

    private void renderGhostNursultan(WorldRenderContext context, Vec3d targetPos, double targetHeight, Color theme, float alpha) {
        long now = System.nanoTime();
        double timeSec = (now / 1_000_000_000.0);
        double iAge = (timeSec % 100000.0) * 20.0;

        float halfHeight = (float) (targetHeight / 2 + 0.1);
        float width = target.getWidth();

        float[] heightOffsets = {halfHeight * 0.6f, -halfHeight * 0.6f, 0f};
        float[] radiusMultipliers = {1.35f, 1.35f, 1.27f};
        float[] tiltAngles = {-15f, -15f, -15f};

        float orbitSpeed = 2.5f;
        float waveSpeed = 3.0f;
        double speedVal = speed.getValue() / 100.0;

        net.minecraft.client.render.RenderLayer layer = throughWalls.isEnabled() ?
                ez.minar.utils.render.Render3DUtils.ADDITIVE_TEXTURE_NO_DEPTH_LAYER.apply(GLOW_TEXTURE) :
                ez.minar.utils.render.Render3DUtils.ADDITIVE_TEXTURE_LAYER.apply(GLOW_TEXTURE);
        VertexConsumer buffer = context.consumers().getBuffer(layer);

        Vec3d cameraPos = context.worldState().cameraRenderState.pos;
        net.minecraft.client.render.Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();

        for (int j = 0; j < 3; j++) {
            for (int i = 0, length = 11; i <= length; i++) {
                double radians = Math.toRadians(((i / 1.8F + iAge * speedVal * orbitSpeed) * length + (j * 90)) % (length * 360));

                if (j == 1) {
                    radians = -radians;
                }

                double sinQuad = Math.sin(Math.toRadians(iAge * waveSpeed * speedVal + i * (j / 2.0 + halfHeight)) * 5.5) / 3.0;
                float offset = ((float) (i + length) / (length + length));

                float radiusVal = width + 0.19f * radiusMultipliers[j];

                double x = Math.cos(radians) * radiusVal;
                double y = halfHeight + sinQuad + heightOffsets[j] - 0.1;
                double z = Math.sin(radians) * radiusVal;

                Vec3d position = targetPos.add(x, y, z);

                float partAlpha = offset * alpha;
                if (partAlpha <= 0.01f) continue;

                float scale = 0.52f * offset;

                int redPart = brightenLikeParticles(theme.getRed());
                int greenPart = brightenLikeParticles(theme.getGreen());
                int bluePart = brightenLikeParticles(theme.getBlue());
                int color = (redPart << 16) | (greenPart << 8) | bluePart;

                drawNursultanParticle(buffer, cameraPos, cameraYaw, cameraPitch, position, scale, tiltAngles[j], color, partAlpha);
            }
        }
    }

    private void drawNursultanParticle(VertexConsumer buffer, Vec3d cameraPos, float cameraYaw, float cameraPitch, Vec3d position, float scale, float rotX, int color, float alpha) {
        MatrixStack matrices = new MatrixStack();
        matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));

        MatrixStack.Entry entry = matrices.peek();

        float minX = -scale / 3.0f;
        float minY = -scale / 2.0f;
        float maxX = scale;
        float maxY = scale;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (Math.clamp(alpha, 0f, 1f) * 255f);

        nursultanVertex(buffer, entry, minX, maxY, 0, 0f, 1f, r, g, b, a);
        nursultanVertex(buffer, entry, maxX, maxY, 0, 1f, 1f, r, g, b, a);
        nursultanVertex(buffer, entry, maxX, minY, 0, 1f, 0f, r, g, b, a);
        nursultanVertex(buffer, entry, minX, minY, 0, 0f, 0f, r, g, b, a);
    }

    private void nursultanVertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        buffer.vertex(entry, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0f, 1f, 0f);
    }

    private void renderGhostRock(WorldRenderContext context, Vec3d targetPos, double targetHeight, Color theme, float alpha) {
        net.minecraft.client.render.RenderLayer layer = throughWalls.isEnabled() ?
                ez.minar.utils.render.Render3DUtils.ADDITIVE_TEXTURE_NO_DEPTH_LAYER.apply(GLOW_TEXTURE) :
                ez.minar.utils.render.Render3DUtils.ADDITIVE_TEXTURE_LAYER.apply(GLOW_TEXTURE);
        VertexConsumer buffer = context.consumers().getBuffer(layer);

        Vec3d cameraPos = context.worldState().cameraRenderState.pos;
        net.minecraft.client.render.Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();

        long now = System.nanoTime();
        double timeSec = now / 1_000_000_000.0;
        double moving = timeSec * speed.getValue() * 5.0;

        float width = target.getWidth() * 1.5F;
        int step = 2;
        int wormTick = 0;
        int wormCD = 0;

        int red = brightenLikeParticles(theme.getRed());
        int green = brightenLikeParticles(theme.getGreen());
        int blue = brightenLikeParticles(theme.getBlue());
        int color = (red << 16) | (green << 8) | blue;

        for (int i = 0; i < 360; i += step) {
            float size = 0.13F + 0.005F * wormTick;
            float bigSize = 0.7F + 0.005F * wormTick;

            if (wormCD > 0) {
                wormCD -= step;
            } else {
                wormTick += step;
                if (wormTick > 50) {
                    wormCD = 100;
                    wormTick = 0;
                } else {
                    float val = Math.max(0.5F, 1.2F - 0.5F * alpha);
                    double sin = Math.sin(Math.toRadians(i + moving * 1.0)) * width * val;
                    double cos = Math.cos(Math.toRadians(i + moving * 1.0)) * width * val;

                    double yOffset = targetHeight / 1.5 + (targetHeight / 3.0) * Math.sin(Math.toRadians(i / 2.0 + moving / 5.0));

                    Vec3d position = targetPos.add(sin, yOffset, cos);

                    float partAlphaGlow = alpha * 0.05f;
                    float partAlphaCore = alpha * 0.6f;

                    if (partAlphaGlow > 0.01f) {
                        drawRockParticle(buffer, cameraPos, cameraYaw, cameraPitch, position, bigSize, color, partAlphaGlow);
                    }
                    if (partAlphaCore > 0.01f) {
                        drawRockParticle(buffer, cameraPos, cameraYaw, cameraPitch, position, size, color, partAlphaCore);
                    }
                }
            }
        }
    }

    private void drawRockParticle(VertexConsumer buffer, Vec3d cameraPos, float cameraYaw, float cameraPitch, Vec3d position, float scale, int color, float alpha) {
        MatrixStack matrices = new MatrixStack();
        matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        MatrixStack.Entry entry = matrices.peek();

        float minX = -scale / 2.0f;
        float minY = -scale / 2.0f;
        float maxX = scale / 2.0f;
        float maxY = scale / 2.0f;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (Math.clamp(alpha, 0f, 1f) * 255f);

        nursultanVertex(buffer, entry, minX, maxY, 0, 0f, 1f, r, g, b, a);
        nursultanVertex(buffer, entry, maxX, maxY, 0, 1f, 1f, r, g, b, a);
        nursultanVertex(buffer, entry, maxX, minY, 0, 1f, 0f, r, g, b, a);
        nursultanVertex(buffer, entry, minX, minY, 0, 0f, 0f, r, g, b, a);
    }

    private void renderSkull(WorldRenderContext context, Vec3d targetPos, double targetHeight,
                             int red, int green, int blue, float alpha) {
        if (!(target instanceof net.minecraft.entity.LivingEntity living)) return;

        float healthRatio = living.getHealth() / living.getMaxHealth();
        Identifier texture = SKULL_TEXTURE_0;
        if (healthRatio <= 0.33f) {
            texture = SKULL_TEXTURE_2;
        } else if (healthRatio <= 0.66f) {
            texture = SKULL_TEXTURE_1;
        }

        Vec3d position = targetPos.add(0.0, targetHeight * 0.56, 0.0);

        float bounceScale = 1.0f + 0.35f * (float)(Math.sin(skullHitAnimation * Math.PI * 2.5) * skullHitAnimation);
        float shakeAngle = (float)(Math.sin(skullHitAnimation * Math.PI * 4.0) * 15.0 * skullHitAnimation);

        float baseSize = (float) skullSize.getValue() * 0.92f * bounceScale * (0.55f + alpha * 0.45f);
        float easedAlpha = Math.clamp(alpha, 0f, 1f);

        Render3DUtils.TexturedBillboardBatch glowBatch = createGlowBatch(context, GLOW_TEXTURE);
        renderGlowTyped(glowBatch, position, baseSize * 1.85f, -shakeAngle * 0.85f, red, green, blue, easedAlpha * 0.65f);

        Render3DUtils.TexturedBillboardBatch skullBatch = throughWalls.isEnabled() ?
                Render3DUtils.additiveTexturedBillboardNoDepthBatch(context, texture) :
                Render3DUtils.additiveTexturedBillboardBatch(context, texture);
        skullBatch.render(position, baseSize, shakeAngle, red, green, blue, easedAlpha);
    }

    private void renderDiamond(WorldRenderContext context, Vec3d targetPos, double targetHeight,
                               float angleDegrees, float baseSize, int red, int green, int blue, float alpha) {
        Vec3d position = targetPos.add(0.0, targetHeight * 0.56, 0.0);

        float pulse = 0.94f + 0.06f * (float) Math.sin(Math.toRadians(angleDegrees * 2.0f));
        float diamondSizeVal = baseSize * 0.92f * pulse * (0.55f + alpha * 0.45f);
        float easedAlpha = Math.clamp(alpha, 0f, 1f);

        Identifier texture = DIAMOND_TEXTURE;
        if (diamondTexture.isEnabled("Capture2")) texture = CAPTURE2_TEXTURE;
        else if (diamondTexture.isEnabled("TargetPro")) texture = TARGETPRO_TEXTURE;

        Render3DUtils.TexturedBillboardBatch diamondBatch = createGlowBatch(context, texture);

        renderGlowTyped(diamondBatch, position, diamondSizeVal, angleDegrees,
                red, green, blue, easedAlpha);
    }

    private Render3DUtils.TexturedBillboardBatch createGlowBatch(WorldRenderContext context, Identifier texture) {
        if (glowMode.isEnabled("Glow")) {
            return throughWalls.isEnabled()
                    ? Render3DUtils.shaderGlowTexturedBillboardNoDepthBatch(context, texture)
                    : Render3DUtils.shaderGlowTexturedBillboardBatch(context, texture);
        }

        return throughWalls.isEnabled()
                ? Render3DUtils.additiveTexturedBillboardNoDepthBatch(context, texture)
                : Render3DUtils.additiveTexturedBillboardBatch(context, texture);
    }

    private void renderGlowTyped(Render3DUtils.TexturedBillboardBatch batch, Vec3d position, float size,
                                 float rotation, int red, int green, int blue, float alpha) {
        if (!glowMode.isEnabled("Glow")) {
            batch.render(position, size, rotation, red, green, blue, alpha);
            return;
        }

        batch.renderShaderGlow(position, size, rotation, red, green, blue, alpha);
    }

    private float timeAngle(long now, double speed) {
        return (float) (((now / 1_000_000_000.0) * speed) % 360.0);
    }

    private Vec3d orbitPosition(Vec3d targetPos, double targetHeight, float angleDegrees, double radius, float trail) {
        double radians = Math.toRadians(angleDegrees);
        double x = Math.cos(radians) * radius;
        double z = Math.sin(radians) * radius;
        double wave = Math.sin(radians * 2.0) * targetHeight * 0.18;
        double y = targetHeight * (0.52 + trail * 0.06) + wave;
        return targetPos.add(x, y, z);
    }

    private float approach(float current, float target, float step) {
        if (current < target) return Math.min(current + step, target);
        return Math.max(current - step, target);
    }

    private float smooth(float value) {
        value = Math.clamp(value, 0f, 1f);
        return value * value * (3f - 2f * value);
    }

    private int brighten(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.35f), 0, 255);
    }

    private int brightenLikeParticles(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.45f), 0, 255);
    }

    private void renderCubes(WorldRenderContext context, Vec3d targetPos, double targetHeight, Color theme, float alpha, long now) {
        int amount = 3;
        float cSize = 0.2f;
        double cRadius = 1.6;
        float cSpeed = 95.0f;

        Vec3d camera = context.worldState().cameraRenderState.pos;

        int red = brightenLikeParticles(theme.getRed());
        int green = brightenLikeParticles(theme.getGreen());
        int blue = brightenLikeParticles(theme.getBlue());

        Render3DUtils.TexturedBillboardBatch glowBatch = createGlowBatch(context, GLOW_TEXTURE);

        double entityRadius = Math.max(0.2, target.getWidth() / 2.0);
        double entityHeight = Math.max(0.2, targetHeight);

        int totalCubes = amount * 3;
        Vec3d[] cubePositions = new Vec3d[totalCubes];

        double baseAngle = Math.toRadians(timeAngle(now, cSpeed));
        double angleStep = Math.PI * 2 / amount;

        double smallRadius = entityRadius * cRadius * 1.2;
        double largeRadius = entityRadius * cRadius * 1.8;

        for (int ring = 0; ring < 3; ring++) {
            double currentRadius = (ring == 1) ? largeRadius : smallRadius;
            double yOffset = (ring == 0) ? entityHeight * 0.15 : (ring == 1) ? entityHeight * 0.5 : entityHeight * 0.85;

            double ringAngleOffset = (ring == 1) ? (angleStep / 2.0) : 0;

            for (int i = 0; i < amount; i++) {
                double angle = (ring == 1 ? -baseAngle : baseAngle) + ringAngleOffset + i * angleStep;

                double x = Math.cos(angle) * currentRadius;
                double z = Math.sin(angle) * currentRadius;

                int index = ring * amount + i;
                cubePositions[index] = targetPos.add(x, yOffset, z);

                renderGlowTyped(glowBatch, cubePositions[index], cSize * 2.8f, 0f, red, green, blue, alpha * 0.85f);
            }
        }

        for (int i = 0; i < totalCubes; i++) {
            Vec3d cubePos = cubePositions[i];

            MatrixStack stack = context.matrices();
            stack.push();

            Vec3d centerRel = cubePos.subtract(camera);
            stack.translate(centerRel.x, centerRel.y, centerRel.z);

            float spin = timeAngle(now, cSpeed * 1.5f);
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.5f));

            Box localBox = new Box(-cSize / 2, -cSize / 2, -cSize / 2, cSize / 2, cSize / 2, cSize / 2);

            Color brightColor = new Color(red, green, blue);

            drawBoxFill(context, stack.peek(), localBox, brightColor, alpha * (float) cubesFillAlpha.getValue());

            stack.pop();
        }
    }

    private void renderCrosses(WorldRenderContext context, Vec3d targetPos, double targetHeight, Color theme, float alpha, long now) {
        int amount = 3;
        float cSize = 0.2f;
        double cRadius = 1.4;
        float cSpeed = 95.0f;

        Vec3d camera = context.worldState().cameraRenderState.pos;

        int red = brightenLikeParticles(theme.getRed());
        int green = brightenLikeParticles(theme.getGreen());
        int blue = brightenLikeParticles(theme.getBlue());

        Render3DUtils.TexturedBillboardBatch glowBatch = createGlowBatch(context, GLOW_TEXTURE);

        double entityRadius = Math.max(0.2, target.getWidth() / 2.0);
        double entityHeight = Math.max(0.2, targetHeight);

        int totalCrosses = amount;
        Vec3d[] crossPositions = new Vec3d[totalCrosses];

        double baseAngle = Math.toRadians(timeAngle(now, cSpeed));
        double angleStep = Math.PI * 2 / amount;

        double currentRadius = entityRadius * cRadius * 1.5;
        double yOffset = entityHeight * 0.5;

        for (int i = 0; i < amount; i++) {
            double angle = baseAngle + i * angleStep;

            double x = Math.cos(angle) * currentRadius;
            double z = Math.sin(angle) * currentRadius;

            crossPositions[i] = targetPos.add(x, yOffset, z);

            renderGlowTyped(glowBatch, crossPositions[i], cSize * 4.0f, 0f, red, green, blue, alpha * 0.85f);
        }

        for (int i = 0; i < totalCrosses; i++) {
            Vec3d crossPos = crossPositions[i];

            MatrixStack stack = context.matrices();
            stack.push();

            Vec3d centerRel = crossPos.subtract(camera);
            stack.translate(centerRel.x, centerRel.y, centerRel.z);

            float spin = timeAngle(now, cSpeed * 1.5f);
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));

            float thickness = cSize / 8.0f;
            Box verticalBox = new Box(-thickness, -cSize, -thickness, thickness, cSize * 1.5, thickness);
            Box horizontalBox = new Box(-cSize * 0.8, cSize * 0.35, -thickness, cSize * 0.8, cSize * 0.35 + thickness * 2, thickness);

            Color brightColor = new Color(red, green, blue);

            drawBoxFill(context, stack.peek(), verticalBox, brightColor, alpha * (float) cubesFillAlpha.getValue());
            drawBoxFill(context, stack.peek(), horizontalBox, brightColor, alpha * (float) cubesFillAlpha.getValue());

            stack.pop();
        }
    }

    private void drawBoxFill(WorldRenderContext context, MatrixStack.Entry entry, Box box, Color color, float alpha) {
        VertexConsumer buffer = context.consumers().getBuffer(throughWalls.isEnabled() ? CUBES_FILL_NO_DEPTH : CUBES_FILL);
        float inset = 0.001F;

        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        quad(buffer, entry, color, x1, y2 + inset, z2, x2, y2 + inset, z2, x2, y2 + inset, z1, x1, y2 + inset, z1, alpha);
        quad(buffer, entry, color, x1, y1 - inset, z1, x2, y1 - inset, z1, x2, y1 - inset, z2, x1, y1 - inset, z2, alpha);
        quad(buffer, entry, color, x1, y1, z1 - inset, x1, y2, z1 - inset, x2, y2, z1 - inset, x2, y1, z1 - inset, alpha);
        quad(buffer, entry, color, x1, y1, z2 + inset, x2, y1, z2 + inset, x2, y2, z2 + inset, x1, y2, z2 + inset, alpha);
        quad(buffer, entry, color, x1 - inset, y1, z1, x1 - inset, y1, z2, x1 - inset, y2, z2, x1 - inset, y2, z1, alpha);
        quad(buffer, entry, color, x2 + inset, y1, z1, x2 + inset, y2, z1, x2 + inset, y2, z2, x2 + inset, y1, z2, alpha);
    }

    private void drawBoxOutline(WorldRenderContext context, MatrixStack.Entry entry, Box box, Color color, float alpha, float width) {
        VertexConsumer buffer = context.consumers().getBuffer(throughWalls.isEnabled() ? CUBES_LINES_NO_DEPTH : CUBES_LINES);

        line(buffer, entry, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, width, alpha);
        line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, width, alpha);
        line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, width, alpha);
        line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, width, alpha);

        line(buffer, entry, color, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, width, alpha);
        line(buffer, entry, color, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, width, alpha);
        line(buffer, entry, color, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, width, alpha);
        line(buffer, entry, color, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, width, alpha);

        line(buffer, entry, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, width, alpha);
        line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, width, alpha);
        line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, width, alpha);
        line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, width, alpha);
    }

    private void line(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      double x1, double y1, double z1, double x2, double y2, double z2, float width, float alpha) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0001F) return;

        buffer.vertex(entry, (float) x1, (float) y1, (float) z1)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0f, 1f) * 255))
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
        buffer.vertex(entry, (float) x2, (float) y2, (float) z2)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0f, 1f) * 255))
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
    }

    private void quad(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4,
                      float alpha) {
        vertex(buffer, entry, color, x1, y1, z1, alpha);
        vertex(buffer, entry, color, x2, y2, z2, alpha);
        vertex(buffer, entry, color, x3, y3, z3, alpha);
        vertex(buffer, entry, color, x4, y4, z4, alpha);
    }

    private void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Color color, float x, float y, float z, float alpha) {
        buffer.vertex(entry, x, y, z)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0F, 1F) * 255F));
    }

}
