package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.awt.Color;

@NewFunction(name = "BlockOverlay", desc = "Подсветка блока, на который вы смотрите", category = Category.RENDER)
public class BlockOverlay extends Function {
    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");
    private static final double ANIMATION_SPEED = 14.0;

    private static final RenderPipeline SEE_THROUGH_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "blockoverlay_see_through_lines"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    public static final RenderLayer SEE_THROUGH_LINES = RenderLayer.of("minar_blockoverlay_see_through_lines",
            RenderSetup.builder(SEE_THROUGH_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private static final RenderPipeline SEE_THROUGH_SHADER_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "blockoverlay_shader"))
                    .withVertexShader(Identifier.of("minar", "block_overlay_shader_vertex"))
                    .withFragmentShader(Identifier.of("minar", "block_overlay_shader_fragment"))
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    public static final RenderLayer SEE_THROUGH_SHADER = RenderLayer.of("minar_blockoverlay_shader",
            RenderSetup.builder(SEE_THROUGH_SHADER_PIPELINE)
                    .texture("Sampler0", WHITE_TEXTURE)
                    .translucent()
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .expectedBufferSize(1536)
                    .build());

    private static final RenderPipeline SEE_THROUGH_CHAMS_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "blockoverlay_chams_fill"))
                    .withVertexShader(Identifier.of("minar", "block_overlay_shader_vertex"))
                    .withFragmentShader(Identifier.of("minar", "block_overlay_chams_fill_fragment"))
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );
    public static final RenderLayer SEE_THROUGH_CHAMS_FILL = RenderLayer.of("minar_blockoverlay_chams_fill",
            RenderSetup.builder(SEE_THROUGH_CHAMS_FILL_PIPELINE)
                    .texture("Sampler0", WHITE_TEXTURE)
                    .translucent()
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .expectedBufferSize(1536)
                    .build());

    public static BlockOverlay Instance;

    private final BooleanSetting fill = new BooleanSetting("Заполнение", true);
    private final BooleanSetting outline = new BooleanSetting("Контур", true);
    private final NumberSetting lineWidth = new NumberSetting("Толщина линий", 2.0, 0.5, 5.0, 0.1);
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность", 0.5, 0.1, 1.0, 0.05);
    private final ModeSetting shaderMode = new ModeSetting("Shader", "WebShader", "ChamsFill", "Nebula", "Water");
    private final ColorSetting overlayColor = new ColorSetting("Цвет", new Color(255, 255, 255));

    private Box animatedBox;
    private long lastFrameTime;

    public BlockOverlay() {
        Instance = this;
        addSettings(fill, outline, lineWidth, fillAlpha, overlayColor, shaderMode);
    }

    @Override
    public void onDisable() {
        animatedBox = null;
        lastFrameTime = 0L;
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.render(context);
    }

    public static boolean shouldRenderVanillaBlockOutline() {
        return Instance == null || !Instance.isEnabled();
    }

    private void render(WorldRenderContext context) {
        if (!(mc.crosshairTarget instanceof BlockHitResult result) || result.getType() != HitResult.Type.BLOCK || mc.world == null) {
            animatedBox = null;
            lastFrameTime = 0L;
            return;
        }

        BlockPos pos = result.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) {
            animatedBox = null;
            lastFrameTime = 0L;
            return;
        }

        Box renderBox = updateAnimatedBox(shape.getBoundingBox().offset(pos));
        if (renderBox == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        Box cameraBox = renderBox.offset(-camera.x, -camera.y, -camera.z);

        if (fill.isEnabled()) {
            drawBoxFill(context, cameraBox);
        }

        if (outline.isEnabled()) {
            drawBoxOutline(context, cameraBox);
        }
    }

    private Box updateAnimatedBox(Box targetBox) {
        long now = System.nanoTime();
        double delta = lastFrameTime == 0L ? 1.0 : Math.min((now - lastFrameTime) / 1_000_000_000.0, 0.05);
        lastFrameTime = now;

        if (animatedBox == null) {
            animatedBox = targetBox;
            return animatedBox;
        }

        double progress = 1.0 - Math.exp(-ANIMATION_SPEED * delta);
        animatedBox = new Box(
                lerp(animatedBox.minX, targetBox.minX, progress),
                lerp(animatedBox.minY, targetBox.minY, progress),
                lerp(animatedBox.minZ, targetBox.minZ, progress),
                lerp(animatedBox.maxX, targetBox.maxX, progress),
                lerp(animatedBox.maxY, targetBox.maxY, progress),
                lerp(animatedBox.maxZ, targetBox.maxZ, progress)
        );

        return animatedBox;
    }

    private double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private void drawBoxFill(WorldRenderContext context, Box box) {
        boolean chamsFill = shaderMode.isEnabled("ChamsFill");
        VertexConsumer buffer = context.consumers().getBuffer(chamsFill ? SEE_THROUGH_CHAMS_FILL : SEE_THROUGH_SHADER);
        MatrixStack.Entry entry = context.matrices().peek();
        Color color = overlayColor.getColor();
        float alpha = (float) fillAlpha.getValue();
        float inset = 0.001F;
        float modeOffset = chamsFill ? 0.0F : shaderModeIndex() * 2.0F;

        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        quad(buffer, entry, color, x1, y2 + inset, z2, x2, y2 + inset, z2, x2, y2 + inset, z1, x1, y2 + inset, z1, alpha, modeOffset);
        quad(buffer, entry, color, x1, y1 - inset, z1, x2, y1 - inset, z1, x2, y1 - inset, z2, x1, y1 - inset, z2, alpha, modeOffset);
        quad(buffer, entry, color, x1, y1, z1 - inset, x1, y2, z1 - inset, x2, y2, z1 - inset, x2, y1, z1 - inset, alpha, modeOffset);
        quad(buffer, entry, color, x1, y1, z2 + inset, x2, y1, z2 + inset, x2, y2, z2 + inset, x1, y2, z2 + inset, alpha, modeOffset);
        quad(buffer, entry, color, x1 - inset, y1, z1, x1 - inset, y1, z2, x1 - inset, y2, z2, x1 - inset, y2, z1, alpha, modeOffset);
        quad(buffer, entry, color, x2 + inset, y1, z1, x2 + inset, y2, z1, x2 + inset, y2, z2, x2 + inset, y1, z2, alpha, modeOffset);
    }

    private void drawBoxOutline(WorldRenderContext context, Box box) {
        VertexConsumer buffer = context.consumers().getBuffer(SEE_THROUGH_LINES);
        MatrixStack.Entry entry = context.matrices().peek();
        Color color = overlayColor.getColor();
        float width = (float) lineWidth.getValue();

        line(buffer, entry, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, width);
        line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, width);

        line(buffer, entry, color, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, width);
        line(buffer, entry, color, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, width);
        line(buffer, entry, color, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, width);

        line(buffer, entry, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, width);
        line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, width);
    }

    private void line(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      double x1, double y1, double z1, double x2, double y2, double z2, float width) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0001F) return;

        buffer.vertex(entry, (float) x1, (float) y1, (float) z1)
                .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
        buffer.vertex(entry, (float) x2, (float) y2, (float) z2)
                .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
    }

    private void quad(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4,
                      float alpha, float modeOffset) {
        vertex(buffer, entry, color, x1, y1, z1, 0F, 1F + modeOffset, alpha);
        vertex(buffer, entry, color, x2, y2, z2, 1F, 1F + modeOffset, alpha);
        vertex(buffer, entry, color, x3, y3, z3, 1F, 0F + modeOffset, alpha);
        vertex(buffer, entry, color, x4, y4, z4, 0F, 0F + modeOffset, alpha);
    }

    private void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Color color, float x, float y, float z,
                        float u, float v, float alpha) {
        buffer.vertex(entry, x, y, z)
                .texture(u, v)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0F, 1F) * 255F));
    }

    private int shaderModeIndex() {
        if (shaderMode.isEnabled("WebShader")) return 2;
        if (shaderMode.isEnabled("Nebula")) return 4;
        if (shaderMode.isEnabled("Water")) return 12;
        return 2;
    }
}
