package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import net.minecraft.client.gui.DrawContext;

public class PostHudLayerRenderEvent extends Event {
    private final DrawContext context;
    private final float tickDelta;
    private final int count;
    private final boolean editing;

    public PostHudLayerRenderEvent(DrawContext context, float tickDelta, int count, boolean editing) {
        this.context = context;
        this.tickDelta = tickDelta;
        this.count = count;
        this.editing = editing;
    }

    public DrawContext getContext() {
        return context;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public int getCount() {
        return count;
    }

    public boolean isEditing() {
        return editing;
    }
}
