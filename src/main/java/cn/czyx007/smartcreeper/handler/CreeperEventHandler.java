package cn.czyx007.smartcreeper.handler;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.SmartCreeper;
import cn.czyx007.smartcreeper.goal.EndermanCarryContainerGoal;
import cn.czyx007.smartcreeper.goal.SkeletonShootContainerGoal;
import cn.czyx007.smartcreeper.goal.SmartExplodeGoal;
import cn.czyx007.smartcreeper.goal.ZombieBreakContainerGoal;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public class CreeperEventHandler {
    private static final Set<SmartExplodeGoal> activeGoals = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
            modifyCreeperAI(creeper);
        } else if (event.getEntity() instanceof AbstractSkeleton skeleton) {
            modifySkeletonAI(skeleton);
        } else if (event.getEntity() instanceof EnderMan enderman) {
            modifyEndermanAI(enderman);
        } else if (event.getEntity() instanceof Zombie zombie) {
            modifyZombieAI(zombie);
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow &&
                event.getRayTraceResult().getType() == HitResult.Type.BLOCK) {

            // 检查是否是骷髅射向容器的箭
            if (arrow.getPersistentData().contains("smartcreeper_target")) {
                String target = arrow.getPersistentData().getString("smartcreeper_target");
                if ("container".equals(target)) {
                    BlockHitResult blockHit = (BlockHitResult) event.getRayTraceResult();
                    BlockPos hitPos = blockHit.getBlockPos();

                    // 检查击中的是否为目标容器
                    if (ContainerTargetUtils.isTargetBlock(arrow.level(), hitPos)) {
                        boolean containerDestroyed = ContainerHitHandler.recordHit(arrow.level(), hitPos);

                        // 只有容器被破坏时才移除箭矢
                        if (containerDestroyed) {
                            arrow.discard();
                        }
                    } else {
                        // 不是目标容器时正常处理箭矢
                        arrow.setNoPhysics(false);
                        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        if (explosion.getDirectSourceEntity() instanceof Creeper creeper) {
            for (SmartExplodeGoal goal : activeGoals) {
                if (goal.getCreeper() == creeper && goal.getTargetPos() != null) {
                    double explosionRadius = explosion.radius();
                    double distSq = goal.getTargetPos().distToCenterSqr(explosion.center());
                    if (distSq <= explosionRadius*explosionRadius) {
                        event.getLevel().destroyBlock(goal.getTargetPos(), false);
                        activeGoals.remove(goal);
                    }
                    break;
                }
            }
        }
    }

    private void modifyCreeperAI(Creeper creeper) {
        try {
            Iterator<WrappedGoal> iterator = creeper.goalSelector.getAvailableGoals().iterator();

            while (iterator.hasNext()) {
                WrappedGoal wrappedGoal = iterator.next();

                // 添加空值检查
                if (wrappedGoal == null) {
                    SmartCreeper.LOGGER.warn("Found null WrappedGoal in creeper, skipping...");
                    continue;
                }

                Goal goal = wrappedGoal.getGoal();

                // 再次检查goal是否为null
                if (goal == null) {
                    SmartCreeper.LOGGER.warn("Found null goal in WrappedGoal, skipping...");
                    continue;
                }

                // 移除原版爆炸AI（SwellGoal）
                if (goal.getClass().getSimpleName().contains("SwellGoal") ||
                        goal.getClass().getSimpleName().contains("CreeperSwellGoal")) {
                    iterator.remove();
                    continue;
                }

                // 如果配置启用，移除对猫的恐惧AI
                if (Config.FEARLESS_OF_CATS.get() && goal instanceof AvoidEntityGoal<?> avoidGoal) {
                    if (shouldRemoveCatFearAI(avoidGoal)) {
                        iterator.remove();
                    }
                }
            }

            // 添加我们的智能爆炸AI，优先级设为3（与原版相同）
            SmartExplodeGoal smartGoal = new SmartExplodeGoal(creeper);
            creeper.goalSelector.addGoal(3, smartGoal);
            activeGoals.add(smartGoal);
        } catch (Exception e) {
            SmartCreeper.LOGGER.error("Failed to modify creeper AI", e);
        }
    }

    private void modifySkeletonAI(AbstractSkeleton skeleton) {
        // 添加射击容器的AI
        if (Config.SKELETON_TARGET_CONTAINERS.get())
            skeleton.goalSelector.addGoal(2, new SkeletonShootContainerGoal(skeleton));
    }

    private void modifyEndermanAI(EnderMan enderman) {
        // 添加搬运容器的AI
        if (Config.ENDERMAN_CARRY_CONTAINERS.get())
            enderman.goalSelector.addGoal(2, new EndermanCarryContainerGoal(enderman));
    }

    private void modifyZombieAI(Zombie zombie) {
        // 添加破坏容器的AI
        if (Config.ZOMBIE_BREAK_CONTAINERS.get())
            zombie.goalSelector.addGoal(2, new ZombieBreakContainerGoal(zombie));
    }

    private boolean shouldRemoveCatFearAI(AvoidEntityGoal<?> avoidGoal) {
        try {
            // 通过反射检查AvoidEntityGoal是否针对猫类
            var field = AvoidEntityGoal.class.getDeclaredField("avoidClass");
            field.setAccessible(true);
            Class<?> avoidClass = (Class<?>) field.get(avoidGoal);
            return Cat.class.isAssignableFrom(avoidClass);
        } catch (Exception e) {
            SmartCreeper.LOGGER.warn("Failed to check avoid entity class", e);
            return false;
        }
    }
}