package ez.minar.system.features.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.managers.BlockEspManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.WorldToScreen;
import ez.minar.utils.render.msdf.Msdf;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.block.TrialSpawnerBlock;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@NewFunction(name = "BlockESP", desc = "Сундуки, шалкеры, спавнеры и древние обломки сквозь стены", category = Category.RENDER)
public class BlockESP extends Function {
    public static BlockESP Instance;

    private static final RenderPipeline FILL_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "blockesp_fill_nodepth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );
    private static final RenderLayer FILL_NO_DEPTH = RenderLayer.of("minar_blockesp_fill_nodepth",
            RenderSetup.builder(FILL_NO_DEPTH_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    private enum Kind {
        CHEST("\u0421\u0443\u043D\u0434\u0443\u043A"),
        SHULKER("\u0428\u0430\u043B\u043A\u0435\u0440"),
        SPAWNER("\u0421\u043F\u0430\u0432\u043D\u0435\u0440"),
        DEBRIS("\u0414\u0440\u0435\u0432\u043D\u0438\u0435 \u043E\u0431\u043B\u043E\u043C\u043A\u0438"),
        CUSTOM("");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private record FoundEntry(Kind kind, Block block) {}

    private record SectionRef(int cx, int cz, int index, double distanceSq) {}

    private record LabelData(Vec3d topCenter, String text, double distanceSq) {}

    private static final class Group {
        final BlockPos start;
        final Box bounds;
        final FoundEntry entry;
        final double distanceSq;

        Group(BlockPos start, Box bounds, FoundEntry entry, double distanceSq) {
            this.start = start;
            this.bounds = bounds;
            this.entry = entry;
            this.distanceSq = distanceSq;
        }
    }

    private final NumberSetting radius = new NumberSetting("\u0420\u0430\u0434\u0438\u0443\u0441", 64.0, 32.0, 164.0, 4.0);
    private final BooleanSetting showChests = new BooleanSetting("\u0421\u0443\u043D\u0434\u0443\u043A\u0438", true);
    private final BooleanSetting showShulkers = new BooleanSetting("\u0428\u0430\u043B\u043A\u0435\u0440\u044B", true);
    private final BooleanSetting showSpawners = new BooleanSetting("\u0421\u043F\u0430\u0432\u043D\u0435\u0440\u044B", true);
    private final BooleanSetting showDebris = new BooleanSetting("\u0414\u0440\u0435\u0432\u043D\u0438\u0435 \u043E\u0431\u043B\u043E\u043C\u043A\u0438", true);
    private final ColorSetting chestColor = new ColorSetting("\u0426\u0432\u0435\u0442 \u0441\u0443\u043D\u0434\u0443\u043A\u043E\u0432", new Color(255, 140, 0));
    private final ColorSetting shulkerColor = new ColorSetting("\u0426\u0432\u0435\u0442 \u0448\u0430\u043B\u043A\u0435\u0440\u043E\u0432", new Color(168, 85, 247));
    private final ColorSetting spawnerColor = new ColorSetting("\u0426\u0432\u0435\u0442 \u0441\u043F\u0430\u0432\u043D\u0435\u0440\u043E\u0432", new Color(15, 15, 15));
    private final ColorSetting debrisColor = new ColorSetting("\u0426\u0432\u0435\u0442 \u043E\u0431\u043B\u043E\u043C\u043A\u043E\u0432", new Color(139, 69, 19));
    private final ModeSetting renderMode = new ModeSetting("\u0420\u0435\u0436\u0438\u043C \u0440\u0435\u043D\u0434\u0435\u0440\u0430",
            "\u041A\u043E\u043D\u0442\u0443\u0440 \u0438 \u0437\u0430\u043B\u0438\u0432\u043A\u0430", "\u041A\u043E\u043D\u0442\u0443\u0440", "\u0417\u0430\u043B\u0438\u0432\u043A\u0430");
    private final NumberSetting lineWidth = new NumberSetting("\u0422\u043E\u043B\u0449\u0438\u043D\u0430 \u043B\u0438\u043D\u0438\u0439", 2.2, 0.5, 5.0, 0.1);
    private final NumberSetting fillAlpha = new NumberSetting("\u041F\u0440\u043E\u0437\u0440\u0430\u0447\u043D\u043E\u0441\u0442\u044C", 0.35, 0.05, 1.0, 0.05);
    private final BooleanSetting tracers = new BooleanSetting("\u0422\u0440\u0430\u0441\u0441\u0435\u0440\u044B", false);
    private final BooleanSetting labels = new BooleanSetting("\u041F\u043E\u0434\u043F\u0438\u0441\u0438", true);
    private final NumberSetting limit = new NumberSetting("\u041B\u0438\u043C\u0438\u0442 \u0431\u043B\u043E\u043A\u043E\u0432", 512.0, 32.0, 2048.0, 32.0);
    private final NumberSetting scanSpeed = new NumberSetting("\u0421\u043A\u043E\u0440\u043E\u0441\u0442\u044C \u0441\u043A\u0430\u043D\u0430", 24.0, 4.0, 128.0, 4.0);

    private final Map<BlockPos, FoundEntry> found = new LinkedHashMap<>();
    private final Map<BlockPos, BoxAnimation> boxAnimations = new LinkedHashMap<>();
    private final List<Group> renderGroups = new ArrayList<>();
    private final List<LabelData> frameLabels = new ArrayList<>();
    private final Set<BlockPos> visitedScratch = new HashSet<>();

    private final ArrayDeque<SectionRef> sectionQueue = new ArrayDeque<>();
    private Set<Block> scanTargets = Set.of();
    private Predicate<BlockState> scanPredicate = state -> false;
    private int targetsSignature = Integer.MIN_VALUE;
    private int beTimer;
    private int pruneTimer;
    private long lastQueueRebuild;
    private long lastWorldFrame;
    private long lastHoverFrame;
    private Group hoveredGroup;
    private float hoverAnimation;

    public BlockESP() {
        Instance = this;
        addSettings(radius, showChests, showShulkers, showSpawners, showDebris,
                chestColor, shulkerColor, spawnerColor, debrisColor,
                renderMode, lineWidth, fillAlpha, tracers, labels, limit, scanSpeed);
    }

    @Override
    public void onDisable() {
        resetScan();
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.render(context);
    }

    // ==================== Обновление целей ====================

    private void updateTargets() {
        Set<Block> customBlocks = BlockEspManager.getBlocks();
        boolean chestsOn = showChests.isEnabled();
        boolean shulkersOn = showShulkers.isEnabled();
        boolean spawnersOn = showSpawners.isEnabled();
        boolean debrisOn = showDebris.isEnabled();

        int signature = Objects.hash(chestsOn, shulkersOn, spawnersOn, debrisOn, customBlocks);
        if (signature == targetsSignature) return;
        targetsSignature = signature;

        Set<Block> targets = new HashSet<>();
        if (debrisOn) targets.add(Blocks.ANCIENT_DEBRIS);
        targets.addAll(customBlocks);
        scanTargets = Set.copyOf(targets);
        scanPredicate = state -> scanTargets.contains(state.getBlock());

        found.clear();
        boxAnimations.clear();
        sectionQueue.clear();
        lastQueueRebuild = 0L;
        beTimer = 10;
    }

    // ==================== Поиск блок-сущностей ====================

    private void scanBlockEntities() {
        if (mc.world == null || mc.player == null) return;
        double maxDistanceSq = radius.getValue() * radius.getValue();
        Vec3d center = mc.player.getEyePos();

        ClientChunkManager chunkManager = mc.world.getChunkManager();
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());
        int chunkRadius = ((int) radius.getValue() >> 4) + 1;

        for (int cx = playerChunk.x - chunkRadius; cx <= playerChunk.x + chunkRadius; cx++) {
            for (int cz = playerChunk.z - chunkRadius; cz <= playerChunk.z + chunkRadius; cz++) {
                WorldChunk chunk = chunkManager.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    double dx = pos.getX() + 0.5 - center.x;
                    double dy = pos.getY() + 0.5 - center.y;
                    double dz = pos.getZ() + 0.5 - center.z;
                    if (dx * dx + dy * dy + dz * dz > maxDistanceSq) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    FoundEntry entry = classify(state);
                    if (entry == null || entry.kind() == Kind.DEBRIS || entry.kind() == Kind.CUSTOM) continue;
                    FoundEntry existing = found.get(pos);
                    if (existing == null || (existing.kind() == Kind.CUSTOM && entry.kind() != Kind.CUSTOM)) {
                        found.put(pos.toImmutable(), entry);
                    }
                }
            }
        }
    }

    private FoundEntry classify(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ChestBlock && showChests.isEnabled()) {
            return new FoundEntry(Kind.CHEST, block);
        }
        if (block instanceof ShulkerBoxBlock && showShulkers.isEnabled()) {
            return new FoundEntry(Kind.SHULKER, block);
        }
        if ((block instanceof SpawnerBlock || block instanceof TrialSpawnerBlock) && showSpawners.isEnabled()) {
            return new FoundEntry(Kind.SPAWNER, block);
        }
        if (block == Blocks.ANCIENT_DEBRIS && showDebris.isEnabled()) {
            return new FoundEntry(Kind.DEBRIS, block);
        }
        if (scanTargets.contains(block)) {
            return new FoundEntry(Kind.CUSTOM, block);
        }
        return null;
    }

