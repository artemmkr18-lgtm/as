package ez.minar.system.neuro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class NeuroModel {
    public static final int FEATURES = 17;
    public static final String DEFAULT_MODEL = "default";
    private static final float CORRELATION_CLAMP = 0.95F;

    private static NeuroModel activeModel;
    private static boolean activeModelLoaded;
    private static String activeModelName;

    private final String name;
    private final int hidden;
    private final int mix;
    private final int freezeCut;
    private final float limitYaw;
    private final float limitPitch;
    private final float[] errorQuantiles;

    private final float[][] gruWi;
    private final float[][] gruWh;
    private final float[] gruBi;
    private final float[] gruBh;

    private final float[][] outW;
    private final float[] outB;

    private NeuroModel(String name, JsonObject json) {
        this.name = name;
        this.hidden = json.get("hidden").getAsInt();
        this.mix = json.get("mix").getAsInt();
        this.freezeCut = json.has("freezeCut") ? json.get("freezeCut").getAsInt() : 12;

        JsonArray limits = json.getAsJsonArray("limits");
        this.limitYaw = limits.get(0).getAsFloat();
        this.limitPitch = limits.get(1).getAsFloat();

        this.errorQuantiles = json.has("error") ? parse1DFloatArray(json.getAsJsonArray("error")) : new float[0];

        JsonObject gru = json.getAsJsonObject("gru");
        this.gruWi = parse2DFloatArray(gru.getAsJsonArray("wi"));
        this.gruWh = parse2DFloatArray(gru.getAsJsonArray("wh"));
        this.gruBi = parse1DFloatArray(gru.getAsJsonArray("bi"));
        this.gruBh = parse1DFloatArray(gru.getAsJsonArray("bh"));

        JsonObject out = json.getAsJsonObject("out");
        this.outW = parse2DFloatArray(out.getAsJsonArray("w"));
        this.outB = parse1DFloatArray(out.getAsJsonArray("b"));
    }

    private static float[][] parse2DFloatArray(JsonArray jsonArray) {
        float[][] result = new float[jsonArray.size()][];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = parse1DFloatArray(jsonArray.get(i).getAsJsonArray());
        }
        return result;
    }

    private static float[] parse1DFloatArray(JsonArray jsonArray) {
        float[] result = new float[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.get(i).getAsFloat();
        }
        return result;
    }

    public static Path getNeuroDir() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path runDir = mc.runDirectory != null ? mc.runDirectory.toPath() : Path.of(".");
        Path minarPath = runDir.resolve("Minar").resolve("neuro");
        try {
            Files.createDirectories(minarPath);
        } catch (Exception ignored) {
        }
        return minarPath;
    }

    public static Path getDataDir() {
        Path dataDir = getNeuroDir().resolve("data");
        try {
            Files.createDirectories(dataDir);
        } catch (Exception ignored) {
        }
        return dataDir;
    }

    public static Path getModelPath(String name) {
        String cleanName = name.endsWith(".json") ? name : (name + ".json");
        return getNeuroDir().resolve(cleanName);
    }

    public static Path getDatasetPath(String name) {
        String cleanName = name.endsWith(".csv") ? name : (name + ".csv");
        return getDataDir().resolve(cleanName);
    }

    public static boolean hasDataset(String name) {
        return Files.isRegularFile(getDatasetPath(name));
    }

    public static List<String> listDatasets() {
        List<String> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(getDataDir())) {
            stream.filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.csv$", ""))
                    .sorted()
                    .forEach(list::add);
        } catch (Exception ignored) {
        }
        return list;
    }

    public static String getActiveModelName() {
        if (activeModelName == null) {
            try {
                Path activeFile = getNeuroDir().resolve("active.txt");
                activeModelName = Files.isRegularFile(activeFile) ? Files.readString(activeFile).trim() : DEFAULT_MODEL;
            } catch (Exception ignored) {
                activeModelName = DEFAULT_MODEL;
            }
            if (activeModelName.isEmpty()) {
                activeModelName = DEFAULT_MODEL;
            }
        }
        return activeModelName;
    }

    public static boolean setActiveModel(String name) {
        if (!hasModel(name)) {
            return false;
        }
        activeModelName = name;
        try {
            Files.createDirectories(getNeuroDir());
            Files.writeString(getNeuroDir().resolve("active.txt"), name);
        } catch (Exception ignored) {
        }
        resetActiveModel();
        return true;
    }

    public static void resetActiveModel() {
        activeModelLoaded = false;
        activeModel = null;
    }

    public static NeuroModel getActiveModel() {
        if (!activeModelLoaded) {
            activeModelLoaded = true;
            activeModel = loadModel(getActiveModelName());
        }
        return activeModel;
    }

    public static boolean hasModel(String name) {
        if (DEFAULT_MODEL.equalsIgnoreCase(name)) {
            return true;
        }
        return Files.isRegularFile(getModelPath(name));
    }

    public static List<String> listModels() {
        List<String> list = new ArrayList<>();
        list.add(DEFAULT_MODEL);
        try (Stream<Path> stream = Files.list(getNeuroDir())) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .filter(name -> !list.contains(name))
                    .sorted()
                    .forEach(list::add);
        } catch (Exception ignored) {
        }
        return list;
    }

    public static NeuroModel loadModel(String name) {
        try {
            JsonObject json;
            Path file = getModelPath(name);
            if (Files.isRegularFile(file)) {
                json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } else {
                if (!DEFAULT_MODEL.equalsIgnoreCase(name)) {
                    return null;
                }
                InputStream is = NeuroModel.class.getClassLoader().getResourceAsStream("assets/minar/neuro/default.json");
                if (is == null) {
                    is = NeuroModel.class.getClassLoader().getResourceAsStream("assets/rockstar/neuro/default.json");
                }
                if (is == null) {
                    return null;
                }
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                }
            }

            if (json.get("features").getAsInt() != FEATURES) {
                return null;
            }
            if (!json.has("mix") || !json.has("gru") || !json.has("out")) {
                return null;
            }
            if (json.has("units") && !"deg".equals(json.get("units").getAsString())) {
                return null;
            }
            return new NeuroModel(name, json);
        } catch (Exception e) {
            return null;
        }
    }

    public float[] createHiddenState() {
        return new float[this.hidden];
    }

    public float[] forward(float[] features, float[] hiddenState) {
        int gateDim = 3 * this.hidden;
        float[] gi = new float[gateDim];
        float[] gh = new float[gateDim];

        for (int i = 0; i < gateDim; i++) {
            float sumI = this.gruBi[i];
            float[] wiRow = this.gruWi[i];
            for (int j = 0; j < wiRow.length; j++) {
                sumI += wiRow[j] * features[j];
            }
            gi[i] = sumI;

            float sumH = this.gruBh[i];
            float[] whRow = this.gruWh[i];
            for (int j = 0; j < whRow.length; j++) {
                sumH += whRow[j] * hiddenState[j];
            }
            gh[i] = sumH;
        }

        for (int i = 0; i < this.hidden; i++) {
            float r = sigmoid(gi[i] + gh[i]);
            float z = sigmoid(gi[this.hidden + i] + gh[this.hidden + i]);
            float n = (float) Math.tanh(gi[2 * this.hidden + i] + r * gh[2 * this.hidden + i]);
            hiddenState[i] = (1.0F - z) * n + z * hiddenState[i];
        }

        float[] output = new float[this.outW.length];
        for (int i = 0; i < this.outW.length; i++) {
            float sum = this.outB[i];
            float[] wRow = this.outW[i];
            for (int j = 0; j < wRow.length; j++) {
                sum += wRow[j] * hiddenState[j];
            }
            output[i] = sum;
        }
        return output;
    }

    public int sampleComponent(float[] output, float randomFloat) {
        float maxPi = output[1];
        for (int i = 1; i < this.mix; i++) {
            maxPi = Math.max(maxPi, output[1 + 6 * i]);
        }
        float sumExp = 0.0F;
        for (int i = 0; i < this.mix; i++) {
            sumExp += (float) Math.exp(output[1 + 6 * i] - maxPi);
        }
        float threshold = randomFloat * sumExp;
        for (int i = 0; i < this.mix; i++) {
            threshold -= (float) Math.exp(output[1 + 6 * i] - maxPi);
            if (threshold <= 0.0F) {
                return i;
            }
        }
        return this.mix - 1;
    }

    public float sampleError(float randomFloat) {
        if (this.errorQuantiles.length == 0) {
            return 0.0F;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, randomFloat)) * (this.errorQuantiles.length - 1);
        int index = (int) clamped;
        if (index >= this.errorQuantiles.length - 1) {
            return this.errorQuantiles[this.errorQuantiles.length - 1];
        }
        return this.errorQuantiles[index] + (this.errorQuantiles[index + 1] - this.errorQuantiles[index]) * (clamped - index);
    }

    public float sampleValue(float mu, float logSigma, boolean isYaw, float z, float scale) {
        float sigma = (float) Math.exp(Math.max(-4.0F, Math.min(1.5F, logSigma)));
        float raw = Math.max(-8.0F, Math.min(8.0F, mu + scale * sigma * z));
        float limit = isYaw ? this.limitYaw : this.limitPitch;
        return Math.max(-limit, Math.min(limit, (float) Math.sinh(raw)));
    }

    public static float sigmoid(float val) {
        return 1.0F / (1.0F + (float) Math.exp(-val));
    }

    public static float tanhClamp(float val) {
        return (float) Math.tanh(val) * CORRELATION_CLAMP;
    }

    public int getHidden() {
        return hidden;
    }

    public int getMix() {
        return mix;
    }

    public int getFreezeCut() {
        return freezeCut;
    }

    public String getName() {
        return name;
    }
}
