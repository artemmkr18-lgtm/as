package ez.minar.mixins.input;

import ez.minar.mixins.interfaces.IInput;
import ez.minar.system.events.EventBus;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.managers.RotationManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardMovementInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void correctSilentMovement(CallbackInfo ci) {
        IInput accessor = (IInput) (Input) (Object) this;

        if (!RotationManager.shouldCorrectInputSilently()) return;

        Vec2f movementVector = accessor.getMovementVector();
        PlayerInput playerInput = accessor.getPlayerInput();
        Vec2f corrected = RotationManager.correctMovementInput(movementVector.y, movementVector.x);

        accessor.setMovementVector(corrected.normalize());
        accessor.setPlayerInput(new PlayerInput(
                corrected.y > 0.0F,
                corrected.y < 0.0F,
                corrected.x > 0.0F,
                corrected.x < 0.0F,
                playerInput.jump(),
                playerInput.sneak(),
                playerInput.sprint()
        ));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onInput(CallbackInfo ci) {
        IInput accessor = (IInput) (Input) (Object) this;
        Vec2f movementVector = accessor.getMovementVector();
        PlayerInput playerInput = accessor.getPlayerInput();
        InputEvent event = new InputEvent(
                movementVector.y,
                movementVector.x,
                playerInput.jump(),
                playerInput.sneak(),
                playerInput.sprint()
        );

        EventBus.post(event);

        accessor.setMovementVector(new Vec2f(event.getStrafe(), event.getForward()));
        accessor.setPlayerInput(new PlayerInput(
                event.getForward() > 0.0F,
                event.getForward() < 0.0F,
                event.getStrafe() > 0.0F,
                event.getStrafe() < 0.0F,
                event.isJump(),
                event.isSneak(),
                event.isSprint()
        ));
    }
}
