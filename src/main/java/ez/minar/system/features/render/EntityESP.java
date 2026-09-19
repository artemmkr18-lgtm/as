/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.pipeline.BlendFunction
 *  com.mojang.blaze3d.pipeline.RenderPipeline
 *  com.mojang.blaze3d.pipeline.RenderPipeline$Snippet
 *  com.mojang.blaze3d.platform.DepthTestFunction
 *  net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
 *  net.minecraft.client.gl.RenderPipelines
 *  net.minecraft.client.gui.screen.ingame.InventoryScreen
 *  net.minecraft.client.model.ModelPart
 *  net.minecraft.client.model.ModelPart$Cuboid
 *  net.minecraft.client.render.LayeringTransform
 *  net.minecraft.client.render.OutputTarget
 *  net.minecraft.client.render.OverlayTexture
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.client.render.RenderLayers
 *  net.minecraft.client.render.RenderSetup
 *  net.minecraft.client.render.VertexConsumer
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  net.minecraft.client.render.entity.model.BipedEntityModel
 *  net.minecraft.client.render.entity.state.LivingEntityRenderState
 *  net.minecraft.client.render.entity.state.PlayerEntityRenderState
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.client.util.math.MatrixStack$Entry
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.EntityType
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.Box
 *  net.minecraft.util.math.RotationAxis
 *  net.minecraft.util.math.Vec3d
 *  org.joml.Quaternionfc
 */
