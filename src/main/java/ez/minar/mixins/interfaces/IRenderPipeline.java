package ez.minar.mixins.interfaces;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipeline.class)
public interface IRenderPipeline {
    @Mutable
    @Accessor("writeDepth")
    void minar$setWriteDepth(boolean writeDepth);
}
