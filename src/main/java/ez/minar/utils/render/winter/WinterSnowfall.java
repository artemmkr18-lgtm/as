package ez.minar.utils.render.winter;

import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class WinterSnowfall {
    private static final Identifier SOFT_FLAKE = Identifier.of("minar", "textures/particles/snow_soft.png");
    private static final Identifier DETAILED_FLAKE = Identifier.of("minar", "textures/particles/snowflake.png");
    private static final Identifier SNOW_COVER = Identifier.of("minar", "textures/particles/snow_cover.png");

    private static final int MAX_FLAKES = 16000;
    private static final int MAX_GROUND_QUADS = 80000;
    private static final float GRID_REFRESH_SECONDS = 0.8f;
    private static final int GRID_MARGIN = 2;
    private static final int MAX_COLUMN_SCAN = 48;
    private static final int FLOATS_PER_QUAD = 24;
    private static final float EDGE_EPS = 0.008f;
    private static final float SIDE_V = 0.35f;

    public static final class Config {
        public float radius = 32.0f;
        public float height = 24.0f;
        public float density = 2.0f;
        public float flakeSize = 1.0f;
        public float fallSpeed = 1.0f;
        public float wind = 1.0f;
        public float opacity = 1.0f;
        public float blizzard = 0.0f;
        public boolean skyOnly = true;
        public boolean groundSnow = true;
        public float groundRadius = 40.0f;
        public float groundDepth = 0.12f;
        public int color = 0xFFEAF4FF;
    }

    private static final WinterSnowfall INSTANCE = new WinterSnowfall();

    public static WinterSnowfall getInstance() {
        return INSTANCE;
    }

    public static void onBlockChanged(BlockPos pos) {
        if (pos == null) return;
        INSTANCE.invalidateBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    private final List<Flake> flakes = new ArrayList<>(2048);
    private final Random random = new Random();
    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();
    private final Quaternionf inverseCamera = new Quaternionf();
    private final Vector3f scratchVelocity = new Vector3f();

    private float[] surfaceBase = new float[0];
    private boolean[] surfaceFull = new boolean[0];
    private float[] surfaceLight = new float[0];
    private int[] surfaceY = new int[0];
    private VoxelShape[] surfaceShapes = new VoxelShape[0];
    private float[] mesh = new float[FLOATS_PER_QUAD * 4096];
    private int meshQuads;
    private float builtDepth = -1.0f;
    private int resolvedY;
    private VoxelShape currentShape;
    private int currentWorldX;
    private int currentWorldZ;
    private final float[] segStart = new float[16];
    private final float[] segEnd = new float[16];
    private final float[] segStartNext = new float[16];
    private final float[] segEndNext = new float[16];
    private VoxelShape resolvedShape;
    private int gridRadius = -1;
    private int gridOriginX = Integer.MIN_VALUE;
    private int gridOriginY = Integer.MIN_VALUE;
    private int gridOriginZ = Integer.MIN_VALUE;
    private float gridAge = Float.MAX_VALUE;
    private boolean dirtyGrid = false;
    private long lastFrameNanos;
    private float clock;

    private WinterSnowfall() {
    }

    public void invalidateBlock(int x, int y, int z) {
        if (this.gridRadius <= 0) return;
        int dx = Math.abs(x - this.gridOriginX);
        int dz = Math.abs(z - this.gridOriginZ);
        if (dx <= this.gridRadius && dz <= this.gridRadius) {
            this.dirtyGrid = true;
        }
    }

    public void reset() {
        this.flakes.clear();
        this.lastFrameNanos = 0L;
        this.clock = 0.0f;
        this.gridRadius = -1;
        this.gridOriginX = Integer.MIN_VALUE;
        this.gridOriginY = Integer.MIN_VALUE;
        this.gridOriginZ = Integer.MIN_VALUE;
        this.gridAge = Float.MAX_VALUE;
        this.dirtyGrid = true;
        this.meshQuads = 0;
        this.surfaceShapes = new VoxelShape[0];
        this.surfaceBase = new float[0];
    }

    public void render(WorldRenderContext context, Camera camera, Config config) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || camera == null) {
            reset();
            return;
        }

        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) this.lastFrameNanos = now;
        float dt = MathHelper.clamp((float) ((now - this.lastFrameNanos) / 1.0E9d), 0.0f, 0.05f);
        this.lastFrameNanos = now;
        this.clock += dt;

        Vec3d cam = camera.getCameraPos();
        float ambient = ambientLight(mc, cam);

        if (config.groundSnow) {
            updateGrid(mc.world, cam, config, dt);
            drawGround(context, cam, config, ambient);
        }
        updateFlakes(mc.world, cam, config, dt);
        drawFlakes(context, camera, cam, config, ambient);
    }

    private int targetFlakes(Config config) {
        float area = (float) Math.PI * config.radius * config.radius;
        int count = Math.round(area * 0.16f * config.density * (1.0f + config.blizzard * 0.65f));
        return MathHelper.clamp(count, 120, MAX_FLAKES);
    }

    private void updateFlakes(ClientWorld level, Vec3d cam, Config config, float dt) {
        int target = targetFlakes(config);
        float cull = (config.radius + 6.0f) * (config.radius + 6.0f);
        for (int i = this.flakes.size() - 1; i >= 0; i--) {
            Flake flake = this.flakes.get(i);
            double dx = flake.x - cam.x;
            double dz = flake.z - cam.z;
            if (dx * dx + dz * dz > cull || flake.y > cam.y + config.height * 1.6) {
                this.flakes.remove(i);
            }
        }
        while (this.flakes.size() > target) this.flakes.remove(this.flakes.size() - 1);

        boolean coldStart = this.flakes.size() < target / 2;
        int budget = Math.min(target - this.flakes.size(), coldStart ? MAX_FLAKES : 500);
        for (int i = 0; i < budget; i++) {
            Flake flake = spawn(level, cam, config, !coldStart);
            if (flake != null) this.flakes.add(flake);
        }

        float windStrength = config.wind * (1.6f + config.blizzard * 5.2f);
        double windAngle = this.clock * 0.045;
        double gust = 0.72 + 0.28 * Math.sin(this.clock * 0.37)
                + 0.14 * Math.sin(this.clock * 1.13 + 1.7);
        double windX = Math.cos(windAngle) * windStrength * gust;
        double windZ = Math.sin(windAngle) * windStrength * gust;
        float flutter = 1.0f - config.blizzard * 0.55f;

        for (int i = 0; i < this.flakes.size(); i++) {
            Flake flake = this.flakes.get(i);
            double swayX = Math.sin(this.clock * flake.swayFrequency + flake.phase)
                    * flake.swayAmplitude * flutter;
            double swayZ = Math.cos(this.clock * flake.swayFrequency * 0.83 + flake.phase * 1.37)
                    * flake.swayAmplitude * flutter;
            double follow = Math.min(1.0, dt * (1.1 + flake.drag * 2.4));
            flake.vx += ((windX * flake.drag + swayX) - flake.vx) * follow;
            flake.vz += ((windZ * flake.drag + swayZ) - flake.vz) * follow;
            flake.vy += (flake.terminal - flake.vy) * Math.min(1.0, dt * 2.2);
            flake.x += flake.vx * dt;
            flake.y += flake.vy * dt;
            flake.z += flake.vz * dt;
            flake.rotation += flake.spin * dt;

            if (flake.y <= flake.groundY + 0.04 || flake.y < cam.y - config.height * 0.8) {
                Flake replacement = spawn(level, cam, config, true);
                if (replacement != null) this.flakes.set(i, replacement);
                else flake.y = cam.y + config.height * (0.6 + this.random.nextDouble() * 0.4);
            }
        }
    }

    private Flake spawn(ClientWorld level, Vec3d cam, Config config, boolean fromTop) {
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(this.random.nextDouble()) * config.radius;
        double x = cam.x + Math.cos(angle) * distance;
        double z = cam.z + Math.sin(angle) * distance;
        double ground = flakeGround(level, MathHelper.floor(x), MathHelper.floor(z));
        if (config.skyOnly && ground > cam.y + config.height * 0.9) return null;

        double y = fromTop
                ? cam.y + config.height * (0.75 + this.random.nextDouble() * 0.45)
                : cam.y - config.height * 0.5 + this.random.nextDouble() * config.height * 1.6;
        if (y <= ground + 0.2) y = ground + 0.2 + this.random.nextDouble() * 2.0;

        Flake flake = new Flake();
        flake.x = x;
        flake.y = y;
        flake.z = z;
        flake.groundY = ground;
        float roll = this.random.nextFloat();
        float base;
        if (roll < 0.60f) base = random(0.04f, 0.08f);
        else if (roll < 0.90f) base = random(0.08f, 0.16f);
        else base = random(0.16f, 0.32f);
        flake.size = base * config.flakeSize;
        flake.detailed = roll >= 0.88f && this.random.nextFloat() < 0.50f;
        float weight = MathHelper.clamp(base / 0.32f, 0.12f, 1.0f);
        flake.drag = 0.35f + (1.0f - weight) * 0.65f;
        flake.terminal = -(0.85 + weight * 3.1) * config.fallSpeed
                * (1.0 + config.blizzard * 1.35);
        flake.swayFrequency = random(0.6f, 1.9f);
        flake.swayAmplitude = random(0.35f, 1.05f) * (1.25f - weight * 0.6f);
        flake.phase = random(0.0f, 6.2831855f);
        flake.spin = random(-1.6f, 1.6f);
        flake.rotation = random(0.0f, 6.2831855f);
        flake.brightness = random(0.80f, 1.0f);
        flake.vy = flake.terminal * 0.6;
        return flake;
    }

    private void drawFlakes(WorldRenderContext context, Camera camera, Vec3d cam, Config config, float ambient) {
        if (this.flakes.isEmpty()) return;
        Quaternionf orientation = context.worldState().cameraRenderState.orientation;
        Vector3f cameraRight = orientation.transform(new Vector3f(1f, 0f, 0f));
        Vector3f cameraUp = orientation.transform(new Vector3f(0f, 1f, 0f));

        orientation.invert(this.inverseCamera);
        float light = MathHelper.clamp(0.42f + ambient * 0.58f, 0.32f, 1.0f);
        float red = ((config.color >> 16) & 0xFF) / 255.0f * light;
        float green = ((config.color >> 8) & 0xFF) / 255.0f * light;
        float blue = (config.color & 0xFF) / 255.0f * light;
        float fadeStart = config.radius * 0.55f;
        float streakScale = 0.045f + config.blizzard * 0.075f;

        drawFlakePass(context, cam, config, cameraRight, cameraUp, false, SOFT_FLAKE,
                red, green, blue, fadeStart, streakScale);
        drawFlakePass(context, cam, config, cameraRight, cameraUp, true, DETAILED_FLAKE,
                red, green, blue, fadeStart, streakScale);
    }

    private void drawFlakePass(WorldRenderContext context, Vec3d cam, Config config,
                               Vector3f cameraRight, Vector3f cameraUp, boolean detailedPass, Identifier texture,
                               float red, float green, float blue, float fadeStart, float streakScale) {
        VertexConsumer consumer = null;
        for (Flake flake : this.flakes) {
            if (flake.detailed != detailedPass) continue;
            double dx = flake.x - cam.x;
            double dy = flake.y - cam.y;
            double dz = flake.z - cam.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            double limit = config.radius + 4.0;
            if (distanceSq > limit * limit) continue;
            double distance = Math.sqrt(distanceSq);
            float fade = distance <= fadeStart ? 1.0f : 1.0f - MathHelper.clamp(
                    (float) ((distance - fadeStart) / (config.radius - fadeStart + 1.0)), 0.0f, 1.0f);
            if (fade <= 0.02f) continue;
            if (distance < 0.35) fade *= (float) (distance / 0.35);
            float alpha = MathHelper.clamp(config.opacity * flake.brightness * fade * 0.92f, 0.0f, 1.0f);
            if (alpha <= 0.008f) continue;

            if (consumer == null) {
                consumer = context.consumers().getBuffer(Render3DUtils.texturedLayer(texture));
            }

            double speed = Math.sqrt(flake.vx * flake.vx + flake.vy * flake.vy + flake.vz * flake.vz);
            float stretch = 1.0f + (float) MathHelper.clamp(speed * streakScale, 0.0, 2.6);
            float tilt = flake.rotation;
            if (stretch > 1.05f && speed > 1.0E-4) {
                this.scratchVelocity.set((float) flake.vx, (float) flake.vy, (float) flake.vz);
                this.scratchVelocity.rotate(this.inverseCamera);
                tilt = (float) Math.atan2(this.scratchVelocity.x, this.scratchVelocity.y);
            }

            float halfWidth = flake.size * 0.5f;
            float halfHeight = flake.size * stretch * 0.5f;

            float cos = (float) Math.cos(tilt);
            float sin = (float) Math.sin(tilt);
            Vector3f r = new Vector3f(cameraRight).mul(cos).add(new Vector3f(cameraUp).mul(sin)).mul(halfWidth);
            Vector3f u = new Vector3f(cameraUp).mul(cos).sub(new Vector3f(cameraRight).mul(sin)).mul(halfHeight);

            float fx = (float) dx;
            float fy = (float) dy;
            float fz = (float) dz;

            int rCol = (int) (red * 255f);
            int gCol = (int) (green * 255f);
            int bCol = (int) (blue * 255f);
            int aCol = (int) (alpha * 255f);

            consumer.vertex(fx - r.x - u.x, fy - r.y - u.y, fz - r.z - u.z)
                    .color(rCol, gCol, bCol, aCol).texture(0.0f, 1.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
            consumer.vertex(fx + r.x - u.x, fy + r.y - u.y, fz + r.z - u.z)
                    .color(rCol, gCol, bCol, aCol).texture(1.0f, 1.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
            consumer.vertex(fx + r.x + u.x, fy + r.y + u.y, fz + r.z + u.z)
                    .color(rCol, gCol, bCol, aCol).texture(1.0f, 0.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
            consumer.vertex(fx - r.x + u.x, fy - r.y + u.y, fz - r.z + u.z)
                    .color(rCol, gCol, bCol, aCol).texture(0.0f, 0.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 1f, 0f);
        }
    }

    private void updateGrid(ClientWorld level, Vec3d cam, Config config, float dt) {
        int radius = MathHelper.clamp((int) Math.ceil(config.groundRadius), 4, 48) + GRID_MARGIN;
        int camX = MathHelper.floor(cam.x);
        int camZ = MathHelper.floor(cam.z);
        this.gridAge += dt;
        boolean moved = Math.abs(camX - this.gridOriginX) >= GRID_MARGIN
                || Math.abs(camZ - this.gridOriginZ) >= GRID_MARGIN
                || Math.abs(MathHelper.floor(cam.y) - this.gridOriginY) > 20;
        boolean resized = radius != this.gridRadius || config.groundDepth != this.builtDepth;
        if (!this.dirtyGrid && !moved && !resized && this.gridAge < GRID_REFRESH_SECONDS) return;

        this.dirtyGrid = false;
        int side = radius * 2 + 1;
        int cells = side * side;
        if (this.surfaceBase.length != cells) {
            this.surfaceBase = new float[cells];
            this.surfaceFull = new boolean[cells];
            this.surfaceLight = new float[cells];
            this.surfaceY = new int[cells];
            this.surfaceShapes = new VoxelShape[cells];
        }
        this.gridRadius = radius;
        this.gridOriginX = camX;
        this.gridOriginZ = camZ;
        this.gridOriginY = MathHelper.floor(cam.y);
        this.gridAge = 0.0f;
        this.builtDepth = config.groundDepth;

        for (int gz = 0; gz < side; gz++) {
            int worldZ = camZ - radius + gz;
            for (int gx = 0; gx < side; gx++) {
                int worldX = camX - radius + gx;
                int index = gz * side + gx;
                this.surfaceBase[index] = Float.NaN;
                this.surfaceFull[index] = false;
                this.surfaceShapes[index] = null;
                if (!resolveSurface(level, worldX, worldZ)) continue;
                VoxelShape shape = this.resolvedShape;
                int y = this.resolvedY;
                this.surfaceY[index] = y;
                this.surfaceShapes[index] = shape;
                this.surfaceBase[index] = (float) (y + shape.getMax(Direction.Axis.Y));
                this.surfaceFull[index] = coversWholeTop(shape);
                this.surfaceLight[index] = columnLight(level, worldX, y + 1, worldZ);
            }
        }
        buildMesh(config);
    }

    private boolean resolveSurface(ClientWorld level, int x, int z) {
        try {
            if (!level.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
            int top = level.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            int minY = level.getBottomY();
            if (top <= minY) return false;
            int bottom = Math.max(minY, top - MAX_COLUMN_SCAN);
            for (int y = top - 1; y >= bottom; y--) {
                this.scratchPos.set(x, y, z);
                BlockState state = level.getBlockState(this.scratchPos);
                if (state.isAir()) continue;
                if (!state.getFluidState().isEmpty()) return false;
                if (isInvisible(state)) continue;
                VoxelShape shape = state.getOutlineShape(level, this.scratchPos);
                if (shape.isEmpty()) continue;
                boolean snowLayer = state.getBlock() instanceof SnowBlock;
                if (!snowLayer && state.getCollisionShape(level, this.scratchPos).isEmpty()) continue;
                this.resolvedY = y;
                this.resolvedShape = shape;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isInvisible(BlockState state) {
        if (state.isOf(Blocks.BARRIER) || state.isOf(Blocks.LIGHT) || state.isOf(Blocks.STRUCTURE_VOID)) return true;
        return state.getRenderType() == BlockRenderType.INVISIBLE && !state.hasBlockEntity();
    }

    private static boolean coversWholeTop(VoxelShape shape) {
        double top = shape.getMax(Direction.Axis.Y);
        for (Box box : shape.getBoundingBoxes()) {
            if (box.maxY >= top - 1.0E-3 && box.minX <= 1.0E-3 && box.minZ <= 1.0E-3
                    && box.maxX >= 1.0 - 1.0E-3 && box.maxZ >= 1.0 - 1.0E-3) return true;
        }
        return false;
    }

    private float columnLight(ClientWorld level, int x, int y, int z) {
        try {
            this.scratchPos.set(x, y, z);
            float light = level.getLightLevel(this.scratchPos) / 15.0f;
            return 0.40f + MathHelper.clamp(light, 0.0f, 1.0f) * 0.60f;
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private void buildMesh(Config config) {
        this.meshQuads = 0;
        int side = this.gridRadius * 2 + 1;
        float depth = MathHelper.clamp(config.groundDepth, 0.02f, 0.4f);
        for (int gz = 0; gz < side; gz++) {
            for (int gx = 0; gx < side; gx++) {
                int index = gz * side + gx;
                VoxelShape shape = this.surfaceShapes[index];
                if (shape == null) continue;
                int worldX = this.gridOriginX - this.gridRadius + gx;
                int worldZ = this.gridOriginZ - this.gridRadius + gz;
                int blockY = this.surfaceY[index];
                float light = this.surfaceLight[index];
                this.currentShape = shape;
                this.currentWorldX = worldX;
                this.currentWorldZ = worldZ;
                for (Box box : shape.getBoundingBoxes()) {
                    double w = box.maxX - box.minX;
                    double d = box.maxZ - box.minZ;
                    if (w < 0.06 || d < 0.06) continue;
                    appendBox(gx, gz, worldX, blockY, worldZ, box, light, depth);
                }
            }
        }
    }

    private void appendBox(int gx, int gz, int worldX, int blockY, int worldZ, Box box,
                           float light, float depth) {
        float top = (float) (blockY + box.maxY);
        float area = (float) ((box.maxX - box.minX) * (box.maxZ - box.minZ));
        float thicknessScale = area >= 0.9f ? 1.0f : MathHelper.clamp(0.45f + area * 0.6f, 0.45f, 1.0f);
        boolean west = box.minX <= 1.0E-3;
        boolean east = box.maxX >= 1.0 - 1.0E-3;
        boolean north = box.minZ <= 1.0E-3;
        boolean south = box.maxZ >= 1.0 - 1.0E-3;

        double wx0 = worldX + box.minX;
        double wx1 = worldX + box.maxX;
        double wz0 = worldZ + box.minZ;
        double wz1 = worldZ + box.maxZ;

        int ao00 = west && north ? cornerOcclusion(gx, gz, -1, -1, top) : 0;
        int ao01 = west && south ? cornerOcclusion(gx, gz, -1, 1, top) : 0;
        int ao11 = east && south ? cornerOcclusion(gx, gz, 1, 1, top) : 0;
        int ao10 = east && north ? cornerOcclusion(gx, gz, 1, -1, top) : 0;

        float h00 = top + thickness(wx0, wz0, depth, thicknessScale, ao00);
        float h01 = top + thickness(wx0, wz1, depth, thicknessScale, ao01);
        float h11 = top + thickness(wx1, wz1, depth, thicknessScale, ao11);
        float h10 = top + thickness(wx1, wz0, depth, thicknessScale, ao10);

        float lx0 = (float) (wx0 - this.gridOriginX) - EDGE_EPS;
        float lx1 = (float) (wx1 - this.gridOriginX) + EDGE_EPS;
        float lz0 = (float) (wz0 - this.gridOriginZ) - EDGE_EPS;
        float lz1 = (float) (wz1 - this.gridOriginZ) + EDGE_EPS;
        float oy = this.gridOriginY;
        float u0 = (float) box.minX, u1 = (float) box.maxX;
        float v0 = (float) box.minZ, v1 = (float) box.maxZ;

        pushQuad(lx0, h00 - oy, lz0, u0, v0, light * occlusionShade(ao00),
                lx0, h01 - oy, lz1, u0, v1, light * occlusionShade(ao01),
                lx1, h11 - oy, lz1, u1, v1, light * occlusionShade(ao11),
                lx1, h10 - oy, lz0, u1, v0, light * occlusionShade(ao10));

        // Organic melted snow drip/overflow along outer edges (lips)
        float baseLip = 0.08f + depth * 0.95f;
        float sideX = light * 0.80f;
        float sideZ = light * 0.88f;

        if (!west || needsSide(gx - 1, gz, top)) {
            float lip0 = calcLip(wx0, wz0, baseLip, thicknessScale);
            float lip1 = calcLip(wx0, wz1, baseLip, thicknessScale);
            emitSideCurved(box, 0, lx0, lz0, lz1, h00 - oy, h01 - oy, top - oy, lip0, lip1, sideX, (float) (top - blockY));
        }
        if (!east || needsSide(gx + 1, gz, top)) {
            float lip0 = calcLip(wx1, wz0, baseLip, thicknessScale);
            float lip1 = calcLip(wx1, wz1, baseLip, thicknessScale);
            emitSideCurved(box, 1, lx1, lz0, lz1, h10 - oy, h11 - oy, top - oy, lip0, lip1, sideX, (float) (top - blockY));
        }
        if (!north || needsSide(gx, gz - 1, top)) {
            float lip0 = calcLip(wx0, wz0, baseLip, thicknessScale);
            float lip1 = calcLip(wx1, wz0, baseLip, thicknessScale);
            emitSideCurved(box, 2, lz0, lx0, lx1, h00 - oy, h10 - oy, top - oy, lip0, lip1, sideZ, (float) (top - blockY));
        }
        if (!south || needsSide(gx, gz + 1, top)) {
            float lip0 = calcLip(wx0, wz1, baseLip, thicknessScale);
            float lip1 = calcLip(wx1, wz1, baseLip, thicknessScale);
            emitSideCurved(box, 3, lz1, lx0, lx1, h01 - oy, h11 - oy, top - oy, lip0, lip1, sideZ, (float) (top - blockY));
        }
    }

    private static float calcLip(double wx, double wz, float baseLip, float scale) {
        float noise = valueNoise(wx * 2.1 + 5.2, wz * 2.1 - 3.7);
        return baseLip * scale * (0.65f + noise * 0.95f);
    }

    private void emitSideCurved(Box box, int side, float plane, float a0, float a1,
                                float hA, float hB, float blockTopLocal, float lipA, float lipB,
                                float shade, float localTop) {
        VoxelShape shape = this.currentShape;
        float originAlong = side < 2 ? (float) (this.currentWorldZ - this.gridOriginZ)
                : (float) (this.currentWorldX - this.gridOriginX);
        int segments = 1;
        float[] segStart = this.segStart;
        float[] segEnd = this.segEnd;
        segStart[0] = a0;
        segEnd[0] = a1;
        if (shape != null) {
            for (Box other : shape.getBoundingBoxes()) {
                if (other == box) continue;
                if (other.minY > localTop + 1.0E-3 || other.maxY <= localTop + 1.0E-3) continue;
                boolean covers = switch (side) {
                    case 0 -> other.minX <= box.minX + 1.0E-3 && other.maxX > box.minX + 1.0E-3;
                    case 1 -> other.maxX >= box.maxX - 1.0E-3 && other.minX < box.maxX - 1.0E-3;
                    case 2 -> other.minZ <= box.minZ + 1.0E-3 && other.maxZ > box.minZ + 1.0E-3;
                    default -> other.maxZ >= box.maxZ - 1.0E-3 && other.minZ < box.maxZ - 1.0E-3;
                };
                if (!covers) continue;
                float c0 = originAlong + (float) (side < 2 ? other.minZ : other.minX) - EDGE_EPS;
                float c1 = originAlong + (float) (side < 2 ? other.maxZ : other.maxX) + EDGE_EPS;
                int count = 0;
                float[] nextStart = this.segStartNext;
                float[] nextEnd = this.segEndNext;
                for (int i = 0; i < segments && count < nextStart.length - 1; i++) {
                    float s0 = segStart[i], s1 = segEnd[i];
                    if (c1 <= s0 || c0 >= s1) {
                        nextStart[count] = s0; nextEnd[count++] = s1;
                        continue;
                    }
                    if (c0 > s0) { nextStart[count] = s0; nextEnd[count++] = c0; }
                    if (c1 < s1) { nextStart[count] = c1; nextEnd[count++] = s1; }
                }
                System.arraycopy(nextStart, 0, segStart, 0, count);
                System.arraycopy(nextEnd, 0, segEnd, 0, count);
                segments = count;
                if (segments == 0) return;
            }
        }
        float span = Math.max(1.0E-4f, a1 - a0);
        for (int i = 0; i < segments; i++) {
            float s0 = segStart[i], s1 = segEnd[i];
            if (s1 - s0 < 0.02f) continue;
            float t0 = (s0 - a0) / span, t1 = (s1 - a0) / span;
            float y0 = MathHelper.lerp(t0, hA, hB);
            float y1 = MathHelper.lerp(t1, hA, hB);
            float currentLip0 = MathHelper.lerp(t0, lipA, lipB);
            float currentLip1 = MathHelper.lerp(t1, lipA, lipB);
            float b0 = blockTopLocal - currentLip0;
            float b1 = blockTopLocal - currentLip1;

            float u0 = s0 - MathHelper.floor(s0), u1 = u0 + (s1 - s0);
            float low = shade * 0.88f;

            if (side < 2) {
                pushQuad(plane, b0, s0, u0, 0.0f, low,
                        plane, y0, s0, u0, SIDE_V, shade,
                        plane, y1, s1, u1, SIDE_V, shade,
                        plane, b1, s1, u1, 0.0f, low);
            } else {
                pushQuad(s0, b0, plane, u0, 0.0f, low,
                        s0, y0, plane, u0, SIDE_V, shade,
                        s1, y1, plane, u1, SIDE_V, shade,
                        s1, b1, plane, u1, 0.0f, low);
            }
        }
    }

    private boolean needsSide(int gx, int gz, float top) {
        int side = this.gridRadius * 2 + 1;
        if (gx < 0 || gz < 0 || gx >= side || gz >= side) return true;
        int index = gz * side + gx;
        float base = this.surfaceBase[index];
        if (Float.isNaN(base)) return true;
        return !(this.surfaceFull[index] && base >= top - 1.0E-3f);
    }

    private int cornerOcclusion(int gx, int gz, int dx, int dz, float top) {
        boolean a = higher(gx + dx, gz, top);
        boolean b = higher(gx, gz + dz, top);
        if (a && b) return 3;
        return (a ? 1 : 0) + (b ? 1 : 0) + (higher(gx + dx, gz + dz, top) ? 1 : 0);
    }

    private boolean higher(int gx, int gz, float top) {
        int side = this.gridRadius * 2 + 1;
        if (gx < 0 || gz < 0 || gx >= side || gz >= side) return false;
        float base = this.surfaceBase[gz * side + gx];
        return !Float.isNaN(base) && base > top + 0.3f;
    }

    private static float occlusionShade(int ao) {
        return 1.0f - ao * 0.13f;
    }

    private static float thickness(double x, double z, float depth, float scale, int ao) {
        float n = valueNoise(x * 0.55, z * 0.55) * 0.65f + valueNoise(x * 1.9 + 17.3, z * 1.9 - 4.1) * 0.35f;
        return depth * scale * (0.75f + n * 0.60f) + depth * 0.30f * ao;
    }

    private static float valueNoise(double x, double z) {
        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);
        float fx = (float) (x - ix);
        float fz = (float) (z - iz);
        fx = fx * fx * (3.0f - 2.0f * fx);
        fz = fz * fz * (3.0f - 2.0f * fz);
        float a = hash(ix, iz);
        float b = hash(ix + 1, iz);
        float c = hash(ix, iz + 1);
        float d = hash(ix + 1, iz + 1);
        return MathHelper.lerp(fz, MathHelper.lerp(fx, a, b), MathHelper.lerp(fx, c, d));
    }

    private void pushQuad(float x0, float y0, float z0, float u0, float v0, float s0,
                          float x1, float y1, float z1, float u1, float v1, float s1,
                          float x2, float y2, float z2, float u2, float v2, float s2,
                          float x3, float y3, float z3, float u3, float v3, float s3) {
        if (this.meshQuads >= MAX_GROUND_QUADS) return;
        int need = (this.meshQuads + 1) * FLOATS_PER_QUAD;
        if (this.mesh.length < need) {
            this.mesh = java.util.Arrays.copyOf(this.mesh, Math.max(need, this.mesh.length * 2));
        }
        int o = this.meshQuads * FLOATS_PER_QUAD;
        float[] m = this.mesh;
        m[o] = x0; m[o + 1] = y0; m[o + 2] = z0; m[o + 3] = u0; m[o + 4] = v0; m[o + 5] = s0;
        m[o + 6] = x1; m[o + 7] = y1; m[o + 8] = z1; m[o + 9] = u1; m[o + 10] = v1; m[o + 11] = s1;
        m[o + 12] = x2; m[o + 13] = y2; m[o + 14] = z2; m[o + 15] = u2; m[o + 16] = v2; m[o + 17] = s2;
        m[o + 18] = x3; m[o + 19] = y3; m[o + 20] = z3; m[o + 21] = u3; m[o + 22] = v3; m[o + 23] = s3;
        this.meshQuads++;
    }

    private void drawGround(WorldRenderContext context, Vec3d cam, Config config, float ambient) {
        if (this.meshQuads <= 0) return;
        float radius = Math.min(config.groundRadius, this.gridRadius - GRID_MARGIN);
        float fadeStart = radius * 0.72f;
        float fadeRange = Math.max(1.0f, radius - fadeStart);
        float sun = MathHelper.clamp(0.55f + ambient * 0.45f, 0.4f, 1.0f);
        float cr = ((config.color >> 16) & 0xFF) / 255.0f;
        float cg = ((config.color >> 8) & 0xFF) / 255.0f;
        float cb = (config.color & 0xFF) / 255.0f;

        float ox = (float) (this.gridOriginX - cam.x);
        float oy = (float) (this.gridOriginY - cam.y);
        float oz = (float) (this.gridOriginZ - cam.z);

        VertexConsumer consumer = null;
        float[] m = this.mesh;
        for (int q = 0; q < this.meshQuads; q++) {
            int o = q * FLOATS_PER_QUAD;
            float cx = (m[o] + m[o + 12]) * 0.5f + ox;
            float cz = (m[o + 2] + m[o + 14]) * 0.5f + oz;
            float flat = (float) Math.sqrt(cx * cx + cz * cz);
            if (flat > radius) continue;
            float fade = flat <= fadeStart ? 1.0f : 1.0f - (flat - fadeStart) / fadeRange;
            float alpha = MathHelper.clamp(config.opacity * fade, 0.0f, 1.0f);
            if (alpha <= 0.02f) continue;

            if (consumer == null) {
                consumer = context.consumers().getBuffer(Render3DUtils.texturedLayer(SNOW_COVER));
            }

            for (int v = 0; v < 4; v++) {
                int p = o + v * 6;
                float shade = MathHelper.clamp(m[p + 5] * sun, 0.0f, 1.0f);
                int r = (int) (cr * shade * 0.97f * 255f);
                int g = (int) (cg * (0.03f + shade * 0.97f) * 255f);
                int b = (int) (Math.min(1.0f, cb * (0.16f + shade * 0.86f)) * 255f);
                consumer.vertex(m[p] + ox, m[p + 1] + oy, m[p + 2] + oz)
                        .color(r, g, b, (int) (alpha * 255f))
                        .texture(m[p + 3], m[p + 4])
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                        .normal(0f, 1f, 0f);
            }
        }
    }

    private double flakeGround(ClientWorld level, int x, int z) {
        if (resolveSurface(level, x, z)) {
            return this.resolvedY + this.resolvedShape.getMax(Direction.Axis.Y);
        }
        try {
            return level.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        } catch (Throwable ignored) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    private float ambientLight(MinecraftClient mc, Vec3d cam) {
        try {
            if (mc.world == null) return 1.0f;
            int light = mc.world.getLightLevel(BlockPos.ofFloored(cam));
            return MathHelper.clamp(light / 15.0f, 0.25f, 1.0f);
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private float random(float min, float max) {
        return min + this.random.nextFloat() * (max - min);
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0f;
    }

    private static final class Flake {
        double x, y, z;
        double vx, vy, vz;
        double terminal = -1.5;
        double groundY = Double.NEGATIVE_INFINITY;
        float size = 0.08f;
        float drag = 0.6f;
        float swayFrequency = 1.0f;
        float swayAmplitude = 0.6f;
        float phase;
        float spin;
        float rotation;
        float brightness = 1.0f;
        boolean detailed;
    }
}
