package ez.minar.system.features.combat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.EventPriority;
import ez.minar.system.events.impl.PacketReceiveEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.render.BlockOverlay;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.MoveCorrection;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.neuro.NeuroManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@NewFunction(name = "AutoCrystal", desc = "Кристалл Аура (Vorkis): авто-обсидиан, спам кристаллами по игрокам/мобам, мгновенный подрыв и 3D ESP", category = Category.COMBAT)
public class AutoCrystal extends Function {

    public static AutoCrystal Instance;

    // --- Настройки дистанций и таргета ---
    private final NumberSetting targetRange = new NumberSetting("Дистанция до цели", 5.0, 1.0, 8.0, 0.1);
    private final NumberSetting placeRange = new NumberSetting("Дистанция плейса", 4.5, 1.0, 6.0, 0.1);
    private final NumberSetting blastRange = new NumberSetting("Радиус взрыва", 5.5, 1.0, 8.0, 0.1);

    private final BooleanSetting targetPlayers = new BooleanSetting("Атаковать игроков", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Атаковать мобов", true);
    private final BooleanSetting standaloneTest = new BooleanSetting("Взрывать без цели", true);

    // --- Ротации ---
    private final ListSetting rotationMode = new ListSetting("Ротация", "Neuro", "Grim", "Быстрая", "Плавная", "Нет");
    private final ListSetting moveCorrection = new ListSetting("Коррекция", "Незаметная", "Нет");
    private final NumberSetting rotationSpeed = new NumberSetting("Скорость ротации", 180.0, 30.0, 360.0, 10.0);

    // --- Механики ---
    private final BooleanSetting autoObsidian = new BooleanSetting("Ставить обсидиан", true);
    private final BooleanSetting instantBreak = new BooleanSetting("Мгновенный взрыв", true);
    private final BooleanSetting selfDamageSafety = new BooleanSetting("Защита от суицида", true);
    private final BooleanSetting syncWithAttackAura = new BooleanSetting("Синхронизация с AttackAura", true);
    private final BooleanSetting autoRefill = new BooleanSetting("Автопополнение хотбара", true);
    private final NumberSetting placeDelay = new NumberSetting("Задержка плейса", 0.0, 0.0, 5.0, 1.0);
    private final NumberSetting breakDelay = new NumberSetting("Задержка взрыва", 0.0, 0.0, 5.0, 1.0);

    // --- Визуалы (ESP из Vorkis) ---
    private final BooleanSetting esp = new BooleanSetting("Подсветка (ESP)", true);
    private final ColorSetting espColor = new ColorSetting("Цвет ESP", new Color(255, 60, 140));
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность заливки", 0.25, 0.05, 1.0, 0.05);
    private final NumberSetting lineWidth = new NumberSetting("Толщина линий", 1.5, 0.5, 4.0, 0.5);

    // --- Внутреннее состояние ---
    private LivingEntity target;
    private BlockPos currentBasePos;
    private EndCrystalEntity currentCrystal;
    private int placeTicks = 0;
    private int breakTicks = 0;
    private boolean rotating = false;
    private final ConcurrentLinkedQueue<FoundBase> placedBases = new ConcurrentLinkedQueue<>();

    // --- Рендер пайплайн ---
    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minar", "autocrystal_fill_nodepth"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final RenderLayer FILL_LAYER = RenderLayer.of("minar_autocrystal_fill_nodepth",
            RenderSetup.builder(FILL_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    public AutoCrystal() {
        Instance = this;
        addSettings(
                targetRange,
                placeRange,
                blastRange,
                targetPlayers,
                targetMobs,
                standaloneTest,
                rotationMode,
                moveCorrection,
                rotationSpeed,
                autoObsidian,
                instantBreak,
                selfDamageSafety,
                syncWithAttackAura,
                autoRefill,
                placeDelay,
                breakDelay,
                esp,
                espColor,
                fillAlpha,
                lineWidth
        );
    }

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    private void reset() {
        target = null;
        currentBasePos = null;
        currentCrystal = null;
        placeTicks = 0;
        breakTicks = 0;
        placedBases.clear();
        if (rotating) {
            RotationManager.reset();
            rotating = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(UpdateEvent event) {
        if (nullCheck.all() || mc.player.isSpectator()) {
            reset();
            return;
        }

        placeTicks++;
        breakTicks++;

        // 1. Поиск цели (вражеского игрока или моба/зомби в радиусе targetRange)
        target = findTarget();

        // 2. Ротация (если есть цель или активный кристалл/база)
        handleRotation();

        // 3. Первоочередное действие: взрыв любого кристалла в радиусе досягаемости
        currentCrystal = findCrystalToBreak(target);
        if (currentCrystal != null) {
            if (breakTicks >= (int) breakDelay.getValue()) {
                breakCrystal(currentCrystal);
                breakTicks = 0;
            }
            if (!instantBreak.isEnabled()) {
                return;
            }
        }

        // Если цели нет и режим без цели отключен — останавливаемся
        if (target == null && !standaloneTest.isEnabled()) {
            currentBasePos = null;
            return;
        }

        // 4. Поиск лучшей позиции для кристалла (обсидиана/бедрока)
        currentBasePos = findBestBasePos(target);
        if (currentBasePos == null) return;

        // 5. Если позиция уже обсидиан или бедрок — ставим кристалл!
        if (isCrystalBase(currentBasePos)) {
            if (placeTicks >= (int) placeDelay.getValue()) {
                if (placeCrystal(currentBasePos)) {
                    placeTicks = 0;
                    addPlacedBase(currentBasePos);
                }
            }
            return;
        }

        // 6. Если позиция свободна и включен autoObsidian — ставим обсидиан
        if (autoObsidian.isEnabled() && mc.world.getBlockState(currentBasePos).isReplaceable()) {
            if (placeTicks >= (int) placeDelay.getValue()) {
                if (placeObsidian(currentBasePos)) {
                    placeTicks = 0;
                    addPlacedBase(currentBasePos);
                }
            }
        }
    }

    private void addPlacedBase(BlockPos pos) {
        for (FoundBase b : placedBases) {
            if (b.pos.equals(pos)) {
                b.lastTime = System.currentTimeMillis();
                return;
            }
        }
        placedBases.add(new FoundBase(pos, System.currentTimeMillis()));
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        if (nullCheck.all()) return;
        if (!instantBreak.isEnabled()) return;

        if (event.getPacket() instanceof EntitySpawnS2CPacket spawnPacket) {
            if (spawnPacket.getEntityType() == EntityType.END_CRYSTAL) {
                Vec3d spawnPos = new Vec3d(spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ());
                double reachSq = placeRange.getValue() * placeRange.getValue();
                double blastSq = blastRange.getValue() * blastRange.getValue();

                if (mc.player.squaredDistanceTo(spawnPos) <= reachSq) {
                    if (target != null && target.squaredDistanceTo(spawnPos) > blastSq) {
                        return;
                    }
                    if (selfDamageSafety.isEnabled() && mc.player.squaredDistanceTo(spawnPos) < 2.25) {
                        return;
                    }

                    Entity entity = mc.world.getEntityById(spawnPacket.getEntityId());
                    if (entity instanceof EndCrystalEntity crystal) {
                        breakCrystal(crystal);
                    }
                }
            }
        }
    }

    private void handleRotation() {
        if (rotationMode.isEnabled("Нет")) {
            if (rotating) {
                RotationManager.reset();
                rotating = false;
            }
            return;
        }

        MoveCorrection corr = moveCorrection.isEnabled("Незаметная")
                ? MoveCorrection.SILENT
                : MoveCorrection.NONE;
        float speed = (float) rotationSpeed.getValue();

        if (rotationMode.isEnabled("Neuro") && target instanceof PlayerEntity playerTarget) {
            Rotation current = getCurrentOrPlayerRotation();
            Rotation predicted = NeuroManager.predictRotation(playerTarget, current);
            RotationManager.rotate(predicted, corr, speed, speed, speed);
            rotating = true;
            return;
        }

        Vec3d aimVec;
        if (currentCrystal != null) {
            aimVec = currentCrystal.getEntityPos().add(0, currentCrystal.getHeight() * 0.5, 0);
        } else if (currentBasePos != null) {
            aimVec = Vec3d.ofCenter(currentBasePos).add(0, 0.5, 0);
        } else if (target != null) {
            aimVec = target.getEyePos();
        } else {
            if (rotating) {
                RotationManager.reset();
                rotating = false;
            }
            return;
        }

        Rotation exact = RotationManager.getRotationTo(aimVec);
        if (rotationMode.isEnabled("Быстрая")) {
            RotationManager.rotate(exact, corr, 360.0F, 360.0F, 360.0F, true);
        } else if (rotationMode.isEnabled("Grim")) {
            RotationManager.rotate(exact, corr, 120.0F, 120.0F, 120.0F, false);
        } else {
            RotationManager.rotate(exact, corr, speed, speed, speed);
        }
        rotating = true;
    }

    private LivingEntity findTarget() {
        // 1. Проверяем синхронизацию с AttackAura
        if (syncWithAttackAura.isEnabled()) {
            AttackAura aura = FunctionManager.getFunction(AttackAura.class);
            if (aura != null && aura.isEnabled()) {
                LivingEntity auraTarget = aura.getTarget();
                if (auraTarget != null && isTargetValid(auraTarget)) {
                    return auraTarget;
                }
            }
        }

        // 2. Ищем ближайшего врага (игрока или моба) в радиусе targetRange
        LivingEntity best = null;
        double bestDistSq = targetRange.getValue() * targetRange.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isTargetValid(living)) continue;

            double distSq = mc.player.squaredDistanceTo(living);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = living;
            }
        }

        return best;
    }

    private boolean isTargetValid(LivingEntity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive() || entity.isRemoved()) return false;
        if (entity.squaredDistanceTo(mc.player) > targetRange.getValue() * targetRange.getValue()) return false;

        if (entity instanceof PlayerEntity player) {
            if (!targetPlayers.isEnabled()) return false;
            if (player.isCreative() || player.isSpectator()) return false;
            if (FriendManager.isFriend(player.getGameProfile().name())) return false;
            return true;
        }

        if (targetMobs.isEnabled() && (entity instanceof Monster || entity instanceof AnimalEntity)) {
            return true;
        }

        return false;
    }

