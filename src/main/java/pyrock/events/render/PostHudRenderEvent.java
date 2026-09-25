package pyrock.events.render;

import net.minecraft.client.gui.DrawContext;

public class PostHudRenderEvent extends ez.minar.system.events.impl.PostHudRenderEvent {
    public PostHudRenderEvent(DrawContext context, float tickDelta) {
        super(context, tickDelta);
    }
}
