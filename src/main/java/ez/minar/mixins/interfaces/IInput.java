/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.input.Input
 *  net.minecraft.util.PlayerInput
 *  net.minecraft.util.math.Vec2f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package ez.minar.mixins.interfaces;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={Input.class})
public interface IInput {
    @Accessor(value="playerInput")
    public PlayerInput getPlayerInput();

    @Accessor(value="playerInput")
    public void setPlayerInput(PlayerInput var1);

    @Accessor(value="movementVector")
    public Vec2f getMovementVector();

    @Accessor(value="movementVector")
    public void setMovementVector(Vec2f var1);
}