    // ==================== Секционный сканер (обломки и кастомные блоки) ====================

    private void rebuildScanQueue() {
        sectionQueue.clear();
        if (scanTargets.isEmpty() || mc.world == null || mc.player == null) return;

        ClientChunkManager chunkManager = mc.world.getChunkManager();
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());
        Vec3d eye = mc.player.getEyePos();
        int chunkRadius = ((int) radius.getValue() >> 4) + 1;
        List<SectionRef> refs = new ArrayList<>();

        for (int cx = playerChunk.x - chunkRadius; cx <= playerChunk.x + chunkRadius; cx++) {
            for (int cz = playerChunk.z - chunkRadius; cz <= playerChunk.z + chunkRadius; cz++) {
                WorldChunk chunk = chunkManager.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int index = 0; index < sections.length; index++) {
                    double centerX = (cx << 4) + 8;
                    double centerY = chunk.getBottomY() + (index << 4) + 8;
                    double centerZ = (cz << 4) + 8;
                    double dx = centerX - eye.x;
                    double dy = centerY - eye.y;
                    double dz = centerZ - eye.z;
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq > Math.pow(radius.getValue() + 12.0, 2)) continue;
                    refs.add(new SectionRef(cx, cz, index, distanceSq));
                }
            }
        }

        refs.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        sectionQueue.addAll(refs);
        lastQueueRebuild = System.currentTimeMillis();
    }

    private void processSectionQueue() {
        if (scanTargets.isEmpty()) return;

        if (sectionQueue.isEmpty()) {
            if (System.currentTimeMillis() - lastQueueRebuild < 2000L) return;
            rebuildScanQueue();
            if (sectionQueue.isEmpty()) return;
        }

        ClientChunkManager chunkManager = mc.world.getChunkManager();
        double maxDistanceSq = Math.pow(radius.getValue(), 2);
        Vec3d eye = mc.player.getEyePos();
        int budget = (int) scanSpeed.getValue();

        while (budget-- > 0 && !sectionQueue.isEmpty()) {
            SectionRef ref = sectionQueue.pollFirst();
            WorldChunk chunk = chunkManager.getChunk(ref.cx(), ref.cz(), ChunkStatus.FULL, false);
            if (chunk == null) continue;
            ChunkSection[] sections = chunk.getSectionArray();
            if (ref.index() >= sections.length) continue;
            ChunkSection section = sections[ref.index()];
            if (section == null || section.isEmpty()) continue;

            PalettedContainer<BlockState> container = section.getBlockStateContainer();
            if (!container.hasAny(scanPredicate)) continue;

            int baseX = ref.cx() << 4;
            int baseZ = ref.cz() << 4;
            int baseY = chunk.getBottomY() + (ref.index() << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!scanPredicate.test(state)) continue;
                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        double dx = pos.getX() + 0.5 - eye.x;
                        double dy = pos.getY() + 0.5 - eye.y;
                        double dz = pos.getZ() + 0.5 - eye.z;
                        if (dx * dx + dy * dy + dz * dz > maxDistanceSq) continue;
                        found.put(pos.toImmutable(), classify(state));
                    }
                }
            }
        }
    }

    // ==================== Очистка устаревших записей ====================

    private void pruneInvalid() {
        if (mc.world == null || mc.player == null) return;
        double maxDistanceSq = radius.getValue() * radius.getValue();
        Vec3d eye = mc.player.getEyePos();

        found.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            double dx = pos.getX() + 0.5 - eye.x;
            double dy = pos.getY() + 0.5 - eye.y;
            double dz = pos.getZ() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSq) return true;

            FoundEntry current = classify(mc.world.getBlockState(pos));
            return current == null || !current.equals(entry.getValue());
        });
    }

    // ==================== Рендер ====================

    private void render(WorldRenderContext context) {
        if (mc.world == null || mc.player == null) return;

        updateTargets();
        if (++beTimer >= 10) {
            beTimer = 0;
            scanBlockEntities();
        }
        processSectionQueue();
        if (++pruneTimer >= 40) {
            pruneTimer = 0;
            pruneInvalid();
        }

        if (found.isEmpty()) {
            renderGroups.clear();
            boxAnimations.clear();
            hoveredGroup = null;
            return;
        }

        Vec3d camera = context.worldState().cameraRenderState.pos;
        Vec3d eye = mc.player.getEyePos();
        float frameDelta = worldDelta();
        int limitValue = (int) limit.getValue();

        visitedScratch.clear();
        renderGroups.clear();
        Set<BlockPos> activeAnimations = new HashSet<>();
        int processed = 0;

        for (Map.Entry<BlockPos, FoundEntry> entry : found.entrySet()) {
            if (processed >= limitValue) break;
            BlockPos start = entry.getKey();
            if (!visitedScratch.add(start)) continue;

            FoundEntry groupEntry = entry.getValue();
            Box mergedBounds = null;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                BlockPos pos = queue.pollFirst();
                processed++;
                Box partBox = new Box(pos).expand(0.002);
                mergedBounds = mergedBounds == null ? partBox : mergedBounds.union(partBox);

                for (BlockPos neighbor : new BlockPos[]{
                        pos.east(), pos.west(), pos.up(), pos.down(), pos.south(), pos.north()
                }) {
                    if (!visitedScratch.contains(neighbor) && groupEntry.equals(found.get(neighbor))) {
                        visitedScratch.add(neighbor);
                        queue.addLast(neighbor);
                    }
                }
            }

            if (mergedBounds == null) continue;
            activeAnimations.add(start);
            BoxAnimation animation = boxAnimations.computeIfAbsent(start, ignored -> new BoxAnimation());
            animation.update(frameDelta, isHovered(start));

            double dx = mergedBounds.getCenter().x - eye.x;
            double dy = mergedBounds.getCenter().y - eye.y;
            double dz = mergedBounds.getCenter().z - eye.z;
            renderGroups.add(new Group(start, mergedBounds, groupEntry,
                    dx * dx + dy * dy + dz * dz));

            Box animatedBox = animateBox(mergedBounds, animation.appear, animation.hover)
                    .offset(-camera.x, -camera.y, -camera.z);
            drawGroup(context, animatedBox, groupEntry, colorFor(groupEntry, start),
                    (float) (animation.appear), animation.hover, camera);
        }
        boxAnimations.keySet().removeIf(key -> !activeAnimations.contains(key));

        collectLabels();
    }

    private boolean isHovered(BlockPos start) {
        return hoveredGroup != null && hoveredGroup.start.equals(start);
    }

    private void drawGroup(WorldRenderContext context, Box cameraBox, FoundEntry entry,
                           Color color, float appear, float hover, Vec3d camera) {
        boolean wantFill = !renderMode.isEnabled("\u041A\u043E\u043D\u0442\u0443\u0440");
        boolean wantOutline = !renderMode.isEnabled("\u0417\u0430\u043B\u0438\u0432\u043A\u0430");
        int appearAlpha = Math.max(1, (int) (255 * Math.clamp(appear, 0F, 1F)));

        if (wantFill) {
            drawBoxFill(context, cameraBox, color, (float) fillAlpha.getValue() * Math.clamp(appear, 0F, 1F));
        }

        if (wantOutline) {
            Color glow = darken(color);
            drawOutline(context, cameraBox, glow, (float) lineWidth.getValue() + 1.8F,
                    Math.max(1, (int) (90 * appear)));
            drawOutline(context, cameraBox, color, (float) lineWidth.getValue(), appearAlpha);
        }

        if (tracers.isEnabled()) {
            drawTracer(context, cameraBox, color, (float) lineWidth.getValue(),
                    Math.max(1, (int) (180 * appear)));
        }
    }

    private void collectLabels() {
        frameLabels.clear();
        if (!labels.isEnabled()) return;

        renderGroups.stream()
                .sorted((a, b) -> Double.compare(a.distanceSq, b.distanceSq))
                .limit(16)
                .forEach(group -> {
                    String text = displayName(group.entry) + "  " + Math.round(Math.sqrt(group.distanceSq)) + "m";
                    Vec3d topCenter = new Vec3d(group.bounds.getCenter().x, group.bounds.maxY + 0.25, group.bounds.getCenter().z);
                    frameLabels.add(new LabelData(topCenter, text, group.distanceSq));
                });
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        DrawContext context = event.getContext();
        if (context == null || mc.player == null || mc.world == null) return;

        float centerX = RenderUtil.getFixedScaledWidth() * 0.5F;
        float centerY = RenderUtil.getFixedScaledHeight() * 0.5F;

        Group aimed = null;
        float bestDistance = 400F;
        for (Group group : renderGroups) {
            float[] screen = WorldToScreen.project(group.bounds.getCenter());
            if (screen == null) continue;
            float dx = screen[0] - centerX;
            float dy = screen[1] - centerY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                aimed = group;
            }
        }

        if (aimed != hoveredGroup) {
            hoveredGroup = aimed;
            hoverAnimation = 0F;
        }
        updateHoverAnimation(aimed != null);
        if (hoverAnimation > 0.01F && hoveredGroup != null) {
            Box bounds = hoveredGroup.bounds;
            String text = displayName(hoveredGroup.entry) + "  "
                    + Math.round(Math.sqrt(hoveredGroup.distanceSq)) + "m";
            Vec3d textTarget = new Vec3d(bounds.getCenter().x, bounds.maxY + 0.45, bounds.getCenter().z);
            float[] screen = WorldToScreen.project(textTarget);
            if (screen != null) {
                int alpha = Math.clamp((int) (hoverAnimation * 255F), 0, 255);
                float scale = 0.86F + hoverAnimation * 0.14F;
                float size = 8F * scale;
                float width = Msdf.width(Msdf.SF_BOLD, text, size);
                float x = screen[0] - width * 0.5F;
                float y = screen[1] - 13F - (1F - hoverAnimation) * 5F;

                RenderUtil.text(context, Msdf.SF_BOLD, x + 0.7F, y + 0.7F, text, size,
                        new Color(0, 0, 0, Math.min(alpha, 150)));
                RenderUtil.text(context, Msdf.SF_BOLD, x, y, text, size,
                        new Color(244, 244, 246, alpha));
            }
        }

        if (!labels.isEnabled()) return;
        for (LabelData label : frameLabels) {
            float[] screen = WorldToScreen.project(label.topCenter());
            if (screen == null) continue;
            float size = 7F;
            float width = Msdf.width(Msdf.SF_BOLD, label.text(), size);
            float x = screen[0] - width * 0.5F;
            float y = screen[1];
            RenderUtil.text(context, Msdf.SF_BOLD, x + 0.6F, y + 0.6F, label.text(), size,
                    new Color(0, 0, 0, 140));
            RenderUtil.text(context, Msdf.SF_BOLD, x, y, label.text(), size,
                    new Color(240, 240, 245, 220));
        }
    }

    private static String displayName(FoundEntry entry) {
        return entry.block() != null ? entry.block().getName().getString() : entry.kind().label;
    }

    private void updateHoverAnimation(boolean aimed) {
        long now = System.nanoTime();
        float delta = lastHoverFrame == 0L ? 0F
                : Math.min((now - lastHoverFrame) / 1_000_000_000F, 0.05F);
        lastHoverFrame = now;
        float target = aimed ? 1F : 0F;
        hoverAnimation += (target - hoverAnimation) * (1F - (float) Math.exp(-14F * delta));
        if (!aimed && hoverAnimation < 0.01F) {
            hoverAnimation = 0F;
            hoveredGroup = null;
        }
    }

    private void resetScan() {
        found.clear();
        renderGroups.clear();
        boxAnimations.clear();
        sectionQueue.clear();
        frameLabels.clear();
        visitedScratch.clear();
        scanTargets = Set.of();
        scanPredicate = state -> false;
        targetsSignature = Integer.MIN_VALUE;
        beTimer = 0;
        pruneTimer = 0;
        lastQueueRebuild = 0L;
        hoveredGroup = null;
        hoverAnimation = 0F;
        lastHoverFrame = 0L;
        lastWorldFrame = 0L;
    }

    private float worldDelta() {
        long now = System.nanoTime();
        float delta = lastWorldFrame == 0L ? 0F
                : Math.min((now - lastWorldFrame) / 1_000_000_000F, 0.05F);
        lastWorldFrame = now;
        return delta;
    }

    // ==================== Геометрия и отрисовка ====================

    private static Box animateBox(Box target, float appear, float hover) {
        float eased = 1F - (float) Math.pow(1F - Math.clamp(appear, 0F, 1F), 3);
        Vec3d center = target.getCenter();
        double halfX = target.getLengthX() * 0.5 * eased;
        double halfY = target.getLengthY() * 0.5 * eased;
        double halfZ = target.getLengthZ() * 0.5 * eased;
        double expansion = 0.015 + hover * 0.075;
        return new Box(
                center.x - halfX, center.y - halfY, center.z - halfZ,
                center.x + halfX, center.y + halfY, center.z + halfZ
        ).expand(expansion);
    }

    private Color colorFor(FoundEntry entry, BlockPos pos) {
        return switch (entry.kind()) {
            case CHEST -> chestColor.getColor();
            case SHULKER -> shulkerColor.getColor();
            case SPAWNER -> spawnerColor.getColor();
            case DEBRIS -> debrisColor.getColor();
            case CUSTOM -> {
                int mapColor = mc.world.getBlockState(pos).getMapColor(mc.world, pos).color;
                yield mapColor == 0 ? new Color(210, 210, 215) : new Color(mapColor);
            }
        };
    }

    private static Color darken(Color color) {
        return new Color(
                Math.max(0, color.getRed() - 55),
                Math.max(0, color.getGreen() - 55),
                Math.max(0, color.getBlue() - 55));
    }

    private static void drawOutline(WorldRenderContext context, Box b, Color color, float width, int alpha) {
        VertexConsumer v = context.consumers().getBuffer(BlockOverlay.SEE_THROUGH_LINES);
        MatrixStack.Entry m = context.matrices().peek();
        line(v, m, color, width, alpha, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ);
        line(v, m, color, width, alpha, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ);
        line(v, m, color, width, alpha, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
        line(v, m, color, width, alpha, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ);
        line(v, m, color, width, alpha, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ);
        line(v, m, color, width, alpha, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ);
        line(v, m, color, width, alpha, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        line(v, m, color, width, alpha, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
        line(v, m, color, width, alpha, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ);
        line(v, m, color, width, alpha, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ);
        line(v, m, color, width, alpha, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ);
        line(v, m, color, width, alpha, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ);
    }

    private static void drawTracer(WorldRenderContext context, Box cameraBox, Color color, float width, int alpha) {
        VertexConsumer v = context.consumers().getBuffer(BlockOverlay.SEE_THROUGH_LINES);
        MatrixStack.Entry m = context.matrices().peek();
        Vec3d center = cameraBox.getCenter();
        line(v, m, color, width, alpha, 0.0, 0.0, 0.0, center.x, center.y, center.z);
    }

    private static void drawBoxFill(WorldRenderContext context, Box b, Color color, float alpha) {
        int a = (int) (Math.clamp(alpha, 0F, 1F) * 255F);
        if (a <= 0) return;
        VertexConsumer v = context.consumers().getBuffer(FILL_NO_DEPTH);
        MatrixStack.Entry m = context.matrices().peek();
        float inset = 0.003F;
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;

        quad(v, m, color, a, x1, y2 + inset, z2, x2, y2 + inset, z2, x2, y2 + inset, z1, x1, y2 + inset, z1);
        quad(v, m, color, a, x1, y1 - inset, z1, x2, y1 - inset, z1, x2, y1 - inset, z2, x1, y1 - inset, z2);
        quad(v, m, color, a, x1, y1, z1 - inset, x1, y2, z1 - inset, x2, y2, z1 - inset, x2, y1, z1 - inset);
        quad(v, m, color, a, x1, y1, z2 + inset, x2, y1, z2 + inset, x2, y2, z2 + inset, x1, y2, z2 + inset);
        quad(v, m, color, a, x1 - inset, y1, z1, x1 - inset, y1, z2, x1 - inset, y2, z2, x1 - inset, y2, z1);
        quad(v, m, color, a, x2 + inset, y1, z1, x2 + inset, y2, z1, x2 + inset, y2, z2, x2 + inset, y1, z2);
    }

    private static void quad(VertexConsumer v, MatrixStack.Entry m, Color c, int a,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        vertex(v, m, c, a, x1, y1, z1);
        vertex(v, m, c, a, x2, y2, z2);
        vertex(v, m, c, a, x3, y3, z3);
        vertex(v, m, c, a, x4, y4, z4);
    }

    private static void vertex(VertexConsumer v, MatrixStack.Entry m, Color c, int a,
                               float x, float y, float z) {
        v.vertex(m, x, y, z).color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static void line(VertexConsumer v, MatrixStack.Entry m, Color c, float width, int alpha,
                             double x1, double y1, double z1, double x2, double y2, double z2) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0001F) return;
        v.vertex(m, (float) x1, (float) y1, (float) z1).color(c.getRed(), c.getGreen(), c.getBlue(), alpha)
                .normal(m, dx / length, dy / length, dz / length).lineWidth(width);
        v.vertex(m, (float) x2, (float) y2, (float) z2).color(c.getRed(), c.getGreen(), c.getBlue(), alpha)
                .normal(m, dx / length, dy / length, dz / length).lineWidth(width);
    }

    private static final class BoxAnimation {
        private float appear;
        private float hover;

        private void update(float delta, boolean hovered) {
            appear += (1F - appear) * (1F - (float) Math.exp(-11F * delta));
            float hoverTarget = hovered ? 1F : 0F;
            hover += (hoverTarget - hover) * (1F - (float) Math.exp(-15F * delta));
        }
    }
}
