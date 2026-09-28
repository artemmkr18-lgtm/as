package ez.minar.system.neuro;

import ez.minar.system.commands.CommandFeedback;
import ez.minar.system.events.EventBus;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.managers.RotationManager.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class NeuroManager {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private static final NeuroPredictor predictor = new NeuroPredictor();
    private static final NeuroRecorder recorder = new NeuroRecorder();

    private static int currentTargetId = -1;
    private static boolean attackImminent;
    private static float lastDeltaYaw;
    private static float lastDeltaPitch;

    private NeuroManager() {
    }

    public static void init() {
        EventBus.register(NeuroManager.class);
        NeuroModel.getActiveModel();
    }

    public static void tick() {
        recorder.tick();
        predictor.tick();
    }

    public static void shutdown() {
        recorder.stop();
        NeuroTrainer.cancel();
    }

    public static void dropActiveSession() {
        predictor.clear();
        currentTargetId = -1;
        attackImminent = false;
        if (recorder.isRecording()) {
            recorder.stop();
        }
    }

    @EventHandler
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() != null) {
            recorder.onAttack(event.getTarget());
            predictor.onAttack();
            attackImminent = true;
        }
    }

    public static Rotation predictRotation(LivingEntity target, Rotation current) {
        if (target == null || MC.player == null) {
            targetNull();
            return current;
        }

        NeuroModel model = NeuroModel.getActiveModel();
        Box box = target.getBoundingBox();
        Vec3d eye = MC.player.getEyePos();
        Vec3d diff = box.getCenter().subtract(eye);
        double flat = Math.max(Math.hypot(diff.x, diff.z), 0.05D);
        float targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, flat));

        if (model == null) {
            return new Rotation(targetYaw, targetPitch);
        }

        float boxSizeYaw = Math.max((float) Math.toDegrees(Math.atan2(box.getLengthX() / 2.0D, flat)), 0.5F);
        float boxSizePitch = Math.max((float) Math.toDegrees(Math.atan2(box.getLengthY() / 2.0D, flat)), 0.5F);
        double dist = NeuroRecorder.distanceToBox(eye, box);

        if (target.getId() != currentTargetId || !predictor.isInitialized()) {
            predictor.reset(model, current.yaw(), current.pitch(), targetYaw, targetPitch);
            currentTargetId = target.getId();
        }

        float gcd = calculateGcd();
        float errorFactor = 0.45F * model.sampleError(ThreadLocalRandom.current().nextFloat());
        float speedFactor = (MC.player != null && MC.player.isSubmergedInWater()) ? 0.8F : 1.1F;

        float[] delta = new float[2];
        boolean ok = predictor.predict(
                model,
                current.yaw(), current.pitch(),
                targetYaw, targetPitch,
                boxSizeYaw, boxSizePitch,
                dist, errorFactor,
                12, 0.7F, 4, speedFactor, gcd, delta
        );

        if (!ok) {
            return current;
        }

        lastDeltaYaw = delta[0];
        lastDeltaPitch = delta[1];

        float newYaw = current.yaw() + delta[0];
        float newPitch = MathHelper.clamp(current.pitch() + delta[1], -90.0F, 90.0F);

        if (attackImminent) {
            predictor.onAttack();
            attackImminent = false;
        }

        return new Rotation(newYaw, newPitch);
    }

    public static void attack() {
        attackImminent = true;
        predictor.onAttack();
    }

    public static void targetNull() {
        predictor.clear();
        currentTargetId = -1;
        attackImminent = false;
    }

    public static void enabled() {
        NeuroModel.resetActiveModel();
        predictor.clear();
        currentTargetId = -1;
        attackImminent = false;
    }

    public static void setAttackImminent(boolean imminent) {
        attackImminent = imminent;
        if (imminent) {
            predictor.onAttack();
        }
    }

    public static boolean isAttackImminent() {
        return attackImminent;
    }

    public static boolean isAttackReady() {
        return predictor.isOnTarget();
    }

    public static float getLastHitProbability() {
        return predictor.isOnTarget() ? 1.0F : 0.5F;
    }

    public static float getLastAttackProbability() {
        return 0.9F;
    }

    public static float getLastPredictedYawDelta() {
        return lastDeltaYaw;
    }

    public static float getLastPredictedPitchDelta() {
        return lastDeltaPitch;
    }

    public static void startRecording(String name) {
        String msg = recorder.start(name);
        CommandFeedback.message(msg, Formatting.GREEN);
    }

    public static void stopRecording() {
        String msg = recorder.stop();
        CommandFeedback.message(msg, Formatting.YELLOW);
    }

    public static boolean isRecording() {
        return recorder.isRecording();
    }

    /** Ticks written so far, for the HUD's recording indicator. */
    public static int recordedTicks() {
        return recorder.getRecordedTicks();
    }

    /** Name of the dataset currently being written, or an empty string. */
    public static String recordingName() {
        String name = recorder.getSessionName();
        return name == null ? "" : name;
    }

    public static void play(String name) {
        loadModel(name);
    }

    public static boolean loadModel(String name) {
        if (!NeuroModel.hasModel(name)) {
            if (NeuroModel.hasDataset(name)) {
                CommandFeedback.message("Файл '" + name + "' является датасетом (.csv), а не обученной моделью!", Formatting.YELLOW);
                CommandFeedback.message("Чтобы сделать из него нейро-модель, запусти обучение: .neuro train " + name, Formatting.AQUA);
                return false;
            }
            CommandFeedback.message("Модель '" + name + "' не найдена. Список доступных: .neuro list", Formatting.RED);
            return false;
        }
        boolean ok = NeuroModel.setActiveModel(name);
        if (ok) {
            predictor.clear();
            CommandFeedback.message("Активная нейро-модель переключена на: " + name, Formatting.GREEN);
        } else {
            CommandFeedback.message("Не удалось загрузить модель '" + name + "'.", Formatting.RED);
        }
        return ok;
    }

    public static List<String> getNames() {
        return NeuroModel.listModels();
    }

    public static String getActiveModelName() {
        return NeuroModel.getActiveModelName();
    }

    public static void cancelActiveWork() {
        if (recorder.isRecording()) {
            recorder.stop();
            CommandFeedback.message("Запись датасета остановлена.", Formatting.YELLOW);
        }
        if (NeuroTrainer.isTraining()) {
            NeuroTrainer.cancel();
        }
    }

    public static void train(String name, int epochs) {
        NeuroTrainer.train(name, epochs);
    }

    public static void printStatus() {
        NeuroModel active = NeuroModel.getActiveModel();
        String modelStatus = (active != null) ? "готово" : "не загрузилась";
        Formatting modelColor = (active != null) ? Formatting.GREEN : Formatting.RED;

        CommandFeedback.message("Модель: " + NeuroModel.getActiveModelName() + " — " + modelStatus + " (всего: " + NeuroModel.listModels().size() + ")", modelColor);

        int datasetFiles = countDatasets();
        if (recorder.isRecording()) {
            CommandFeedback.message("Запись: пишется " + recorder.getSessionName() + " (" + recorder.getRecordedTicks() + " тиков)", Formatting.AQUA);
        } else {
            CommandFeedback.message("Датасетов сохранено: " + datasetFiles + " шт. (папка: .neuro dir)", Formatting.GRAY);
        }

        if (NeuroTrainer.isTraining()) {
            CommandFeedback.message("Тренер: выполняется обучение в фоне...", Formatting.YELLOW);
        } else {
            CommandFeedback.message("Тренер: готов к работе (.neuro train <имя> [эпохи])", Formatting.GRAY);
        }
    }

    private static int countDatasets() {
        try (Stream<Path> stream = Files.list(NeuroModel.getDataDir())) {
            return (int) stream.filter(p -> p.getFileName().toString().endsWith(".csv")).count();
        } catch (Exception e) {
            return 0;
        }
    }

    private static float calculateGcd() {
        if (MC.options == null) {
            return 0.15F;
        }
        double sens = MC.options.getMouseSensitivity().getValue();
        double d2 = sens * 0.6D + 0.2D;
        return (float) (d2 * d2 * d2 * 1.2D);
    }
}
