package ez.minar.mixins.render;

import ez.minar.system.features.misc.FreeCam;
import ez.minar.system.features.render.SmoothCamera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void minar$freeCamRotation(Args args) {
        FreeCam freeCam = FreeCam.getInstance();
        if (freeCam != null && freeCam.isEnabled()) {
            float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
            args.set(0, freeCam.getCameraYaw(tickDelta));
            args.set(1, freeCam.getCameraPitch(tickDelta));
            thirdPerson = true;
        }
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void minar$freeCamPosition(Args args) {
        FreeCam freeCam = FreeCam.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (freeCam != null && freeCam.isEnabled()) {
            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            args.set(0, freeCam.getCameraX(tickDelta));
            args.set(1, freeCam.getCameraY(tickDelta));
            args.set(2, freeCam.getCameraZ(tickDelta));
            return;
        }


    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void minar$smoothCameraRotation(Args args) {
        SmoothCamera smoothCamera = SmoothCamera.getInstance();
        if (smoothCamera == null || freeCamActive() || !smoothCamera.shouldApply()) return;
        float[] smoothed = smoothCamera.smoothRotation(args.get(0), args.get(1));
        args.set(0, smoothed[0]);
        args.set(1, smoothed[1]);
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void minar$smoothCameraPosition(Args args) {
        SmoothCamera smoothCamera = SmoothCamera.getInstance();
        if (smoothCamera == null || freeCamActive() || !smoothCamera.shouldApply()) return;
        Vec3d smoothed = smoothCamera.smoothPosition(new Vec3d(args.get(0), args.get(1), args.get(2)));
        args.set(0, smoothed.x);
        args.set(1, smoothed.y);
        args.set(2, smoothed.z);
    }

    private static boolean freeCamActive() {
        FreeCam freeCam = FreeCam.getInstance();
        return freeCam != null && freeCam.isEnabled();
    }
}
