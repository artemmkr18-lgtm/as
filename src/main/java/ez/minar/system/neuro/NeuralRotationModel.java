package ez.minar.system.neuro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Dependency-free temporal MLP trained on complete combat demonstration sessions.
 * Input: window of HISTORY feature frames (relative geometry, velocities, cooldown, movement state).
 * Output: Gaussian motor command (yaw/pitch acceleration), hit logit, attack decision logit,
 * and movement actions (forward, strafe, jump, sprint).
 */
public final class NeuralRotationModel {
    public static final int FEATURES = 22;
    public static final int HISTORY = 8;
    public static final int INPUTS = FEATURES * HISTORY;
    public static final int LABELS = 8;
    public static final int HIDDEN1 = 128;
    public static final int HIDDEN2 = 64;
    public static final int OUTPUTS = 10;
    public static final float MAX_ACCEL = 2.0F;
    public static final float LOG_SIGMA_MIN = -4.5F;
    public static final float LOG_SIGMA_MAX = -0.5F;

    public static final int FEATURE_VEL_YAW = 10;
    public static final int FEATURE_VEL_PITCH = 11;
    public static final int FEATURE_ACCEL_YAW = 12;
    public static final int FEATURE_ACCEL_PITCH = 13;

    private static final int FILE_MAGIC = 0x4E455552;
    private static final int FILE_VERSION = 14;
    private static final float SIGMA_LOSS_WEIGHT = 0.25F;
    private static final int BATCH_SIZE = 32;
    private static final float GRADIENT_CLIP = 5.0F;
    private static final float HIT_LOSS_WEIGHT = 0.5F;
    private static final float ATTACK_LOSS_WEIGHT = 0.8F;

    private final float[][] w1 = new float[HIDDEN1][INPUTS];
    private final float[] b1 = new float[HIDDEN1];
    private final float[][] w2 = new float[HIDDEN2][HIDDEN1];
    private final float[] b2 = new float[HIDDEN2];
    private final float[][] w3 = new float[OUTPUTS][HIDDEN2];
    private final float[] b3 = new float[OUTPUTS];

    private NeuralRotationModel() {
    }

    public static NeuralRotationModel train(List<TrainingSample> samples, long seed) {
        return train(samples, seed, () -> false);
    }

