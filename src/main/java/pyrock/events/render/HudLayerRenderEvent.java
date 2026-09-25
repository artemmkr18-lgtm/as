package pyrock.events.render;

import net.minecraft.client.gui.DrawContext;

public class HudLayerRenderEvent extends ez.minar.system.events.impl.HudLayerRenderEvent {
    public HudLayerRenderEvent(DrawContext context, float tickDelta, int count, boolean editing) {
        super(context, tickDelta, count, editing);
    }
}
