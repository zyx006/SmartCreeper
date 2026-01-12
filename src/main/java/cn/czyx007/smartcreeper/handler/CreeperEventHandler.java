package cn.czyx007.smartcreeper.handler;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.SmartCreeper;
import cn.czyx007.smartcreeper.goal.EndermanCarryContainerGoal;
import cn.czyx007.smartcreeper.goal.SkeletonShootContainerGoal;
import cn.czyx007.smartcreeper.goal.SmartExplodeGoal;
import cn.czyx007.smartcreeper.goal.ZombieBreakContainerGoal;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.Explosion;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CreeperEventHandler {
    private static final Set<SmartExplodeGoal> activeGoals = ConcurrentHashMap.newKeySet();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityCreeper) {
            modifyCreeperAI((EntityCreeper) event.getEntity());
        } else if (event.getEntity() instanceof AbstractSkeleton) {
            modifySkeletonAI((AbstractSkeleton) event.getEntity());
        } else if (event.getEntity() instanceof EntityEnderman) {
            modifyEndermanAI((EntityEnderman) event.getEntity());
        } else if (event.getEntity() instanceof EntityZombie) {
            modifyZombieAI((EntityZombie) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity() instanceof EntityArrow &&
                event.getRayTraceResult().typeOfHit == RayTraceResult.Type.BLOCK) {

            EntityArrow arrow = (EntityArrow) event.getEntity();

            // 检查是否是骷髅射向容器的箭
            if (arrow.getEntityData().hasKey("smartcreeper_target")) {
                String target = arrow.getEntityData().getString("smartcreeper_target");
                if ("container".equals(target)) {
                    BlockPos hitPos = event.getRayTraceResult().getBlockPos();

                    // 检查击中的是否为目标容器
                    if (ContainerTargetUtils.isTargetBlock(arrow.world, hitPos)) {
                        boolean containerDestroyed = ContainerHitHandler.recordHit(arrow.world, hitPos);

                        // 只有容器被破坏时才移除箭矢
                        if (containerDestroyed) {
                            arrow.setDead();
                        }
                    } else {
                        // 不是目标容器时正常处理箭矢
                        arrow.setNoGravity(false);
                        arrow.pickupStatus = EntityArrow.PickupStatus.ALLOWED;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        if (explosion.getExplosivePlacedBy() instanceof EntityCreeper) {
            EntityCreeper creeper = (EntityCreeper) explosion.getExplosivePlacedBy();
            for (SmartExplodeGoal goal : activeGoals) {
                if (goal.getCreeper() == creeper && goal.getTargetPos() != null) {
                    double distSq = goal.getTargetPos().distanceSq(
                            explosion.getPosition().x,
                            explosion.getPosition().y,
                            explosion.getPosition().z
                    );
                    if (distSq <= 25.0) { // 5格距离平方
                        event.getWorld().destroyBlock(goal.getTargetPos(), false);
                        activeGoals.remove(goal);
                    }
                    break;
                }
            }
        }
    }

    private void modifyCreeperAI(EntityCreeper creeper) {
        // 移除原版恐惧AI
        Iterator<EntityAITasks.EntityAITaskEntry> iterator = creeper.tasks.taskEntries.iterator();
        while (iterator.hasNext()) {
            EntityAITasks.EntityAITaskEntry entry = iterator.next();
            EntityAIBase task = entry.action;

            // 如果配置启用，移除对豹猫的恐惧AI
            if (Config.FEARLESS_OF_CATS && task instanceof EntityAIAvoidEntity) {
                EntityAIAvoidEntity<?> avoidTask = (EntityAIAvoidEntity<?>) task;
                if (shouldRemoveOcelotFearAI(avoidTask)) {
                    iterator.remove();
                }
            }
        }

        // 添加我们的智能爆炸AI
        SmartExplodeGoal smartGoal = new SmartExplodeGoal(creeper);
        creeper.tasks.addTask(1, smartGoal);
        activeGoals.add(smartGoal);
    }

    private void modifySkeletonAI(AbstractSkeleton skeleton) {
        // 添加射击容器的AI
        if (Config.SKELETON_TARGET_CONTAINERS) {
            skeleton.tasks.addTask(2, new SkeletonShootContainerGoal(skeleton));
        }
    }

    private void modifyEndermanAI(EntityEnderman enderman) {
        // 添加搬运容器的AI
        if (Config.ENDERMAN_CARRY_CONTAINERS) {
            enderman.tasks.addTask(2, new EndermanCarryContainerGoal(enderman));
        }
    }

    private void modifyZombieAI(EntityZombie zombie) {
        // 添加破坏容器的AI
        if (Config.ZOMBIE_BREAK_CONTAINERS) {
            zombie.tasks.addTask(2, new ZombieBreakContainerGoal(zombie));
        }
    }

    private boolean shouldRemoveOcelotFearAI(EntityAIAvoidEntity<?> avoidTask) {
        try {
            // 通过反射检查EntityAIAvoidEntity是否针对豹猫类
            Field field = EntityAIAvoidEntity.class.getDeclaredField("classToAvoid");
            field.setAccessible(true);
            Class<?> avoidClass = (Class<?>) field.get(avoidTask);
            return EntityOcelot.class.isAssignableFrom(avoidClass);
        } catch (Exception e) {
            SmartCreeper.LOGGER.warn("Failed to check avoid entity class", e);
            return false;
        }
    }
}