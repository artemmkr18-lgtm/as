package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.awt.Color;

@NewFunction(name = "SkeletonESP", desc = "Каркас игроков поверх мира", category = Category.RENDER)
public class SkeletonESP extends Function {
    // The reference pose math mixes radians and degrees; these are the resulting tilts.
    private static final float GLIDE_TILT = 89.95f;
    private static final float SNEAK_TILT = 28.65f;
    private static final float GLIDE_ARM = -11.5f;
    private static final float GLIDE_LEG = 5.7f;

    // LimbAnimator amplitude is blocks-per-tick (~0.1 while walking), so it needs a gain
    // before it turns into a visible stride. Positive X rotation leans a limb backwards.
    private static final float STRIDE_GAIN = 3.0f;
    private static final float ARM_SWING = 45.0f;
    private static final float LEG_SWING = 38.0f;
    private static final float KNEE_BEND = 1.2f;
    private static final float ELBOW_BEND = 0.5f;
    private static final float HAND_SWING = 45.8f;

    private static final int[][] BONES = {
            {0, 1}, {0, 2}, {2, 3},
            {0, 4}, {0, 7},
            {4, 5}, {5, 6},
            {7, 8}, {8, 9},
            {3, 10}, {3, 13},
            {10, 11}, {11, 12},
            {13, 14}, {14, 15}
    };
    // 0 neck, 1 head, 2 waist, 3 pelvis, 4-6 left arm, 7-9 right arm, 10-12 left leg, 13-15 right leg.
    private static final int JOINT_COUNT = 16;

    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "skeleton_esp_lines"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build());
    private static final RenderLayer LINES = RenderLayer.of("minar_skeleton_esp_lines",
            RenderSetup.builder(LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());
    private static final RenderPipeline LINES_THROUGH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "skeleton_esp_lines_through"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build());
    private static final RenderLayer LINES_THROUGH = RenderLayer.of("minar_skeleton_esp_lines_through",
            RenderSetup.builder(LINES_THROUGH_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    public static SkeletonESP Instance;

    private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", false);
    private final ColorSetting color = new ColorSetting("Цвет", Color.WHITE);
    private final NumberSetting lineWidth = new NumberSetting("Толщина линии", 1.6, 0.5, 4.0, 0.1);
    private final NumberSetting opacity = new NumberSetting("Непрозрачность", 1.0, 0.15, 1.0, 0.05);
    private final BooleanSetting renderSelf = new BooleanSetting("Рендерить себя", false);
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь блоки", true);

    public SkeletonESP() {
        Instance = this;
        addSettings(themeColor, color, lineWidth, opacity, renderSelf, throughWalls);
        themeColor.runnable(() -> color.setVisible(!themeColor.isEnabled()));
        color.setVisible(false);
    }

    public static void renderWorld(WorldRenderContext context) {
        SkeletonESP esp = FunctionManager.getFunction(SkeletonESP.class);
        if (esp == null || !esp.isEnabled()) return;
        esp.render(context);
    }

    private void render(WorldRenderContext context) {
        if (mc.world == null || mc.player == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d camera = context.worldState().cameraRenderState.pos;
        MatrixStack.Entry entry = context.matrices().peek();
        VertexConsumer buffer = context.consumers().getBuffer(throughWalls.isEnabled() ? LINES_THROUGH : LINES);

        Color source = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();
        int alpha = Math.round(Math.clamp((float) opacity.getValue(), 0.0f, 1.0f) * 255.0f);
        Color lineColor = new Color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
        float width = (float) lineWidth.getValue();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player) || player.isInvisible()) continue;
            if (player == mc.player && (!renderSelf.isEnabled() || firstPerson)) continue;

            Vec3d origin = player.getLerpedPos(tickDelta);
            Vec3d[] joints = pose(player, tickDelta);
            for (int[] bone : BONES) {
                Vec3d start = joints[bone[0]];
                Vec3d end = joints[bone[1]];
                line(buffer, entry, lineColor, width,
                        origin.x + start.x - camera.x, origin.y + start.y - camera.y, origin.z + start.z - camera.z,
                        origin.x + end.x - camera.x, origin.y + end.y - camera.y, origin.z + end.z - camera.z);
            }
        }
    }

    private Vec3d[] pose(PlayerEntity player, float tickDelta) {
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float pitch = player.getPitch(tickDelta);
        float swing = player.limbAnimator.getAnimationProgress(tickDelta);
        float stride = MathHelper.clamp(player.limbAnimator.getAmplitude(tickDelta) * STRIDE_GAIN, 0.0f, 1.0f);
        float handSwing = player.getHandSwingProgress(tickDelta);
        float height = player.getHeight();
        boolean gliding = player.isGliding();
        boolean sneaking = player.isSneaking();

        Vec3d[] joints = new Vec3d[JOINT_COUNT];
        MatrixStack ms = new MatrixStack();

        if (sneaking && !gliding) {
            ms.translate(0.0, 0.125, 0.0);
        }
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        if (gliding) {
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(GLIDE_TILT + pitch));
        } else if (sneaking) {
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(SNEAK_TILT));
        }
        if (sneaking && !gliding) {
            ms.translate(0.0, -0.13, 0.0);
        }

        ms.push();
        ms.translate(0.0, height * 0.75f, 0.0);
        joints[0] = positionOf(ms);

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - headYaw));
        if (!gliding) {
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
        ms.translate(0.0, height * 0.15f, 0.0);
        joints[1] = positionOf(ms);
        ms.pop();

        ms.push();
        ms.translate(0.25, 0.0, 0.0);
        joints[4] = positionOf(ms);
        float leftArm = gliding ? GLIDE_ARM : MathHelper.cos(swing * 0.6662f + (float) Math.PI) * ARM_SWING * stride;
        if (gliding) {
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-5.0f));
        }
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(leftArm));
        ms.translate(0.0, -0.25, 0.0);
        joints[5] = positionOf(ms);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-Math.max(0.0f, -leftArm) * ELBOW_BEND));
        ms.translate(0.0, -0.25, 0.0);
        joints[6] = positionOf(ms);
        ms.pop();

        ms.push();
        ms.translate(-0.25, 0.0, 0.0);
        joints[7] = positionOf(ms);
        float rightArm = gliding ? GLIDE_ARM : MathHelper.cos(swing * 0.6662f) * ARM_SWING * stride;
        if (handSwing > 0.0f && !gliding) {
            float collapse = 1.0f - handSwing;
            float swingRot = MathHelper.sin(collapse * collapse * (float) Math.PI);
            float yawFactor = MathHelper.clamp((headYaw - bodyYaw) / 75.0f, -1.0f, 1.0f);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swingRot * 15.0f * yawFactor));
            rightArm -= swingRot * HAND_SWING;
        }
        if (gliding) {
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(5.0f));
        }
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rightArm));
        ms.translate(0.0, -0.25, 0.0);
        joints[8] = positionOf(ms);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-Math.max(0.0f, -rightArm) * ELBOW_BEND));
        ms.translate(0.0, -0.25, 0.0);
        joints[9] = positionOf(ms);
        ms.pop();

        ms.pop();

        ms.push();
        ms.translate(0.0, height * 0.5f, 0.0);
        joints[2] = positionOf(ms);
        ms.pop();

        ms.push();
        ms.translate(0.0, height * 0.3f, 0.0);
        joints[3] = positionOf(ms);

        ms.push();
        ms.translate(0.125, 0.0, 0.0);
        joints[10] = positionOf(ms);
        float leftLeg = gliding ? GLIDE_LEG : MathHelper.cos(swing * 0.6662f) * LEG_SWING * stride;
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(leftLeg));
        ms.translate(0.0, -0.25, 0.0);
        joints[11] = positionOf(ms);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.max(0.0f, -leftLeg) * KNEE_BEND));
        ms.translate(0.0, -0.25, 0.0);
        joints[12] = positionOf(ms);
        ms.pop();

        ms.push();
        ms.translate(-0.125, 0.0, 0.0);
        joints[13] = positionOf(ms);
        float rightLeg = gliding ? GLIDE_LEG : MathHelper.cos(swing * 0.6662f + (float) Math.PI) * LEG_SWING * stride;
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rightLeg));
        ms.translate(0.0, -0.25, 0.0);
        joints[14] = positionOf(ms);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.max(0.0f, -rightLeg) * KNEE_BEND));
        ms.translate(0.0, -0.25, 0.0);
        joints[15] = positionOf(ms);
        ms.pop();

        ms.pop();
        return joints;
    }

    private Vec3d positionOf(MatrixStack ms) {
        Vector3f position = ms.peek().getPositionMatrix().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        return new Vec3d(position.x, position.y, position.z);
    }

    private void line(VertexConsumer buffer, MatrixStack.Entry entry, Color color, float width,
                      double x1, double y1, double z1, double x2, double y2, double z2) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0E-4f) return;

        buffer.vertex(entry, (float) x1, (float) y1, (float) z1)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
        buffer.vertex(entry, (float) x2, (float) y2, (float) z2)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
    }
}
