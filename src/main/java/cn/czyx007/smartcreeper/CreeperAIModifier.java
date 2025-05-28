package cn.czyx007.smartcreeper;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

public class CreeperAIModifier {
    // 缓存Field对象，这样只需要进行一次反射查找
    private static Field classToAvoidField = null;
    private static boolean fieldInitialized = false;

    /**
     * 初始化反射Field，只执行一次
     */
    private static void initializeField() {
        if (fieldInitialized) return;

        try {
            classToAvoidField = ObfuscationReflectionHelper.findField(
                    EntityAIAvoidEntity.class,
                    "classToAvoid"
            );
            classToAvoidField.setAccessible(true);
            SmartCreeper.LOGGER.debug("Successfully initialized classToAvoid field cache");
        } catch (Exception e) {
            SmartCreeper.LOGGER.error("Failed to initialize classToAvoid field", e);
            classToAvoidField = null;
        } finally {
            fieldInitialized = true;
        }
    }

    /**
     * 获取EntityAIAvoidEntity的classToAvoid字段值
     */
    private static Class<?> getClassToAvoid(EntityAIAvoidEntity avoidEntity) {
        // 确保Field已初始化
        if (!fieldInitialized) {
            initializeField();
        }

        if (classToAvoidField == null) {
            return null;
        }

        try {
            return (Class<?>) classToAvoidField.get(avoidEntity);
        } catch (IllegalAccessException e) {
            SmartCreeper.LOGGER.warn("Failed to get classToAvoid value", e);
            return null;
        }
    }

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
        // 移除原版爆炸AI和恐惧AI
        for (EntityAITasks.EntityAITaskEntry task : creeper.tasks.taskEntries.toArray(new EntityAITasks.EntityAITaskEntry[0])) {
            if (task.action instanceof net.minecraft.entity.ai.EntityAICreeperSwell) {
                creeper.tasks.removeTask(task.action);
            }
            // 如果配置启用，移除对猫的恐惧AI
            if (SmartCreeper.fearlessOfCats && task.action instanceof EntityAIAvoidEntity) {
                if (shouldRemoveCatFearAI((EntityAIAvoidEntity) task.action)) {
                    creeper.tasks.removeTask(task.action);
                }
            }
        }

        // 添加我们的智能爆炸AI，优先级设为3（与原版相同）
        creeper.tasks.addTask(3, new EntityAISmartExplode(creeper));
        SmartCreeper.LOGGER.debug("Added smart explode AI to creeper, maintaining vanilla explosion power and range");
    }

    /**
     * 检查是否应该移除对猫的恐惧AI
     * 使用缓存的Field对象进行高效反射
     */
    private boolean shouldRemoveCatFearAI(EntityAIAvoidEntity avoidEntity) {
        Class<?> classToAvoid = getClassToAvoid(avoidEntity);
        return classToAvoid != null && EntityOcelot.class.isAssignableFrom(classToAvoid);
    }
}