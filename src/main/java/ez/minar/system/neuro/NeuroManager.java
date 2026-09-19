package ez.minar.system.neuro;

import com.mojang.authlib.GameProfile;
import ez.minar.system.commands.CommandFeedback;
import ez.minar.system.managers.RotationManager.Rotation;
import ez.minar.system.managers.UnhookManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NeuroManager {
    private static final String DATASET_EXTENSION = ".csv";
    private static final String MODEL_EXTENSION = ".model";
    private static final String DATASET_HEADER_V2 = "# Minar Neuro dataset v2";
    private static final String DATASET_HEADER_V1 = "# Minar Neuro dataset v1";
    private static final float MAX_ROTATION_DELTA = 30.0F;
    private static final float DESYNC_RESET_DELTA = 45.0F;
    private static final float MAX_INFERENCE_SIGMA = 0.08F;
    private static final double HIT_CHECK_RANGE = 3.0D;
    private static final double HITBOX_SHRINK_HORIZONTAL = 0.15D;
    private static final double HITBOX_SHRINK_VERTICAL = 0.05D;
    private static final int FEATURES = NeuralRotationModel.FEATURES;
    private static final int HISTORY = NeuralRotationModel.HISTORY;
    private static final int VEL_YAW_INDEX = NeuralRotationModel.FEATURE_VEL_YAW;
    private static final int VEL_PITCH_INDEX = NeuralRotationModel.FEATURE_VEL_PITCH;

    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Random RANDOM = new Random();
    private static final List<NeuralRotationModel.TrainingSample> recordingSamples = new ArrayList<>();
    private static final AtomicBoolean training = new AtomicBoolean();
    private static final AtomicBoolean cancelTrainingRequested = new AtomicBoolean();

    private static Path directory;
    private static OtherClientPlayerEntity dummy;
    private static LivingEntity recordTarget;
    private static String recordingName;
    private static final Deque<float[]> recordWindow = new ArrayDeque<>();
    private static float[] pendingWindow;
    private static boolean hasLastCamera;
    private static float lastCameraYaw;
    private static float lastCameraPitch;
    private static float previousRecordedVelYaw;
    private static float previousRecordedVelPitch;
    private static int overlayTicks;

    private static volatile NeuralRotationModel activeModel;
    private static volatile String activeModelName;
    private static int inferenceTargetId = Integer.MIN_VALUE;
    private static final Deque<float[]> inferenceWindow = new ArrayDeque<>();
    private static float previousPredictedYawDelta;
    private static float previousPredictedPitchDelta;
    private static float previousInferenceVelYaw;
    private static float previousInferenceVelPitch;
    private static float humanVelYaw;
    private static float humanVelPitch;
    private static float humanStiffness = 0.36F;
    private static float humanDamping = 0.55F;
    private static float quantRemainderYaw;
    private static float quantRemainderPitch;
    private static float tremorPhase;
    private static float lastSeenYaw;
    private static float lastSeenPitch;
    private static boolean hasLastSeenRotation;
    private static volatile float lastHitProbability;
    private static int modelLostTicks;
    private static float wanderYaw;
    private static float wanderPitch;
    private static float wanderTargetYaw;
    private static float wanderTargetPitch;
    private static int wanderRetargetTicks;
    private static boolean attackImminent;

    private NeuroManager() {
    }

    public static void init() {
        directory = FabricLoader.getInstance().getGameDir().resolve("Minar").resolve("neuro");
    }

    public static void tick() {
        if (!isRecording()) {
            return;
        }
        if (MC.player == null || MC.world == null) {
            cancelRecording("Запись остановлена: мир больше недоступен.");
            return;
        }
        if (dummy != null && dummy.isRemoved()) {
            dummy = null;
        }
        // Запись на живой цели: умерла/ушла — берём ближайшую другую. Цели нет —
        // тик пропускается (пауза), битые семплы в датасет не попадают.
        if (dummy == null && (recordTarget == null || recordTarget.isRemoved() || !recordTarget.isAlive())) {
            recordTarget = findRecordingTarget();
            if (recordTarget != null) {
                hasLastCamera = false;
                recordWindow.clear();
                pendingWindow = null;
                previousRecordedVelYaw = 0.0F;
                previousRecordedVelPitch = 0.0F;
            }
        }
        if (recordTarget == null) {
            if (++overlayTicks >= 10) {
                overlayTicks = 0;
                MC.inGameHud.setOverlayMessage(
                        Text.literal("Neuro: наведитесь на любого игрока/моба в радиусе 12 блоков...")
                                .formatted(Formatting.YELLOW),
                        false
                );
            }
            return;
        }

        float cameraYaw = MC.player.getYaw();
        float cameraPitch = MC.player.getPitch();

        if (!hasLastCamera) {
            hasLastCamera = true;
            lastCameraYaw = cameraYaw;
            lastCameraPitch = cameraPitch;
            pushWindow(recordWindow, createFeatures(MC.player, recordTarget, cameraYaw, cameraPitch,
                    0.0F, 0.0F, 0.0F, 0.0F));
            pendingWindow = flatten(recordWindow);
        } else {
            float yawDelta = MathHelper.wrapDegrees(cameraYaw - lastCameraYaw);
            float pitchDelta = cameraPitch - lastCameraPitch;
            float velYaw = MathHelper.clamp(yawDelta / MAX_ROTATION_DELTA, -1.0F, 1.0F);
            float velPitch = MathHelper.clamp(pitchDelta / MAX_ROTATION_DELTA, -1.0F, 1.0F);

            if (pendingWindow != null) {
                int base = NeuralRotationModel.INPUTS - FEATURES;
                float accelYaw = MathHelper.clamp(velYaw - pendingWindow[base + VEL_YAW_INDEX],
                        -NeuralRotationModel.MAX_ACCEL, NeuralRotationModel.MAX_ACCEL);
                float accelPitch = MathHelper.clamp(velPitch - pendingWindow[base + VEL_PITCH_INDEX],
                        -NeuralRotationModel.MAX_ACCEL, NeuralRotationModel.MAX_ACCEL);
                float hit = doesRotationHit(recordTarget, cameraYaw, cameraPitch) ? 1.0F : 0.0F;
                recordingSamples.add(new NeuralRotationModel.TrainingSample(
                        pendingWindow,
                        new float[]{accelYaw, accelPitch, hit}
                ));
            }

            float accelYawNorm = MathHelper.clamp(velYaw - previousRecordedVelYaw,
                    -NeuralRotationModel.MAX_ACCEL, NeuralRotationModel.MAX_ACCEL);
            float accelPitchNorm = MathHelper.clamp(velPitch - previousRecordedVelPitch,
                    -NeuralRotationModel.MAX_ACCEL, NeuralRotationModel.MAX_ACCEL);
            pushWindow(recordWindow, createFeatures(MC.player, recordTarget, cameraYaw, cameraPitch,
                    velYaw, velPitch, accelYawNorm, accelPitchNorm));
            pendingWindow = flatten(recordWindow);
            previousRecordedVelYaw = velYaw;
            previousRecordedVelPitch = velPitch;
            lastCameraYaw = cameraYaw;
            lastCameraPitch = cameraPitch;
        }

        if (++overlayTicks >= 5) {
            overlayTicks = 0;
            MC.inGameHud.setOverlayMessage(
                    Text.literal("Neuro: запись '" + recordingName + "' — " + recordingSamples.size()
                                    + " семплов, цель: " + recordTarget.getName().getString())
                            .formatted(Formatting.AQUA),
                    false
            );
        }
    }

    private static LivingEntity findRecordingTarget() {
        if (MC.player == null || MC.world == null) {
            return null;
        }
        LivingEntity best = null;
        double bestDistance = 144.0D; // 12 блоков
        for (Entity entity : MC.world.getEntities()) {
            if (entity == MC.player || !(entity instanceof LivingEntity living)) continue;
            if (!living.isAlive()) continue;
            double distance = MC.player.squaredDistanceTo(living);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    public static boolean startRecording(String name) {
        if (!isValidName(name)) {
            CommandFeedback.message("Название: 1-32 символа, только буквы, цифры, _ и -.", Formatting.YELLOW);
            return false;
        }
        if (MC.player == null || MC.world == null) {
            CommandFeedback.message("Сначала зайдите в мир.", Formatting.RED);
            return false;
        }
        if (training.get()) {
            cancelTraining();
            CommandFeedback.message("Предыдущее обучение отменено.", Formatting.YELLOW);
        }

        if (isRecording()) {
            cancelRecording(null);
        }
        removeDummy();
        resetInference();
        activeModel = null;
        activeModelName = null;

        // Приоритет — живая цель в радиусе 6 блоков: датасет с реальной наводки на
        // движущегося игрока учит трекинг и реакции. Дамми (статичный) — только
        // фолбэк, когда рядом никого нет.
        LivingEntity liveTarget = findRecordingTarget();
        if (liveTarget == null) {
            Vec3d look = MC.player.getRotationVec(1.0F);
            Vec3d horizontal = new Vec3d(look.x, 0.0D, look.z);
            if (horizontal.lengthSquared() < 1.0E-6D) {
                horizontal = new Vec3d(0.0D, 0.0D, 1.0D);
            } else {
                horizontal = horizontal.normalize();
            }
            Vec3d spawn = new Vec3d(MC.player.getX(), MC.player.getY(), MC.player.getZ())
                    .add(horizontal.multiply(3.0D));

            dummy = new OtherClientPlayerEntity(
                    MC.world,
                    new GameProfile(UUID.randomUUID(), "NeuroDummy")
            );
            dummy.setPosition(spawn.x, MC.player.getY(), spawn.z);
            dummy.setYaw(MC.player.getYaw() + 180.0F);
            dummy.setPitch(0.0F);
            MC.world.addEntity(dummy);
        }

        clearRecordingState();
        recordTarget = liveTarget != null ? liveTarget : dummy;
        recordingName = name;
        overlayTicks = 0;
        if (liveTarget != null) {
            CommandFeedback.message("Запись Neuro '" + name + "' начата. Цель: "
                            + liveTarget.getName().getString()
                            + ". Водите по ней прицел естественно — пишется ВАША наводка.",
                    Formatting.GREEN);
        } else {
            CommandFeedback.message("Запись Neuro '" + name + "' начата. Наводитесь на дамми; удары не записываются.",
                    Formatting.GREEN);
        }
        return true;
    }

    public static boolean stopRecording() {
        if (!isRecording()) {
            CommandFeedback.message("Запись Neuro не запущена.", Formatting.YELLOW);
            return false;
        }

        String name = recordingName;
        List<NeuralRotationModel.TrainingSample> samples = new ArrayList<>(recordingSamples);
        clearRecordingState();
        removeDummy();

        if (samples.size() < 32) {
            CommandFeedback.message("Слишком мало данных: " + samples.size() + ". Запишите минимум 32 семпла.",
                    Formatting.RED);
            return false;
        }

        try {
            ensureDirectory();
            writeDataset(datasetPath(name), samples);
            Files.deleteIfExists(modelPath(name));
            CommandFeedback.message("Датасет '" + name + "' сохранён: " + samples.size()
                    + " семплов. Запуск: .neuro play " + name, Formatting.GREEN);
            return true;
        } catch (IOException exception) {
            CommandFeedback.message("Не удалось сохранить датасет: " + exception.getMessage(), Formatting.RED);
            return false;
        }
    }

    public static boolean play(String name) {
        if (!isValidName(name)) {
            CommandFeedback.message("Некорректное название Neuro-модели.", Formatting.YELLOW);
            return false;
        }
        if (isRecording()) {
            CommandFeedback.message("Сначала завершите запись командой .neuro stop.", Formatting.YELLOW);
            return false;
        }
        if (!training.compareAndSet(false, true)) {
            CommandFeedback.message("Обучение уже выполняется. Используйте .neuro cancel для сброса.", Formatting.YELLOW);
            return false;
        }

        cancelTrainingRequested.set(false);
        Path modelPath = modelPath(name);
        Path datasetPath = datasetPath(name);
        CompletableFuture.runAsync(() -> {
            try {
                NeuralRotationModel model;
                if (Files.isRegularFile(modelPath)) {
                    try {
                        model = NeuralRotationModel.load(modelPath);
                    } catch (IOException incompatibleModel) {
                        if (!Files.isRegularFile(datasetPath)) {
                            throw incompatibleModel;
                        }
                        List<NeuralRotationModel.TrainingSample> samples = readDataset(datasetPath);
                        model = NeuralRotationModel.train(samples, name.hashCode() * 31L + samples.size(), cancelTrainingRequested::get);
                        model.save(modelPath);
                    }
                } else {
                    if (!Files.isRegularFile(datasetPath)) {
                        throw new IOException("датасет '" + name + "' не найден");
                    }
                    List<NeuralRotationModel.TrainingSample> samples = readDataset(datasetPath);
                    model = NeuralRotationModel.train(samples, name.hashCode() * 31L + samples.size(), cancelTrainingRequested::get);
                    model.save(modelPath);
                }

                NeuralRotationModel loadedModel = model;
                MC.execute(() -> {
                    activeModel = loadedModel;
                    activeModelName = name;
                    resetInference();
                    CommandFeedback.message("Neuro-модель '" + name + "' готова к работе! Выберите ротацию Neuro в ауре.",
                            Formatting.GREEN);
                });
            } catch (Exception exception) {
                if (cancelTrainingRequested.get() || exception instanceof java.util.concurrent.CancellationException) {
                    MC.execute(() -> CommandFeedback.message("Обучение остановлено.", Formatting.YELLOW));
                } else {
                    MC.execute(() -> CommandFeedback.message(
                            "Не удалось запустить Neuro: " + exception.getMessage(), Formatting.RED));
                }
            } finally {
                training.set(false);
            }
        });

        CommandFeedback.message("Подготовка Neuro-модели '" + name + "'...", Formatting.AQUA);
        return true;
    }

    public static void cancelTraining() {
        cancelTrainingRequested.set(true);
        training.set(false);
    }

    public static void cancelActiveWork() {
        boolean hadWork = false;
        if (isRecording()) {
            cancelRecording("Запись отменена пользователем.");
            hadWork = true;
        }
        if (training.get()) {
            cancelTraining();
            CommandFeedback.message("Обучение отменено.", Formatting.YELLOW);
            hadWork = true;
        }
        if (!hadWork) {
            CommandFeedback.message("Нет активной записи или обучения.", Formatting.GRAY);
        }
    }

    public static Rotation predictRotation(LivingEntity target, Rotation current) {
        NeuralRotationModel model = activeModel;
        if (target == null || MC.player == null) {
            return current;
        }
        if (inferenceTargetId != target.getId()) {
            resetInference();
            inferenceTargetId = target.getId();
            lastSeenYaw = current.yaw();
            lastSeenPitch = current.pitch();
            hasLastSeenRotation = true;
        }

        // Точка прицеливания "дышит": человек не держит перекрестие прибитым к
        // хитбоксу — она дрейфует вокруг корпуса и периодически съезжает с него.
        // При скором ударе дрейф стягивается к центру: человек точнее в момент замаха.
        updateWander();
        Rotation aim = getAimRotation(target);
        float aimYaw = aim.yaw() + wanderYaw;
        float aimPitch = MathHelper.clamp(aim.pitch() + wanderPitch, -90.0F, 90.0F);
        float yawError = MathHelper.wrapDegrees(aimYaw - current.yaw());
        float pitchError = aimPitch - current.pitch();

        float desiredYaw;
        float desiredPitch;

        if (model != null) {
            // 100% нейросеть: модель, обученная на записи ВАШЕЙ наводки, полностью
            // ведёт камеру. Детерминированный контур здесь не участвует — любая
            // формульная составляющая это отпечаток для ML-античита.
            float velYaw = 0.0F;
            float velPitch = 0.0F;
            if (hasLastSeenRotation) {
                float appliedYaw = MathHelper.wrapDegrees(current.yaw() - lastSeenYaw);
                float appliedPitch = current.pitch() - lastSeenPitch;
                if (Math.abs(appliedYaw) > DESYNC_RESET_DELTA || Math.abs(appliedPitch) > DESYNC_RESET_DELTA) {
                    inferenceWindow.clear();
                    previousInferenceVelYaw = 0.0F;
                    previousInferenceVelPitch = 0.0F;
                } else {
                    velYaw = MathHelper.clamp(appliedYaw / MAX_ROTATION_DELTA, -1.0F, 1.0F);
                    velPitch = MathHelper.clamp(appliedPitch / MAX_ROTATION_DELTA, -1.0F, 1.0F);
                }
            }

            float accelYaw = MathHelper.clamp(velYaw - previousInferenceVelYaw,
                    -NeuralRotationModel.MAX_ACCEL, NeuralRotationModel.MAX_ACCEL);
            float accelPitch = MathHelper.clamp(velPitch - previousInferenceVelPitch,
                    -NeuralRotationModel.MAX_ACCEL, NeuralRotationModel.MAX_ACCEL);

            float[] features = createFeatures(MC.player, target, current.yaw(), current.pitch(),
                    velYaw, velPitch, accelYaw, accelPitch);
            pushWindow(inferenceWindow, features);
            float[] output = model.predict(flatten(inferenceWindow));

            previousInferenceVelYaw = velYaw;
            previousInferenceVelPitch = velPitch;

            float hitProbability = NeuralRotationModel.sigmoid(output[4]);
            lastHitProbability = hitProbability;

            float angularError = (float) Math.hypot(yawError, pitchError);

            // Close to target: scale down random noise to eliminate high-frequency jitter
            float temperature = MathHelper.clamp(angularError / 50.0F, 0.0F, 0.35F)
                    * (1.0F - 0.5F * hitProbability);

            float sigmaYaw = Math.min(NeuralRotationModel.sigmaFromLog(output[2]), MAX_INFERENCE_SIGMA);
            float sigmaPitch = Math.min(NeuralRotationModel.sigmaFromLog(output[3]), MAX_INFERENCE_SIGMA);
            float commandYaw = output[0] + (float) RANDOM.nextGaussian() * sigmaYaw * temperature;
            float commandPitch = output[1] + (float) RANDOM.nextGaussian() * sigmaPitch * temperature;

            // Biomechanical motor damping: prevents velocity overshoot and oscillation (jitter)
            float motorDamping = 0.65F;
            float integratedVelYaw = velYaw * (1.0F - motorDamping) + commandYaw * motorDamping;
            float integratedVelPitch = velPitch * (1.0F - motorDamping) + commandPitch * motorDamping;
            integratedVelYaw = MathHelper.clamp(integratedVelYaw, -1.0F, 1.0F);
            integratedVelPitch = MathHelper.clamp(integratedVelPitch, -1.0F, 1.0F);

            // Proximity scaling: decelerate near target (Fitts's law) to settle cleanly on hitbox
            float proximityFactor = MathHelper.clamp(angularError / 16.0F, 0.18F, 1.0F);

            // Smooth continuous tracking gain without hard 2.5° cliff
            float trackingGain = MathHelper.clamp(angularError / 28.0F, 0.12F, 0.40F);

            float rawDesiredYaw = (integratedVelYaw * MAX_ROTATION_DELTA * proximityFactor) + (yawError * trackingGain);
            float rawDesiredPitch = (integratedVelPitch * MAX_ROTATION_DELTA * proximityFactor) + (pitchError * trackingGain);

            rawDesiredYaw = MathHelper.clamp(rawDesiredYaw, -MAX_ROTATION_DELTA, MAX_ROTATION_DELTA);
            rawDesiredPitch = MathHelper.clamp(rawDesiredPitch, -MAX_ROTATION_DELTA, MAX_ROTATION_DELTA);

            // Inertia smoothing: eliminates 20Hz jitter while keeping flicks responsive
            desiredYaw = previousPredictedYawDelta * 0.25F + rawDesiredYaw * 0.75F;
            desiredPitch = previousPredictedPitchDelta * 0.25F + rawDesiredPitch * 0.75F;
            previousPredictedYawDelta = desiredYaw;
            previousPredictedPitchDelta = desiredPitch;

            // Watchdog: модель надолго потеряла цель (>60° больше секунды) — сброс
            // окна, как будто человек заново перехватил прицел. Спасает от вечного
            // промаха на сыром датасете.
            if (angularError > 60.0F) {
                if (++modelLostTicks > 20) {
                    resetInference();
                    inferenceTargetId = target.getId();
                }
            } else {
                modelLostTicks = 0;
            }
        } else {
            // Нет модели — детерминированный контур-заглушка (для ML-античита это
            // отпечаток, годится только проверить механику). Для игры — запишите
            // датасет: .neuro record <имя> рядом с живым игроком.
            humanVelYaw = humanVelYaw * (1.0F - humanDamping) + yawError * humanStiffness;
            humanVelPitch = humanVelPitch * (1.0F - humanDamping) + pitchError * humanStiffness;

            // Физиологический тремор ~9 Гц + моторный шум: после квантования выглядит
            // как одиночные шаги ±1 по сетке — так дрожит реальная рука.
            tremorPhase += 2.6F + RANDOM.nextFloat() * 0.5F;
            float tremorYaw = (float) Math.sin(tremorPhase) * 0.10F
                    + (float) RANDOM.nextGaussian() * 0.05F;
            float tremorPitch = (float) Math.cos(tremorPhase * 0.9F) * 0.08F
                    + (float) RANDOM.nextGaussian() * 0.05F;

            desiredYaw = MathHelper.clamp(humanVelYaw, -MAX_ROTATION_DELTA, MAX_ROTATION_DELTA) + tremorYaw;
            desiredPitch = MathHelper.clamp(humanVelPitch, -MAX_ROTATION_DELTA, MAX_ROTATION_DELTA) + tremorPitch;
        }

        // Квантование на сетку чувствительности мыши с переносом остатка: каждая
        // дельта — целое число шагов сенсы (gcdError ≈ 0 у SlothAC/Grim), остаток
        // не теряется, поэтому мелкие движения не залипают. Реальная мышь выдаёт
        // дельты на этой же сетке, поэтому реализм не страдает.
        float grid = mouseGridStep();
        float deltaYaw = quantizeToGrid(desiredYaw, grid, true);
        float deltaPitch = quantizeToGrid(desiredPitch, grid, false);

        lastSeenYaw = current.yaw();
        lastSeenPitch = current.pitch();
        hasLastSeenRotation = true;

        return new Rotation(
                current.yaw() + deltaYaw,
                MathHelper.clamp(current.pitch() + deltaPitch, -90.0F, 90.0F)
        );
    }

    private static float quantizeToGrid(float desired, float grid, boolean yaw) {
        if (grid <= 1.0E-6F) {
            return MathHelper.clamp(desired, -MAX_ROTATION_DELTA, MAX_ROTATION_DELTA);
        }
        float withRemainder = desired + (yaw ? quantRemainderYaw : quantRemainderPitch);
        int steps = Math.round(withRemainder / grid);
        float quantized = MathHelper.clamp(steps * grid, -MAX_ROTATION_DELTA, MAX_ROTATION_DELTA);
        float remainder = MathHelper.clamp(withRemainder - steps * grid, -grid, grid);
        if (yaw) {
            quantRemainderYaw = remainder;
        } else {
            quantRemainderPitch = remainder;
        }
        return quantized;
    }

    private static float mouseGridStep() {
        if (MC.options == null) {
            return 0.0F;
        }
        double d = MC.options.getMouseSensitivity().getValue() * 0.6000000238418579D
                + 0.20000000298023224D;
        double f = d * d * d * 8.0D;
        return (float) (f * 0.15D);
    }

    public static void setAttackImminent(boolean value) {
        attackImminent = value;
    }

    private static void updateWander() {
        if (--wanderRetargetTicks <= 0) {
            wanderRetargetTicks = 10 + RANDOM.nextInt(15);
            float radius = 0.4F + RANDOM.nextFloat() * 1.0F; // 0.4-1.4° от точки наводки
            float angle = RANDOM.nextFloat() * 6.2832F;
            wanderTargetYaw = (float) Math.cos(angle) * radius;
            wanderTargetPitch = (float) Math.sin(angle) * radius * 0.5F;
        }
        // Перед ударом дрейф стягивается к корпусу (lock 0.05), между ударами держится компактно
        float lock = attackImminent ? 0.05F : 0.5F;
        wanderYaw += (wanderTargetYaw * lock - wanderYaw) * 0.30F;
        wanderPitch += (wanderTargetPitch * lock - wanderPitch) * 0.30F;
    }

    private static Rotation getAimRotation(LivingEntity target) {
        Vec3d eyePos = MC.player.getEyePos();
        Vec3d aimPoint = getSafeAimPoint(target);

        double dx = aimPoint.x - eyePos.x;
        double dz = aimPoint.z - eyePos.z;
        double dy = aimPoint.y - eyePos.y;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(horizontal, 1.0E-6D)));
        return new Rotation(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
    }

    public static boolean hasActiveModel() {
        return activeModel != null;
    }

    public static String getActiveModelName() {
        return activeModelName;
    }

    public static float getLastHitProbability() {
        return lastHitProbability;
    }

    public static List<String> getNames() {
        ensureInitialized();
        try {
            ensureDirectory();
            Set<String> names = new LinkedHashSet<>();
            try (var paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .forEach(fileName -> {
                            String name = removeExtension(fileName, DATASET_EXTENSION);
                            if (name == null) {
                                name = removeExtension(fileName, MODEL_EXTENSION);
                            }
                            if (name != null && isValidName(name)) {
                                names.add(name);
                            }
                        });
            }
            return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-zА-Яа-яЁё0-9_-]{1,32}");
    }

    public static void shutdown() {
        if (UnhookManager.isUnhooked()) {
            dropActiveSession();
            return;
        }

        if (isRecording() && recordingSamples.size() >= 32) {
            stopRecording();
        } else {
            dropActiveSession();
        }
    }

    public static void dropActiveSession() {
        clearRecordingState();
        removeDummy();
    }

    private static float[] createFeatures(LivingEntity observer, LivingEntity target,
                                          float cameraYaw, float cameraPitch,
                                          float velYaw, float velPitch,
                                          float accelYaw, float accelPitch) {
        Box box = target.getBoundingBox();
        Vec3d eyePos = observer.getEyePos();
        Vec3d safePoint = getSafeAimPoint(target);
        Vec3d center = box.getCenter();

        double safeDx = safePoint.x - eyePos.x;
        double safeDy = safePoint.y - eyePos.y;
        double safeDz = safePoint.z - eyePos.z;
        double safeHorizontal = Math.sqrt(safeDx * safeDx + safeDz * safeDz);
        double safeDistance = Math.sqrt(safeHorizontal * safeHorizontal + safeDy * safeDy);
        float safeYaw = (float) Math.toDegrees(Math.atan2(safeDz, safeDx)) - 90.0F;
        float safePitch = (float) -Math.toDegrees(Math.atan2(safeDy, Math.max(safeHorizontal, 1.0E-6D)));

        double centerDx = center.x - eyePos.x;
        double centerDy = center.y - eyePos.y;
        double centerDz = center.z - eyePos.z;
        double centerHorizontal = Math.sqrt(centerDx * centerDx + centerDz * centerDz);
        float centerYaw = (float) Math.toDegrees(Math.atan2(centerDz, centerDx)) - 90.0F;
        float centerPitch = (float) -Math.toDegrees(Math.atan2(centerDy, Math.max(centerHorizontal, 1.0E-6D)));

        Vec3d targetVelocity = target.getVelocity();
        Vec3d observerVelocity = observer.getVelocity();
        double yawRadians = Math.toRadians(cameraYaw);
        float sin = (float) Math.sin(yawRadians);
        float cos = (float) Math.cos(yawRadians);

        float cooldown = observer instanceof PlayerEntity player
                ? MathHelper.clamp(player.getAttackCooldownProgress(0.5F), 0.0F, 1.0F)
                : 1.0F;

        return new float[]{
                MathHelper.wrapDegrees(safeYaw - cameraYaw) / 180.0F,
                MathHelper.clamp((safePitch - cameraPitch) / 90.0F, -1.0F, 1.0F),
                MathHelper.wrapDegrees(centerYaw - cameraYaw) / 180.0F,
                MathHelper.clamp((centerPitch - cameraPitch) / 90.0F, -1.0F, 1.0F),
                MathHelper.clamp((float) safeDistance / 6.0F, 0.0F, 2.0F),
                MathHelper.clamp((float) safeHorizontal / 6.0F, 0.0F, 2.0F),
                MathHelper.clamp((float) (targetVelocity.x * cos + targetVelocity.z * sin), -1.0F, 1.0F),
                MathHelper.clamp((float) (-targetVelocity.x * sin + targetVelocity.z * cos), -1.0F, 1.0F),
                MathHelper.clamp((float) (observerVelocity.x * cos + observerVelocity.z * sin), -1.0F, 1.0F),
                MathHelper.clamp((float) (-observerVelocity.x * sin + observerVelocity.z * cos), -1.0F, 1.0F),
                velYaw,
                velPitch,
                accelYaw,
                accelPitch,
                cooldown,
                MathHelper.clamp((float) (box.maxX - box.minX) * 0.5F, 0.0F, 1.0F),
                MathHelper.clamp((float) (box.maxY - box.minY) * 0.5F, 0.0F, 1.0F)
        };
    }

    private static Box getShrunkBox(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        double shrinkX = (box.maxX - box.minX) * HITBOX_SHRINK_HORIZONTAL;
        double shrinkY = (box.maxY - box.minY) * HITBOX_SHRINK_VERTICAL;
        double shrinkZ = (box.maxZ - box.minZ) * HITBOX_SHRINK_HORIZONTAL;
        return box.contract(shrinkX, shrinkY, shrinkZ);
    }

    private static Vec3d getSafeAimPoint(LivingEntity entity) {
        Box box = getShrunkBox(entity);
        return new Vec3d(
                (box.minX + box.maxX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.55D,
                (box.minZ + box.maxZ) * 0.5D
        );
    }

    private static boolean doesRotationHit(LivingEntity entity, float yaw, float pitch) {
        if (MC.player == null) {
            return false;
        }
        Vec3d eyePos = MC.player.getEyePos();
        float pitchRadians = pitch * ((float) Math.PI / 180.0F);
        float yawRadians = -yaw * ((float) Math.PI / 180.0F);
        float yawCos = MathHelper.cos(yawRadians);
        float yawSin = MathHelper.sin(yawRadians);
        float pitchCos = MathHelper.cos(pitchRadians);
        float pitchSin = MathHelper.sin(pitchRadians);
        Vec3d look = new Vec3d(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
        Vec3d rayEnd = eyePos.add(look.multiply(HIT_CHECK_RANGE + 0.5D));
        return getShrunkBox(entity).raycast(eyePos, rayEnd).isPresent();
    }

    private static void pushWindow(Deque<float[]> window, float[] features) {
        window.addLast(features);
        while (window.size() > HISTORY) {
            window.pollFirst();
        }
    }

    private static float[] flatten(Deque<float[]> window) {
        List<float[]> frames = new ArrayList<>(window);
        while (frames.size() < HISTORY) {
            frames.add(0, frames.get(0));
        }
        float[] input = new float[NeuralRotationModel.INPUTS];
        int index = 0;
        for (float[] frame : frames) {
            for (float value : frame) {
                input[index++] = value;
            }
        }
        return input;
    }

    private static void writeDataset(Path path, List<NeuralRotationModel.TrainingSample> samples)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(DATASET_HEADER_V2 + "; history=" + HISTORY + "; features=" + FEATURES
                    + "; labels=" + NeuralRotationModel.LABELS);
            writer.newLine();
            for (NeuralRotationModel.TrainingSample sample : samples) {
                boolean first = true;
                for (float value : sample.input()) {
                    if (!first) writer.write(',');
                    writer.write(String.format(Locale.ROOT, "%.8f", value));
                    first = false;
                }
                for (float value : sample.output()) {
                    writer.write(',');
                    writer.write(String.format(Locale.ROOT, "%.8f", value));
                }
                writer.newLine();
            }
        }
    }

    private static List<NeuralRotationModel.TrainingSample> readDataset(Path path) throws IOException {
        List<NeuralRotationModel.TrainingSample> samples = new ArrayList<>();
        boolean headerSeen = false;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.startsWith("#")) {
                    if (line.startsWith(DATASET_HEADER_V2)) {
                        headerSeen = true;
                    } else if (line.startsWith(DATASET_HEADER_V1)) {
                        throw new IOException("датасет старого формата v1 — перезапишите запись");
                    }
                    continue;
                }
                String[] values = line.split(",");
                if (values.length != NeuralRotationModel.INPUTS + NeuralRotationModel.LABELS) {
                    throw new IOException("повреждённая строка датасета");
                }
                float[] input = new float[NeuralRotationModel.INPUTS];
                float[] output = new float[NeuralRotationModel.LABELS];
                try {
                    for (int i = 0; i < input.length; i++) {
                        input[i] = Float.parseFloat(values[i]);
                    }
                    for (int i = 0; i < output.length; i++) {
                        output[i] = Float.parseFloat(values[input.length + i]);
                    }
                } catch (NumberFormatException exception) {
                    throw new IOException("некорректное число в датасете", exception);
                }
                samples.add(new NeuralRotationModel.TrainingSample(input, output));
            }
        }
        if (!headerSeen) {
            throw new IOException("неизвестный формат датасета");
        }
        if (samples.size() < 32) {
            throw new IOException("для обучения нужно минимум 32 семпла");
        }
        return samples;
    }

    private static void cancelRecording(String message) {
        clearRecordingState();
        removeDummy();
        if (message != null) {
            CommandFeedback.message(message, Formatting.RED);
        }
    }

    private static void clearRecordingState() {
        recordingName = null;
        recordTarget = null;
        recordingSamples.clear();
        recordWindow.clear();
        pendingWindow = null;
        hasLastCamera = false;
        lastCameraYaw = 0.0F;
        lastCameraPitch = 0.0F;
        previousRecordedVelYaw = 0.0F;
        previousRecordedVelPitch = 0.0F;
    }

    private static void removeDummy() {
        if (dummy != null && !dummy.isRemoved()) {
            dummy.discard();
        }
        dummy = null;
    }

    private static void resetInference() {
        inferenceTargetId = Integer.MIN_VALUE;
        inferenceWindow.clear();
        previousPredictedYawDelta = 0.0F;
        previousPredictedPitchDelta = 0.0F;
        previousInferenceVelYaw = 0.0F;
        previousInferenceVelPitch = 0.0F;
        lastSeenYaw = 0.0F;
        lastSeenPitch = 0.0F;
        hasLastSeenRotation = false;
        lastHitProbability = 0.0F;
        modelLostTicks = 0;
        wanderYaw = 0.0F;
        wanderPitch = 0.0F;
        wanderTargetYaw = 0.0F;
        wanderTargetPitch = 0.0F;
        wanderRetargetTicks = 0;
        // На каждый захват цели — новые коэффициенты контура: статистика дельт
        // не схлопывается в одну точку, профиль не повторяется от фрага к фрагу.
        humanVelYaw = 0.0F;
        humanVelPitch = 0.0F;
        humanStiffness = 0.30F + RANDOM.nextFloat() * 0.12F;
        humanDamping = 0.48F + RANDOM.nextFloat() * 0.14F;
        quantRemainderYaw = 0.0F;
        quantRemainderPitch = 0.0F;
        tremorPhase = RANDOM.nextFloat() * 6.2832F;
    }

    private static boolean isRecording() {
        return recordingName != null;
    }

    private static Path datasetPath(String name) {
        ensureInitialized();
        return directory.resolve(name + DATASET_EXTENSION);
    }

    private static Path modelPath(String name) {
        ensureInitialized();
        return directory.resolve(name + MODEL_EXTENSION);
    }

    private static void ensureInitialized() {
        if (directory == null) {
            init();
        }
    }

    private static void ensureDirectory() throws IOException {
        ensureInitialized();
        Files.createDirectories(directory);
    }

    private static String removeExtension(String value, String extension) {
        return value.endsWith(extension) ? value.substring(0, value.length() - extension.length()) : null;
    }
}