    public static NeuralRotationModel train(List<TrainingSample> samples, long seed, java.util.function.BooleanSupplier cancelCheck) {
        if (samples.size() < 32) {
            throw new IllegalArgumentException("Для обучения нужно минимум 32 семпла.");
        }

        List<TrainingSample> data = new ArrayList<>(samples.size());
        for (TrainingSample sample : samples) {
            data.add(new TrainingSample(sample.input().clone(), sample.output().clone()));
        }

        // Yaw and lateral strafe are symmetric: mirror every sample across the camera forward axis.
        // A left flick and strafe teaches the right flick and strafe.
        int originalSize = data.size();
        for (int i = 0; i < originalSize; i++) {
            TrainingSample source = data.get(i);
            float[] input = source.input().clone();
            float[] output = source.output().clone();
            for (int frame = 0; frame < HISTORY; frame++) {
                int base = frame * FEATURES;
                input[base] = -input[base]; // safeYawDelta
                input[base + 2] = -input[base + 2]; // centerYawDelta
                input[base + 6] = -input[base + 6]; // targetVelocityLateral (rel X)
                input[base + 8] = -input[base + 8]; // observerVelocityLateral (rel X)
                input[base + FEATURE_VEL_YAW] = -input[base + FEATURE_VEL_YAW];
                input[base + FEATURE_ACCEL_YAW] = -input[base + FEATURE_ACCEL_YAW];
            }
            output[0] = -output[0]; // accelYaw inverted
            output[5] = -output[5]; // strafe inverted
            data.add(new TrainingSample(input, output));
        }

        // Large-error states are rare in recordings but decide acquisition speed: oversample them.
        originalSize = data.size();
        for (int i = 0; i < originalSize; i++) {
            TrainingSample source = data.get(i);
            float error = lastFrameError(source.input());
            int extraCopies = error >= 60.0F ? 2 : (error >= 30.0F ? 1 : 0);
            for (int copy = 0; copy < extraCopies; copy++) {
                data.add(new TrainingSample(source.input().clone(), source.output().clone()));
            }
        }

        NeuralRotationModel model = new NeuralRotationModel();
        Random random = new Random(seed);
        model.initialize(random);

        AdamState adam = new AdamState();
        float learningRate = 0.0045F;
        int epochs = data.size() < 4_000 ? 75 : 55;
        int step = 0;

        for (int epoch = 0; epoch < epochs; epoch++) {
            if (cancelCheck.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException("Обучение отменено.");
            }
            Collections.shuffle(data, random);
            for (int start = 0; start < data.size(); start += BATCH_SIZE) {
                int end = Math.min(data.size(), start + BATCH_SIZE);
                float[][] gW1 = new float[HIDDEN1][INPUTS];
                float[] gB1 = new float[HIDDEN1];
                float[][] gW2 = new float[HIDDEN2][HIDDEN1];
                float[] gB2 = new float[HIDDEN2];
                float[][] gW3 = new float[OUTPUTS][HIDDEN2];
                float[] gB3 = new float[OUTPUTS];
                int batchCount = end - start;

                for (int index = start; index < end; index++) {
                    TrainingSample sample = data.get(index);
                    float[] h1 = new float[HIDDEN1];
                    float[] h2 = new float[HIDDEN2];
                    float[] output = model.forward(sample.input(), h1, h2);

                    float labelYaw = sample.output()[0];
                    float labelPitch = sample.output()[1];
                    float labelHit = sample.output()[2];
                    float labelAttack = sample.output().length > 3 ? sample.output()[3] : 0.0F;
                    float labelForward = sample.output().length > 4 ? sample.output()[4] : 0.0F;
                    float labelStrafe = sample.output().length > 5 ? sample.output()[5] : 0.0F;
                    float labelJump = sample.output().length > 6 ? sample.output()[6] : 0.0F;
                    float labelSprint = sample.output().length > 7 ? sample.output()[7] : 0.0F;

                    float sigmaYaw = (float) Math.exp(clampLogSigma(output[2]));
                    float sigmaPitch = (float) Math.exp(clampLogSigma(output[3]));

                    float errYaw = output[0] - labelYaw;
                    float errPitch = output[1] - labelPitch;

                    int base = INPUTS - FEATURES;
                    float sampleYawError = Math.abs(sample.input()[base]) * 180.0F;
                    float samplePitchError = Math.abs(sample.input()[base + 1]) * 90.0F;
                    float sampleError = (float) Math.hypot(sampleYawError, samplePitchError);
                    float errorWeight = 1.0F + 3.0F * Math.min(1.0F, sampleError / 45.0F);
                    if (labelHit < 0.5F && sampleError < 15.0F) {
                        errorWeight *= 1.75F;
                    }

                    float[] dOut = new float[OUTPUTS];
                    // 0: yaw command
                    dOut[0] = 2.0F * errYaw * errorWeight;
                    // 1: pitch command
                    dOut[1] = 2.0F * errPitch * errorWeight;

                    // 2-3: uncertainty sigma head
                    float gradSigmaYaw = (1.0F - (errYaw * errYaw) / (sigmaYaw * sigmaYaw)) * SIGMA_LOSS_WEIGHT;
                    float gradSigmaPitch = (1.0F - (errPitch * errPitch) / (sigmaPitch * sigmaPitch)) * SIGMA_LOSS_WEIGHT;
                    if ((output[2] <= LOG_SIGMA_MIN && gradSigmaYaw > 0.0F)
                            || (output[2] >= LOG_SIGMA_MAX && gradSigmaYaw < 0.0F)) {
                        gradSigmaYaw = 0.0F;
                    }
                    if ((output[3] <= LOG_SIGMA_MIN && gradSigmaPitch > 0.0F)
                            || (output[3] >= LOG_SIGMA_MAX && gradSigmaPitch < 0.0F)) {
                        gradSigmaPitch = 0.0F;
                    }
                    dOut[2] = gradSigmaYaw;
                    dOut[3] = gradSigmaPitch;

                    // 4: hit logit
                    dOut[4] = HIT_LOSS_WEIGHT * (sigmoid(output[4]) - labelHit);
                    // 5: attack decision logit
                    dOut[5] = ATTACK_LOSS_WEIGHT * (sigmoid(output[5]) - labelAttack);
                    // 6: movement forward
                    dOut[6] = 1.0F * (output[6] - labelForward);
                    // 7: movement strafe
                    dOut[7] = 1.0F * (output[7] - labelStrafe);
                    // 8: jump logit
                    dOut[8] = 0.5F * (sigmoid(output[8]) - labelJump);
                    // 9: sprint logit
                    dOut[9] = 0.5F * (sigmoid(output[9]) - labelSprint);

                    for (int i = 0; i < OUTPUTS; i++) {
                        dOut[i] = clamp(dOut[i] / batchCount, -GRADIENT_CLIP, GRADIENT_CLIP);
                    }

                    float[] dH2 = new float[HIDDEN2];
                    for (int i = 0; i < OUTPUTS; i++) {
                        gB3[i] += dOut[i];
                        for (int j = 0; j < HIDDEN2; j++) {
                            gW3[i][j] += dOut[i] * h2[j];
                            dH2[j] += dOut[i] * model.w3[i][j];
                        }
                    }
                    for (int i = 0; i < HIDDEN2; i++) {
                        dH2[i] *= reluDerivative(h2[i]);
                    }

                    float[] dH1 = new float[HIDDEN1];
                    for (int i = 0; i < HIDDEN2; i++) {
                        gB2[i] += dH2[i];
                        for (int j = 0; j < HIDDEN1; j++) {
                            gW2[i][j] += dH2[i] * h1[j];
                            dH1[j] += dH2[i] * model.w2[i][j];
                        }
                    }
                    for (int i = 0; i < HIDDEN1; i++) {
                        dH1[i] *= reluDerivative(h1[i]);
                    }

                    for (int i = 0; i < HIDDEN1; i++) {
                        gB1[i] += dH1[i];
                        for (int j = 0; j < INPUTS; j++) {
                            gW1[i][j] += dH1[i] * sample.input()[j];
                        }
                    }
                }

                step++;
                float beta1Power = (float) Math.pow(0.9D, step);
                float beta2Power = (float) Math.pow(0.999D, step);
                applyAdam(model.w3, gW3, adam.mW3, adam.vW3, step, beta1Power, beta2Power, learningRate, 0.0001F);
                applyAdam(model.b3, gB3, adam.mB3, adam.vB3, step, beta1Power, beta2Power, learningRate, 0.0F);
                applyAdam(model.w2, gW2, adam.mW2, adam.vW2, step, beta1Power, beta2Power, learningRate, 0.0001F);
                applyAdam(model.b2, gB2, adam.mB2, adam.vB2, step, beta1Power, beta2Power, learningRate, 0.0F);
                applyAdam(model.w1, gW1, adam.mW1, adam.vW1, step, beta1Power, beta2Power, learningRate, 0.0001F);
                applyAdam(model.b1, gB1, adam.mB1, adam.vB1, step, beta1Power, beta2Power, learningRate, 0.0F);
            }
            learningRate *= 0.993F;
        }

        return model;
    }

