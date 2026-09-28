package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.features.combat.rotation.CustomRotation;
import ez.minar.system.managers.RotationManager;
import ez.minar.system.managers.RotationManager.MoveCorrection;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.neuro.NeuroManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ListSetting;
import ez.minar.system.settings.impl.MultiListSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.Random;

@NewFunction(name = "AttackAura", desc = "Бьёт женщин и детей", category = Category.COMBAT)
public class AttackAura extends Function {
    private static final float ATTACK_COOLDOWN_TICK_DELTA = 0.5F;
    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.9F;
    private static final float MAX_SERVER_ROTATION_STEP = 30.0F;
    private static final float CRIT_SERVER_ROTATION_STEP = 60.0F;
    private static final float SILENT_RETURN_STEP = 18.0F;
    private static final float ATTACK_AIM_TOLERANCE = 2.0F;
    private static final float CRIT_AIM_TOLERANCE = 8.0F;
    private static final int RW_FLICK_HIT_INTERVAL = 50;
    private static final long RW_FLICK_DURATION_MS = 200L;
    private static final int FAST_SPRINT_DROP_TICKS = 3;

    // 14 alternating settings matching the reference photo 1:1
    private final ListSetting rotationMode = new ListSetting("Ротация", "Нет", "Простая", "ReallyWorld", "Нейро");
    private final ListSetting otvodkaMode = new ListSetting("Отводка", "Нет", "Плавная", "Камера");
    private final NumberSetting range = new NumberSetting("Дистанция Атаки", 3.0, 1.0, 5.0, 0.1);
    private final NumberSetting aimRange = new NumberSetting("Дистанция наводки", 3.0, 1.0, 7.0, 0.1);
    private final BooleanSetting onlyCrits = new BooleanSetting("Только криты", true);
    private final BooleanSetting smartCriticals = new BooleanSetting("Умные криты", false);
    private final ListSetting throughWalls = new ListSetting("Через стены", "Нет", "Без обхода", "Через двери", "Обход RW", "Обход FT");
    private final BooleanSetting aimCheck = new BooleanSetting("Проверка наводки", false);
    private final BooleanSetting targetFollow = new BooleanSetting("Преследовать", true);
    private final BooleanSetting onlyWithWeapon = new BooleanSetting("Только с оружием", false);
    private final BooleanSetting swapMace = new BooleanSetting("Свап на булаву", false);
    private final BooleanSetting noAttackOpenInv = new BooleanSetting("Не бить при открытом инве", true);
    private final MultiListSetting targets = new MultiListSetting("Цели", "Игроки", "Животные", "Мобы", "Невидимые", "Голые", "Друзья", "Мертвые");
    private final ListSetting sortMode = new ListSetting("Сортировка", "По дистанции", "По здоровью", "По полю зрения");

    // Internal subsettings
    private final ListSetting grimAimPoint = new ListSetting("Точка Grim", "Rockstar", "Глаза", "Центр", "Случайная");
    private final NumberSetting grimSmooth = new NumberSetting("Плавность Grim", 1.0, 0.2, 2.0, 0.1);
    private final BooleanSetting rwFlick = new BooleanSetting("RW Flick", true);
    private final NumberSetting rwFlickHits = new NumberSetting("Flick каждые (ударов)", 50.0, 5.0, 150.0, 5.0);
    private final NumberSetting rwSpeed = new NumberSetting("Скорость RW", 1.1, 0.5, 2.5, 0.1);
    private final BooleanSetting rwJitter = new BooleanSetting("Jitter RW", true);
    private final NumberSetting matrixSpeed = new NumberSetting("Скорость Matrix", 1.0, 0.2, 2.5, 0.1);
    private final BooleanSetting matrixJitter = new BooleanSetting("Jitter Matrix", true);
    private final NumberSetting hvhYawSpeed = new NumberSetting("Скорость Yaw HvH", 360.0, 60.0, 360.0, 10.0);
    private final NumberSetting hvhPitchSpeed = new NumberSetting("Скорость Pitch HvH", 180.0, 30.0, 180.0, 10.0);
    private final NumberSetting legitSpeed = new NumberSetting("Скорость", 6.0, 1.0, 20.0, 0.5);
    private final BooleanSetting legitPitch = new BooleanSetting("Управлять Pitch", true);
    private final CustomRotation customRotation = new CustomRotation();
    private final ListSetting moveCorrection = new ListSetting("Коррекция", "Незаметная", "Направленная", "Нет");
    private final MultiListSetting noAttackIf = new MultiListSetting("Не бить если", "Ешь еду", "Открыт инвентарь");
    private final BooleanSetting focusTarget = new BooleanSetting("Фокусировать одну цель", true);
    private final ListSetting criticalSpeed = new ListSetting("Скорость критов", "Обычная", "С задержкой");
    private final ListSetting resetMode = new ListSetting("Сброс", "Silent", "ClientLook", "Нет");
    private final ListSetting sprintResetMode = new ListSetting("Сброс спринта", "Быстрый", "Легитный");
    private final ListSetting attackType = new ListSetting("\u0412\u0438\u0434 \u0443\u0434\u0430\u0440\u043e\u0432", "1.9", "1.8");
    private final NumberSetting clickCps = new NumberSetting("\u041a\u041f\u0421", 10.0, 1.0, 20.0, 1.0);

    private float silentYaw;
    private float silentPitch;
    private LivingEntity target;
    private long lastAttackTime;
    private long nextClickAttackDelay;
    private long nextCriticalAttackTime;
    private long lastCriticalDelay = -1L;
    private int lastAttackTargetId;
    private int sprintDropTicks;
    private int jumpCriticalWaitTicks;
    private boolean legitSprintPrepared;
    private boolean legitSprintIntent;
    private long legitSprintRestoreTime;
    private boolean auraAttacking;
    private int ascendingTicks;
    private final Random spookyRandom = new Random();

    private int rwHitCounter;
    private long rwFlickUntil;
    private int customHitCounter;
    private long customFlickUntil;
    private float matrixJitterYawFreq;
    private float matrixJitterPitchFreq;
    private long matrixFreqUpdateTime;
    private long lastFrame = 0;
    private float legitLastPitch;
    private boolean legitPitchInitialized;

