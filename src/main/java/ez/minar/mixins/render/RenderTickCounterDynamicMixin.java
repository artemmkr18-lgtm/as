package ez.minar.mixins.render;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import ez.minar.system.managers.TimerManager;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterDynamicMixin {
    @Shadow private float dynamicDeltaTicks;
    @Shadow private float tickProgress;
    @Shadow private long lastTimeMillis;
    @Shadow private float tickTime;
    @Shadow private FloatUnaryOperator targetMillisPerTick;

    @Inject(method = "beginRenderTick(J)I", at = @At("HEAD"), cancellable = true)
    private void beginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        float timer = TimerManager.getTimer();
        if (timer == 1.0F) {
            return;
        }

        this.dynamicDeltaTicks = (float) (timeMillis - this.lastTimeMillis) / this.targetMillisPerTick.apply(this.tickTime) * timer;
        this.lastTimeMillis = timeMillis;
        this.tickProgress += this.dynamicDeltaTicks;
        int ticks = (int) this.tickProgress;
        this.tickProgress -= ticks;
        cir.setReturnValue(ticks);
    }
}
