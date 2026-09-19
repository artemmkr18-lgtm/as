package ez.minar.system.features.combat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.mixins.interfaces.IMinecraftClient;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.render.BlockOverlay;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.MoveCorrection;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.concurrent.ConcurrentLinkedQueue;

@NewFunction(name = "AutoClicker", desc = "Автоматические клики (CPS) и умный ломатель блоков/якорей вокруг цели с 3D ESP", category = Category.COMBAT)
public class AutoClicker extends Function {

    public static AutoClicker Instance;

    // --- Настройки режима и триггера ---
    private final ListSetting trigger = new ListSetting("Триггер", "Всегда", "При зажатии", "При наводке");
    private final NumberSetting cps = new NumberSetting("ЛКМ CPS", 12.0, 1.0, 30.0, 1.0);
    private final BooleanSetting cooldownCheck = new BooleanSetting("Ждать кулдаун атаки", false);

    // --- Атака сущностей ---
    private final BooleanSetting attackEntities = new BooleanSetting("Атака сущностей", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Атака мобов", true);
    private final NumberSetting entityRange = new NumberSetting("Дистанция сущностей", 4.0, 1.0, 6.0, 0.1);

    // --- Умный ломатель блоков (Vorkis Engine) ---
    private final BooleanSetting autoMineBlocks = new BooleanSetting("Ломать блоки вокруг", true);
    private final ListSetting blockMode = new ListSetting(
            "Тип блоков",
            "Все полезные",
            "Якоря",
            "Обсидиан",
            "Паутина",
            "Все ломаемые"
    );
    private final NumberSetting blockReach = new NumberSetting("Дистанция блоков", 4.5, 1.0, 6.0, 0.1);
    private final NumberSetting searchRadius = new NumberSetting("Радиус сканирования", 3.5, 1.0, 6.0, 0.5);
    private final BooleanSetting interactAnchor = new BooleanSetting("Клик по якорю", true);
    private final BooleanSetting autoTool = new BooleanSetting("Авто инструмент", true);
    private final BooleanSetting silentSwap = new BooleanSetting("Возврат слота", true);

    // --- Правый клик (ПКМ) ---
    private final BooleanSetting rightClick = new BooleanSetting("Авто ПКМ", false);
    private final NumberSetting rightCps = new NumberSetting("ПКМ CPS", 15.0, 1.0, 30.0, 1.0);

    // --- Ротация ---
    private final BooleanSetting rotate = new BooleanSetting("Ротация к блоку", false);
    private final ListSetting rotationMode = new ListSetting("Тип ротации", "Быстрая", "Плавная", "Grim", "Нет");
    private final BooleanSetting syncWithAttackAura = new BooleanSetting("Цель из AttackAura", true);

    // --- Визуалы (ESP) ---
    private final BooleanSetting esp = new BooleanSetting("Подсветка (ESP)", true);
    private final ColorSetting espColor = new ColorSetting("Цвет ESP", new Color(155, 89, 255));
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность заливки", 0.25, 0.05, 1.0, 0.05);
    private final NumberSetting lineWidth = new NumberSetting("Толщина линий", 1.5, 0.5, 4.0, 0.5);

    // --- Внутреннее состояние ---
    private long lastLeftClickTime = 0;
    private long lastRightClickTime = 0;
    private int originalSlot = -1;
    private boolean hasSwapped = false;
    private BlockPos currentTarget = null;
    private Direction currentDirection = Direction.UP;
    private final ConcurrentLinkedQueue<FoundBlock> foundBlocks = new ConcurrentLinkedQueue<>();

    // --- Рендер пайплайн ---
    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "autoclicker_fill_nodepth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of("minar_autoclicker_fill_nodepth",
            RenderSetup.builder(FILL_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    public AutoClicker() {
        Instance = this;
        addSettings(
                trigger,
                cps,
                cooldownCheck,
                attackEntities,
                targetMobs,
                entityRange,
                autoMineBlocks,
                blockMode,
                blockReach,
                searchRadius,
                interactAnchor,
                autoTool,
                silentSwap,
                rightClick,
                rightCps,
                rotate,
                rotationMode,
                syncWithAttackAura,
                esp,
                espColor,
                fillAlpha,
                lineWidth
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        restoreSlot();
        resetState();
    }

    private void resetState() {
        currentTarget = null;
        currentDirection = Direction.UP;
        foundBlocks.clear();
        lastLeftClickTime = 0;
        lastRightClickTime = 0;
    }

    private void restoreSlot() {
        if (hasSwapped && originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            hasSwapped = false;
            originalSlot = -1;
        }
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all() || mc.player.isSpectator() || mc.currentScreen != null) {
            restoreSlot();
            currentTarget = null;
            return;
        }

        long now = System.currentTimeMillis();

        // Проверяем условие активации триггера
        boolean isAttackPressed = mc.options.attackKey.isPressed();
        boolean isUsePressed = mc.options.useKey.isPressed();
        boolean triggerActive;

        if (trigger.isEnabled("При зажатии")) {
            triggerActive = isAttackPressed;
        } else if (trigger.isEnabled("При наводке")) {
            triggerActive = mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS;
        } else {
            // "Всегда"
            triggerActive = true;
        }

        // --- Обработка правого клика (ПКМ) ---
        if (rightClick.isEnabled() && (trigger.isEnabled("Всегда") || isUsePressed)) {
            long rightDelay = (long) (1000.0 / Math.max(1.0, rightCps.getValue()));
            if (now - lastRightClickTime >= rightDelay) {
                lastRightClickTime = now;
                ((IMinecraftClient) mc).setItemUseCooldown(0);
                ((IMinecraftClient) mc).invokeDoItemUse();
            }
        }

        if (!triggerActive) {
            if (hasSwapped && silentSwap.isEnabled()) {
                restoreSlot();
            }
            currentTarget = null;
            return;
        }

        // --- Расчет интервала ЛКМ CPS ---
        long leftDelay = (long) (1000.0 / Math.max(1.0, cps.getValue()));
        if (now - lastLeftClickTime < leftDelay) {
            return;
        }

        // Проверка кулдауна оружия (если включена)
        if (cooldownCheck.isEnabled() && mc.player.getAttackCooldownProgress(0.5f) < 0.92f) {
            return;
        }

        boolean performedAction = false;

        // 1. Проверяем цель под прицелом (Crosshair)
        if (mc.crosshairTarget instanceof EntityHitResult entityHit && attackEntities.isEnabled()) {
            Entity entity = entityHit.getEntity();
            if (isValidEntityTarget(entity)) {
                ((IMinecraftClient) mc).invokeDoAttack();
                lastLeftClickTime = now;
                return;
            }
        } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir()) {
                if (autoTool.isEnabled()) {
                    ensureBestTool(state);
                }
                ((IMinecraftClient) mc).invokeDoAttack();
                addToFoundBlocks(pos);
                currentTarget = pos;
                lastLeftClickTime = now;
                return;
            }
        }

        // 2. Умный поиск сущности в радиусе (если под прицелом никого нет)
        if (attackEntities.isEnabled()) {
            LivingEntity targetEntity = findNearbyLivingEntity();
            if (targetEntity != null) {
                mc.interactionManager.attackEntity(mc.player, targetEntity);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastLeftClickTime = now;
                performedAction = true;
            }
        }

        // 3. Умное сканирование и ломание блоков вокруг (Vorkis Engine)
        if (autoMineBlocks.isEnabled()) {
            LivingEntity centerTarget = findBlockScanCenter();
            TargetCandidate candidate = findBestTargetBlock(centerTarget);

            if (candidate != null) {
                currentTarget = candidate.pos;
                currentDirection = candidate.side;
                BlockState targetState = mc.world.getBlockState(candidate.pos);

                // Ротация на блок (если включена)
                if (rotate.isEnabled() && !rotationMode.isEnabled("Нет")) {
                    Vec3d hitVec = candidate.hitVec != null ? candidate.hitVec : Vec3d.ofCenter(candidate.pos);
                    Rotation targetRot = RotationManager.getRotationTo(hitVec);
                    MoveCorrection corr = MoveCorrection.NONE;
                    if (rotationMode.isEnabled("Быстрая")) {
                        RotationManager.rotate(targetRot, corr, 360.0F, 360.0F, 360.0F, true);
                    } else if (rotationMode.isEnabled("Grim")) {
                        RotationManager.rotate(targetRot, corr, 120.0F, 120.0F, 120.0F, false);
                    } else {
                        RotationManager.rotate(targetRot, corr, 60.0F, 60.0F, 60.0F, false);
                    }
                }

                // Переключение на инструмент
                if (autoTool.isEnabled()) {
                    ensureBestTool(targetState);
                }

                // Взмах рукой
                mc.player.swingHand(Hand.MAIN_HAND);

                // Клик/Взрыв якоря или ломание блока
                if (interactAnchor.isEnabled() && targetState.isOf(Blocks.RESPAWN_ANCHOR)) {
                    BlockHitResult hitResult = new BlockHitResult(
                            candidate.hitVec != null ? candidate.hitVec : Vec3d.ofCenter(candidate.pos),
                            candidate.side,
                            candidate.pos,
                            false
                    );
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                } else {
                    mc.interactionManager.attackBlock(candidate.pos, candidate.side);
                    mc.interactionManager.updateBlockBreakingProgress(candidate.pos, candidate.side);
                }

                addToFoundBlocks(candidate.pos);
                lastLeftClickTime = now;
                performedAction = true;
            } else {
                currentTarget = null;
            }
        }

        // 4. Если ничего специфичного не найдено, но зажат клик или стоит "Всегда"
        if (!performedAction && (isAttackPressed || trigger.isEnabled("Всегда"))) {
            ((IMinecraftClient) mc).invokeDoAttack();
            lastLeftClickTime = now;
        }
    }

