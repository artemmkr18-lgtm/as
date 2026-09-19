package ez.minar.system.features.misc;

/**
 * Runtime switch used by mixins inside Figura. Figura's classes and mixins are
 * still initialized by Fabric, but its ticking and network startup are blocked
 * while Cosmetics is disabled.
 */
public final class FiguraRuntimeGate {
    private static volatile boolean enabled;

    private FiguraRuntimeGate() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        FiguraRuntimeGate.enabled = enabled;
        try {
            org.figuramc.figura.avatar.AvatarManager.panic = !enabled;
        } catch (Throwable ignored) {
        }
    }
}
