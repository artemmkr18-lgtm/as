package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class Render2DEvent extends Event {
    @Getter private final RenderTickCounter tickCounter;
    @Getter private final DrawContext context;

    public Render2DEvent(RenderTickCounter tickCounter) {
        this(tickCounter, null);
    }

    public Render2DEvent(RenderTickCounter tickCounter, DrawContext context) {
        this.tickCounter = tickCounter;
        this.context = context;
    }
}