    public AttackAura() {
        setKeybind(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
        rotationMode.setMode("Нейро");
        otvodkaMode.setMode("Плавная");
        targets.setEnabledOptions(java.util.Set.of("Игроки", "Животные", "Мобы", "Невидимые"));
        addSettings(
                rotationMode,     // 0 (L)
                otvodkaMode,      // 1 (R)
                range,            // 2 (L)
                aimRange,         // 3 (R)
                onlyCrits,        // 4 (L)
                smartCriticals,   // 5 (R)
                throughWalls,     // 6 (L)
                aimCheck,         // 7 (R)
                targetFollow,     // 8 (L)
                onlyWithWeapon,   // 9 (R)
                swapMace,         // 10 (L)
                noAttackOpenInv,  // 11 (R)
                targets,          // 12 (L)
                sortMode          // 13 (R)
        );
    }

    private void updateRotationSettingsVisibility() {
        boolean isGrim = rotationMode.isEnabled("Grim");
        grimAimPoint.setVisible(isGrim);
        grimSmooth.setVisible(isGrim);

        boolean isRw = rotationMode.isEnabled("ReallyWorld");
        rwFlick.setVisible(isRw);
        rwFlickHits.setVisible(isRw);
        rwSpeed.setVisible(isRw);
        rwJitter.setVisible(isRw);

        boolean isMatrix = rotationMode.isEnabled("Matrix");
        matrixSpeed.setVisible(isMatrix);
        matrixJitter.setVisible(isMatrix);

        boolean isHvh = rotationMode.isEnabled("HvH");
        hvhYawSpeed.setVisible(isHvh);
        hvhPitchSpeed.setVisible(isHvh);

        boolean isLegit = rotationMode.isEnabled("Legit");
        legitSpeed.setVisible(isLegit);
        legitPitch.setVisible(isLegit);

        customRotation.updateVisibility(rotationMode.isEnabled("Custom"));
    }

    private void updateAttackSettingsVisibility() {
        clickCps.setVisible(attackType.isEnabled("1.8"));
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            silentYaw = mc.player.getYaw();
            silentPitch = mc.player.getPitch();
        }
        lastAttackTime = 0L;
        nextClickAttackDelay = 0L;
        nextCriticalAttackTime = 0L;
        lastCriticalDelay = -1L;
        lastAttackTargetId = -1;
        rwHitCounter = 0;
        rwFlickUntil = 0L;
        customHitCounter = 0;
        customFlickUntil = 0L;
        customRotation.resetRuntime();
        matrixJitterYawFreq = 60.0F;
        matrixJitterPitchFreq = 80.0F;
        matrixFreqUpdateTime = 0L;
        lastFrame = 0;
        legitPitchInitialized = false;
        resetSprintDrop();
        jumpCriticalWaitTicks = 0;
        ascendingTicks = 0;
        auraAttacking = false;
        RotationManager.setKeepClientRenderPitch(false);
        RotationManager.reset();
        NeuroManager.enabled();
    }

    @Override
    public void onDisable() {
        target = null;
        lastAttackTime = 0L;
        nextClickAttackDelay = 0L;
        nextCriticalAttackTime = 0L;
        lastCriticalDelay = -1L;
        lastAttackTargetId = -1;
        rwFlickUntil = 0L;
        customFlickUntil = 0L;
        resetSprintDrop();
        super.onDisable();
        jumpCriticalWaitTicks = 0;
        auraAttacking = false;
        RotationManager.setKeepClientRenderPitch(false);
        RotationManager.reset();
        NeuroManager.targetNull();
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!rotationMode.isEnabled("Legit")) {
            lastFrame = 0;
            legitPitchInitialized = false;
            return;
        }

        if (mc.player == null || mc.world == null) return;

        long currentFrame = System.nanoTime();
        if (lastFrame == 0) {
            lastFrame = currentFrame;
            return;
        }
        float dt = (currentFrame - lastFrame) / 1_000_000_000.0f;
        lastFrame = currentFrame;

        if (dt > 0.1f) dt = 0.1f;
        if (mc.currentScreen != null) return;

        if (target == null) return;

        Vec3d aimPoint = getRockstarAimPoint(target);
        Vec3d yawAimPoint = getLegitYawAimPoint(target);

        double dX = yawAimPoint.x - mc.player.getX();
        double dZ = yawAimPoint.z - mc.player.getZ();
        double dY = aimPoint.y - mc.player.getEyeY();

