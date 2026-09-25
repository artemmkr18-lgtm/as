package pyrock.events.render;

import net.minecraft.client.gui.DrawContext;

public class PostHudLayerRenderEvent extends ez.minar.system.events.impl.PostHudLayerRenderEvent {
    public PostHudLayerRenderEvent(DrawContext context, float tickDelta, int count, boolean editing) {
        super(context, tickDelta, count, editing);
    }
}
