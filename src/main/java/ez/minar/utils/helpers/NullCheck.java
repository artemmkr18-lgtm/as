package ez.minar.utils.helpers;

import net.minecraft.client.MinecraftClient;

public class NullCheck {
    MinecraftClient mc = MinecraftClient.getInstance();

    public boolean player() {
        return mc.player == null;
    }

    public boolean all() {
        return mc.player == null || mc.world == null;
    }

    public boolean world() {
        return mc.world == null;
    }
}