        double pitchX = aimPoint.x - mc.player.getX();
        double pitchZ = aimPoint.z - mc.player.getZ();
        double dist = Math.sqrt(pitchX * pitchX + pitchZ * pitchZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dY, dist));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(targetPitch - mc.player.getPitch());

        float factor = (float) legitSpeed.getValue();

        float stepYaw = yawDiff * factor * dt;
        boolean manualPitchInput = legitPitchInitialized
                && Math.abs(MathHelper.wrapDegrees(mc.player.getPitch() - legitLastPitch)) > 0.001F;
        float stepPitch = legitPitch.isEnabled() && !manualPitchInput
                ? pitchDiff * factor * dt
                : 0.0F;

        if (Math.abs(stepYaw) > Math.abs(yawDiff)) stepYaw = yawDiff;
        if (Math.abs(stepPitch) > Math.abs(pitchDiff)) stepPitch = pitchDiff;

        float jitterValue = 0.0f;
        float jitterYaw = jitterValue > 0 ? (float) ((Math.random() - 0.5) * jitterValue) : 0f;
        float jitterPitch = jitterValue > 0 ? (float) ((Math.random() - 0.5) * jitterValue) : 0f;

        mc.player.setYaw(mc.player.getYaw() + stepYaw + jitterYaw);
        if (legitPitch.isEnabled()) {
            mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + stepPitch + jitterPitch, -90.0F, 90.0F));
        }
        legitLastPitch = mc.player.getPitch();
        legitPitchInitialized = true;
    }

    @EventHandler
    public void onTick(UpdateEvent event) {
        if (nullCheck.all()) {
            target = null;
            lastAttackTargetId = -1;
            rwFlickUntil = 0L;
            customFlickUntil = 0L;
            resetSprintDrop();
            jumpCriticalWaitTicks = 0;
            RotationManager.setKeepClientRenderPitch(false);
            RotationManager.reset();
            return;
        }

        updateJumpCriticalWait();
        target = findTarget();

        if (target == null) {
            NeuroManager.targetNull();
            lastAttackTargetId = -1;
            rwFlickUntil = 0L;
            customFlickUntil = 0L;
            resetSprintDrop();
            RotationManager.setKeepClientRenderPitch(false);
            if (RotationManager.isActive()) {
                if (resetMode.isEnabled("Silent")) {
                    releaseSilentRotation();
                } else if (resetMode.isEnabled("ClientLook")) {
                    applyClientLookReset();
                } else {
                    RotationManager.reset();
                }
            } else {
                RotationManager.reset();
            }
            syncSilentRotation();
            return;
        }

        if (shouldCancelLegitSprintPreparation()) {
            resetSprintDrop();
        }

        boolean preparedSprintDropThisTick = false;

        if (shouldPrepareFastSprintReset()) {
            mc.player.setSprinting(false);
            sprintDropTicks = FAST_SPRINT_DROP_TICKS;
            preparedSprintDropThisTick = true;
        }

        if (shouldPrepareLegitSprintReset()) {
            legitSprintIntent = true;
            mc.player.setSprinting(false);
            legitSprintPrepared = true;
            legitSprintRestoreTime = System.currentTimeMillis() + 90L;
            preparedSprintDropThisTick = true;
        }



        // Neuro: сообщаем заранее, что удар близко — дрейф прицела начнёт
        // стягиваться к корпусу до свинга, а не в сам тик удара.
        NeuroManager.setAttackImminent(isAttackImminent());

        Rotation rotation = getRotation(target);
        if (rotationMode.isEnabled("Legit")) {
            RotationManager.setKeepClientRenderPitch(false);
            RotationManager.reset();
            silentYaw = mc.player.getYaw();
            silentPitch = mc.player.getPitch();
        } else {
            RotationSpeed speed = getRotationSpeed(rotation);
            RotationManager.setKeepClientRenderPitch(false);
            boolean isGrim = rotationMode.isEnabled("Grim");
            boolean isCustom = rotationMode.isEnabled("Custom");
            float maxStep = isGrim
                    ? MAX_SERVER_ROTATION_STEP
                    : (isCustom ? customRotation.maxStepValue() : 360.0F);
            // Neuro квантует дельты сам (с переносом остатка) — RotationManager
            // должен применять GCD как обычно, иначе gcdError > 0 палит ротацию.
            boolean skipGcd = isCustom && customRotation.skipGcd();
            RotationManager.rotate(rotation, getMoveCorrection(), speed.yaw(), speed.pitch(), speed.returnSpeed(), skipGcd, maxStep);
            silentYaw = RotationManager.getCurrentRotation().yaw();
            silentPitch = RotationManager.getCurrentRotation().pitch();
        }

        if (target != null && mc.interactionManager != null && !preparedSprintDropThisTick && canAttack(rotation)) {
            attackTarget();
        }
    }



    private LivingEntity findTarget() {
        if (focusTarget.isEnabled() && target != null && isValidTarget(target, getEffectiveAimRange() + 0.35)) {
            return target;
        }

        double maxDistance = getEffectiveAimRange();
        LivingEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity, maxDistance)) continue;

            LivingEntity living = (LivingEntity) entity;

            double score = getTargetScore(living);
            if (score < bestScore) {
                bestScore = score;
                bestTarget = living;
            }
        }

        return bestTarget;
    }

    private double getTargetScore(LivingEntity entity) {
        double score;
        if (sortMode.isEnabled("По здоровью") || sortMode.isEnabled("Здоровье")) {
            score = entity.getHealth() + entity.getAbsorptionAmount();
        } else if (sortMode.isEnabled("По полю зрения") || sortMode.isEnabled("Угол обзора")) {
            score = getFovToEntity(entity);
        } else {
            score = mc.player.squaredDistanceTo(entity);
        }
        // Stickiness bonus so normal knockback doesn't cause chaotic target flickering
        if (target != null && entity == target) {
            score -= 3.5;
        }
        return score;
    }

    private double getFovToEntity(LivingEntity entity) {
        Rotation rotation = RotationManager.getRotationTo(getRockstarAimPoint(entity));
        float yawDelta = Math.abs(MathHelper.wrapDegrees(rotation.yaw() - mc.player.getYaw()));
        float pitchDelta = Math.abs(MathHelper.wrapDegrees(rotation.pitch() - mc.player.getPitch()));
        return Math.hypot(yawDelta, pitchDelta);
    }


    private boolean isValidTarget(Entity entity, double maxDistance) {
        if (entity == mc.player || !(entity instanceof LivingEntity)) return false;
        LivingEntity living = (LivingEntity) entity;
        if (entity instanceof ArmorStandEntity) return false;
        if (!entity.isAlive() || !entity.isAttackable() || living.isDead() || living.getHealth() <= 0.0F || living.deathTime > 0) return false;
        if (entity.isInvisible() && !targets.isEnabled("Невидимые")) return false;
        if (entity instanceof PlayerEntity player) {
            AntiBot antiBot = FunctionManager.getFunction(AntiBot.class);
            if (antiBot != null && antiBot.isEnabled() && antiBot.isBot(player)) return false;
            if (NoFriendDamage.shouldIgnore(player)) return false;
            if (!targets.isEnabled("Игроки")) return false;
        }
        if (entity instanceof AnimalEntity && !targets.isEnabled("Животные")) return false;
        if (entity instanceof MobEntity && !targets.isEnabled("Мобы")) return false;

        if (rotationMode.isEnabled("Legit")) {
            Rotation rotation = RotationManager.getRotationTo(getRockstarAimPoint(living));
            float yawDiff = Math.abs(MathHelper.wrapDegrees(rotation.yaw() - mc.player.getYaw()));
            float pitchDiff = Math.abs(MathHelper.wrapDegrees(rotation.pitch() - mc.player.getPitch()));
            if (Math.hypot(yawDiff, pitchDiff) > 90.0) {
                return false;
            }
        }

        return mc.player.squaredDistanceTo(entity) <= maxDistance * maxDistance;
    }

    private Rotation getRotation(LivingEntity entity) {
        if (rotationMode.isEnabled("Legit")) return new Rotation(mc.player.getYaw(), mc.player.getPitch());
        if (rotationMode.isEnabled("Neuro")) {
            return NeuroManager.predictRotation(entity, getCurrentOrPlayerRotation());
        }
        if (rotationMode.isEnabled("Custom")) return getCustomRotation(entity);
        if (rotationMode.isEnabled("CakeWorld")) return getCakeWorldRotation(entity);
        if (rotationMode.isEnabled("ReallyWorld")) return getReallyWorldRotation(entity);
        if (rotationMode.isEnabled("Matrix")) return getMatrixRotation(entity);
        if (rotationMode.isEnabled("HvH")) return getHvHRotation(entity);
        return getGrimRotation(entity);
    }

    private Rotation getCustomRotation(LivingEntity entity) {
        return customRotation.compute(
                entity,
                getCurrentOrPlayerRotation(),
                isAttackWindowOpen(entity),
                spookyRandom,
                customFlickUntil
        );
    }






    private Rotation getCakeWorldRotation(LivingEntity entity) {
        Rotation current = RotationManager.isActive()
                ? RotationManager.getCurrentRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());

        if (entity == null) {
            return current;
        }

        Rotation targetRotation = RotationManager.getRotationTo(getRandomAimPoint(entity));
        float yawDelta = MathHelper.wrapDegrees(targetRotation.yaw() - current.yaw());
        float pitchDelta = MathHelper.wrapDegrees(targetRotation.pitch() - current.pitch());
        float rotationDifference = Math.max((float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta)), 0.001f);

        boolean canAttackNow = mc.player.squaredDistanceTo(entity) <= getEffectiveRange() * getEffectiveRange()
                && isAttackReadyByType() && !shouldSkipAttackByState() && canAttackTargetThroughBlocks(entity);

        float randomYawOffset = canAttackNow ? 0 : (6.0F + spookyRandom.nextFloat() * 18.0F) * (float) Math.sin(System.currentTimeMillis() / 22D);
        float randomPitchOffset = canAttackNow ? 0 : (2.0F + spookyRandom.nextFloat() * 22.0F) * (float) Math.cos(System.currentTimeMillis() / 22D);

        boolean canAttackIn2Ticks = mc.player.squaredDistanceTo(entity) <= getEffectiveRange() * getEffectiveRange()
                && (isHoldingAirstack() || isClickAttackType() || mc.player.getAttackCooldownProgress(ATTACK_COOLDOWN_TICK_DELTA) >= 0.75f);

        float straightLineYaw = Math.abs(yawDelta / rotationDifference) * (canAttackIn2Ticks ? 66.0F : 33.0F);
        float straightLinePitch = Math.abs(pitchDelta / rotationDifference) * (canAttackIn2Ticks ? 44.0F : 22.0F);

        float newYaw = current.yaw() + MathHelper.clamp(yawDelta, -straightLineYaw, straightLineYaw) + randomYawOffset;
        float newPitch = current.pitch() + MathHelper.clamp(pitchDelta, -straightLinePitch, straightLinePitch) + randomPitchOffset;

        return new Rotation(newYaw, MathHelper.clamp(newPitch, -90.0F, 90.0F));
    }

    private Vec3d getRandomAimPoint(LivingEntity entity) {
        net.minecraft.util.math.Box box = entity.getBoundingBox();
        return new Vec3d(
                box.minX + spookyRandom.nextDouble() * (box.maxX - box.minX),
                box.minY + spookyRandom.nextDouble() * (box.maxY - box.minY),
                box.minZ + spookyRandom.nextDouble() * (box.maxZ - box.minZ)
        );
    }

    private Vec3d getRandomAnatomicalPoint(LivingEntity entity) {
        net.minecraft.util.math.Box box = entity.getBoundingBox();
        double x = box.minX + (box.maxX - box.minX) * 0.5;
        double z = box.minZ + (box.maxZ - box.minZ) * 0.5;
        double y;

        int part = spookyRandom.nextInt(3);
        if (part == 0) {
            y = entity.getEyeY() - 0.1;
        } else if (part == 1) {
            y = box.minY + (box.maxY - box.minY) * 0.5;
        } else {
            y = box.minY + (box.maxY - box.minY) * 0.15;
        }


        x += (spookyRandom.nextDouble() - 0.5) * (box.maxX - box.minX) * 0.7;
        z += (spookyRandom.nextDouble() - 0.5) * (box.maxZ - box.minZ) * 0.7;

        return new Vec3d(x, y, z);
    }



    private Vec3d getRockstarAimPoint(LivingEntity entity) {
        Vec3d nearest = RotationManager.getNearestPoint(entity);
        return new Vec3d(
                nearest.x,
                MathHelper.clamp(lerp(mc.player.getEyeY(), entity.getEyeY(), 0.5), entity.getBoundingBox().minY, entity.getBoundingBox().maxY),
                nearest.z
        );
    }

    private Vec3d getLegitYawAimPoint(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double halfWidthX = (box.maxX - box.minX) * 0.25D;
        double halfWidthZ = (box.maxZ - box.minZ) * 0.25D;

        return new Vec3d(
                MathHelper.clamp(mc.player.getX(), centerX - halfWidthX, centerX + halfWidthX),
                box.getCenter().y,
                MathHelper.clamp(mc.player.getZ(), centerZ - halfWidthZ, centerZ + halfWidthZ)
        );
    }



    private Rotation getCurrentOrPlayerRotation() {
        return RotationManager.isActive()
                ? RotationManager.getCurrentRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }



    private Rotation getGrimRotation(LivingEntity entity) {
        Rotation current = getCurrentOrPlayerRotation();
        if (entity == null) return current;

        Vec3d aimPoint = getAimPoint(entity, grimAimPoint.getActiveMode());
        Rotation targetRot = RotationManager.getRotationTo(aimPoint);
        float yawDelta = MathHelper.wrapDegrees(targetRot.yaw() - current.yaw());
        float pitchDelta = targetRot.pitch() - current.pitch();

        float smooth = (float) MathHelper.clamp(grimSmooth.getValue(), 0.1, 1.0);
        float nextYaw = (float) lerp(current.yaw(), current.yaw() + yawDelta, smooth);
        float nextPitch = (float) lerp(current.pitch(), current.pitch() + pitchDelta, smooth);

        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private Vec3d getAimPoint(LivingEntity entity, String mode) {
        return switch (mode) {
            case "Глаза" -> new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
            case "Центр" -> entity.getBoundingBox().getCenter();
            case "Ноги" -> new Vec3d(entity.getX(), entity.getY() + 0.2, entity.getZ());
            case "Случайная" -> getRandomAimPoint(entity);
            default -> getRockstarAimPoint(entity);
        };
    }

    private Rotation getReallyWorldRotation(LivingEntity entity) {
        Rotation current = getCurrentOrPlayerRotation();
        if (entity == null) return current;

        long now = System.currentTimeMillis();
        if (rwFlick.isEnabled() && now < rwFlickUntil) {
            return new Rotation(current.yaw(), (float) lerp(current.pitch(), current.pitch() - 90.0F, 0.55));
        }

        Rotation targetRot = RotationManager.getRotationTo(getRockstarAimPoint(entity));
        float yawDelta = MathHelper.wrapDegrees(targetRot.yaw() - current.yaw());
        float pitchDelta = targetRot.pitch() - current.pitch();
        double total = Math.max(Math.hypot(yawDelta, pitchDelta), 0.001);

        float yawCap = (float) (Math.abs(yawDelta / total) * 180.0);
        float pitchCap = (float) (Math.abs(pitchDelta / total) * 180.0);
        float clampedYaw = MathHelper.clamp(yawDelta, -yawCap, yawCap);
        float clampedPitch = MathHelper.clamp(pitchDelta, -pitchCap, pitchCap);

        float speed = (float) (rwSpeed.getValue() * randomBetween(spookyRandom, 0.95F, 1.05F));
        float nextYaw = (float) lerp(current.yaw(), current.yaw() + clampedYaw, speed);
        float nextPitch = (float) lerp(current.pitch(), current.pitch() + clampedPitch, speed);

        if (rwJitter.isEnabled() && !isAttackWindowOpen(entity)) {
            nextYaw += -6.0F * (float) Math.cos(now / 90.0);
            nextPitch += 6.0F * (float) Math.sin(now / 90.0);
        }

        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private Rotation getMatrixRotation(LivingEntity entity) {
        Rotation current = getCurrentOrPlayerRotation();
        if (entity == null) return current;

        long now = System.currentTimeMillis();
        if (now - matrixFreqUpdateTime > 2000L) {
            matrixJitterYawFreq = randomBetween(spookyRandom, 15.0F, 145.0F);
            matrixJitterPitchFreq = randomBetween(spookyRandom, 15.0F, 145.0F);
            matrixFreqUpdateTime = now;
        }

        Rotation targetRot = RotationManager.getRotationTo(getRockstarAimPoint(entity));
        float yawDelta = MathHelper.wrapDegrees(targetRot.yaw() - current.yaw());
        float pitchDelta = targetRot.pitch() - current.pitch();

        boolean attack = isAttackWindowOpen(entity);
        float yawCap = attack ? 360.0F : 100.0F;
        float pitchCap = 180.0F;
        float clampedYaw = MathHelper.clamp(yawDelta, -yawCap, yawCap);
        float clampedPitch = MathHelper.clamp(pitchDelta, -pitchCap, pitchCap);

        float speed = (float) (matrixSpeed.getValue() * (attack ? 1.0F : randomBetween(spookyRandom, 0.0F, 0.5F)));
        float nextYaw = (float) lerp(current.yaw(), current.yaw() + clampedYaw, speed);
        float nextPitch = (float) lerp(current.pitch(), current.pitch() + clampedPitch, speed);

        if (matrixJitter.isEnabled() && !attack) {
            nextYaw += randomBetween(spookyRandom, 0.0F, 6.0F) * (float) Math.sin(now / (double) matrixJitterYawFreq);
            nextPitch += randomBetween(spookyRandom, 1.0F, 3.0F) * (float) Math.sin(now / (double) matrixJitterPitchFreq);
        }

        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private Rotation getHvHRotation(LivingEntity entity) {
        Rotation current = getCurrentOrPlayerRotation();
        if (entity == null) return current;

        Rotation targetRot = RotationManager.getRotationTo(getRockstarAimPoint(entity));
        float yawDelta = MathHelper.wrapDegrees(targetRot.yaw() - current.yaw());
        float pitchDelta = targetRot.pitch() - current.pitch();

        float maxYaw = (float) hvhYawSpeed.getValue();
        float maxPitch = (float) hvhPitchSpeed.getValue();
        float clampedYaw = MathHelper.clamp(yawDelta, -maxYaw, maxYaw);
        float clampedPitch = MathHelper.clamp(pitchDelta, -maxPitch, maxPitch);

        float nextYaw = current.yaw() + clampedYaw;
        float nextPitch = current.pitch() + clampedPitch;

        if (!isAttackWindowOpen(entity)) {
            long ms = System.currentTimeMillis();
            nextYaw += 5.0F * (float) Math.sin(ms / 45.0);
            nextPitch += 5.0F * (float) Math.sin(ms / 45.0);
        }

        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private RotationSpeed getRotationSpeed(Rotation rotation) {
        if (rotationMode.isEnabled("Grim")) {
            return new RotationSpeed(MAX_SERVER_ROTATION_STEP, MAX_SERVER_ROTATION_STEP, MAX_SERVER_ROTATION_STEP);
        }
        if (rotationMode.isEnabled("Custom")) {
            CustomRotation.Speeds speeds = customRotation.speeds();
            return new RotationSpeed(speeds.yaw(), speeds.pitch(), speeds.returnSpeed());
        }
        return new RotationSpeed(360.0F, 360.0F, 180.0F);
    }

    private Vec3d getExactAimPoint(LivingEntity entity) {
        return getRockstarAimPoint(entity);
    }

    private boolean tryAimExactlyForHit(Rotation appliedRotation) {
        if (target == null) return false;
        if (rotationMode.isEnabled("Neuro")) {
            Rotation current = RotationManager.isActive()
                    ? RotationManager.getCurrentRotation()
                    : new Rotation(mc.player.getYaw(), mc.player.getPitch());
            return doesRotationHitTarget(current);
        }
        if (rotationMode.isEnabled("Legit")) {
            return isActuallyAimedAtTarget(appliedRotation);
        }
        Rotation exact = RotationManager.getRotationTo(getExactAimPoint(target));
        Rotation current = RotationManager.isActive()
                ? RotationManager.getCurrentRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());

        float targetPitch = exact.pitch();

        float yawDelta = MathHelper.wrapDegrees(exact.yaw() - current.yaw());
        float pitchDelta = targetPitch - current.pitch();

        boolean critReady = isCriticalHitReady();
        float stepLimit = critReady ? CRIT_SERVER_ROTATION_STEP : MAX_SERVER_ROTATION_STEP;

        if (Math.abs(yawDelta) > stepLimit || Math.abs(pitchDelta) > stepLimit) {
            return false;
        }

        RotationManager.rotate(new Rotation(exact.yaw(), targetPitch), getMoveCorrection(), stepLimit, stepLimit, MAX_SERVER_ROTATION_STEP);
        silentYaw = RotationManager.getCurrentRotation().yaw();
        silentPitch = RotationManager.getCurrentRotation().pitch();
        return isActuallyAimedAtTarget(appliedRotation);
    }

    private boolean doesRotationHitTarget(Rotation rotation) {
        if (target == null || mc.player == null) return false;
        Vec3d eyePos = mc.player.getEyePos();
        Box box = target.getBoundingBox().expand(Math.max(0.1D, target.getTargetingMargin()));
        if (box.contains(eyePos)) return true;
        float pitch = rotation.pitch() * ((float) Math.PI / 180.0F);
        float yaw = -rotation.yaw() * ((float) Math.PI / 180.0F);
        float yawCos = MathHelper.cos(yaw);
        float yawSin = MathHelper.sin(yaw);
        float pitchCos = MathHelper.cos(pitch);
        float pitchSin = MathHelper.sin(pitch);
        Vec3d look = new Vec3d(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
        Vec3d rayEnd = eyePos.add(look.multiply(getEffectiveRange() + 0.6D));
        return box.raycast(eyePos, rayEnd).isPresent();
    }

    private Vec3d getStableHitPoint(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        return new Vec3d(
                (box.minX + box.maxX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.58D,
                (box.minZ + box.maxZ) * 0.5D
        );
    }

    private boolean isActuallyAimedAtTarget(Rotation appliedRotation) {
        if (rotationMode.isEnabled("Legit")) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                net.minecraft.util.hit.EntityHitResult entityHit = (net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget;
                if (entityHit.getEntity() == target) return true;
            }

            Vec3d eyePos = mc.player.getEyePos();
            float pitch = mc.player.getPitch() * ((float) Math.PI / 180.0F);
            float yaw = -mc.player.getYaw() * ((float) Math.PI / 180.0F);
            float yawCos = MathHelper.cos(yaw);
            float yawSin = MathHelper.sin(yaw);
            float pitchCos = MathHelper.cos(pitch);
            float pitchSin = MathHelper.sin(pitch);
            Vec3d look = new Vec3d(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
            Vec3d rayEnd = eyePos.add(look.multiply(getEffectiveRange() + 0.5D));

            return target.getBoundingBox().raycast(eyePos, rayEnd).isPresent();
        }

        Rotation exact = RotationManager.getRotationTo(getExactAimPoint(target));
        Rotation current = RotationManager.isActive()
                ? RotationManager.getCurrentRotation()
                : new Rotation(mc.player.getYaw(), mc.player.getPitch());

        float targetPitch = exact.pitch();

        float yawDelta = Math.abs(MathHelper.wrapDegrees(exact.yaw() - current.yaw()));
        float pitchDelta = Math.abs(targetPitch - current.pitch());
        float tolerance = isCriticalHitReady() ? CRIT_AIM_TOLERANCE : ATTACK_AIM_TOLERANCE;


        return yawDelta <= tolerance && pitchDelta <= tolerance;
    }

    private boolean isAimedAt(Rotation rotation) {
        return isAimedAt(rotation, 18.0F, 18.0F);
    }

    private boolean isAimedAt(Rotation rotation, float yawTolerance, float pitchTolerance) {
        float yawDelta = Math.abs(MathHelper.wrapDegrees(rotation.yaw() - silentYaw));
        float pitchDelta = Math.abs(MathHelper.wrapDegrees(rotation.pitch() - silentPitch));

        return yawDelta <= yawTolerance && pitchDelta <= pitchTolerance;
    }

    private boolean isAttackWindowOpen(LivingEntity entity) {
        if (entity == null) return false;
        if (shouldSkipAttackByState()) return false;
        if (mc.player.squaredDistanceTo(entity) > getEffectiveRange() * getEffectiveRange()) return false;
        if (!canAttackTargetThroughBlocks(entity)) return false;
        if (!isAttackReadyByType()) return false;
        return canConfiguredCritical();
    }

    private boolean canAttack(Rotation rotation) {
        if (shouldSkipAttackByState()) return false;

        if (mc.player.squaredDistanceTo(target) > getEffectiveRange() * getEffectiveRange()) return false;
        if (!canAttackTargetThroughBlocks(target)) return false;
        if (!isAttackReadyByType()) return false;
        if (!(canConfiguredCritical())) return false;

        if (!tryAimExactlyForHit(rotation)) return false;
        ez.minar.system.features.movement.AirStuck airStuck =
                FunctionManager.getFunction(ez.minar.system.features.movement.AirStuck.class);
        if (airStuck != null && airStuck.prepareCriticalFreeze(target.getId())) return false;
        if (airStuck != null && !airStuck.isCriticalAttackReady(target.getId())) return false;

        return true;
    }

    private boolean canConfiguredCritical() {
        if (isClickAttackType()) return true;
        if (mc.player != null && mc.player.isGliding()) return true;
        if (mc.player != null
                && (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS))) {
            return true;
        }
        return sprintResetMode.isEnabled("Легитный") ? canLegitCritical() : canCritical();
    }


    private boolean isAttackReadyByType() {
        if (!isClickAttackType()) return isAttackCooledDown();
        return lastAttackTime == 0L
                || System.currentTimeMillis() - lastAttackTime >= nextClickAttackDelay;
    }

    private boolean isAttackImminent() {
        if (isClickAttackType()) {
            return lastAttackTime != 0L
                    && System.currentTimeMillis() - lastAttackTime >= nextClickAttackDelay - 150L;
        }
        return mc.player.getAttackCooldownProgress(ATTACK_COOLDOWN_TICK_DELTA) >= 0.55F;
    }

    private boolean isClickAttackType() {
        return attackType.isEnabled("1.8");
    }

    private long nextClickAttackDelay() {
        double cps = MathHelper.clamp(clickCps.getValue(), 1.0, 20.0);
        double spread = Math.max(0.5, cps * 0.12);
        double randomizedCps = MathHelper.clamp(
                cps + (spookyRandom.nextDouble() * 2.0 - 1.0) * spread,
                1.0,
                20.0
        );
        return Math.max(1L, Math.round(1000.0 / randomizedCps));
    }

    private long lastFireworkTime = 0;

    private boolean isHoldingAirstack() {
        if (mc.player == null) return false;
        for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
            net.minecraft.item.ItemStack stack = mc.player.getStackInHand(hand);
            if (stack != null && !stack.isEmpty()) {
                String name = stack.getName().getString().toLowerCase();
                if (name.contains("аирстак") || name.contains("airstack")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldSkipAttackByState() {
        if (noAttackIf.isEnabled("Ешь еду")
                && mc.player.isUsingItem()
                && mc.player.getActiveItem().getUseAction() == UseAction.EAT) {
            return true;
        }

        if ((noAttackIf.isEnabled("Открыт инвентарь") || noAttackOpenInv.isEnabled())
                && mc.currentScreen instanceof InventoryScreen) {
            return true;
        }

        ez.minar.system.features.player.ElytraHelper elytraHelper = FunctionManager.getFunction(ez.minar.system.features.player.ElytraHelper.class);
        if (elytraHelper != null && elytraHelper.isWorking()) {
            lastFireworkTime = System.currentTimeMillis();
            return true;
        }

        if (mc.options.useKey.isPressed() && (mc.player.getMainHandStack().isOf(net.minecraft.item.Items.FIREWORK_ROCKET) || mc.player.getOffHandStack().isOf(net.minecraft.item.Items.FIREWORK_ROCKET))) {
            lastFireworkTime = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    private boolean canAttackTargetThroughBlocks(LivingEntity entity) {
        if (!throughWalls.isEnabled("Нет")) return true;
        if (mc.player.squaredDistanceTo(entity) > aimRange.getValue() * aimRange.getValue()) return true;

        return canSeePoint(entity.getEyePos())
                || canSeePoint(entity.getBoundingBox().getCenter())
                || canSeePoint(getRockstarAimPoint(entity));
    }

    private boolean canSeePoint(Vec3d point) {
        BlockHitResult result = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                point,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    private double getEffectiveRange() {
        return isHoldingAirstack() ? 6.0 : range.getValue();
    }

    private double getEffectiveAimRange() {
        return isHoldingAirstack() ? Math.max(6.0, aimRange.getValue()) : aimRange.getValue();
    }

    private boolean isAttackCooledDown() {
        if (isHoldingAirstack()) return true;
        if (criticalSpeed.isEnabled("С задержкой") && System.currentTimeMillis() < nextCriticalAttackTime) return false;
        return mc.player.getAttackCooldownProgress(ATTACK_COOLDOWN_TICK_DELTA) >= ATTACK_COOLDOWN_THRESHOLD;
    }

    private void updateJumpCriticalWait() {
        if (mc.options.jumpKey.isPressed()) {
            jumpCriticalWaitTicks = 4;
        } else if (jumpCriticalWaitTicks > 0) {
            jumpCriticalWaitTicks--;
        }
    }

    private boolean isNotCrit() {
        double effectiveJumpHeight = mc.player.getStepHeight();
        Vec3d jumpVec = new Vec3d(0, effectiveJumpHeight, 0);
        Vec3d allowedMovement = Entity.adjustMovementForCollisions(mc.player, jumpVec, mc.player.getBoundingBox(), mc.world, java.util.List.of());

        return mc.player.isInLava()
                || mc.player.isClimbing()
                || mc.player.isSubmergedIn(net.minecraft.registry.tag.FluidTags.WATER)
                || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING)
                || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                || mc.player.hasVehicle()
                || mc.player.getAbilities().flying
                || (allowedMovement.y < mc.player.getStepHeight() - 0.5 && mc.player.isOnGround() || mc.player.getVelocity().y == -0.005 && mc.player.isSubmergedInWater());
    }

    private boolean canCritical() {
        return isCriticalHitReady();
    }

    private boolean hasCriticalDescent() {
        return mc.player.fallDistance > 0.0F;
    }

    private boolean isCriticalHitReady() {
        return !isHoldingAirstack()
                && !isNotCrit()
                && !mc.player.isOnGround()
                && hasCriticalDescent();
    }

    private boolean isAscendingForCritical() {
        return !mc.player.isOnGround() && mc.player.getVelocity().y > 0.0D;
    }

    private boolean canSmartGroundHit() {
        return smartCriticals.isEnabled()
                && mc.player.isOnGround()
                && !isJumpingForCritical();
    }

    private boolean isJumpingForCritical() {
        return mc.options.jumpKey.isPressed() || jumpCriticalWaitTicks > 0;
    }

    private boolean willBeCriticalSoon() {
        return !isHoldingAirstack()
                && !isNotCrit()
                && !mc.player.isOnGround()
                && (hasCriticalDescent() || mc.player.getVelocity().y < 0.0D);
    }

    private boolean canLegitCritical() {
        return isCriticalHitReady();
    }

    private boolean shouldPrepareFastSprintReset() {
        if (!sprintResetMode.isEnabled("Быстрый")) return false;
        if (sprintDropTicks > 0) return false;
        if (target == null) return false;
        if (!mc.player.isSprinting()) return false;
        if (!willBeCriticalSoon()) return false;
        if (shouldSkipAttackByState()) return false;
        if (mc.player.squaredDistanceTo(target) > getEffectiveRange() * getEffectiveRange()) return false;
        if (!canAttackTargetThroughBlocks(target)) return false;
        return mc.player.getAttackCooldownProgress(ATTACK_COOLDOWN_TICK_DELTA) >= 0.78F;
    }

    private boolean shouldPrepareLegitSprintReset() {
        if (!sprintResetMode.isEnabled("Легитный")) return false;
        if (legitSprintPrepared || isLegitSprintHeld()) return false;
        if (target == null) return false;
        if (!mc.player.isSprinting() && !mc.options.sprintKey.isPressed()) return false;
        if (!willBeCriticalSoon()) return false;
        if (shouldSkipAttackByState()) return false;
        if (mc.player.squaredDistanceTo(target) > getEffectiveRange() * getEffectiveRange()) return false;
        if (!canAttackTargetThroughBlocks(target)) return false;
        return mc.player.getAttackCooldownProgress(ATTACK_COOLDOWN_TICK_DELTA) >= 0.82F;
    }

    private boolean shouldCancelLegitSprintPreparation() {
        if (!legitSprintPrepared) return false;
        if (!sprintResetMode.isEnabled("Легитный")) return true;
        if (target == null) return true;
        if (shouldSkipAttackByState()) return true;
        if (!willBeCriticalSoon() && !isCriticalHitReady()) return true;
        if (mc.player.squaredDistanceTo(target) > getEffectiveRange() * getEffectiveRange()) return true;
        return !canAttackTargetThroughBlocks(target);
    }

    private MoveCorrection getMoveCorrection() {
        if (moveCorrection.isEnabled("Незаметная")) return MoveCorrection.SILENT;
        if (moveCorrection.isEnabled("Направленная")) return MoveCorrection.DIRECT;
        return MoveCorrection.NONE;
    }

    private void attackTarget() {
        boolean criticalHitReady = isCriticalHitReady();
        boolean sprintIntent = mc.player.isSprinting() || mc.options.sprintKey.isPressed() || legitSprintIntent;
        boolean legitMode = sprintResetMode.isEnabled("Легитный");
        boolean preResetSprint = criticalHitReady && !legitMode;
        if (preResetSprint && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }



        if (mc.player.isGliding() && System.currentTimeMillis() - lastFireworkTime > 2000) {
            mc.player.setVelocity(0, 0, 0);
        }

        mc.interactionManager.attackEntity(mc.player, target);
        NeuroManager.attack();
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        if (criticalHitReady && sprintIntent) {
            if (!preResetSprint && mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            if (legitMode) {
                legitSprintPrepared = false;
                legitSprintIntent = false;
                legitSprintRestoreTime = System.currentTimeMillis() + 110L;
            } else {
                sprintDropTicks = 2;
            }
        }

        long now = System.currentTimeMillis();
        lastAttackTime = now;
        nextClickAttackDelay = isClickAttackType() ? nextClickAttackDelay() : 0L;
        nextCriticalAttackTime = criticalSpeed.isEnabled("С задержкой") ? now + nextCriticalDelay() : 0L;

        if (rotationMode.isEnabled("ReallyWorld") && rwFlick.isEnabled()) {
            rwHitCounter++;
            if (rwHitCounter % (int) rwFlickHits.getValue() == 0) {
                rwFlickUntil = now + RW_FLICK_DURATION_MS;
            }
        }

        if (rotationMode.isEnabled("Custom") && customRotation.flickEnabled()) {
            customHitCounter++;
            if (customRotation.shouldTriggerFlick(customHitCounter)) {
                customFlickUntil = now + customRotation.flickDurationMs();
            }
        }

        lastAttackTargetId = target.getId();
    }

    public boolean isTrackingTarget(int entityId) {
        return target != null && target.isAlive() && target.getId() == entityId;
    }

    public boolean hasTarget() {
        return target != null && target.isAlive();
    }

    private long nextCriticalDelay() {
        long delay;
        do {
            delay = 360L + spookyRandom.nextInt(261);
        } while (delay == lastCriticalDelay);

        lastCriticalDelay = delay;
        return delay;
    }


    @EventHandler
    public void onInput(InputEvent event) {
        if (!isDroppingSprint()) return;
        event.setSprint(false);
        if (sprintDropTicks > 0) {
            sprintDropTicks--;
        }
    }

    public boolean isDroppingSprint() {
        return sprintDropTicks > 0 || isLegitSprintHeld();
    }

    private boolean isLegitSprintHeld() {
        return sprintResetMode.isEnabled("Легитный")
                && (legitSprintPrepared || legitSprintRestoreTime > System.currentTimeMillis());
    }

    private void resetSprintDrop() {
        sprintDropTicks = 0;
        legitSprintPrepared = false;
        legitSprintIntent = false;
        legitSprintRestoreTime = 0L;
    }

    private void applyClientLookReset() {
        if (mc.player != null && RotationManager.getCurrentRotation() != null) {
            mc.player.setYaw(RotationManager.getCurrentRotation().yaw());
            mc.player.setPitch(RotationManager.getCurrentRotation().pitch());
        }
        RotationManager.reset();
    }

    private void releaseSilentRotation() {
        RotationManager.setKeepClientRenderPitch(false);
        RotationManager.releaseSmooth(Math.min(SILENT_RETURN_STEP, 10.0F));
        syncSilentRotation();
    }

    private void syncSilentRotation() {
        Rotation current = RotationManager.getCurrentRotation();
        silentYaw = current.yaw();
        silentPitch = current.pitch();
    }

    public LivingEntity getTarget() {
        return isEnabled() ? target : null;
    }

    public LivingEntity getTargetForTpAura(double maxDistance) {
        if (nullCheck.all()) return null;
        if (target == null || !target.isAlive() || mc.player.squaredDistanceTo(target) > maxDistance * maxDistance) {
            target = findTarget(maxDistance);
        }
        return target;
    }

    private LivingEntity findTarget(double maxDistance) {
        LivingEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity, maxDistance)) continue;

            LivingEntity living = (LivingEntity) entity;
            double score = getTargetScore(living);
            if (score < bestScore) {
                bestScore = score;
                bestTarget = living;
            }
        }

        return bestTarget;
    }

    public boolean canTpAuraAttack(LivingEntity entity) {
        if (nullCheck.all() || entity == null || !entity.isAlive()) return false;
        if (shouldSkipAttackByState()) return false;
        return isAttackReadyByType();
    }

    public void attackFromTpAura(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return;
        target = entity;
        attackTarget();
    }


    public boolean isAttackWindowReady() {
        if (!isEnabled() || target == null) return false;
        if (shouldSkipAttackByState()) return false;
        if (mc.player.squaredDistanceTo(target) > getEffectiveRange() * getEffectiveRange()) return false;
        if (!canAttackTargetThroughBlocks(target)) return false;
        if (!isAttackReadyByType()) return false;
        return canConfiguredCritical();
    }




    private float randomBetween(Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private double clampGaussian(double scale) {
        return MathHelper.clamp(spookyRandom.nextGaussian() * scale, -scale * 2.0D, scale * 2.0D);
    }

    private double lerp(double from, double to, double delta) {
        return from + (to - from) * delta;
    }


    private record RotationSpeed(float yaw, float pitch, float returnSpeed) {
    }
}
