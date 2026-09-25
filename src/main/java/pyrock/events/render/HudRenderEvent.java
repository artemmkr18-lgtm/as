package pyrock.events.render;

import net.minecraft.client.gui.DrawContext;

public class HudRenderEvent extends ez.minar.system.events.impl.HudRenderEvent {
    public HudRenderEvent(DrawContext context, float tickDelta) {
        super(context, tickDelta);
    }
}
