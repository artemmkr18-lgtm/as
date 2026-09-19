package ez.minar.utils.helpers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.movement.Sprint;

public class InventoryUtil {

    public static final InventoryUtil instance = new InventoryUtil();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static InventoryUtil getInstance() {
        return instance;
    }

    public static void swapWithBypassGrim(Runnable runnable) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        try {
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                Sprint sprint = FunctionManager.getFunction(Sprint.class);
                if (sprint == null || !sprint.isEnabled()) mc.options.sprintKey.setPressed(false);
            }
            runnable.run();
            NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
