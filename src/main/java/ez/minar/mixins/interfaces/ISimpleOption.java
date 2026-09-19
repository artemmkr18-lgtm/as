/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.option.SimpleOption
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package ez.minar.mixins.interfaces;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={SimpleOption.class})
public interface ISimpleOption {
    @Accessor(value="value")
    public void minar$setValue(Object var1);

    @Accessor(value="value")
    public Object minar$getValue();
}
