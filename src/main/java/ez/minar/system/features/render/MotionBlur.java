package ez.minar.system.features.render;

import ez.minar.mixins.interfaces.IGameRenderer;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.helpers.MonitorInfoProvider;
import ez.minar.utils.render.pipeline.WorldMotionBlurPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

@NewFunction(name = "MotionBlur", desc = "Реалистичное размытие движения камеры и мира", category = Category.RENDER)
public class MotionBlur extends Function {
    public static MotionBlur Instance;

    public final NumberSetting strength = new NumberSetting("Сила", 0.8, 0.1, 2.0, 0.05);
    public final ModeSetting quality = new ModeSetting("Качество", "Высокое", "Низкое", "Среднее", "Высокое", "Ультра");
    public final ModeSetting algorithm = new ModeSetting("Алгоритм", "Centered", "Centered", "Backwards");
    public final ModeSetting motionType = new ModeSetting("Тип движения", "Все", "Все", "Только поворот", "Только перемещение");
    public final BooleanSetting useRRC = new BooleanSetting("Адаптация к монитору", true);

    private final Matrix4f prevProj = new Matrix4f();
    private final Matrix4f prevViewRot = new Matrix4f();
    private Vec3d prevCamPos = new Vec3d(0, 0, 0);
    private float prevFov = 70.0f;
    private boolean hasPrevFrame = false;

    private long lastFrameTimeNs = 0L;
    private float currentFps = 60.0f;

    public MotionBlur() {
        Instance = this;
        addSettings(strength, quality, algorithm, motionType, useRRC);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetHistory();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetHistory();
    }

    public void resetHistory() {
        hasPrevFrame = false;
        lastFrameTimeNs = 0L;
    }

    public static void renderWorld(WorldRenderContext context) {
        MotionBlur motionBlur = FunctionManager.getFunction(MotionBlur.class);
        if (motionBlur == null || !motionBlur.isEnabled()) {
            return;
        }

        motionBlur.render(context);
    }

    private void render(WorldRenderContext context) {
        if (mc.world == null || mc.player == null || mc.gameRenderer == null) {
            hasPrevFrame = false;
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) {
            hasPrevFrame = false;
            return;
        }

        float tickProgress = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d camPos = camera.getCameraPos();
        float fov = ((IGameRenderer) mc.gameRenderer).minar$getFov(camera, tickProgress, true);
        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(fov);
        if (proj == null) {
            hasPrevFrame = false;
            return;
        }

        Matrix4f invProj = new Matrix4f(proj).invert();
        Matrix4f viewRot = new Matrix4f().rotation(camera.getRotation());

        if (!hasPrevFrame || camPos.distanceTo(prevCamPos) > 10.0) {
            prevProj.set(proj);
            prevViewRot.set(viewRot);
            prevCamPos = camPos;
            prevFov = fov;
            hasPrevFrame = true;
            lastFrameTimeNs = System.nanoTime();
            return;
        }

        long now = System.nanoTime();
        if (lastFrameTimeNs > 0L) {
            float dt = (now - lastFrameTimeNs) / 1_000_000_000.0f;
            if (dt > 0.0001f && dt < 0.5f) {
                float instantFps = 1.0f / dt;
                currentFps = currentFps * 0.85f + instantFps * 0.15f;
            }
        }
        lastFrameTimeNs = now;

        MonitorInfoProvider.updateDisplayInfo();
        int refreshRate = MonitorInfoProvider.getRefreshRate();

        float baseStrength = (float) strength.getValue();
        float scaledStrength = baseStrength;
        if (useRRC.isEnabled()) {
            float fpsOverRefresh = (refreshRate > 0) ? (currentFps / (float) refreshRate) : 1.0f;
            if (fpsOverRefresh < 1.0f) fpsOverRefresh = 1.0f;
            scaledStrength = baseStrength * fpsOverRefresh;
        }

        int qualityIndex = switch (quality.getActiveMode()) {
            case "Низкое" -> 0;
            case "Среднее" -> 1;
            case "Высокое" -> 2;
            case "Ультра" -> 3;
            default -> 2;
        };

        Matrix4f effectivePrevRot = viewRot;
        Vec3d effectivePrevPos = camPos;

        if (motionType.isEnabled("Все")) {
            effectivePrevRot = prevViewRot;
            effectivePrevPos = prevCamPos;
        } else if (motionType.isEnabled("Только поворот")) {
            effectivePrevRot = prevViewRot;
            effectivePrevPos = camPos;
        } else if (motionType.isEnabled("Только перемещение")) {
            effectivePrevRot = viewRot;
            effectivePrevPos = prevCamPos;
        }

        // Fast early-out if camera is stationary / hasn't rotated
        boolean rotEqual = viewRot.equals(effectivePrevRot, 1e-5f);
        boolean posEqual = camPos.squaredDistanceTo(effectivePrevPos) < 1e-6;
        if (rotEqual && posEqual && Math.abs(fov - prevFov) < 1e-4f) {
            prevProj.set(proj);
            prevViewRot.set(viewRot);
            prevCamPos = camPos;
            prevFov = fov;
            return;
        }

        int sampleCount = switch (qualityIndex) {
            case 0 -> 4;
            case 1 -> 8;
            case 2 -> 12;
            case 3 -> 16;
            default -> 8;
        };

        if (currentFps < 40.0f) {
            sampleCount = Math.max(4, sampleCount / 2);
        } else if (currentFps < 60.0f) {
            sampleCount = Math.max(6, (int) (sampleCount * 0.75f));
        } else {
            sampleCount = Math.min(16, sampleCount);
        }

        float algoValue = algorithm.isEnabled("Centered") ? 1.0f : 0.0f;
        float maxVelocity = 0.25f;

        WorldMotionBlurPipeline.render(
                proj,
                invProj,
                viewRot,
                prevProj,
                effectivePrevRot,
                camPos,
                effectivePrevPos,
                fov,
                prevFov,
                scaledStrength,
                sampleCount,
                algoValue,
                maxVelocity
        );

        prevProj.set(proj);
        prevViewRot.set(viewRot);
        prevCamPos = camPos;
        prevFov = fov;
    }
}
