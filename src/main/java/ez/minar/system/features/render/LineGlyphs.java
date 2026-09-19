package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@NewFunction(name = "Line Glyphs", desc = "LineGlyphs из Govno 1в1", category = Category.RENDER)
public class LineGlyphs extends Function {

    private static final Identifier GLOW_TEXTURE = Identifier.of("minar", "images/particles/glow.png");

    private static final RenderPipeline PIPELINE_TRANSLUCENT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "line_glyphs_govno_translucent"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderLayer LAYER_TRANSLUCENT = RenderLayer.of("minar_line_glyphs_govno_translucent",
            RenderSetup.builder(PIPELINE_TRANSLUCENT)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private static final RenderPipeline PIPELINE_ADDITIVE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "line_glyphs_govno_additive"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderLayer LAYER_ADDITIVE = RenderLayer.of("minar_line_glyphs_govno_additive",
            RenderSetup.builder(PIPELINE_ADDITIVE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    public static LineGlyphs get;
    public NumberSetting GlyphsCount;
    public BooleanSetting SlowSpeed;
    public BooleanSetting ApplyStippleLines;
    public NumberSetting StippleStepPixels;
    public BooleanSetting LinesGlowing;
    public ModeSetting ColorMode;
    public ColorSetting PickColor1;
    public ColorSetting PickColor2;

    private final Random RAND = new Random(93882L);
    private final List<Vec3d> temp3dVecs = new ArrayList<>();
    private final List<GliphsVecGen> GLIPHS_VEC_GENS = new ArrayList<>();
    private final AnimationUtils stateAnim = new AnimationUtils(0.0F, 0.0F, 0.1F);

    public LineGlyphs() {
        get = this;
        this.GlyphsCount = new NumberSetting("GlyphsCount", 70.0, 10.0, 200.0, 1.0);
        this.SlowSpeed = new BooleanSetting("SlowSpeed", false);
        this.ApplyStippleLines = new BooleanSetting("ApplyStippleLines", false);
        this.StippleStepPixels = new NumberSetting("StippleStepPixels", 3.0, 0.5, 20.0, 0.5);
        this.LinesGlowing = new BooleanSetting("LinesGlowing", false);
        this.ColorMode = new ModeSetting("ColorMode", "Client", "Rainbow", "Client", "Picker", "DoublePicker");
        this.PickColor1 = new ColorSetting("PickColor1", new Color(100, 255, 100));
        this.PickColor2 = new ColorSetting("PickColor2", new Color(60, 60, 255));

        addSettings(GlyphsCount, SlowSpeed, ApplyStippleLines, StippleStepPixels, LinesGlowing, ColorMode, PickColor1, PickColor2);

        ApplyStippleLines.runnable(this::updateVisibility);
        ColorMode.runnable(this::updateVisibility);
        updateVisibility();
    }

    private void updateVisibility() {
        StippleStepPixels.setVisible(ApplyStippleLines.isEnabled());
        PickColor1.setVisible(ColorMode.isEnabled("Picker") || ColorMode.isEnabled("DoublePicker"));
        PickColor2.setVisible(ColorMode.isEnabled("DoublePicker"));
    }

    @Override
    public void onEnable() {
        this.stateAnim.to = 1.0F;
    }

    @Override
    public void onDisable() {
        this.stateAnim.to = 0.0F;
    }

    private int[] lineMoveSteps() {
        return new int[]{0, 3};
    }

    private int[] lineStepsAmount() {
        return new int[]{7, 12};
    }

    private int[] spawnRanges() {
        return new int[]{6, 24, 0, 12};
    }

    private int maxObjCount() {
        return (int) this.GlyphsCount.getValue();
    }

    private int minGliphsJointDst() {
        return 8;
    }

    private int getR360X() {
        return this.RAND.nextInt(0, 4) * 90;
    }

    private int getR360Y() {
        return this.RAND.nextInt(-2, 2) * 90;
    }

    private int[] getR360XY() {
        return new int[]{this.RAND.nextInt(0, 4) * 90, this.RAND.nextInt(-1, 1) * 90};
    }

    private int[] getA90R(int[] outdated) {
        int a = outdated[0];
        int ao = a;
        int b = outdated[1];
        int bo = b;

        for (int maxAttempt = 150; maxAttempt > 0 && Math.abs(b - bo) != 90; --maxAttempt) {
            b = this.getR360Y();
        }

        for (int var7 = 5; var7 > 0 && (Math.abs(a - ao) != 90 && Math.abs(a - ao) != 270); --var7) {
            a = this.getR360X();
        }

        return new int[]{a, b};
    }

    private Vec3i offsetFromRXYR(Vec3i vec3i, int[] rxy, int r) {
        float yawR = (float) Math.toRadians((float) rxy[0]);
        float pitchR = (float) Math.toRadians((float) rxy[1]);
        float r1 = (float) r;
        int ry = (int) (Math.sin(pitchR) * r1);
        if (pitchR != 0.0F) {
            r1 = 0.0F;
        }

        int rx = (int) (-(Math.sin(yawR) * r1));
        int rz = (int) (Math.cos(yawR) * r1);
        int xi = vec3i.getX() + rx;
        int yi = vec3i.getY() + ry;
        int zi = vec3i.getZ() + rz;
        return new Vec3i(xi, yi, zi);
    }

    private float moveAdvanceFromTicks(int ticksSet, int ticksExpiring, float pTicks) {
        if (ticksSet <= 0) return 1.0F;
        return Math.min(Math.max(1.0F - ((float) ticksExpiring - pTicks) / (float) ticksSet, 0.0F), 1.0F);
    }

    private List<Vec3d> getSmoothTickedFromList(List<Vec3i> vec3is, float moveAdvance) {
        if (!this.temp3dVecs.isEmpty()) {
            this.temp3dVecs.clear();
        }

        for (Vec3i vec3i : vec3is) {
            double x = (double) vec3i.getX();
            double y = (double) vec3i.getY();
            double z = (double) vec3i.getZ();
            if (vec3is.size() >= 2 && vec3i == vec3is.get(vec3is.size() - 1)) {
                Vec3i prevVec3i = vec3is.get(vec3is.size() - 2);
                x = lerp((double) prevVec3i.getX(), x, (double) moveAdvance);
                y = lerp((double) prevVec3i.getY(), y, (double) moveAdvance);
                z = lerp((double) prevVec3i.getZ(), z, (double) moveAdvance);
            }

            this.temp3dVecs.add(new Vec3d(x, y, z));
        }

        return this.temp3dVecs;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private Vec3i randGliphSpawnPos() {
        if (mc.player == null) return new Vec3i(0, 0, 0);
        int[] spawnRanges = this.spawnRanges();
        double dst = (double) this.RAND.nextInt(spawnRanges[0], spawnRanges[1]);
        double fov = mc.options.getFov().getValue();
        int minYaw = (int) (mc.player.getYaw() - fov * 0.75);
        int maxYaw = (int) (mc.player.getYaw() + fov * 0.75);
        if (maxYaw <= minYaw) maxYaw = minYaw + 1;
        float radianYaw = (float) Math.toRadians(this.RAND.nextInt(minYaw, maxYaw));
        int randXOff = (int) (-((double) Math.sin(radianYaw) * dst));
        int randYOff = this.RAND.nextInt(-spawnRanges[2], spawnRanges[3]);
        int randZOff = (int) ((double) Math.cos(radianYaw) * dst);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        return new Vec3i((int) (cam.x + randXOff), (int) (cam.y + randYOff), (int) (cam.z + randZOff));
    }

    private void addAllGliphs(int countCap) {
        for (int maxAttempt = 8; maxAttempt > 0 && this.GLIPHS_VEC_GENS.stream().filter((gliphsVecGen) -> gliphsVecGen.alphaPC.to != 0.0F).count() < (long) countCap; --maxAttempt) {
            int[] lineStepsAmount = this.lineStepsAmount();

            while (this.GLIPHS_VEC_GENS.size() < countCap) {
                Vec3i pos = this.randGliphSpawnPos();
                this.GLIPHS_VEC_GENS.add(new GliphsVecGen(pos, this.RAND.nextInt(lineStepsAmount[0], lineStepsAmount[1])));
            }
        }
    }

    private void gliphsRemoveAuto(float moduleAlphaPC) {
        synchronized (this.GLIPHS_VEC_GENS) {
            this.GLIPHS_VEC_GENS.removeIf((gliphsVecGen) -> gliphsVecGen.isToRemove(moduleAlphaPC));
        }
    }

    private void gliphsUpdate() {
        synchronized (this.GLIPHS_VEC_GENS) {
            if (!this.GLIPHS_VEC_GENS.isEmpty()) {
                this.GLIPHS_VEC_GENS.forEach(GliphsVecGen::update);
            }
        }
    }

    private void gliphsClear() {
        synchronized (this.GLIPHS_VEC_GENS) {
            if (!this.GLIPHS_VEC_GENS.isEmpty()) {
                this.GLIPHS_VEC_GENS.clear();
            }
        }
    }

    @EventHandler
    private void onUpdate(UpdateEvent event) {
        this.stateAnim.update();
        if (mc.player == null || mc.world == null) {
            this.gliphsClear();
            return;
        }

        if (!this.isEnabled()) {
            if (this.stateAnim.anim < 0.03F && this.stateAnim.to == 0.0F) {
                this.gliphsClear();
                return;
            }
        }

        this.gliphsUpdate();
        if (this.isEnabled()) {
            this.addAllGliphs(this.maxObjCount());
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        if (get == null) return;
        get.stateAnim.update();

        float alphaPC;
        if (get.isEnabled()) {
            get.stateAnim.to = 1.0F;
            alphaPC = get.stateAnim.getAnim();
        } else {
            if (get.stateAnim.anim < 0.03F && get.stateAnim.to == 0.0F) {
                get.gliphsClear();
                return;
            }

            get.stateAnim.to = 0.0F;
            alphaPC = get.stateAnim.getAnim();
        }

        float pTicks = get.mc.getRenderTickCounter().getTickProgress(false);
        get.gliphsRemoveAuto(alphaPC);
        get.drawAllGliphs(context, alphaPC, pTicks);
    }

    private static Color stateColor(int index, float alphaPC) {
        Color color = Color.WHITE;
        if (get != null) {
            switch (get.ColorMode.getActiveMode()) {
                case "Rainbow" -> {
                    float hue = ((System.currentTimeMillis() / 20f + (long) index * 2f) % 360f) / 360f;
                    color = Color.getHSBColor(hue, 1.0F, 1.0F);
                }
                case "Client" -> {
                    if (ThemeManager.twoColors) {
                        float time = ((System.currentTimeMillis() + (long) index * 100L) % 2000L) / 2000f;
                        float t = (float) (Math.sin(time * Math.PI * 2) * 0.5f + 0.5f);
                        color = lerpColor(ThemeManager.Theme_Color, ThemeManager.Theme_Color2, t);
                    } else {
                        color = ThemeManager.getThemeColor();
                    }
                }
                case "Picker" -> color = get.PickColor1.getColor();
                case "DoublePicker" -> {
                    float t = (float) Math.sin((System.currentTimeMillis() / 400.0) + (index / 8.0)) * 0.5f + 0.5f;
                    color = lerpColor(get.PickColor1.getColor(), get.PickColor2.getColor(), t);
                }
            }
        }

        // ColorUtils.getOverallColorFrom(color, white, 0.1F) - 10% white mix for vibrancy
        int r = (int) (color.getRed() + (255 - color.getRed()) * 0.1F);
        int g = (int) (color.getGreen() + (255 - color.getGreen()) * 0.1F);
        int b = (int) (color.getBlue() + (255 - color.getBlue()) * 0.1F);
        int a = Math.clamp((int) (alphaPC * 255.0F), 0, 255);
        return new Color(r, g, b, a);
    }

    private static Color lerpColor(Color c1, Color c2, float t) {
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        return new Color(r, g, b);
    }

    private void drawAllGliphs(WorldRenderContext context, float alphaPC, float pTicks) {
        if (this.GLIPHS_VEC_GENS.isEmpty()) return;

        List<GliphsVecGen> filteredGens;
        synchronized (this.GLIPHS_VEC_GENS) {
            filteredGens = this.GLIPHS_VEC_GENS.stream()
                    .filter((gliphsVecGen) -> alphaPC * gliphsVecGen.getAlphaPC() * 255.0F >= 1.0F)
                    .toList();
        }
        if (filteredGens.isEmpty()) return;

        float stipple = (float) this.StippleStepPixels.getValue();
        boolean useStipple = this.ApplyStippleLines.isEnabled();
        boolean glowing = this.LinesGlowing.isEnabled();

        RenderLayer layer = glowing ? LAYER_ADDITIVE : LAYER_TRANSLUCENT;
        VertexConsumer buffer = context.consumers().getBuffer(layer);
        MatrixStack.Entry entry = context.matrices().peek();
        Vec3d cam = context.worldState().cameraRenderState.pos;

        // Pass 1: Main lines
        int colorIndex = 0;
        for (GliphsVecGen filteredGen : filteredGens) {
            ++colorIndex;
            renderGlyphLines(buffer, entry, filteredGen, colorIndex, 180, alphaPC * filteredGen.alphaPC.anim, pTicks, cam, 1.0F, 0.0F, useStipple, stipple);
        }

        // Passes 2 and 3: Glow bloom (when LinesGlowing is on)
        if (glowing) {
            colorIndex = 0;
            for (GliphsVecGen filteredGen : filteredGens) {
                ++colorIndex;
                renderGlyphLines(buffer, entry, filteredGen, colorIndex, 180, alphaPC * filteredGen.alphaPC.anim * 0.1F, pTicks, cam, 1.5F, 4.0F, false, 0.0F);
            }

            colorIndex = 0;
            for (GliphsVecGen filteredGen : filteredGens) {
                ++colorIndex;
                renderGlyphLines(buffer, entry, filteredGen, colorIndex, 180, alphaPC * filteredGen.alphaPC.anim * 0.04F, pTicks, cam, 1.9F, 9.0F, false, 0.0F);
            }
        }

        // Pass 4: Glowing node dots (Govno point render 1в1, done after line buffer finishes)
        Render3DUtils.TexturedBillboardBatch dotBatch = Render3DUtils.additiveTexturedBillboardBatch(context, GLOW_TEXTURE);
        colorIndex = 0;
        for (GliphsVecGen filteredGen : filteredGens) {
            ++colorIndex;
            renderGlyphPoints(dotBatch, filteredGen, colorIndex, 180, alphaPC * filteredGen.alphaPC.anim, pTicks, cam, 1.0F, 0.0F);
        }
    }

    private static float calcLineWidth(GliphsVecGen gliphVecGen, Vec3d cameraPos) {
        Vec3i pos = gliphVecGen.vecGens.stream()
                .sorted(Comparator.comparingDouble((vec3i) -> -new Vec3d(vec3i.getX(), vec3i.getY(), vec3i.getZ()).squaredDistanceTo(cameraPos)))
                .findAny()
                .orElse(new Vec3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        double dst = cameraPos.distanceTo(new Vec3d(pos.getX(), pos.getY(), pos.getZ()));
        return 1.0E-4F + 3.0F * (float) Math.clamp(1.0F - dst / 20.0F, 0.0F, 1.0F);
    }

    private static void renderGlyphPoints(Render3DUtils.TexturedBillboardBatch dotBatch,
                                          GliphsVecGen gliphVecGen, int objIndex, int colorIndexStep,
                                          float alphaPC, float pTicks, Vec3d cam,
                                          float lineWidthMul, float lineWidthAddPerm) {
        if (alphaPC * 255.0F < 1.0F || gliphVecGen.vecGens.size() < 2) return;
        List<Vec3d> vecs = gliphVecGen.getPosVectors(pTicks);
        if (vecs.isEmpty()) return;

        float baseWidth = calcLineWidth(gliphVecGen, cam);
        float pointSize = Math.max(0.06F, Math.min(baseWidth * 3.0F * lineWidthMul + lineWidthAddPerm, 40.0F) * 0.035F);

        int colorIndex = objIndex;
        for (int i = 0; i < vecs.size(); i++) {
            Vec3d vec3d = vecs.get(i);
            float aPC = alphaPC * (0.25F + (float) i / (float) gliphVecGen.vecGens.size() / 1.75F);
            Color c = stateColor(colorIndex, aPC);
            dotBatch.render(vec3d, pointSize, 0.0F, c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0F);
            colorIndex += colorIndexStep;
        }
    }

    private static void renderGlyphLines(VertexConsumer buffer, MatrixStack.Entry entry,
                                         GliphsVecGen gliphVecGen, int objIndex, int colorIndexStep,
                                         float alphaPC, float pTicks, Vec3d cam,
                                         float lineWidthMul, float lineWidthAddPerm,
                                         boolean stipple, float stippleStep) {
        if (alphaPC * 255.0F < 1.0F || gliphVecGen.vecGens.size() < 2) return;

        float baseWidth = calcLineWidth(gliphVecGen, cam);
        float lineWidth = Math.max(0.4F, Math.min(baseWidth * lineWidthMul + lineWidthAddPerm, 40.0F));

        List<Vec3d> vecs = gliphVecGen.getPosVectors(pTicks);
        if (vecs.size() < 2) return;

        int colorIndex = objIndex;
        for (int i = 0; i < vecs.size() - 1; i++) {
            Vec3d p1 = vecs.get(i);
            Vec3d p2 = vecs.get(i + 1);

            int ci1 = colorIndex;
            int ci2 = colorIndex + colorIndexStep;

            float aPC1 = alphaPC * (0.25F + (float) i / (float) gliphVecGen.vecGens.size() / 1.75F);
            float aPC2 = alphaPC * (0.25F + (float) (i + 1) / (float) gliphVecGen.vecGens.size() / 1.75F);

            Color c1 = stateColor(ci1, aPC1);
            Color c2 = stateColor(ci2, aPC2);

            drawSegment(buffer, entry, p1, p2, cam, c1, c2, lineWidth, stipple, stippleStep);
            colorIndex += colorIndexStep;
        }
    }

    private static void drawSegment(VertexConsumer buffer, MatrixStack.Entry entry,
                                    Vec3d p1, Vec3d p2, Vec3d cam,
                                    Color c1, Color c2, float width,
                                    boolean useStipple, float stippleStep) {
        Vec3d delta = p2.subtract(p1);
        double len = delta.length();
        if (len <= 1e-4) return;

        Vec3d dir = delta.multiply(1.0 / len);
        float nx = (float) dir.x;
        float ny = (float) dir.y;
        float nz = (float) dir.z;

        if (!useStipple) {
            Vec3d v1 = p1.subtract(cam);
            Vec3d v2 = p2.subtract(cam);
            line(buffer, entry, v1, v2, nx, ny, nz, c1, c2, width);
            return;
        }

        // Exact stipple pattern: GL_LINE_STIPPLE 0xAAAA (dash = gap)
        double dLen = Math.max(0.08, (double) stippleStep * 0.08);
        double gLen = dLen;
        double period = dLen + gLen;

        double current = 0.0;
        while (current < len) {
            double dEnd = Math.min(current + dLen, len);

            float t1 = (float) (current / len);
            float t2 = (float) (dEnd / len);
            Color dashC1 = lerpColorAlpha(c1, c2, t1);
            Color dashC2 = lerpColorAlpha(c1, c2, t2);

            Vec3d subP1 = p1.add(dir.multiply(current)).subtract(cam);
            Vec3d subP2 = p1.add(dir.multiply(dEnd)).subtract(cam);

            line(buffer, entry, subP1, subP2, nx, ny, nz, dashC1, dashC2, width);

            current += period;
        }
    }

    private static Color lerpColorAlpha(Color c1, Color c2, float t) {
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
    }

    private static void line(VertexConsumer buffer, MatrixStack.Entry entry,
                             Vec3d v1, Vec3d v2,
                             float nx, float ny, float nz,
                             Color c1, Color c2, float width) {
        buffer.vertex(entry, (float) v1.x, (float) v1.y, (float) v1.z)
                .color(c1.getRed(), c1.getGreen(), c1.getBlue(), c1.getAlpha())
                .normal(entry, nx, ny, nz)
                .lineWidth(width);
        buffer.vertex(entry, (float) v2.x, (float) v2.y, (float) v2.z)
                .color(c2.getRed(), c2.getGreen(), c2.getBlue(), c2.getAlpha())
                .normal(entry, nx, ny, nz)
                .lineWidth(width);
    }

    public static class AnimationUtils {
        public float anim;
        public float to;
        public float speed;

        public AnimationUtils(float start, float target, float speed) {
            this.anim = start;
            this.to = target;
            this.speed = speed;
        }

        public void update() {
            anim += (to - anim) * speed;
            if (Math.abs(to - anim) < 0.001f) {
                anim = to;
            }
        }

        public float getAnim() {
            return anim;
        }
    }

    private class GliphsVecGen {
        private final List<Vec3i> vecGens = new ArrayList<>();
        private int currentStepTicks;
        private int lastStepSet;
        private int stepsAmount;
        private int[] lastYawPitch;
        private final AnimationUtils alphaPC = new AnimationUtils(0.1F, 1.0F, 0.075F);

        public GliphsVecGen(Vec3i spawnPos, int maxStepsAmount) {
            this.vecGens.add(spawnPos);
            this.lastYawPitch = LineGlyphs.this.getR360XY();
            this.stepsAmount = maxStepsAmount;
        }

        private void update() {
            alphaPC.update();
            if (this.stepsAmount == 0) {
                this.alphaPC.to = 0.0F;
            }

            if (this.currentStepTicks > 0) {
                this.currentStepTicks -= LineGlyphs.this.SlowSpeed.isEnabled() ? 1 : 2;
                if (this.currentStepTicks < 0) {
                    this.currentStepTicks = 0;
                }
            } else {
                this.lastYawPitch = LineGlyphs.this.getA90R(this.lastYawPitch);
                this.lastStepSet = this.currentStepTicks = LineGlyphs.this.RAND.nextInt(LineGlyphs.this.lineMoveSteps()[0], LineGlyphs.this.lineMoveSteps()[1]);
                this.vecGens.add(LineGlyphs.this.offsetFromRXYR(
                        this.vecGens.get(this.vecGens.size() - 1),
                        this.lastYawPitch,
                        this.lastStepSet));
                --this.stepsAmount;
            }
        }

        public List<Vec3d> getPosVectors(float pTicks) {
            return LineGlyphs.this.getSmoothTickedFromList(this.vecGens, LineGlyphs.this.moveAdvanceFromTicks(this.lastStepSet, this.currentStepTicks, pTicks));
        }

        public float getAlphaPC() {
            return Math.clamp(this.alphaPC.getAnim(), 0.0F, 1.0F);
        }

        public void setWantToRemove() {
            this.stepsAmount = 0;
        }

        public boolean isToRemove(float moduleAlphaPC) {
            return moduleAlphaPC * (this.alphaPC.to == 0.0F ? this.getAlphaPC() : 1.0F) * 255.0F < 1.0F;
        }
    }
}
