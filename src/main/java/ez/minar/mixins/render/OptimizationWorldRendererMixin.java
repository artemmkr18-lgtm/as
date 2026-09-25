package ez.minar.mixins.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import ez.minar.system.features.render.Optimization;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class OptimizationWorldRendererMixin {

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void minar$skipWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (Optimization.shouldSkipWeatherStatic()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void minar$skipClouds(FrameGraphBuilder frameGraphBuilder, CloudRenderMode cloudRenderMode, Vec3d cameraPos, long time, float tickDelta, int renderDistance, float skyAngle, CallbackInfo ci) {
        if (Optimization.shouldSkipCloudsStatic()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void minar$skipSky(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (Optimization.shouldSkipSkyStatic()) {
            ci.cancel();
        }
    }

    @Inject(method = "fillBlockEntityRenderStates", at = @At("TAIL"))
    private void minar$cullFarBlockEntities(Camera camera, float tickDelta, WorldRenderState worldRenderState, CallbackInfo ci) {
        if (camera == null || worldRenderState == null || worldRenderState.blockEntityRenderStates == null) return;
        double maxDistanceSq = Optimization.getBlockEntityMaxDistanceSqStatic();
        if (maxDistanceSq == Double.MAX_VALUE) return;

        Vec3d cameraPos = camera.getCameraPos();
        if (cameraPos == null) return;

        try {
            worldRenderState.blockEntityRenderStates.removeIf(state -> {
                if (state == null || state.pos == null) return false;
                double dx = state.pos.getX() + 0.5 - cameraPos.x;
                double dy = state.pos.getY() + 0.5 - cameraPos.y;
                double dz = state.pos.getZ() + 0.5 - cameraPos.z;
                return dx * dx + dy * dy + dz * dz > maxDistanceSq;
            });
        } catch (Throwable ignored) {
        }
    }
}
