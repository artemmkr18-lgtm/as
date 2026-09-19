package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.math.Vec3d;

@Getter
@AllArgsConstructor
public class EventOnMovePost extends Event {
    private final float speed;
    private final Vec3d movementInput;
}
