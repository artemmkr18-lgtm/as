package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.menu.ThemeManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@NewFunction(name = "HitWave", desc = "Эффект волны при ударе", category = Category.RENDER)
public class HitWave extends Function {
    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");

    private static final RenderPipeline WAVE_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "hitwave_lines_depth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .build()
    );
    private static final RenderLayer WAVE_LINES = RenderLayer.of("minar_hitwave_lines_depth",
            RenderSetup.builder(WAVE_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());



    public static HitWave Instance;

    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final BooleanSetting outline = new BooleanSetting("Контур", true);
    private final NumberSetting lineWidth = new NumberSetting("Толщина линий", 2.0, 0.5, 5.0, 0.1);
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность заливки", 0.15, 0.01, 1.0, 0.01);
    private final NumberSetting duration = new NumberSetting("Длительность (сек)", 1.5, 0.5, 3.0, 0.1);
    private final NumberSetting maxRadius = new NumberSetting("Максимальный радиус", 12.0, 5.0, 20.0, 1.0);
    private final NumberSetting waveWidth = new NumberSetting("Ширина волны", 2.5, 1.0, 5.0, 0.5);
    private final BooleanSetting useThemeColor = new BooleanSetting("Использовать цвет темы", true);
    private final ColorSetting waveColor = new ColorSetting("Цвет волны", new Color(255, 255, 255));

    private final List<WaveEffect> waveEffects = new ArrayList<>();

    public HitWave() {
        Instance = this;
        addSettings(fill, outline, lineWidth, fillAlpha, duration, maxRadius, waveWidth, useThemeColor, waveColor);
        useThemeColor.runnable(() -> waveColor.setVisible(!useThemeColor.isEnabled()));
        waveColor.setVisible(!useThemeColor.isEnabled());
    }

    @Override
    public void onDisable() {
        waveEffects.clear();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent e) {
        if (e.getTarget() == null || mc.world == null) return;
        Vec3d pos = e.getTarget().getEntityPos();
        BlockPos basePos = BlockPos.ofFloored(pos.x, pos.y - 0.1, pos.z);
        waveEffects.add(new WaveEffect(basePos, System.currentTimeMillis()));
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled() || Instance.waveEffects.isEmpty()) return;
        Instance.render(context);
    }

    private void render(WorldRenderContext context) {
        Iterator<WaveEffect> iterator = waveEffects.iterator();
        while (iterator.hasNext()) {
            WaveEffect wave = iterator.next();
            if (wave.isExpired()) {
                iterator.remove();
                continue;
            }
            wave.render(context);
        }
    }

    private class WaveEffect {
        private final BlockPos centerPos;
        private final long startTime;
        private final List<CachedBlock> cachedBlocks = new ArrayList<>();

        public WaveEffect(BlockPos centerPos, long startTime) {
            this.centerPos = centerPos;
            this.startTime = startTime;

            int maxR = (int) maxRadius.getValue();
            for (int x = -maxR; x <= maxR; x++) {
                for (int z = -maxR; z <= maxR; z++) {
                    float distSq = x * x + z * z;
                    if (distSq > maxR * maxR) continue;

                    BlockPos checkPos = centerPos.add(x, 0, z);
                    BlockPos renderPos = findSurface(checkPos);
                    if (renderPos == null) continue;

                    BlockState state = mc.world.getBlockState(renderPos);
                    VoxelShape shape = state.getOutlineShape(mc.world, renderPos);
                    if (shape.isEmpty()) continue;

                    cachedBlocks.add(new CachedBlock(renderPos, shape.getBoundingBox(), (float) Math.sqrt(distSq)));
                }
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTime > (long) (duration.getValue() * 1000);
        }

        public void render(WorldRenderContext context) {
            if (mc.world == null) return;
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = (float) (elapsed / (duration.getValue() * 1000f));
            float currentRadius = (float) (progress * maxRadius.getValue());
            float waveW = (float) waveWidth.getValue();
            float globalAlpha = (float) Math.pow(1.0f - progress, 0.6);

            int rendered = 0;
            int maxPerFrame = 400;

            float minRadSq = (currentRadius - waveW) * (currentRadius - waveW);
            float maxRadSq = (currentRadius + 0.5f) * (currentRadius + 0.5f);

            Color baseColor = getColor();
            int red = useThemeColor.isEnabled() ? brighten(baseColor.getRed()) : baseColor.getRed();
            int green = useThemeColor.isEnabled() ? brighten(baseColor.getGreen()) : baseColor.getGreen();
            int blue = useThemeColor.isEnabled() ? brighten(baseColor.getBlue()) : baseColor.getBlue();
            
            Color glowColor = new Color(red, green, blue);
            Color coreColor = new Color(core(red), core(green), core(blue));

            int maxR = (int) maxRadius.getValue();

            Vec3d camera = context.worldState().cameraRenderState.pos;

            for (CachedBlock cb : cachedBlocks) {
                if (rendered >= maxPerFrame) break;
                float distance = cb.distance;
                if (distance < currentRadius - waveW || distance > currentRadius + 0.5f) continue;

                rendered++;
                float localAlpha = 1.0f - Math.abs(distance - currentRadius) / waveW;
                localAlpha = Math.max(0, Math.min(1, localAlpha)) * globalAlpha;

                if (localAlpha > 0.05f) {
                    Box box = cb.box.offset(cb.pos).offset(-camera.x, -camera.y, -camera.z);
                    if (fill.isEnabled()) {
                        float fAlpha = (float) (fillAlpha.getValue() * localAlpha);
                        drawBoxFill(context, box, glowColor, fAlpha);
                    }
                    if (outline.isEnabled()) {
                        drawBoxOutline(context, box, coreColor, localAlpha);
                    }
                }
            }
        }

        private BlockPos findSurface(BlockPos pos) {
            for (int y = 2; y >= -4; y--) {
                BlockPos p = pos.up(y);
                BlockState state = mc.world.getBlockState(p);
                BlockState upState = mc.world.getBlockState(p.up());
                
                boolean currentSolid = !state.getCollisionShape(mc.world, p).isEmpty();
                boolean upSolid = !upState.getCollisionShape(mc.world, p.up()).isEmpty();

                if (currentSolid && !upSolid) {
                    return p;
                }
            }
            return null;
        }

        private class CachedBlock {
            final BlockPos pos;
            final Box box;
            final float distance;

            CachedBlock(BlockPos pos, Box box, float distance) {
                this.pos = pos;
                this.box = box;
                this.distance = distance;
            }
        }
    }

    private Color getColor() {
        if (useThemeColor.isEnabled()) {
            return ThemeManager.getThemeColor();
        }
        return waveColor.getColor();
    }

    private static int brighten(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.45f), 0, 255);
    }

    private static int core(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.72f), 0, 255);
    }

    private void drawBoxFill(WorldRenderContext context, Box box, Color color, float alpha) {
        VertexConsumer buffer = context.consumers().getBuffer(
                ez.minar.utils.render.Render3DUtils.texturedLayer(WHITE_TEXTURE));
        MatrixStack.Entry entry = context.matrices().peek();
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

    private void drawBoxOutline(WorldRenderContext context, Box box, Color color, float alpha) {
        VertexConsumer buffer = context.consumers().getBuffer(WAVE_LINES);
        MatrixStack.Entry entry = context.matrices().peek();
        float width = (float) lineWidth.getValue();

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
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0F, 1F) * 255))
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
        buffer.vertex(entry, (float) x2, (float) y2, (float) z2)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0F, 1F) * 255))
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
    }

    private void quad(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4,
                      float alpha) {
        vertex(buffer, entry, color, x1, y1, z1, 0F, 1F, alpha);
        vertex(buffer, entry, color, x2, y2, z2, 1F, 1F, alpha);
        vertex(buffer, entry, color, x3, y3, z3, 1F, 0F, alpha);
        vertex(buffer, entry, color, x4, y4, z4, 0F, 0F, alpha);
    }

    private void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Color color, float x, float y, float z,
                        float u, float v, float alpha) {
        buffer.vertex(entry, x, y, z)
                .texture(u, v)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0F, 1F) * 255F))
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0f, 1f, 0f);
    }
}
