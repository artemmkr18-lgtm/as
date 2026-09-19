/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.screen.slot.Slot
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package ez.minar.mixins.interfaces;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={HandledScreen.class})
public interface IHandledScreen {
    @Accessor(value="focusedSlot")
    public Slot getFocusedSlot();

    @Accessor(value="focusedSlot")
    public void setFocusedSlot(Slot slot);
}
