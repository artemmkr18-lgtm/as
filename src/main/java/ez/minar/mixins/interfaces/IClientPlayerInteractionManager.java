/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.network.ClientPlayerInteractionManager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package ez.minar.mixins.interfaces;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ClientPlayerInteractionManager.class})
public interface IClientPlayerInteractionManager {
    @Accessor(value="blockBreakingCooldown")
    public void setBlockBreakingCooldown(int var1);
}