    public float[] predict(float[] input) {
        if (input.length != INPUTS) {
            throw new IllegalArgumentException("Expected " + INPUTS + " inputs, got " + input.length);
        }

        float[] output = forward(input, new float[HIDDEN1], new float[HIDDEN2]);
        output[0] = clamp(output[0], -MAX_ACCEL, MAX_ACCEL);
        output[1] = clamp(output[1], -MAX_ACCEL, MAX_ACCEL);
        output[2] = clampLogSigma(output[2]);
        output[3] = clampLogSigma(output[3]);
        output[6] = clamp(output[6], -1.0F, 1.0F); // forward
        output[7] = clamp(output[7], -1.0F, 1.0F); // strafe
        return output;
    }

    private float[] forward(float[] input, float[] h1, float[] h2) {
        for (int i = 0; i < HIDDEN1; i++) {
            float sum = b1[i];
            for (int j = 0; j < INPUTS; j++) {
                sum += w1[i][j] * input[j];
            }
            h1[i] = relu(sum);
        }

        for (int i = 0; i < HIDDEN2; i++) {
            float sum = b2[i];
            for (int j = 0; j < HIDDEN1; j++) {
                sum += w2[i][j] * h1[j];
            }
            h2[i] = relu(sum);
        }

        float[] output = new float[OUTPUTS];
        for (int i = 0; i < OUTPUTS; i++) {
            float sum = b3[i];
            for (int j = 0; j < HIDDEN2; j++) {
                sum += w3[i][j] * h2[j];
            }
            output[i] = sum;
        }
        return output;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt(FILE_MAGIC);
            output.writeInt(FILE_VERSION);
            output.writeInt(INPUTS);
            output.writeInt(HIDDEN1);
            output.writeInt(HIDDEN2);
            output.writeInt(OUTPUTS);
            writeMatrix(output, w1);
            writeVector(output, b1);
            writeMatrix(output, w2);
            writeVector(output, b2);
            writeMatrix(output, w3);
            writeVector(output, b3);
        }
    }

    public static NeuralRotationModel load(Path path) throws IOException {
        NeuralRotationModel model = new NeuralRotationModel();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != FILE_MAGIC || input.readInt() != FILE_VERSION
                    || input.readInt() != INPUTS || input.readInt() != HIDDEN1
                    || input.readInt() != HIDDEN2 || input.readInt() != OUTPUTS) {
                throw new IOException("Неподдерживаемый формат Neuro-модели.");
            }
            readMatrix(input, model.w1);
            readVector(input, model.b1);
            readMatrix(input, model.w2);
            readVector(input, model.b2);
            readMatrix(input, model.w3);
            readVector(input, model.b3);
        }
        return model;
    }

    public static float sigmoid(float value) {
        return (float) (1.0D / (1.0D + Math.exp(-value)));
    }

    private static float lastFrameError(float[] input) {
        int base = INPUTS - FEATURES;
        float yawError = Math.abs(input[base]) * 180.0F;
        float pitchError = Math.abs(input[base + 1]) * 90.0F;
        return (float) Math.hypot(yawError, pitchError);
    }

    public static float sigmaFromLog(float logSigma) {
        return (float) Math.exp(clampLogSigma(logSigma));
    }

    private static float clampLogSigma(float value) {
        return clamp(value, LOG_SIGMA_MIN, LOG_SIGMA_MAX);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void initialize(Random random) {
        float s1 = (float) Math.sqrt(2.0D / INPUTS);
        float s2 = (float) Math.sqrt(2.0D / HIDDEN1);
        float s3 = (float) Math.sqrt(1.0D / HIDDEN2);
        for (int i = 0; i < HIDDEN1; i++) {
            for (int j = 0; j < INPUTS; j++) {
                w1[i][j] = (float) (random.nextGaussian() * s1);
            }
        }
        for (int i = 0; i < HIDDEN2; i++) {
            for (int j = 0; j < HIDDEN1; j++) {
                w2[i][j] = (float) (random.nextGaussian() * s2);
            }
        }
        for (int i = 0; i < OUTPUTS; i++) {
            for (int j = 0; j < HIDDEN2; j++) {
                w3[i][j] = (float) (random.nextGaussian() * s3);
            }
        }
        b3[2] = -2.0F;
        b3[3] = -2.0F;
    }

    private static void applyAdam(float[][] weights, float[][] gradients, float[][] m, float[][] v,
                                  int step, float beta1Power, float beta2Power, float learningRate, float decay) {
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights[i].length; j++) {
                float gradient = gradients[i][j] + decay * weights[i][j];
                weights[i][j] = adam(weights[i][j], gradient, m[i], v[i], j, step, beta1Power, beta2Power, learningRate);
            }
        }
    }

    private static void applyAdam(float[] values, float[] gradients, float[] m, float[] v,
                                  int step, float beta1Power, float beta2Power, float learningRate, float decay) {
        for (int i = 0; i < values.length; i++) {
            float gradient = gradients[i] + decay * values[i];
            values[i] = adam(values[i], gradient, m, v, i, step, beta1Power, beta2Power, learningRate);
        }
    }

    private static float adam(float value, float gradient, float[] m, float[] v, int index,
                              int step, float beta1Power, float beta2Power, float learningRate) {
        m[index] = 0.9F * m[index] + 0.1F * gradient;
        v[index] = 0.999F * v[index] + 0.001F * gradient * gradient;
        float correctedM = m[index] / Math.max(1.0E-8F, 1.0F - beta1Power);
        float correctedV = v[index] / Math.max(1.0E-8F, 1.0F - beta2Power);
        return value - learningRate * correctedM / ((float) Math.sqrt(correctedV) + 1.0E-7F);
    }

    private static float relu(float value) {
        return Math.max(0.0F, value);
    }

    private static float reluDerivative(float activated) {
        return activated > 0.0F ? 1.0F : 0.0F;
    }

    private static void writeMatrix(DataOutputStream output, float[][] matrix) throws IOException {
        for (float[] row : matrix) {
            writeVector(output, row);
        }
    }

    private static void writeVector(DataOutputStream output, float[] vector) throws IOException {
        for (float value : vector) {
            output.writeFloat(value);
        }
    }

    private static void readMatrix(DataInputStream input, float[][] matrix) throws IOException {
        for (float[] row : matrix) {
            readVector(input, row);
        }
    }

    private static void readVector(DataInputStream input, float[] vector) throws IOException {
        for (int i = 0; i < vector.length; i++) {
            vector[i] = input.readFloat();
        }
    }

    private static final class AdamState {
        private final float[][] mW1 = new float[HIDDEN1][INPUTS];
        private final float[][] vW1 = new float[HIDDEN1][INPUTS];
        private final float[] mB1 = new float[HIDDEN1];
        private final float[] vB1 = new float[HIDDEN1];
        private final float[][] mW2 = new float[HIDDEN2][HIDDEN1];
        private final float[][] vW2 = new float[HIDDEN2][HIDDEN1];
        private final float[] mB2 = new float[HIDDEN2];
        private final float[] vB2 = new float[HIDDEN2];
        private final float[][] mW3 = new float[OUTPUTS][HIDDEN2];
        private final float[][] vW3 = new float[OUTPUTS][HIDDEN2];
        private final float[] mB3 = new float[OUTPUTS];
        private final float[] vB3 = new float[OUTPUTS];
    }

    public record TrainingSample(float[] input, float[] output) {
        public TrainingSample {
            if (input.length != INPUTS || output.length != LABELS) {
                throw new IllegalArgumentException("Invalid training sample shape: input=" + input.length + ", output=" + output.length);
            }
        }
    }
}