package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.MultiSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.pipeline.ChamsPipeline;
import java.awt.Color;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@NewFunction(name="EntityESP", desc="ESP для игроков и сущностей", category=Category.RENDER)
public class EntityESP
extends Function {
    public static EntityESP Instance;
    private static final Identifier WHITE_TEXTURE;
    public static final RenderPipeline GLOW_BOX_LINES_PIPELINE;
    public static final RenderLayer GLOW_BOX_LINES;
    public static final RenderPipeline GLOW_BOX_LINES_NO_DEPTH_PIPELINE;
    public static final RenderLayer GLOW_BOX_LINES_NO_DEPTH;
    private static final RenderPipeline ENTITY_SHADER_PIPELINE;
    public static final RenderLayer ENTITY_SHADER;
    private static final RenderPipeline ENTITY_CHAMS_FILL_PIPELINE;
    public static final RenderLayer ENTITY_CHAMS_FILL;
    private final MultiSetting targets = new MultiSetting("Цели", "Враждебные", "Животные", "Друзья", "Игроки");
    private final MultiSetting effects = new MultiSetting("Эффекты", "Box", "Обводка");
    private final ColorSetting friendColor = new ColorSetting("\u0426\u0432\u0435\u0442 \u0434\u0440\u0443\u0437\u0435\u0439", new Color(85, 255, 120));
    private final BooleanSetting themeColor = new BooleanSetting("\u0426\u0432\u0435\u0442 \u0442\u0435\u043c\u044b", true);
    private final ColorSetting color = new ColorSetting("\u0426\u0432\u0435\u0442", new Color(255, 255, 255));
    private final NumberSetting fillAlpha = new NumberSetting("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c \u0437\u0430\u043b\u0438\u0432\u043a\u0438", 0.65, 0.05, 1.0, 0.05);
    private final BooleanSetting respectDepth = new BooleanSetting("\u041d\u0435 \u0432\u0438\u0434\u0435\u0442\u044c \u0441\u043a\u0432\u043e\u0437\u044c \u0441\u0442\u0435\u043d\u044b", true);
    private final ModeSetting outlineMode = new ModeSetting("\u0422\u0438\u043f \u043e\u0431\u0432\u043e\u0434\u043a\u0438", "\u041e\u0431\u044b\u0447\u043d\u0430\u044f", "Glow");
    private final NumberSetting outlineWidth = new NumberSetting("\u0428\u0438\u0440\u0438\u043d\u0430 \u043e\u0431\u0432\u043e\u0434\u043a\u0438", 2.0, 1.0, 8.0, 0.5);
    private final NumberSetting glowStrength = new NumberSetting("\u0421\u0438\u043b\u0430 \u0441\u0432\u0435\u0447\u0435\u043d\u0438\u044f", 1.15, 0.2, 3.0, 0.05);
    private final ModeSetting boxMode = new ModeSetting("Вид Box", "Solid", "Dotted");
    private final NumberSetting boxLineWidth = new NumberSetting("Ширина линии Box", 2.5, 0.5, 8.0, 0.5);
    private final BooleanSetting boxRounding = new BooleanSetting("Закругление", false);
    private final Map<LivingEntityRenderState, LivingEntity> renderedEntities = new WeakHashMap<>();

    public EntityESP() {
        Instance = this;
        this.addSettings(this.targets, this.effects, this.boxMode, this.boxLineWidth, this.boxRounding, this.friendColor, this.themeColor, this.color, this.fillAlpha, this.respectDepth, this.outlineMode, this.outlineWidth, this.glowStrength);
        this.themeColor.runnable(this::updateVisibility);
        this.targets.runnable(this::updateVisibility);
        this.effects.runnable(this::updateVisibility);
        this.outlineMode.runnable(this::updateVisibility);
        this.updateVisibility();
    }

    public void renderEntityMask(Runnable renderLiving, LivingEntityRenderState state) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.effects.isEnabled("Обводка")) {
            boolean friend = this.isFriendPlayer(state);
            if (friend) {
                ChamsPipeline.renderFriendEntityMask(renderLiving, this.respectDepth.isEnabled());
            } else {
                ChamsPipeline.renderEntityMask(renderLiving, this.respectDepth.isEnabled());
            }
        } else {
            renderLiving.run();
        }
    }

    private int getOutlineType() {
        if (!this.effects.isEnabled("\u041e\u0431\u0432\u043e\u0434\u043a\u0430")) return 0;
        if (this.outlineMode.isEnabled("Glow")) return 2;
        return 1;
    }

    public void drawPostEffect() {
        if (this.isEnabled() && this.effects.isEnabled("\u041e\u0431\u0432\u043e\u0434\u043a\u0430")) {
            int outlineType = this.getOutlineType();
            if (outlineType != 0) {
                if (ChamsPipeline.hasNormalEntityMask()) {
                    Color selected = this.selectedColor();
                    ChamsPipeline.draw(selected, 0.0f, -1, outlineType, (float)this.outlineWidth.getValue(), (float)this.glowStrength.getValue());
                }
                if (ChamsPipeline.hasFriendEntityMask()) {
                    Color friendCol = this.friendColor.getColor();
                    ChamsPipeline.drawFriend(friendCol, 0.0f, -1, outlineType, (float)this.outlineWidth.getValue(), (float)this.glowStrength.getValue());
                }
            }
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        org.joml.Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(mc.options.getFov().getValue().floatValue());
        org.joml.Matrix4f viewProj = new org.joml.Matrix4f(proj);
        viewProj.mul(context.matrices().peek().getPositionMatrix());
        ChamsPipeline.capturedInvViewProj = viewProj.invert();

        EntityESP esp = FunctionManager.getFunction(EntityESP.class);
        if (esp != null && esp.isEnabled()) {
            if (esp.effects.isEnabled("Box")) {
                esp.renderGlowBoxes(context);
            }
            if (esp.effects.isEnabled("\u041e\u0431\u0432\u043e\u0434\u043a\u0430") && ChamsPipeline.hasEntityMask()) {
                esp.drawPostEffect();
            }
        }
        ChamsPipeline.finishEntityFrame();
    }

    public boolean shouldRenderLiving(LivingEntityRenderState state) {
        if (!this.isEnabled() || state == null || state.invisible) {
            return false;
        }
        if (!this.effects.isEnabled("Обводка")) {
            return false;
        }
        LivingEntity entity = this.renderedEntities.get(state);
        return entity != null && !this.isInventorySelfPreview(state) && this.isSelectedTarget(entity);
    }

    public void trackRenderedEntity(LivingEntity entity, LivingEntityRenderState state) {
        this.renderedEntities.put(state, entity);
    }

    private void renderGlowBoxes(WorldRenderContext context) {
        if (this.mc.world == null || this.mc.player == null) {
            return;
        }
        MatrixStack.Entry entry = context.matrices().peek();
        Vec3d camera = context.worldState().cameraRenderState.pos;
        float tickDelta = this.mc.getRenderTickCounter().getTickProgress(false);
        for (Entity entity : this.mc.world.getEntities()) {
            PlayerEntity player;
            LivingEntity living;
            if (!(entity instanceof LivingEntity) || !this.shouldRenderGlowBox(living = (LivingEntity)entity)) continue;
            boolean friend = entity instanceof PlayerEntity && FriendManager.isFriend((player = (PlayerEntity)entity).getName().getString());
            Color renderColor = friend ? this.friendColor.getColor() : this.selectedColor();
            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Vec3d offset = lerped.subtract(entity.getEntityPos());
            Box box = entity instanceof PlayerEntity
                    ? ez.minar.utils.render.FiguraBounds.get((PlayerEntity) entity)
                            .offset(offset)
                            .offset(-camera.x, -camera.y, -camera.z)
                    : entity.getBoundingBox().offset(offset)
                            .offset(-camera.x, -camera.y, -camera.z);
            this.drawGlowBox(context.consumers(), entry, box, renderColor, (float)this.fillAlpha.getValue());
        }
    }

    private void drawGlowBox(VertexConsumerProvider consumers, MatrixStack.Entry entry, Box box, Color color, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        VertexConsumer fillBuffer = consumers.getBuffer(
                ez.minar.utils.render.Render3DUtils.texturedLayer(WHITE_TEXTURE));
        Color fillColor = this.withAlpha(color, alpha * 0.18f);
        if (this.boxRounding.isEnabled()) {
            this.drawRoundedBoxFill(fillBuffer, entry, box, fillColor);
        } else {
            this.drawBoxFill(fillBuffer, entry, box, fillColor);
        }
        VertexConsumer lineBuffer = consumers.getBuffer(this.respectDepth.isEnabled() ? GLOW_BOX_LINES : GLOW_BOX_LINES_NO_DEPTH);
        float width = (float)this.boxLineWidth.getValue();
        Color lineColor = this.withAlpha(color, alpha);
        if (this.boxRounding.isEnabled()) {
            this.drawRoundedBoxOutline(lineBuffer, entry, box, lineColor, width, this.boxMode.isEnabled("Dotted"));
        } else if (this.boxMode.isEnabled("Dotted")) {
            this.drawDottedBoxOutline(lineBuffer, entry, box, lineColor, width);
        } else {
            this.drawBoxOutline(lineBuffer, entry, box, lineColor, width);
        }
    }

    private void drawBoxFill(VertexConsumer buffer, MatrixStack.Entry entry, Box box, Color color) {
        this.quad(buffer, entry, color, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ);
        this.quad(buffer, entry, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        this.quad(buffer, entry, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ);
        this.quad(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.minY, box.maxZ);
        this.quad(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, box.minX, box.minY, box.minZ);
        this.quad(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ);
    }

    private void drawRoundedBoxFill(VertexConsumer buffer, MatrixStack.Entry entry, Box box, Color color) {
        double radius = this.getBoxRoundingRadius(box);
        if (radius <= 0.001) {
            this.drawBoxFill(buffer, entry, box, color);
            return;
        }

        int cornerSteps = 5;
        double[][] perimeter = new double[cornerSteps * 4][2];
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        int index = 0;

        for (int corner = 0; corner < 4; corner++) {
            double cornerX = corner == 0 || corner == 3 ? box.maxX - radius : box.minX + radius;
            double cornerZ = corner < 2 ? box.maxZ - radius : box.minZ + radius;
            double startAngle = corner * Math.PI * 0.5;
            for (int step = 0; step < cornerSteps; step++) {
                double angle = startAngle + Math.PI * 0.5 * step / (cornerSteps - 1);
                perimeter[index][0] = cornerX + Math.cos(angle) * radius;
                perimeter[index][1] = cornerZ + Math.sin(angle) * radius;
                index++;
            }
        }

        for (int i = 0; i < perimeter.length; i++) {
            double[] current = perimeter[i];
            double[] next = perimeter[(i + 1) % perimeter.length];

            // Degenerate quads form triangle fans for the rounded caps.
            this.quad(buffer, entry, color,
                    centerX, box.maxY, centerZ,
                    current[0], box.maxY, current[1],
                    next[0], box.maxY, next[1],
                    centerX, box.maxY, centerZ);
            this.quad(buffer, entry, color,
                    centerX, box.minY, centerZ,
                    next[0], box.minY, next[1],
                    current[0], box.minY, current[1],
                    centerX, box.minY, centerZ);

            this.quad(buffer, entry, color,
                    current[0], box.minY, current[1],
                    next[0], box.minY, next[1],
                    next[0], box.maxY, next[1],
                    current[0], box.maxY, current[1]);
        }
    }

    private void quad(VertexConsumer buffer, MatrixStack.Entry entry, Color color, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        this.vertex(buffer, entry, color, x1, y1, z1, 0.0f, 1.0f);
        this.vertex(buffer, entry, color, x2, y2, z2, 1.0f, 1.0f);
        this.vertex(buffer, entry, color, x3, y3, z3, 1.0f, 0.0f);
        this.vertex(buffer, entry, color, x4, y4, z4, 0.0f, 0.0f);
    }

    private void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Color color, double x, double y, double z, float u, float v) {
        buffer.vertex(entry, (float)x, (float)y, (float)z).texture(u, v).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry, 0.0f, 1.0f, 0.0f);
    }

    private void drawBoxOutline(VertexConsumer buffer, MatrixStack.Entry entry, Box box, Color color, float width) {
        this.line(buffer, entry, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, width);
        this.line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, width);
        this.line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, width);
        this.line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, width);
        this.line(buffer, entry, color, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, width);
        this.line(buffer, entry, color, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, width);
        this.line(buffer, entry, color, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, width);
        this.line(buffer, entry, color, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, width);
        this.line(buffer, entry, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, width);
        this.line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, width);
        this.line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, width);
        this.line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, width);
    }

    private void drawDottedBoxOutline(VertexConsumer buffer, MatrixStack.Entry entry, Box box, Color color, float width) {
        double[][] edges = {
                {box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ},
                {box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ},
                {box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ},
                {box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ},
                {box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ},
                {box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ},
                {box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ},
                {box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ},
                {box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ},
                {box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ},
                {box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ},
                {box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ}
        };
        for (double[] edge : edges) {
            this.dottedLine(buffer, entry, color, edge, width);
        }
    }

    private void drawRoundedBoxOutline(VertexConsumer buffer, MatrixStack.Entry entry, Box box,
                                       Color color, float width, boolean dotted) {
        double radius = this.getBoxRoundingRadius(box);
        if (radius <= 0.001) {
            this.drawBoxOutline(buffer, entry, box, color, width);
            return;
        }

        // Rounded rectangles on the top and bottom. A single XZ arc is enough
        // for every visible corner; XY/YZ arcs create redundant loops.
        for (int sy : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                drawStyledLine(buffer, entry, color,
                        box.minX + radius, side(box.minY, box.maxY, sy), side(box.minZ, box.maxZ, sz),
                        box.maxX - radius, side(box.minY, box.maxY, sy), side(box.minZ, box.maxZ, sz),
                        width, dotted);
            }
        }
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                drawStyledLine(buffer, entry, color,
                        side(box.minX, box.maxX, sx), side(box.minY, box.maxY, sy), box.minZ + radius,
                        side(box.minX, box.maxX, sx), side(box.minY, box.maxY, sy), box.maxZ - radius,
                        width, dotted);
            }
        }

        double diagonalInset = radius * (1.0 - 1.0 / Math.sqrt(2.0));
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                for (int sy : new int[]{-1, 1}) {
                    double cx = side(box.minX, box.maxX, sx) - sx * radius;
                    double cz = side(box.minZ, box.maxZ, sz) - sz * radius;
                    drawCornerArc(buffer, entry, color, width, dotted,
                            cx, side(box.minY, box.maxY, sy), cz, sx, sz, radius);
                }
                double verticalX = side(box.minX, box.maxX, sx) - sx * diagonalInset;
                double verticalZ = side(box.minZ, box.maxZ, sz) - sz * diagonalInset;
                drawStyledLine(buffer, entry, color,
                        verticalX, box.minY, verticalZ,
                        verticalX, box.maxY, verticalZ, width, dotted);
            }
        }
    }

    private double getBoxRoundingRadius(Box box) {
        return Math.min(0.14, Math.min(
                box.maxX - box.minX,
                Math.min(box.maxY - box.minY, box.maxZ - box.minZ)) * 0.22);
    }

    private void drawCornerArc(VertexConsumer buffer, MatrixStack.Entry entry, Color color, float width,
                               boolean dotted, double cx, double cy, double cz,
                               int firstSign, int secondSign, double radius) {
        int steps = 5;
        for (int i = 0; i < steps; i++) {
            if (dotted && (i & 1) != 0) continue;
            double a = Math.PI * 0.5 * i / steps;
            double b = Math.PI * 0.5 * (i + 1) / steps;
            this.line(buffer, entry, color,
                    cx + firstSign * radius * Math.cos(a), cy, cz + secondSign * radius * Math.sin(a),
                    cx + firstSign * radius * Math.cos(b), cy, cz + secondSign * radius * Math.sin(b),
                    width);
        }
    }

    private void drawStyledLine(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                                double x1, double y1, double z1, double x2, double y2, double z2,
                                float width, boolean dotted) {
        if (dotted) {
            this.dottedLine(buffer, entry, color, new double[]{x1, y1, z1, x2, y2, z2}, width);
        } else {
            this.line(buffer, entry, color, x1, y1, z1, x2, y2, z2, width);
        }
    }

    private double side(double min, double max, int sign) {
        return sign < 0 ? min : max;
    }

    private void dottedLine(VertexConsumer buffer, MatrixStack.Entry entry, Color color, double[] edge, float width) {
        double dx = edge[3] - edge[0], dy = edge[4] - edge[1], dz = edge[5] - edge[2];
        int segments = Math.max(1, (int)Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.12));
        for (int i = 0; i < segments; i += 2) {
            double from = (double)i / segments;
            double to = (double)Math.min(i + 1, segments) / segments;
            this.line(buffer, entry, color,
                    edge[0] + dx * from, edge[1] + dy * from, edge[2] + dz * from,
                    edge[0] + dx * to, edge[1] + dy * to, edge[2] + dz * to, width);
        }
    }

    private void line(VertexConsumer buffer, MatrixStack.Entry entry, Color color, double x1, double y1, double z1, double x2, double y2, double z2, float width) {
        float dx = (float)(x2 - x1);
        float dy = (float)(y2 - y1);
        float dz = (float)(z2 - z1);
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0E-4f) {
            return;
        }
        buffer.vertex(entry, (float)x1, (float)y1, (float)z1).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).normal(entry, dx / length, dy / length, dz / length).lineWidth(width);
        buffer.vertex(entry, (float)x2, (float)y2, (float)z2).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).normal(entry, dx / length, dy / length, dz / length).lineWidth(width);
    }

    private Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    private boolean shouldRenderGlowBox(LivingEntity entity) {
        if (!entity.isAlive() || entity.isInvisible()) {
            return false;
        }
        if (entity == this.mc.player) {
            return false;
        }
        return this.isSelectedTarget(entity);
    }

    private boolean isSelectedTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity)entity;
            return FriendManager.isFriend(player.getName().getString())
                    ? this.targets.isEnabled("Друзья")
                    : this.targets.isEnabled("Игроки");
        }
        if (entity instanceof HostileEntity) return this.targets.isEnabled("Враждебные");
        return entity instanceof AnimalEntity && this.targets.isEnabled("Животные");
    }
    private boolean isInventorySelfPreview(LivingEntityRenderState state) {
        if (!(this.mc.currentScreen instanceof InventoryScreen)) return false;
        if (this.mc.player == null) return false;
        if (!(state instanceof PlayerEntityRenderState)) return false;
        PlayerEntityRenderState playerState = (PlayerEntityRenderState)state;
        if (playerState.id != this.mc.player.getId()) return false;
        return true;
    }

    private boolean isInventorySelfPreview(Entity entity) {
        return this.mc.currentScreen instanceof InventoryScreen && this.mc.player != null && entity == this.mc.player;
    }

    private void updateVisibility() {
        this.color.setVisible(!this.themeColor.isEnabled());
        this.friendColor.setVisible(this.targets.isEnabled("Друзья"));
        this.boxMode.setVisible(this.effects.isEnabled("Box"));
        this.boxLineWidth.setVisible(this.effects.isEnabled("Box"));
        this.boxRounding.setVisible(this.effects.isEnabled("Box"));
        this.fillAlpha.setVisible(this.effects.isEnabled("Box"));
        this.respectDepth.setVisible(this.effects.isEnabled("Box") || this.effects.isEnabled("Обводка"));

        boolean hasOutline = this.effects.isEnabled("Обводка");
        this.outlineMode.setVisible(hasOutline);
        this.outlineWidth.setVisible(hasOutline);
        this.glowStrength.setVisible(hasOutline && this.outlineMode.isEnabled("Glow"));
    }

    private boolean isFriendPlayer(LivingEntityRenderState state) {
        PlayerEntityRenderState playerState;
        block5: {
            block4: {
                if (!(state instanceof PlayerEntityRenderState)) break block4;
                playerState = (PlayerEntityRenderState)state;
                if (this.mc.world != null) break block5;
            }
            return false;
        }
        Entity entity = this.mc.world.getEntityById(playerState.id);
        if (!(entity instanceof PlayerEntity)) {
            return false;
        }
        PlayerEntity player = (PlayerEntity)entity;
        return FriendManager.isFriend(player.getName().getString());
    }

    private Color selectedColor() {
        Color selected;
        Color color = selected = this.themeColor.isEnabled() ? ThemeManager.getThemeColor() : this.color.getColor();
        if (!this.themeColor.isEnabled()) {
            return selected;
        }
        return new Color(EntityESP.brighten(selected.getRed()), EntityESP.brighten(selected.getGreen()), EntityESP.brighten(selected.getBlue()));
    }

    private static int brighten(int value) {
        return Math.clamp((int)((float)value + (float)(255 - value) * 0.45f), 0, 255);
    }

    public boolean hasOutlineEffect() {
        return this.isEnabled() && this.effects.isEnabled("\u041e\u0431\u0432\u043e\u0434\u043a\u0430");
    }

    public boolean isRespectDepthEnabled() {
        return this.respectDepth.isEnabled();
    }

    private static final DepthTestFunction LEQUAL_FUNC = getLequalFunc();

    private static DepthTestFunction getLequalFunc() {
        return DepthTestFunction.LEQUAL_DEPTH_TEST;
    }

    static {
        WHITE_TEXTURE = Identifier.of((String)"minecraft", (String)"textures/block/white_concrete.png");
        GLOW_BOX_LINES_PIPELINE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET}).withLocation(Identifier.of((String)"minar", (String)"entitychams_glow_box_lines")).withBlend(BlendFunction.LIGHTNING).withDepthTestFunction(LEQUAL_FUNC).withDepthWrite(false).build());
        GLOW_BOX_LINES = RenderLayer.of((String)"minar_entitychams_glow_box_lines", (RenderSetup)RenderSetup.builder((RenderPipeline)GLOW_BOX_LINES_PIPELINE).layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).outputTarget(OutputTarget.MAIN_TARGET).build());
        GLOW_BOX_LINES_NO_DEPTH_PIPELINE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET}).withLocation(Identifier.of((String)"minar", (String)"entitychams_glow_box_lines_no_depth")).withBlend(BlendFunction.LIGHTNING).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
        GLOW_BOX_LINES_NO_DEPTH = RenderLayer.of((String)"minar_entitychams_glow_box_lines_no_depth", (RenderSetup)RenderSetup.builder((RenderPipeline)GLOW_BOX_LINES_NO_DEPTH_PIPELINE).layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).outputTarget(OutputTarget.MAIN_TARGET).build());

        ENTITY_SHADER_PIPELINE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET}).withLocation(Identifier.of((String)"minar", (String)"entityesp_shader")).withVertexShader(Identifier.of((String)"minar", (String)"block_overlay_shader_vertex")).withFragmentShader(Identifier.of((String)"minar", (String)"block_overlay_shader_fragment")).withUniform("Globals", UniformType.UNIFORM_BUFFER).withBlend(BlendFunction.TRANSLUCENT).withDepthTestFunction(LEQUAL_FUNC).withDepthWrite(false).withCull(false).build());
        ENTITY_SHADER = RenderLayer.of((String)"minar_entityesp_shader", (RenderSetup)RenderSetup.builder((RenderPipeline)ENTITY_SHADER_PIPELINE).texture("Sampler0", WHITE_TEXTURE).translucent().outputTarget(OutputTarget.MAIN_TARGET).expectedBufferSize(1536).build());

        ENTITY_CHAMS_FILL_PIPELINE = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET}).withLocation(Identifier.of((String)"minar", (String)"entityesp_chams_fill")).withVertexShader(Identifier.of((String)"minar", (String)"block_overlay_shader_vertex")).withFragmentShader(Identifier.of((String)"minar", (String)"block_overlay_chams_fill_fragment")).withUniform("Globals", UniformType.UNIFORM_BUFFER).withBlend(BlendFunction.TRANSLUCENT).withDepthTestFunction(LEQUAL_FUNC).withDepthWrite(false).withCull(false).build());
        ENTITY_CHAMS_FILL = RenderLayer.of((String)"minar_entityesp_chams_fill", (RenderSetup)RenderSetup.builder((RenderPipeline)ENTITY_CHAMS_FILL_PIPELINE).texture("Sampler0", WHITE_TEXTURE).translucent().outputTarget(OutputTarget.MAIN_TARGET).expectedBufferSize(1536).build());

    }
}
