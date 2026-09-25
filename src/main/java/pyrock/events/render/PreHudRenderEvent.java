package pyrock.events.render;

import net.minecraft.client.gui.DrawContext;

public class PreHudRenderEvent extends ez.minar.system.events.impl.PreHudRenderEvent {
    public PreHudRenderEvent(DrawContext context, float tickDelta) {
        super(context, tickDelta);
    }
}
