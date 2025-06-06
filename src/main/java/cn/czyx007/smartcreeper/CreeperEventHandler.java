package cn.czyx007.smartcreeper;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CreeperEventHandler {
    private static final Set<SmartExplodeGoal> activeGoals = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
            modifyCreeperAI(creeper);
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
        // 移除原版爆炸AI和恐惧AI
        for (WrappedGoal wrappedGoal : creeper.goalSelector.getAvailableGoals()) {
            Goal goal = wrappedGoal.getGoal();

            // 移除原版爆炸AI（SwellGoal）
            if (goal.getClass().getSimpleName().contains("SwellGoal") ||
                    goal.getClass().getSimpleName().contains("CreeperSwellGoal")) {
                creeper.goalSelector.removeGoal(goal);
            }

            // 如果配置启用，移除对猫的恐惧AI
            if (Config.FEARLESS_OF_CATS.get() && goal instanceof AvoidEntityGoal<?> avoidGoal) {
                if (shouldRemoveCatFearAI(avoidGoal)) {
                    creeper.goalSelector.removeGoal(goal);
                }
            }
        }

        // 添加我们的智能爆炸AI，优先级设为3（与原版相同）
        SmartExplodeGoal smartGoal = new SmartExplodeGoal(creeper);
        creeper.goalSelector.addGoal(3, smartGoal);
        activeGoals.add(smartGoal);
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