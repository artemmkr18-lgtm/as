package ez.minar.system.features.combat;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.entity.LivingEntity;

@NewFunction(name = "TriggerBot", desc = "Бьёт сущностей при наводке", category = Category.COMBAT)
public class TriggerBot extends Function {

    private final BooleanSetting onlyCrits = new BooleanSetting("Только криты", true);
    
    private long lastAttackTime = 0;

    public TriggerBot() {
        addSettings(onlyCrits);
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        HitResult hitResult = mc.crosshairTarget;

        if (System.currentTimeMillis() - lastAttackTime < 450) return;

        if (hitResult instanceof EntityHitResult entityHitResult) {

            if (!(entityHitResult.getEntity() instanceof PlayerEntity)) return;

            float cooled = mc.player.getAttackCooldownProgress(1.5f);

            if (cooled < 0.92f)
                return;

            if (onlyCrits.isEnabled() && mc.player.fallDistance < 0.2f) return;

            boolean sprinting = mc.player.isSprinting();

            if (sprinting) {
                mc.player.setSprinting(false);
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }

            lastAttackTime = System.currentTimeMillis();
            mc.interactionManager.attackEntity(mc.player, entityHitResult.getEntity());
            mc.player.swingHand(Hand.MAIN_HAND);

            if (sprinting) {
                mc.player.setSprinting(true);
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
        }
    }

    public boolean isDroppingSprint() {
        return false;
    }

    public LivingEntity getTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof PlayerEntity player) {
            return player;
        }
        return null;
    }
}