    private void ensureBestTool(BlockState state) {
        int bestSlot = findBestToolSlot(state);
        if (bestSlot != -1 && bestSlot != mc.player.getInventory().getSelectedSlot()) {
            if (!hasSwapped) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
                hasSwapped = true;
            }
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    private int findBestToolSlot(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private LivingEntity findBlockScanCenter() {
        // 1. Проверяем цель AttackAura
        if (syncWithAttackAura.isEnabled()) {
            AttackAura aura = FunctionManager.getFunction(AttackAura.class);
            if (aura != null && aura.isEnabled()) {
                LivingEntity auraTarget = aura.getTarget();
                if (auraTarget != null && isValidEntityTarget(auraTarget)) {
                    return auraTarget;
                }
            }
        }

        // 2. Ищем ближайшего вражеского игрока
        PlayerEntity nearestPlayer = null;
        double bestDistSq = searchRadius.getValue() * searchRadius.getValue() * 4.0;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidPlayer(player)) continue;
            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) return nearestPlayer;

        // 3. Если вражеских игроков нет (одиночная игра или тест), сканируем вокруг самого себя!
        return mc.player;
    }

    private LivingEntity findNearbyLivingEntity() {
        LivingEntity closest = null;
        double maxDistSq = entityRange.getValue() * entityRange.getValue();
        double bestDistSq = maxDistSq;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidEntityTarget(living)) continue;

