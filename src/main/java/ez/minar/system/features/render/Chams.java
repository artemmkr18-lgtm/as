package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.ChamsPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NewFunction(name = "Chams", desc = "Цветная модель игроков, боксы и шейдеры", category = Category.RENDER)
public class Chams extends Function {
    public static Chams Instance;

    public final ModeSetting mode = new ModeSetting("Режим", "Боксы", "Solid", "Металл", "Блюр", "Шейдер");
    public final ModeSetting shaderMode = new ModeSetting("Шейдер", "Web", "Plasma", "Waves", "Water", "Energy", "Rainbow", "Fire", "Smoke");
    public final ModeSetting renderStyle = new ModeSetting("Стиль шейдера", "Модель", "Пост-процесс");
    public final ModeSetting targets = new ModeSetting("Цели", "Все", "Враги", "Друзья");

    public final BooleanSetting useThemeColor = new BooleanSetting("Цвет темы", true);
    public final ColorSetting color = new ColorSetting("Цвет", new Color(255, 80, 80));
    public final ColorSetting friendColor = new ColorSetting("Цвет друзей", new Color(0, 230, 90));

    public final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);
    public final BooleanSetting hideOriginal = new BooleanSetting("Скрыть модель", true);
    public final BooleanSetting renderArmor = new BooleanSetting("Отображать броню", false);
    public final BooleanSetting outerLayer = new BooleanSetting("Второй слой", false);

    public final BooleanSetting fill = new BooleanSetting("Заливка", true);
    public final NumberSetting fillAlpha = new NumberSetting("Прозрачность заливки", 55.0, 0.0, 255.0, 5.0);

    public final BooleanSetting lines = new BooleanSetting("Линии", true);
    public final NumberSetting alpha = new NumberSetting("Прозрачность линий", 170.0, 0.0, 255.0, 5.0);
    public final NumberSetting lineWidth = new NumberSetting("Толщина линий", 1.5, 0.5, 5.0, 0.5);
    public final NumberSetting layers = new NumberSetting("Слои", 1.0, 1.0, 3.0, 1.0);

    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");

    public static final RenderPipeline BOX_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "chams_box_fill"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    public static final RenderLayer BOX_FILL = RenderLayer.of("minar_chams_box_fill",
            RenderSetup.builder(BOX_FILL_PIPELINE)
                    .texture("Sampler0", WHITE_TEXTURE)
                    .translucent()
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .expectedBufferSize(8192)
                    .build()
    );

    public static final RenderPipeline BOX_FILL_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "chams_box_fill_no_depth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    public static final RenderLayer BOX_FILL_NO_DEPTH = RenderLayer.of("minar_chams_box_fill_no_depth",
            RenderSetup.builder(BOX_FILL_NO_DEPTH_PIPELINE)
                    .texture("Sampler0", WHITE_TEXTURE)
                    .translucent()
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .expectedBufferSize(8192)
                    .build()
    );

    public static final RenderPipeline CHAMS_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "chams_lines"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    public static final RenderLayer CHAMS_LINES = RenderLayer.of("minar_chams_lines",
            RenderSetup.builder(CHAMS_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .expectedBufferSize(8192)
                    .build()
    );

    public static final RenderPipeline CHAMS_LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "chams_lines_no_depth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    public static final RenderLayer CHAMS_LINES_NO_DEPTH = RenderLayer.of("minar_chams_lines_no_depth",
            RenderSetup.builder(CHAMS_LINES_NO_DEPTH_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .expectedBufferSize(8192)
                    .build()
    );

    private static final VertexConsumerProvider.Immediate CHAMS_CONSUMERS = VertexConsumerProvider.immediate(new BufferAllocator(786432));
    private static final Map<ModelPart, List<ModelPart.Cuboid>> CUBOID_CACHE = new ConcurrentHashMap<>();
    private static Field cuboidField = null;

    static {
        try {
            for (Field f : ModelPart.class.getDeclaredFields()) {
                if (f.getType() == List.class) {
                    f.setAccessible(true);
                    cuboidField = f;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public Chams() {
        Instance = this;
        mode.runnable(this::updateVisibility);
        useThemeColor.runnable(this::updateVisibility);
        targets.runnable(this::updateVisibility);
        fill.runnable(this::updateVisibility);
        lines.runnable(this::updateVisibility);
        hideOriginal.runnable(this::updateVisibility);
        renderStyle.runnable(this::updateVisibility);

        addSettings(mode, shaderMode, renderStyle, targets,
                useThemeColor, color, friendColor,
                throughWalls, hideOriginal, renderArmor, outerLayer,
                fill, fillAlpha, lines, alpha, lineWidth, layers);
        updateVisibility();
    }

    private void updateVisibility() {
        boolean isShader = mode.isEnabled("Шейдер");
        shaderMode.setVisible(isShader);
        renderStyle.setVisible(isShader);

        color.setVisible(!useThemeColor.isEnabled());
        friendColor.setVisible(targets.isEnabled("Все") || targets.isEnabled("Друзья"));

        boolean isPost = isPostShaderActive();

        layers.setVisible(!isPost);
        outerLayer.setVisible(!isPost);

        lines.setVisible(!isPost);
        lineWidth.setVisible(!isPost && lines.isEnabled());
        alpha.setVisible(!isPost && lines.isEnabled());

        fill.setVisible(!isPost);
        fillAlpha.setVisible(isPost || fill.isEnabled());

        renderArmor.setVisible(hideOriginal.isEnabled());
    }

    public boolean shouldRender(LivingEntityRenderState state) {
        if (!isEnabled() || state == null) return false;
        if (!(state instanceof PlayerEntityRenderState playerState)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && playerState.id == mc.player.getId()) {
            if (mc.options.getPerspective().isFirstPerson() && !(mc.currentScreen instanceof InventoryScreen)) {
                return false;
            }
        }

        String name = playerState.playerName != null ? playerState.playerName.getString() : "";
        boolean isFriend = !name.isEmpty() && FriendManager.isFriend(name);
        if (targets.isEnabled("Враги") && isFriend) return false;
        if (targets.isEnabled("Друзья") && !isFriend) return false;
        return true;
    }

    public boolean isFriend(LivingEntityRenderState state) {
        if (state instanceof PlayerEntityRenderState playerState && playerState.playerName != null) {
            String name = playerState.playerName.getString();
            return !name.isEmpty() && FriendManager.isFriend(name);
        }
        return false;
    }

    public boolean shouldHideOriginal(LivingEntityRenderState state) {
        return shouldRender(state) && hideOriginal.isEnabled();
    }

    public boolean shouldSuppressFeatures(LivingEntityRenderState state) {
        return shouldRender(state) && hideOriginal.isEnabled() && !renderArmor.isEnabled();
    }

    public boolean isPostShaderActive() {
        return isEnabled() && mode.isEnabled("Шейдер") && renderStyle.isEnabled("Пост-процесс");
    }

    public void render(LivingEntityRenderState state, MatrixStack ms, EntityModel<?> model) {
        if (!isEnabled()) return;
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        if (!(model instanceof BipedEntityModel<?> bipedModel)) return;

        String name = playerState.playerName != null ? playerState.playerName.getString() : "";
        boolean isFriend = !name.isEmpty() && FriendManager.isFriend(name);

        renderPlayerParts(ms, bipedModel, isFriend);
    }

    private void renderPlayerParts(MatrixStack ms, BipedEntityModel<?> model, boolean isFriend) {
        boolean depth = !throughWalls.isEnabled();
        Color baseCol = getBaseColor(isFriend);

        boolean doFill = fill.isEnabled();
        int baseAlphaVal = (int) fillAlpha.getValue();

        boolean doLines = lines.isEnabled();
        int lineAlphaVal = (int) alpha.getValue();
        float lw = (float) lineWidth.getValue();
        int lineCol = withAlpha(baseCol, lineAlphaVal);

        int layerCount = (int) layers.getValue();
        boolean renderOuter = outerLayer.isEnabled();
        float time = (System.currentTimeMillis() % 100000L) / 1000f;

        int steps = switch (mode.getActiveMode()) {
            case "Боксы", "Solid" -> 1;
            case "Металл", "Блюр" -> 2;
            case "Шейдер" -> 3;
            default -> 2;
        };

        // PASS 1: Fill geometry
        if (doFill && baseAlphaVal > 0) {
            VertexConsumer fillConsumer = CHAMS_CONSUMERS.getBuffer(depth ? BOX_FILL : BOX_FILL_NO_DEPTH);
            for (int l = 0; l < layerCount; l++) {
                float expand = l * 0.4f;
                int layerFillAlpha = Math.max(1, baseAlphaVal / (l + 1));

                renderPartFills(ms, model.head, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                renderPartFills(ms, model.body, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                renderPartFills(ms, model.rightArm, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                renderPartFills(ms, model.leftArm, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                renderPartFills(ms, model.rightLeg, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                renderPartFills(ms, model.leftLeg, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);

                if (renderOuter && model instanceof PlayerEntityModel pm) {
                    renderPartFills(ms, pm.hat, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                    renderPartFills(ms, pm.jacket, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                    renderPartFills(ms, pm.rightSleeve, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                    renderPartFills(ms, pm.leftSleeve, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                    renderPartFills(ms, pm.rightPants, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                    renderPartFills(ms, pm.leftPants, expand, baseCol, l, layerFillAlpha, fillConsumer, time, steps);
                }
            }
            CHAMS_CONSUMERS.draw();
        }

        // PASS 2: Line geometry
        if (doLines && lineAlphaVal > 0) {
            VertexConsumer lineConsumer = CHAMS_CONSUMERS.getBuffer(depth ? CHAMS_LINES : CHAMS_LINES_NO_DEPTH);
            for (int l = 0; l < layerCount; l++) {
                float expand = l * 0.4f;

                renderPartLines(ms, model.head, expand, lineCol, lw, lineConsumer);
                renderPartLines(ms, model.body, expand, lineCol, lw, lineConsumer);
                renderPartLines(ms, model.rightArm, expand, lineCol, lw, lineConsumer);
                renderPartLines(ms, model.leftArm, expand, lineCol, lw, lineConsumer);
                renderPartLines(ms, model.rightLeg, expand, lineCol, lw, lineConsumer);
                renderPartLines(ms, model.leftLeg, expand, lineCol, lw, lineConsumer);

                if (renderOuter && model instanceof PlayerEntityModel pm) {
                    renderPartLines(ms, pm.hat, expand, lineCol, lw, lineConsumer);
                    renderPartLines(ms, pm.jacket, expand, lineCol, lw, lineConsumer);
                    renderPartLines(ms, pm.rightSleeve, expand, lineCol, lw, lineConsumer);
                    renderPartLines(ms, pm.leftSleeve, expand, lineCol, lw, lineConsumer);
                    renderPartLines(ms, pm.rightPants, expand, lineCol, lw, lineConsumer);
                    renderPartLines(ms, pm.leftPants, expand, lineCol, lw, lineConsumer);
                }
            }
            CHAMS_CONSUMERS.draw();
        }
    }

    private void renderPartFills(MatrixStack ms, ModelPart part, float expand,
                                 Color baseCol, int layerIndex, int layerFillAlpha,
                                 VertexConsumer fillConsumer, float time, int steps) {
        if (part == null || part.hidden) return;
        List<ModelPart.Cuboid> cuboids = getCuboids(part);
        if (cuboids.isEmpty()) return;

        ms.push();
        part.applyTransform(ms);
        MatrixStack.Entry entry = ms.peek();

        for (ModelPart.Cuboid cuboid : cuboids) {
            float x1 = (cuboid.minX - expand) * 0.0625f;
            float y1 = (cuboid.minY - expand) * 0.0625f;
            float z1 = (cuboid.minZ - expand) * 0.0625f;
            float x2 = (cuboid.maxX + expand) * 0.0625f;
            float y2 = (cuboid.maxY + expand) * 0.0625f;
            float z2 = (cuboid.maxZ + expand) * 0.0625f;

            renderCuboidFill(entry, x1, y1, z1, x2, y2, z2, baseCol, layerIndex, layerFillAlpha, fillConsumer, time, steps);
        }

        ms.pop();
    }

    private void renderPartLines(MatrixStack ms, ModelPart part, float expand,
                                 int lineCol, float lw, VertexConsumer lineConsumer) {
        if (part == null || part.hidden) return;
        List<ModelPart.Cuboid> cuboids = getCuboids(part);
        if (cuboids.isEmpty()) return;

        ms.push();
        part.applyTransform(ms);
        MatrixStack.Entry entry = ms.peek();

        for (ModelPart.Cuboid cuboid : cuboids) {
            float x1 = (cuboid.minX - expand) * 0.0625f;
            float y1 = (cuboid.minY - expand) * 0.0625f;
            float z1 = (cuboid.minZ - expand) * 0.0625f;
            float x2 = (cuboid.maxX + expand) * 0.0625f;
            float y2 = (cuboid.maxY + expand) * 0.0625f;
            float z2 = (cuboid.maxZ + expand) * 0.0625f;

            renderCuboidLines(entry, x1, y1, z1, x2, y2, z2, lineCol, lw, lineConsumer);
        }

        ms.pop();
    }

    private void renderCuboidFill(MatrixStack.Entry entry, float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  Color baseCol, int layerIndex, int layerFillAlpha,
                                  VertexConsumer fillConsumer, float time, int steps) {
        // Bottom (-Y)
        renderFace(fillConsumer, entry, 0f, -1f, 0f,
                x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2,
                baseCol, layerFillAlpha, layerIndex, time, steps);

        // Top (+Y)
        renderFace(fillConsumer, entry, 0f, 1f, 0f,
                x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1,
                baseCol, layerFillAlpha, layerIndex, time, steps);

        // North (-Z)
        renderFace(fillConsumer, entry, 0f, 0f, -1f,
                x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1,
                baseCol, layerFillAlpha, layerIndex, time, steps);

        // South (+Z)
        renderFace(fillConsumer, entry, 0f, 0f, 1f,
                x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2,
                baseCol, layerFillAlpha, layerIndex, time, steps);

        // West (-X)
        renderFace(fillConsumer, entry, -1f, 0f, 0f,
                x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1,
                baseCol, layerFillAlpha, layerIndex, time, steps);

        // East (+X)
        renderFace(fillConsumer, entry, 1f, 0f, 0f,
                x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2,
                baseCol, layerFillAlpha, layerIndex, time, steps);
    }

    private void renderFace(VertexConsumer buffer, MatrixStack.Entry entry,
                            float nx, float ny, float nz,
                            float x00, float y00, float z00,
                            float x10, float y10, float z10,
                            float x11, float y11, float z11,
                            float x01, float y01, float z01,
                            Color baseCol, int layerFillAlpha, int layerIndex, float time, int steps) {
        if (steps <= 1) {
            int c1 = getVertexColor(entry, nx, ny, nz, x00, y00, z00, 0f, 0f, baseCol, layerFillAlpha, layerIndex, time);
            int c2 = getVertexColor(entry, nx, ny, nz, x10, y10, z10, 1f, 0f, baseCol, layerFillAlpha, layerIndex, time);
            int c3 = getVertexColor(entry, nx, ny, nz, x11, y11, z11, 1f, 1f, baseCol, layerFillAlpha, layerIndex, time);
            int c4 = getVertexColor(entry, nx, ny, nz, x01, y01, z01, 0f, 1f, baseCol, layerFillAlpha, layerIndex, time);
            quad(buffer, entry, c1, c2, c3, c4, x00, y00, z00, x10, y10, z10, x11, y11, z11, x01, y01, z01, nx, ny, nz);
            return;
        }

        for (int iy = 0; iy < steps; iy++) {
            float vA = (float) iy / steps;
            float vB = (float) (iy + 1) / steps;

            for (int ix = 0; ix < steps; ix++) {
                float uA = (float) ix / steps;
                float uB = (float) (ix + 1) / steps;

                float px00 = lerp2D(x00, x10, x11, x01, uA, vA);
                float py00 = lerp2D(y00, y10, y11, y01, uA, vA);
                float pz00 = lerp2D(z00, z10, z11, z01, uA, vA);

                float px10 = lerp2D(x00, x10, x11, x01, uB, vA);
                float py10 = lerp2D(y00, y10, y11, y01, uB, vA);
                float pz10 = lerp2D(z00, z10, z11, z01, uB, vA);

                float px11 = lerp2D(x00, x10, x11, x01, uB, vB);
                float py11 = lerp2D(y00, y10, y11, y01, uB, vB);
                float pz11 = lerp2D(z00, z10, z11, z01, uB, vB);

                float px01 = lerp2D(x00, x10, x11, x01, uA, vB);
                float py01 = lerp2D(y00, y10, y11, y01, uA, vB);
                float pz01 = lerp2D(z00, z10, z11, z01, uA, vB);

                int c00 = getVertexColor(entry, nx, ny, nz, px00, py00, pz00, uA, vA, baseCol, layerFillAlpha, layerIndex, time);
                int c10 = getVertexColor(entry, nx, ny, nz, px10, py10, pz10, uB, vA, baseCol, layerFillAlpha, layerIndex, time);
                int c11 = getVertexColor(entry, nx, ny, nz, px11, py11, pz11, uB, vB, baseCol, layerFillAlpha, layerIndex, time);
                int c01 = getVertexColor(entry, nx, ny, nz, px01, py01, pz01, uA, vB, baseCol, layerFillAlpha, layerIndex, time);

                quad(buffer, entry, c00, c10, c11, c01, px00, py00, pz00, px10, py10, pz10, px11, py11, pz11, px01, py01, pz01, nx, ny, nz);
            }
        }
    }

    private static float lerp2D(float c00, float c10, float c11, float c01, float u, float v) {
        float bottom = c00 + (c10 - c00) * u;
        float top = c01 + (c11 - c01) * u;
        return bottom + (top - bottom) * v;
    }

    private void renderCuboidLines(MatrixStack.Entry entry, float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   int lineCol, float lw,
                                   VertexConsumer lineConsumer) {
        int la = (lineCol >> 24) & 0xFF;
        int lr = (lineCol >> 16) & 0xFF;
        int lg = (lineCol >> 8) & 0xFF;
        int lb = lineCol & 0xFF;

        // Bottom 4 edges
        drawEdge(lineConsumer, entry, x1, y1, z1, x2, y1, z1, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x2, y1, z1, x2, y1, z2, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x2, y1, z2, x1, y1, z2, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x1, y1, z2, x1, y1, z1, lr, lg, lb, la, lw);

        // Top 4 edges
        drawEdge(lineConsumer, entry, x1, y2, z1, x2, y2, z1, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x2, y2, z1, x2, y2, z2, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x2, y2, z2, x1, y2, z2, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x1, y2, z2, x1, y2, z1, lr, lg, lb, la, lw);

        // Vertical 4 edges
        drawEdge(lineConsumer, entry, x1, y1, z1, x1, y2, z1, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x2, y1, z1, x2, y2, z1, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x2, y1, z2, x2, y2, z2, lr, lg, lb, la, lw);
        drawEdge(lineConsumer, entry, x1, y1, z2, x1, y2, z2, lr, lg, lb, la, lw);
    }

    private void drawEdge(VertexConsumer lineBuffer, MatrixStack.Entry entry,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          int r, int g, int b, int a, float lw) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 1.0e-4f) return;
        float nx = dx / len, ny = dy / len, dzNorm = dz / len;
        lineBuffer.vertex(entry, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, dzNorm).lineWidth(lw);
        lineBuffer.vertex(entry, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, dzNorm).lineWidth(lw);
    }

    private void quad(VertexConsumer buffer, MatrixStack.Entry entry,
                      int col1, int col2, int col3, int col4,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float nx, float ny, float nz) {
        buffer.vertex(entry, x1, y1, z1).color((col1 >> 16) & 0xFF, (col1 >> 8) & 0xFF, col1 & 0xFF, (col1 >> 24) & 0xFF).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, nx, ny, nz);
        buffer.vertex(entry, x2, y2, z2).color((col2 >> 16) & 0xFF, (col2 >> 8) & 0xFF, col2 & 0xFF, (col2 >> 24) & 0xFF).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, nx, ny, nz);
        buffer.vertex(entry, x3, y3, z3).color((col3 >> 16) & 0xFF, (col3 >> 8) & 0xFF, col3 & 0xFF, (col3 >> 24) & 0xFF).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, nx, ny, nz);
        buffer.vertex(entry, x4, y4, z4).color((col4 >> 16) & 0xFF, (col4 >> 8) & 0xFF, col4 & 0xFF, (col4 >> 24) & 0xFF).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, nx, ny, nz);
    }

    private int getVertexColor(MatrixStack.Entry entry, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v,
                               Color baseCol, int layerFillAlpha, int layerIndex, float time) {
        int r = baseCol.getRed();
        int g = baseCol.getGreen();
        int b = baseCol.getBlue();
        int a = layerFillAlpha;

        if (mode.isEnabled("Solid")) {
            // Pure flat vibrant unlit color
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        if (mode.isEnabled("Металл")) {
            // Chrome reflection & specular shine
            float wx = nx, wy = ny, wz = nz;
            float chrome = (float) Math.sin((wy * 3.5f + wx * 2.0f + wz * 1.5f + y * 4.0f) * 3.0f + time * 2.0f);
            chrome = (chrome + 1.0f) * 0.5f;
            chrome = (float) Math.pow(chrome, 4.0);

            float rim = (float) Math.pow(1.0f - Math.abs(wz), 2.0);
            float shine = Math.clamp(chrome * 0.85f + rim * 0.65f, 0.0f, 1.0f);

            r = Math.clamp((int) (r * 0.5f + 255f * shine * 0.9f), 0, 255);
            g = Math.clamp((int) (g * 0.5f + 255f * shine * 0.9f), 0, 255);
            b = Math.clamp((int) (b * 0.5f + 255f * shine * 0.9f), 0, 255);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        if (mode.isEnabled("Блюр")) {
            // Neon glow edge + pulsing translucent core
            float rim = (float) Math.pow(1.0f - Math.abs(nz), 2.5);
            float pulse = (float) (0.85 + 0.15 * Math.sin(time * 3.5 + y * 6.0));
            float glow = Math.clamp(rim * pulse, 0.0f, 1.0f);

            r = Math.clamp((int) (r * 0.6f + 200f * glow), 0, 255);
            g = Math.clamp((int) (g * 0.6f + 200f * glow), 0, 255);
            b = Math.clamp((int) (b * 0.6f + 200f * glow), 0, 255);
            int newA = Math.clamp((int) (a * (0.4f + 0.6f * glow)), 0, 255);
            return (newA << 24) | (r << 16) | (g << 8) | b;
        }

        if (mode.isEnabled("Шейдер")) {
            switch (shaderMode.getActiveMode()) {
                case "Web" -> {
                    // Cyber / Spiderweb grid across (u, v)
                    float cellU = Math.abs((u * 4.0f + time * 0.35f) % 1.0f - 0.5f);
                    float cellV = Math.abs((v * 4.0f + time * 0.35f) % 1.0f - 0.5f);
                    float d = Math.min(cellU, cellV);
                    boolean isWeb = d < 0.12f;
                    if (isWeb) {
                        r = Math.clamp((int) (r * 0.4f + 220), 0, 255);
                        g = Math.clamp((int) (g * 0.4f + 240), 0, 255);
                        b = Math.clamp((int) (b * 0.4f + 255), 0, 255);
                    } else {
                        r = (int) (r * 0.18f);
                        g = (int) (g * 0.18f);
                        b = (int) (b * 0.18f);
                        a = (int) (a * 0.4f);
                    }
                }
                case "Plasma" -> {
                    // Electric multi-colored boiling plasma
                    float p1 = (float) Math.sin(x * 10.0 + time * 3.0);
                    float p2 = (float) Math.sin(y * 8.0 - time * 2.5);
                    float p3 = (float) Math.sin((x + y + z) * 8.0 + time * 2.0);
                    float plasma = (p1 + p2 + p3) / 3.0f * 0.5f + 0.5f;

                    r = (int) (255 * (1.0f - plasma) + 0 * plasma);
                    g = (int) (10 * (1.0f - plasma) + 240 * plasma);
                    b = (int) (160 * (1.0f - plasma) + 255 * plasma);
                    a = Math.clamp((int) (a * (0.6f + 0.4f * plasma)), 0, 255);
                }
                case "Waves" -> {
                    // Pulsing horizontal energy scanlines
                    float wave = (float) ((Math.sin(y * 14.0 - time * 6.0) + 1.0) * 0.5);
                    wave = (float) Math.pow(wave, 4.0);
                    r = Math.clamp((int) (r * 0.3f + (r + 180) * wave * 0.7f), 0, 255);
                    g = Math.clamp((int) (g * 0.3f + (g + 180) * wave * 0.7f), 0, 255);
                    b = Math.clamp((int) (b * 0.3f + (b + 180) * wave * 0.7f), 0, 255);
                    a = Math.clamp((int) (a * (0.35f + 0.65f * wave)), 0, 255);
                }
                case "Water" -> {
                    // Swimming pool caustics & aquatic ripple
                    float c1 = (float) Math.sin(x * 12.0 + time * 2.2 + Math.cos(z * 10.0 + time * 1.6));
                    float c2 = (float) Math.cos(y * 12.0 - time * 2.0 + Math.sin(x * 10.0 - time * 1.4));
                    float caustic = (float) Math.pow(Math.clamp((c1 * c2 + 1.0f) * 0.5f, 0.0f, 1.0f), 2.5);

                    r = (int) (15 + (100 - 15) * caustic + 155 * Math.pow(caustic, 3));
                    g = (int) (60 + (255 - 60) * caustic);
                    b = (int) (190 + (255 - 190) * caustic);
                    a = Math.clamp((int) (a * (0.5f + 0.5f * caustic)), 0, 255);
                }
                case "Energy" -> {
                    // Crackling lightning bolts
                    float spark1 = (float) Math.pow(Math.clamp(1.0 - Math.abs(Math.sin(x * 14.0 + y * 18.0 + time * 9.0)), 0.0, 1.0), 10.0);
                    float spark2 = (float) Math.pow(Math.clamp(1.0 - Math.abs(Math.sin(y * 16.0 - z * 14.0 - time * 11.0)), 0.0, 1.0), 10.0);
                    float spark = Math.max(spark1, spark2);

                    r = Math.clamp((int) (r * 0.25f + 250f * spark), 0, 255);
                    g = Math.clamp((int) (g * 0.35f + 250f * spark), 0, 255);
                    b = Math.clamp((int) (b * 0.60f + 255f * spark), 0, 255);
                    a = Math.clamp((int) (a * (0.4f + 0.6f * spark)), 0, 255);
                }
                case "Rainbow" -> {
                    // Fluid RGB rainbow waterfall
                    float hue = (float) ((y * 0.7f - time * 0.35f + x * 0.3f) % 1.0f);
                    if (hue < 0) hue += 1.0f;
                    int rgb = Color.HSBtoRGB(hue, 0.95f, 1.0f);
                    r = (rgb >> 16) & 0xFF;
                    g = (rgb >> 8) & 0xFF;
                    b = rgb & 0xFF;
                }
                case "Fire" -> {
                    // Roaring flames rising upward
                    float flameNoise = (float) (Math.sin(x * 12.0 + time * 8.0) * Math.cos(z * 12.0 + time * 7.0));
                    float flame = Math.clamp((1.0f - y) * 0.6f + flameNoise * 0.25f, 0.0f, 1.0f);
                    float flicker = (float) (0.88 + 0.12 * Math.sin(time * 24.0));
                    flame *= flicker;

                    if (flame > 0.65f) {
                        r = 255;
                        g = (int) (180 + 75 * ((flame - 0.65f) / 0.35f));
                        b = 20;
                    } else if (flame > 0.30f) {
                        r = 255;
                        g = (int) (60 + 120 * ((flame - 0.30f) / 0.35f));
                        b = 10;
                    } else {
                        r = (int) (180 * (flame / 0.30f) + 40);
                        g = 15;
                        b = 10;
                    }
                    a = Math.clamp((int) (a * (0.5f + 0.5f * flame)), 0, 255);
                }
                case "Smoke" -> {
                    // Dark ghostly swirling mist
                    float s1 = (float) Math.sin(x * 6.0 + time * 1.5 + y * 3.0);
                    float s2 = (float) Math.cos(z * 6.0 - time * 1.8 + y * 4.0);
                    float smoke = (s1 + s2) * 0.5f * 0.5f + 0.5f;

                    r = (int) (25 + (110 - 25) * smoke);
                    g = (int) (25 + (95 - 25) * smoke);
                    b = (int) (35 + (135 - 35) * smoke);
                    a = Math.clamp((int) (a * (0.35f + 0.55f * smoke)), 0, 255);
                }
            }
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        // Default (Boxes / standard directional brightness)
        float factor = switch ((int) ny) {
            case 1 -> 1.0f;
            case -1 -> 0.52f;
            default -> (nx != 0) ? 0.70f : 0.85f;
        };
        r = Math.clamp((int) (r * factor), 0, 255);
        g = Math.clamp((int) (g * factor), 0, 255);
        b = Math.clamp((int) (b * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private Color getBaseColor(boolean isFriend) {
        if (isFriend) {
            return friendColor.getColor();
        }
        if (useThemeColor.isEnabled()) {
            return ThemeManager.getThemeColor();
        }
        return color.getColor();
    }

    private int withAlpha(Color c, int a) {
        return ((Math.clamp(a, 0, 255) & 0xFF) << 24)
                | ((c.getRed() & 0xFF) << 16)
                | ((c.getGreen() & 0xFF) << 8)
                | (c.getBlue() & 0xFF);
    }

    @SuppressWarnings("unchecked")
    public static List<ModelPart.Cuboid> getCuboids(ModelPart part) {
        if (part == null) return List.of();
        List<ModelPart.Cuboid> cached = CUBOID_CACHE.get(part);
        if (cached != null) return cached;

        if (cuboidField != null) {
            try {
                cached = (List<ModelPart.Cuboid>) cuboidField.get(part);
                if (cached != null && !cached.isEmpty()) {
                    CUBOID_CACHE.put(part, cached);
                    return cached;
                }
            } catch (Exception ignored) {
            }
        }

        List<ModelPart.Cuboid> collected = new ArrayList<>();
        MatrixStack dummy = new MatrixStack();
        part.forEachCuboid(dummy, (entry, name, idx, cuboid) -> collected.add(cuboid));
        if (!collected.isEmpty()) {
            CUBOID_CACHE.put(part, collected);
        }
        return collected;
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        if (Instance.isPostShaderActive() && ChamsPipeline.hasChamsMask()) {
            Instance.drawPostEffect();
        }
        ChamsPipeline.finishChamsFrame();
    }

    private void drawPostEffect() {
        int shaderIdx = 1;
        if (mode.isEnabled("Solid")) {
            shaderIdx = 0;
        } else if (mode.isEnabled("Металл")) {
            shaderIdx = 9;
        } else if (mode.isEnabled("Блюр")) {
            shaderIdx = 10;
        } else if (mode.isEnabled("Шейдер")) {
            shaderIdx = switch (shaderMode.getActiveMode()) {
                case "Web" -> 1;
                case "Plasma" -> 2;
                case "Waves" -> 3;
                case "Water" -> 4;
                case "Energy" -> 5;
                case "Rainbow" -> 6;
                case "Fire" -> 7;
                case "Smoke" -> 8;
                default -> 1;
            };
        }

        float fAlpha = (float) (fillAlpha.getValue() / 255.0);
        float lWidth = (float) lineWidth.getValue();
        int outlineType = lines.isEnabled() ? 1 : 0;

        Color mainColor = getBaseColor(false);
        ChamsPipeline.drawChams(mainColor, fAlpha, shaderIdx, outlineType, lWidth, 1.0f);

        if (ChamsPipeline.hasFriendChamsMask()) {
            Color friendCol = getBaseColor(true);
            ChamsPipeline.drawChamsFriend(friendCol, fAlpha, shaderIdx, outlineType, lWidth, 1.0f);
        }
    }
}
