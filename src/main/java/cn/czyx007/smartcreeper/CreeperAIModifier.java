package cn.czyx007.smartcreeper;

import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

public class CreeperAIModifier {
    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CreeperAIModifier());
    }

    @SubscribeEvent
    public void onEntitySpawn(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityCreeper) {
            EntityCreeper creeper = (EntityCreeper) event.getEntity();
            modifyCreeperAI(creeper);
        }
    }

    private void modifyCreeperAI(EntityCreeper creeper) {
        // 移除原版爆炸AI
        for (EntityAITasks.EntityAITaskEntry task : creeper.tasks.taskEntries.toArray(new EntityAITasks.EntityAITaskEntry[0])) {
            if (task.action instanceof net.minecraft.entity.ai.EntityAICreeperSwell) {
                creeper.tasks.removeTask(task.action);
            }
            // 如果配置启用，移除对猫的恐惧AI
            if (SmartCreeper.fearlessOfCats && task.action instanceof net.minecraft.entity.ai.EntityAIAvoidEntity) {
                // 使用反射获取classToAvoid
                try {
                    Field classToAvoidField = net.minecraft.entity.ai.EntityAIAvoidEntity.class.getDeclaredField("classToAvoid");
                    classToAvoidField.setAccessible(true);
                    Class<?> classToAvoid = (Class<?>) classToAvoidField.get(task.action);

                    if (classToAvoid == EntityOcelot.class) {
                        creeper.tasks.removeTask(task.action);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    SmartCreeper.LOGGER.error("Failed to remove creeper's fear of cats", e);
                }
            }
        }

        // 添加我们的智能爆炸AI，优先级设为3（与原版相同）
        creeper.tasks.addTask(3, new EntityAISmartExplode(creeper));

        // 确保保持原版的爆炸威力和范围
        // 这些数值由原版EntityCreeper类控制，我们不做修改
        SmartCreeper.LOGGER.debug("Added smart explode AI to creeper, maintaining vanilla explosion power and range");
    }
}