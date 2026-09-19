package ez.minar.system.events;

import lombok.Getter;
import lombok.Setter;

public abstract class Event {
    @Getter @Setter private boolean cancelled = false;
    public Event() {
        this.cancelled = false;
    }
}