    private EndCrystalEntity findCrystalToBreak(LivingEntity currentTarget) {
        Vec3d eyePos = mc.player.getEyePos();
        double reachSq = placeRange.getValue() * placeRange.getValue();
        double blastSq = blastRange.getValue() * blastRange.getValue();

        Box searchBox = mc.player.getBoundingBox().expand(placeRange.getValue() + 1.0);
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                searchBox,
                c -> c.isAlive() && !c.isRemoved()
        );

        EndCrystalEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (EndCrystalEntity crystal : crystals) {
            Vec3d crystalPos = crystal.getEntityPos();
            if (eyePos.squaredDistanceTo(crystalPos) > reachSq) continue;
            if (currentTarget != null && currentTarget.squaredDistanceTo(crystalPos) > blastSq) continue;

            if (selfDamageSafety.isEnabled() && mc.player.squaredDistanceTo(crystalPos) < 2.25) {
                continue;
            }

            double distSq = (currentTarget != null) ? currentTarget.squaredDistanceTo(crystalPos) : eyePos.squaredDistanceTo(crystalPos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = crystal;
            }
        }

        return best;
    }

    private void breakCrystal(EndCrystalEntity crystal) {
        if (rotationMode.isEnabled("Neuro")) {
            NeuroManager.setAttackImminent(true);
        }
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private BlockPos findBestBasePos(LivingEntity currentTarget) {
        BlockPos centerPos = (currentTarget != null) ? currentTarget.getBlockPos() : mc.player.getBlockPos();
        Vec3d eyePos = mc.player.getEyePos();
        double reachSq = placeRange.getValue() * placeRange.getValue();
        double blastSq = blastRange.getValue() * blastRange.getValue();

        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        int scanR = (currentTarget != null) ? 2 : 3;

        for (int y = -1; y <= 1; y++) {
            for (int x = -scanR; x <= scanR; x++) {
                for (int z = -scanR; z <= scanR; z++) {
                    if (Math.abs(x) == scanR && Math.abs(z) == scanR) continue;

                    BlockPos pos = centerPos.add(x, y, z);
                    Vec3d center = Vec3d.ofCenter(pos);

                    if (eyePos.squaredDistanceTo(center) > reachSq) continue;
                    if (currentTarget != null && currentTarget.squaredDistanceTo(center) > blastSq) continue;

                    if (selfDamageSafety.isEnabled() && mc.player.squaredDistanceTo(center) < 2.25) {
                        continue;
                    }

                    // Проверка свободного места для кристалла над базой
                    BlockPos above = pos.up();
                    if (!mc.world.getBlockState(above).isAir() && !hasCrystalAt(pos)) {
                        continue;
                    }

                    boolean isObsidian = isCrystalBase(pos);
                    if (isObsidian) {
                        double dist = (currentTarget != null) ? currentTarget.squaredDistanceTo(center) : eyePos.squaredDistanceTo(center);
                        double score = dist - 1000.0;
                        if (score < bestScore) {
                            bestScore = score;
                            bestPos = pos;
                        }
                    } else if (autoObsidian.isEnabled() && mc.world.getBlockState(pos).isReplaceable()) {
                        if (findPlacementAgainst(pos) != null && !isBlockedByEntities(pos)) {
                            double dist = (currentTarget != null) ? currentTarget.squaredDistanceTo(center) : eyePos.squaredDistanceTo(center);
                            double score = dist;
                            if (score < bestScore) {
                                bestScore = score;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean isBlockedByEntities(BlockPos pos) {
        Box box = new Box(pos);
        return !mc.world.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive).isEmpty();
    }

    private boolean hasCrystalAt(BlockPos obsidianPos) {
        Box box = new Box(obsidianPos.up());
        return !mc.world.getEntitiesByClass(EndCrystalEntity.class, box, Entity::isAlive).isEmpty();
    }

    private boolean placeCrystal(BlockPos obsidianPos) {
        Hand hand = Hand.MAIN_HAND;
        int slot = -1;

        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
            hand = Hand.OFF_HAND;
        } else if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            hand = Hand.MAIN_HAND;
        } else {
            slot = autoRefill.isEnabled() ? findOrRefillSlot(Items.END_CRYSTAL) : findHotbarSlot(Items.END_CRYSTAL);
            if (slot == -1) return false;
        }

        Vec3d hitVec = new Vec3d(
                obsidianPos.getX() + 0.5,
                obsidianPos.getY() + 1.0,
                obsidianPos.getZ() + 0.5
        );
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, obsidianPos, false);

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (hand == Hand.MAIN_HAND && slot != -1) {
            mc.player.getInventory().setSelectedSlot(slot);
        }

        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);

        if (hand == Hand.MAIN_HAND && slot != -1) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        return true;
    }

    private boolean placeObsidian(BlockPos pos) {
        Hand hand = Hand.MAIN_HAND;
        int slot = -1;

        if (mc.player.getOffHandStack().isOf(Items.OBSIDIAN)) {
            hand = Hand.OFF_HAND;
        } else if (mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
            hand = Hand.MAIN_HAND;
        } else {
            slot = autoRefill.isEnabled() ? findOrRefillSlot(Items.OBSIDIAN) : findHotbarSlot(Items.OBSIDIAN);
            if (slot == -1) return false;
        }

        PlacementTarget placement = findPlacementAgainst(pos);
        if (placement == null) return false;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (hand == Hand.MAIN_HAND && slot != -1) {
            mc.player.getInventory().setSelectedSlot(slot);
        }

        BlockHitResult hitResult = new BlockHitResult(
                placement.hitVec(),
                placement.face(),
                placement.block(),
                false
        );
        mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        mc.player.swingHand(hand);

        if (hand == Hand.MAIN_HAND && slot != -1) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        return true;
    }

    private PlacementTarget findPlacementAgainst(BlockPos placePos) {
        if (!mc.world.getBlockState(placePos).isReplaceable()) {
            return null;
        }

        Direction[] order = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.UP
        };

        for (Direction direction : order) {
            BlockPos neighbor = placePos.offset(direction);
            if (mc.world.getBlockState(neighbor).isReplaceable()) continue;

            Direction face = direction.getOpposite();
            Vec3d hitVec = new Vec3d(
                    neighbor.getX() + 0.5 + face.getOffsetX() * 0.5,
                    neighbor.getY() + 0.5 + face.getOffsetY() * 0.5,
                    neighbor.getZ() + 0.5 + face.getOffsetZ() * 0.5
            );
            return new PlacementTarget(neighbor, face, hitVec);
        }

        return null;
    }

    private boolean isCrystalBase(BlockPos pos) {
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findOrRefillSlot(Item item) {
        int slot = findHotbarSlot(item);
        if (slot != -1) return slot;

        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                int targetHotbar = 8;
                for (int h = 0; h < 9; h++) {
                    if (mc.player.getInventory().getStack(h).isEmpty()) {
                        targetHotbar = h;
                        break;
                    }
                }
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        targetHotbar,
                        SlotActionType.SWAP,
                        mc.player
                );
                return targetHotbar;
            }
        }
        return -1;
    }

    private Rotation getCurrentOrPlayerRotation() {
        return RotationManager.isActive()
                ? RotationManager.getCurrentRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    public boolean isCrystalling() {
        return isEnabled() && (target != null || currentCrystal != null || currentBasePos != null);
    }

    public LivingEntity getTarget() {
        return target;
    }

    // --- 3D World Rendering (ESP из Vorkis) ---
    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.renderCrystalEsp(context);
    }

    private void renderCrystalEsp(WorldRenderContext context) {
        if (!esp.isEnabled() || mc.world == null || mc.player == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        Color baseColor = espColor.getColor();
        float lineW = (float) lineWidth.getValue();
        float baseFillAlpha = (float) fillAlpha.getValue();
        long now = System.currentTimeMillis();

        // 1. Очистка устаревших баз
        long expireTime = 1500L;
        placedBases.removeIf(b -> now - b.lastTime > expireTime);

        // 2. Рендер установленных баз с затуханием
        for (FoundBase base : placedBases) {
            float progress = 1.0f - (float) (now - base.lastTime) / (float) expireTime;
            progress = Math.clamp(progress, 0.0f, 1.0f);

            int alphaInt = (int) (progress * 255.0f);
            float currentFillAlpha = progress * baseFillAlpha;

            Box box = new Box(base.pos).offset(-camera.x, -camera.y, -camera.z);

            drawBoxFill(context, box, baseColor, currentFillAlpha);
            drawOutline(context, box, baseColor, lineW, alphaInt);
        }

        // 3. Яркая подсветка текущей базы
        if (currentBasePos != null) {
            Box box = new Box(currentBasePos).offset(-camera.x, -camera.y, -camera.z);

            Color highlightColor = new Color(
                    Math.min(255, baseColor.getRed() + 30),
                    Math.min(255, baseColor.getGreen() + 30),
                    Math.min(255, baseColor.getBlue() + 30)
            );

            drawBoxFill(context, box, highlightColor, Math.min(1.0f, baseFillAlpha * 1.6f));
            drawOutline(context, box, Color.WHITE, lineW + 0.8f, 255);
        }

        // 4. Подсветка целевого кристалла
        if (currentCrystal != null && currentCrystal.isAlive()) {
            Box crystalBox = currentCrystal.getBoundingBox().offset(-camera.x, -camera.y, -camera.z);
            Color crystalColor = new Color(255, 30, 30);
            drawBoxFill(context, crystalBox, crystalColor, baseFillAlpha);
            drawOutline(context, crystalBox, crystalColor, lineW, 255);
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

    private static class FoundBase {
        final BlockPos pos;
        long lastTime;

        FoundBase(BlockPos pos, long lastTime) {
            this.pos = pos;
            this.lastTime = lastTime;
        }
    }

    private record PlacementTarget(BlockPos block, Direction face, Vec3d hitVec) {
    }
}
