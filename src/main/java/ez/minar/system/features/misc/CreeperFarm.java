package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.system.settings.impl.TextSetting;
import ez.minar.utils.helpers.FarmNavigator;
import ez.minar.utils.helpers.FarmTimer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

@NewFunction(name = "CreeperFarm", desc = "Автоматический фарм криперов", category = Category.MISC)
public class CreeperFarm extends Function {
    public final TextSetting pos1 = new TextSetting("Pos1", "0 64 0", 32);
    public final TextSetting pos2 = new TextSetting("Pos2", "0 64 0", 32);
    public final NumberSetting attackRange = new NumberSetting("Дистанция атаки", 3.5, 2.0, 6.0, 0.5);
    public final NumberSetting retreatDistance = new NumberSetting("Отступление", 15.0, 5.0, 30.0, 1.0);
    public final BooleanSetting lootXp = new BooleanSetting("Подбирать опыт", true);

    private final FarmTimer attackTimer = new FarmTimer();

    private BlockPos[] patrolCorners;
    private int patrolIndex;

    public CreeperFarm() {
        addSettings(pos1, pos2, attackRange, retreatDistance, lootXp);
    }

    @Override
    public void onEnable() {
        patrolIndex = 0;
        rebuildPatrol();
        attackTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        FarmNavigator.stop();
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(UpdateEvent e) {
        if (nullCheck.all() || mc.interactionManager == null) {
            return;
        }

        BlockPos corner1 = parsePos(pos1);
        BlockPos corner2 = parsePos(pos2);
        if (corner1 == null || corner2 == null) {
            return;
        }

        if (patrolCorners == null) {
            rebuildPatrol();
        }

        CreeperEntity ignited = findIgnitedCreeper();
        if (ignited != null) {
            Vec3d away = mc.player.getEntityPos().subtract(ignited.getEntityPos()).normalize();
            Vec3d retreat = mc.player.getEntityPos().add(away.multiply(retreatDistance.getValue()));
            navigate(retreat);
            return;
        }

        ItemEntity loot = findLoot(corner1, corner2);
        if (loot != null) {
            navigate(loot.getEntityPos());
            return;
        }

        CreeperEntity target = findNearestCreeper(corner1, corner2);
        if (target != null) {
            double distance = mc.player.distanceTo(target);
            if (distance <= attackRange.getValue()) {
                FarmNavigator.stop();
                if (attackTimer.tryReset(500L)) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else {
                navigate(target.getEntityPos());
            }
            return;
        }

        patrol();
    }

    private void patrol() {
        if (patrolCorners == null || patrolCorners.length == 0) {
            return;
        }

        BlockPos target = patrolCorners[patrolIndex];
        double distSq = mc.player.squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (distSq < 2.0) {
            patrolIndex = (patrolIndex + 1) % patrolCorners.length;
            target = patrolCorners[patrolIndex];
        }

        navigate(new Vec3d(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
    }

    private void navigate(Vec3d target) {
        if (target == null) {
            FarmNavigator.stop();
            return;
        }
        FarmNavigator.goTo(target, 1.6);
    }

    private void rebuildPatrol() {
        BlockPos corner1 = parsePos(pos1);
        BlockPos corner2 = parsePos(pos2);
        if (corner1 == null || corner2 == null || mc.player == null) {
            patrolCorners = null;
            return;
        }

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        int y = mc.player.getBlockY();

        patrolCorners = new BlockPos[]{
                new BlockPos(minX, y, minZ),
                new BlockPos(maxX, y, minZ),
                new BlockPos(maxX, y, maxZ),
                new BlockPos(minX, y, maxZ)
        };
    }

    private CreeperEntity findIgnitedCreeper() {
        Box box = mc.player.getBoundingBox().expand(retreatDistance.getValue());
        List<CreeperEntity> creepers = mc.world.getEntitiesByClass(CreeperEntity.class, box, entity -> entity.isAlive());
        for (CreeperEntity creeper : creepers) {
            if (creeper.getFuseSpeed() > 0 || creeper.isIgnited()) {
                return creeper;
            }
        }
        return null;
    }

    private CreeperEntity findNearestCreeper(BlockPos corner1, BlockPos corner2) {
        Box box = Box.enclosing(corner1, corner2).expand(1.0);
        return mc.world.getEntitiesByClass(CreeperEntity.class, box, CreeperEntity::isAlive).stream()
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);
    }

    private ItemEntity findLoot(BlockPos corner1, BlockPos corner2) {
        ItemEntity gunpowder = findItemEntity(corner1, corner2, Items.GUNPOWDER);
        if (gunpowder != null) {
            return gunpowder;
        }
        if (!lootXp.isEnabled()) {
            return null;
        }
        ItemEntity xp = findItemEntity(corner1, corner2, Items.EXPERIENCE_BOTTLE);
        if (xp == null) {
            return null;
        }

        List<CreeperEntity> nearby = mc.world.getEntitiesByClass(
                CreeperEntity.class,
                xp.getBoundingBox().expand(4.0),
                CreeperEntity::isAlive
        );
        return nearby.isEmpty() ? xp : null;
    }

    private ItemEntity findItemEntity(BlockPos corner1, BlockPos corner2, Item item) {
        Box box = Box.enclosing(corner1, corner2).expand(1.0);
        return mc.world.getEntitiesByClass(ItemEntity.class, box, entity -> entity.isAlive() && entity.getStack().isOf(item)).stream()
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);
    }

    private BlockPos parsePos(TextSetting setting) {
        String[] parts = setting.getValue().trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
