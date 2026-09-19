package ez.minar.system.features.player;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.utils.helpers.MoveUtil;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

@NewFunction(name = "SwapSetting", desc = "Общий режим свапов для всех сваповых модулей", category = Category.PLAYER)
public class SwapSetting extends Function {
    private final ModeSetting swapMode = new ModeSetting("Режим свапов", "Legit", "Packet", "ReallyWorld");

    public SwapSetting() {
        addSettings(swapMode);
        swapMode.runnable(() -> {
            ElytraHelper helper = FunctionManager.getFunction(ElytraHelper.class);
            if (helper != null) {
                helper.updateMatrixBypassVisibility();
            }
        });
    }

    public boolean isPacket() {
        return swapMode.isEnabled("Packet");
    }

    public boolean isLegit() {
        return swapMode.isEnabled("Legit");
    }

    public boolean isReallyWorld() {
        return swapMode.isEnabled("ReallyWorld");
    }

    public static boolean isPacketMode() {
        SwapSetting setting = FunctionManager.getFunction(SwapSetting.class);
        return setting != null && setting.isPacket();
    }

    public static boolean isLegitMode() {
        SwapSetting setting = FunctionManager.getFunction(SwapSetting.class);
        return setting == null || setting.isLegit();
    }

    public static boolean isReallyWorldMode() {
        SwapSetting setting = FunctionManager.getFunction(SwapSetting.class);
        return setting != null && setting.isReallyWorld();
    }

    /** Legit или ReallyWorld — свап с остановкой движения (не пакетный). */
    public static boolean isSlowSwapMode() {
        return !isPacketMode();
    }

    /** Редко +1 тик — для легита, без замедления свапа. */
    private static int microJitter() {
        return ThreadLocalRandom.current().nextInt(8) == 0 ? 1 : 0;
    }

    public static int nextStopDelayTicks() {
        return isReallyWorldMode() ? microJitter() : 0;
    }

    public static int nextClickDelayTicks() {
        return isReallyWorldMode() ? microJitter() : 0;
    }

    public static int nextResumeDelayTicks() {
        return isReallyWorldMode() ? microJitter() : 0;
    }

    public static int nextPieceDelayTicks() {
        return isReallyWorldMode() ? microJitter() : 0;
    }

    /**
     * ReallyWorld: достаточно сбросить инпут (не ждём полной остановки по скорости).
     * Клики идут по 1 за тик — быстро, но не пачкой в один пакет.
     */
    public static boolean isReadyToSwap() {
        if (!isReallyWorldMode()) {
            return true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && !MoveUtil.isMoving();
    }
}
