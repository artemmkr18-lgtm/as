package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import net.minecraft.client.gui.DrawContext;

public class PostHudRenderEvent extends Event {
    private final DrawContext context;
    private final float tickDelta;

    public PostHudRenderEvent(DrawContext context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
    }

    public DrawContext getContext() {
        return context;
    }

    public float getTickDelta() {
        return tickDelta;
    }
}
