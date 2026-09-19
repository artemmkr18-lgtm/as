package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class InputEvent extends Event {
    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;
}
