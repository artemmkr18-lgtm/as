/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.session.Session
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package ez.minar.mixins.interfaces;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={MinecraftClient.class})
public interface IMinecraftClient {
    @Accessor(value="itemUseCooldown")
    public void setItemUseCooldown(int var1);

    @Invoker(value="doAttack")
    public boolean invokeDoAttack();

    @Invoker(value="doItemUse")
    public void invokeDoItemUse();

    @Mutable
    @Accessor(value="session")
    public void setSession(Session var1);
}
