package ez.minar.mixins.render;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Shadow private net.minecraft.client.render.BuiltChunkStorage chunks;

    @Inject(method = "scheduleBlockRenders(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void onScheduleBlockRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        if (this.chunks == null) ci.cancel();
    }

    @Inject(method = "scheduleChunkRender", at = @At("HEAD"), cancellable = true)
    private void onScheduleChunkRender(int x, int y, int z, CallbackInfo ci) {
        if (this.chunks == null) ci.cancel();
    }
}
