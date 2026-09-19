package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.managers.FriendManager;
import net.minecraft.entity.player.PlayerEntity;

@NewFunction(name = "NoFriendDamage", desc = "Запрещает AttackAura бить друзей", category = Category.COMBAT)
public class NoFriendDamage extends Function {
    public static boolean shouldIgnore(PlayerEntity player) {
        NoFriendDamage function = FunctionManager.getFunction(NoFriendDamage.class);
        return function != null
                && function.isEnabled()
                && FriendManager.isFriend(player.getGameProfile().name());
    }
}
