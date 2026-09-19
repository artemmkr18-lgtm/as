package ez.minar.system.events.impl;

import ez.minar.system.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.input.KeyInput;

@Getter
@AllArgsConstructor
public class KeyEvent extends Event {
    int action;
    KeyInput input;
}