            double distSq = mc.player.squaredDistanceTo(living);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                closest = living;
            }
        }

        return closest;
    }

    private boolean isValidEntityTarget(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (entity.squaredDistanceTo(mc.player) > entityRange.getValue() * entityRange.getValue()) return false;

        if (entity instanceof PlayerEntity player) {
            return isValidPlayer(player);
        }

        if (targetMobs.isEnabled() && (entity instanceof Monster || entity instanceof AnimalEntity)) {
            return true;
        }

        return false;
    }

    private boolean isValidPlayer(PlayerEntity player) {
        if (player == null || player == mc.player) return false;
        if (!player.isAlive() || player.isSpectator()) return false;
        if (FriendManager.isFriend(player.getGameProfile().name())) return false;
        return true;
    }

    private TargetCandidate findBestTargetBlock(LivingEntity centerTarget) {
        if (centerTarget == null) return null;

        BlockPos centerPos = centerTarget.getBlockPos();
        Vec3d eyePos = mc.player.getEyePos();
        double maxReach = blockReach.getValue();
        int radius = (int) Math.ceil(searchRadius.getValue());

        TargetCandidate best = null;
        double bestScore = -Double.MAX_VALUE;

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    mutablePos.set(centerPos.getX() + dx, centerPos.getY() + dy, centerPos.getZ() + dz);

                    BlockState state = mc.world.getBlockState(mutablePos);
                    if (state.isAir()) continue;

                    if (!isBlockMatchingFilter(state)) continue;

                    BlockPos immutablePos = mutablePos.toImmutable();

                    // Проверяем дистанцию и видимость грани через raycast
                    RaycastFaceResult faceResult = checkBlockVisibility(eyePos, immutablePos, maxReach);
                    if (faceResult == null) continue;

                    double distToPlayer = eyePos.distanceTo(faceResult.hitVec);
                    if (distToPlayer > maxReach) continue;

                    double score = calculateBlockScore(state, immutablePos, centerPos, distToPlayer);

                    if (score > bestScore) {
                        bestScore = score;
                        best = new TargetCandidate(immutablePos, faceResult.side, faceResult.hitVec, score);
                    }
                }
            }
        }

        return best;
    }

    private boolean isBlockMatchingFilter(BlockState state) {
        Block block = state.getBlock();
        if (blockMode.isEnabled("Якоря")) {
            return block == Blocks.RESPAWN_ANCHOR;
        } else if (blockMode.isEnabled("Обсидиан")) {
            return block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
        } else if (blockMode.isEnabled("Паутина")) {
            return block == Blocks.COBWEB;
        } else if (blockMode.isEnabled("Все полезные")) {
            return block == Blocks.RESPAWN_ANCHOR
                    || block == Blocks.OBSIDIAN
                    || block == Blocks.CRYING_OBSIDIAN
                    || block == Blocks.COBWEB
                    || block == Blocks.ENDER_CHEST
                    || block == Blocks.DIRT_PATH
                    || block == Blocks.ANVIL
                    || block == Blocks.CHIPPED_ANVIL
                    || block == Blocks.DAMAGED_ANVIL;
        } else {
            // Все ломаемые
            return state.getHardness(mc.world, BlockPos.ORIGIN) >= 0.0f;
        }
    }

    private double calculateBlockScore(BlockState state, BlockPos blockPos, BlockPos centerPos, double distToPlayer) {
        double score = 0.0;
        Block b = state.getBlock();

        if (b == Blocks.RESPAWN_ANCHOR) {
            score += 250.0;
        } else if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
            score += 180.0;
        } else if (b == Blocks.COBWEB) {
            score += 150.0;
        } else if (b == Blocks.ENDER_CHEST) {
            score += 120.0;
        } else {
            score += 60.0;
        }

        // Блок под ногами цели (бурроу)
        if (blockPos.getX() == centerPos.getX() && blockPos.getZ() == centerPos.getZ()) {
            if (blockPos.getY() == centerPos.getY() || blockPos.getY() == centerPos.getY() - 1) {
                score += 100.0;
            }
        }

        // Чем ближе к игроку, тем удобнее бить
        score -= distToPlayer * 5.0;

        return score;
    }

    private RaycastFaceResult checkBlockVisibility(Vec3d eyePos, BlockPos pos, double maxReach) {
        Direction[] directions = Direction.values();

        RaycastFaceResult bestResult = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : directions) {
            Vec3d center = Vec3d.ofCenter(pos);
            Vec3d faceCenter = center.add(
                    dir.getOffsetX() * 0.49,
                    dir.getOffsetY() * 0.49,
                    dir.getOffsetZ() * 0.49
            );

            double dist = eyePos.distanceTo(faceCenter);
            if (dist > maxReach) continue;

            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    eyePos,
                    faceCenter,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                if (dist < bestDist) {
                    bestDist = dist;
                    bestResult = new RaycastFaceResult(hit.getSide(), hit.getPos());
                }
            }
        }

        // Запасной вариант: попадание по дистанции до центра блока
        if (bestResult == null) {
            Vec3d center = Vec3d.ofCenter(pos);
            double dist = eyePos.distanceTo(center);
            if (dist <= maxReach) {
                Direction opp = Direction.getFacing(
                        (float) (eyePos.x - center.x),
                        (float) (eyePos.y - center.y),
                        (float) (eyePos.z - center.z)
                );
                bestResult = new RaycastFaceResult(opp, center);
            }
        }

        return bestResult;
    }

    private void addToFoundBlocks(BlockPos pos) {
        for (FoundBlock fb : foundBlocks) {
            if (fb.pos.equals(pos)) {
                fb.lastUpdated = System.currentTimeMillis();
                return;
            }
        }
        foundBlocks.add(new FoundBlock(pos, System.currentTimeMillis()));
    }

    // --- 3D World Rendering ---
    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.renderBlocks(context);
    }

    private void renderBlocks(WorldRenderContext context) {
        if (!esp.isEnabled() || mc.world == null || mc.player == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        MatrixStack.Entry m = context.matrices().peek();
        Color baseColor = espColor.getColor();
        float lineW = (float) lineWidth.getValue();
        float baseFillAlpha = (float) fillAlpha.getValue();
        long now = System.currentTimeMillis();

        // 1. Очистка устаревших блоков
        long expireTime = 1500L;
        foundBlocks.removeIf(block -> now - block.lastUpdated > expireTime || mc.world.getBlockState(block.pos).isAir());

        // 2. Рендер блоков из очереди foundBlocks
        for (FoundBlock block : foundBlocks) {
            float progress = 1.0f - (float) (now - block.lastUpdated) / (float) expireTime;
            progress = Math.clamp(progress, 0.0f, 1.0f);

            int alphaInt = (int) (progress * 255.0f);
            float currentFillAlpha = progress * baseFillAlpha;

            Box box = new Box(block.pos).offset(-camera.x, -camera.y, -camera.z);

            drawBoxFill(context, box, baseColor, currentFillAlpha);
            drawOutline(context, box, baseColor, lineW, alphaInt);
        }

        // 3. Яркая подсветка текущего выбранного блока
        if (currentTarget != null && !mc.world.getBlockState(currentTarget).isAir()) {
            Box box = new Box(currentTarget).offset(-camera.x, -camera.y, -camera.z);

            Color highlightColor = new Color(
                    Math.min(255, baseColor.getRed() + 40),
                    Math.min(255, baseColor.getGreen() + 40),
                    Math.min(255, baseColor.getBlue() + 40)
            );

            drawBoxFill(context, box, highlightColor, Math.min(1.0f, baseFillAlpha * 1.5f));
            drawOutline(context, box, Color.WHITE, lineW + 0.8f, 255);
        }
    }

    private static void drawOutline(WorldRenderContext context, Box b, Color color, float width, int alpha) {
        if (alpha <= 0) return;
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

    private static void drawBoxFill(WorldRenderContext context, Box b, Color color, float alpha) {
        int a = (int) (Math.clamp(alpha, 0F, 1F) * 255F);
        if (a <= 0) return;
        VertexConsumer v = context.consumers().getBuffer(FILL_LAYER);
        MatrixStack.Entry m = context.matrices().peek();
        float inset = 0.002F;
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;

        quad(v, m, color, a, x1, y2 + inset, z2, x2, y2 + inset, z2, x2, y2 + inset, z1, x1, y2 + inset, z1);
        quad(v, m, color, a, x1, y1 - inset, z1, x2, y1 - inset, z1, x2, y1 - inset, z2, x1, y1 - inset, z2);
        quad(v, m, color, a, x1, y1, z1 - inset, x1, y2, z1 - inset, x2, y2, z1 - inset, x2, y1, z1 - inset);
        quad(v, m, color, a, x1, y1, z2 + inset, x2, y2, z2 + inset, x2, y2, z2 + inset, x1, y2, z2 + inset);
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

    // --- Вспомогательные структуры данных ---
    private static class FoundBlock {
        final BlockPos pos;
        long lastUpdated;

        FoundBlock(BlockPos pos, long lastUpdated) {
            this.pos = pos;
            this.lastUpdated = lastUpdated;
        }
    }

    private static class TargetCandidate {
        final BlockPos pos;
        final Direction side;
        final Vec3d hitVec;
        final double score;

        TargetCandidate(BlockPos pos, Direction side, Vec3d hitVec, double score) {
            this.pos = pos;
            this.side = side;
            this.hitVec = hitVec;
            this.score = score;
        }
    }

    private static class RaycastFaceResult {
        final Direction side;
        final Vec3d hitVec;

        RaycastFaceResult(Direction side, Vec3d hitVec) {
            this.side = side;
            this.hitVec = hitVec;
        }
    }
}
