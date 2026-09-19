package ez.minar.system.menu.cosmetics;

import net.minecraft.client.render.entity.state.EntityRenderState;
import org.figuramc.figura.avatar.Avatar;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds a cached preview avatar to the render-state created by UIHelper.
 */
public final class FiguraPreviewAvatarContext {
    private static final ThreadLocal<Avatar> CURRENT = new ThreadLocal<>();
    private static final Map<EntityRenderState, Avatar> BY_STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FiguraPreviewAvatarContext() {
    }

    public static void begin(Avatar avatar) {
        CURRENT.set(avatar);
    }

    public static void end() {
        CURRENT.remove();
    }

    public static void capture(EntityRenderState state) {
        Avatar avatar = CURRENT.get();
        if (avatar != null) {
            BY_STATE.put(state, avatar);
        }
    }

    public static Avatar get(EntityRenderState state) {
        return BY_STATE.get(state);
    }

    public static void clear() {
        CURRENT.remove();
        BY_STATE.clear();
    }
}
