package ez.minar.system.neuro;

import ez.minar.system.commands.CommandFeedback;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NeuroTrainer {
    private static final AtomicBoolean trainingActive = new AtomicBoolean(false);
    private static volatile Process activeProcess;

    private NeuroTrainer() {
    }

    public static boolean isTraining() {
        return trainingActive.get();
    }

    public static void cancel() {
        if (trainingActive.get() && activeProcess != null) {
            try {
                activeProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            trainingActive.set(false);
            CommandFeedback.message("Обучение прервано.", Formatting.RED);
        }
    }

    public static void train(String modelName, int epochs) {
        if (!trainingActive.compareAndSet(false, true)) {
            CommandFeedback.message("Обучение уже выполняется!", Formatting.YELLOW);
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                Path trainerDir = NeuroModel.getNeuroDir().resolve("trainer");
                Files.createDirectories(trainerDir);

                Path trainScript = trainerDir.resolve("train_aura.py");
                Path commonScript = trainerDir.resolve("common.py");

                extractAsset("train_aura.py", trainScript);
                extractAsset("common.py", commonScript);

                Path dataDir = NeuroModel.getDataDir();
                Path outFile = NeuroModel.getModelPath(modelName);

                int epochCount = epochs <= 0 ? 400 : epochs;
                CommandFeedback.message("Запуск нейро-тренера (" + epochCount + " эпох)...", Formatting.AQUA);

                ProcessBuilder pb = new ProcessBuilder(
                        "python3",
                        "-u",
                        trainScript.toAbsolutePath().toString(),
                        "--data", dataDir.toAbsolutePath().toString(),
                        "--out", outFile.toAbsolutePath().toString(),
                        "--epochs", String.valueOf(epochCount)
                );
                pb.directory(trainerDir.toFile());
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");
                pb.redirectErrorStream(true);

                activeProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;

                        if (trimmed.startsWith("!")) {
                            CommandFeedback.message("[Neuro] " + trimmed, Formatting.YELLOW);
                        } else if (trimmed.startsWith("epoch") || trimmed.startsWith("val loss") || trimmed.contains("сессий")) {
                            CommandFeedback.message("[Neuro] " + trimmed, Formatting.GRAY);
                        } else {
                            CommandFeedback.message("[Neuro] " + trimmed, Formatting.DARK_GRAY);
                        }
                    }
                }

                int code = activeProcess.waitFor();
                if (code == 0 && Files.isRegularFile(outFile)) {
                    CommandFeedback.message("Модель '" + modelName + "' успешно обучена и сохранена!", Formatting.GREEN);
                    NeuroModel.setActiveModel(modelName);
                } else {
                    CommandFeedback.message("Тренер завершился с кодом " + code + ". Проверьте записанные датасеты (.neuro data).", Formatting.RED);
                }
            } catch (Exception e) {
                CommandFeedback.message("Ошибка запуска тренера: " + e.getMessage(), Formatting.RED);
            } finally {
                trainingActive.set(false);
                activeProcess = null;
            }
        }, "Neuro-Trainer-Thread");

        thread.setDaemon(true);
        thread.start();
    }

    private static void extractAsset(String assetName, Path target) {
        try {
            InputStream is = NeuroTrainer.class.getClassLoader().getResourceAsStream("assets/minar/neuro/trainer/" + assetName);
            if (is == null) {
                is = NeuroTrainer.class.getClassLoader().getResourceAsStream("assets/rockstar/neuro/trainer/" + assetName);
            }
            if (is != null) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                is.close();
            }
        } catch (Exception ignored) {
        }
    }
}
