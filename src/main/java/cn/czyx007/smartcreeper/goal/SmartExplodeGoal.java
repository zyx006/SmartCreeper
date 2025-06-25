package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class SmartExplodeGoal extends EntityAIBase {
    private final EntityCreeper creeper;
    private final World world;
    private BlockPos targetPos = null;
    private boolean isChargedByCat = false;

    public SmartExplodeGoal(EntityCreeper creeper) {
        this.creeper = creeper;
        this.world = creeper.world;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        // 始终检查猫的交互（无论是否在执行其他任务）
        checkForCat();

        // 查找目标方块
        int range = this.isChargedByCat ?
                Config.CHARGED_SEARCH_RANGE :
                Config.NORMAL_SEARCH_RANGE;

        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.creeper, range, 3);
        return this.targetPos != null;
    }

    private void checkForCat() {
        if (!Config.FEARLESS_OF_CATS) return;

        // 检查6格范围内是否有猫
        AxisAlignedBB searchArea = this.creeper.getEntityBoundingBox().grow(6.0);
        List<EntityOcelot> ocelots = world.getEntitiesWithinAABB(EntityOcelot.class, searchArea);

        List<EntityAnimal> catsAndOcelots = new ArrayList<>(ocelots);

        if (!catsAndOcelots.isEmpty() && !this.creeper.getPowered()) {
            if (!this.world.isRemote) {
                EntityLightningBolt lightningBolt = new EntityLightningBolt(world,
                        this.creeper.posX, this.creeper.posY, this.creeper.posZ, true);
                this.creeper.onStruckByLightning(lightningBolt);
            }
            this.creeper.setHealth(this.creeper.getMaxHealth());
            this.isChargedByCat = true;

            // 重置寻路以响应新状态
            this.creeper.getNavigator().clearPath();
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 持续检查猫的交互
        checkForCat();
        if (this.targetPos == null || !ContainerTargetUtils.isTargetBlock(this.world, this.targetPos)) {
            return false;
        }

        int range = this.isChargedByCat ?
                Config.CHARGED_SEARCH_RANGE :
                Config.NORMAL_SEARCH_RANGE;
        double maxDist = range * range;
        return this.creeper.getDistanceSq(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5) < maxDist;
    }

    @Override
    public void startExecuting() {
        if (this.targetPos != null) {
            // 使用原版速度 (1.0)
            this.creeper.getNavigator().tryMoveToXYZ(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0);
        }
    }

    @Override
    public void updateTask() {
        if (this.targetPos != null) {
            // 检查距离 - 使用原版爆炸触发距离
            double distSq = this.creeper.getDistanceSq(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
            double explodeDist = 9.0; // 原版3格距离的平方

            if (distSq < explodeDist) {
                this.creeper.ignite(); // 点燃
            } else {
                // 继续移动 - 使用原版速度
                this.creeper.getNavigator().tryMoveToXYZ(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        1.0);
            }
        }
    }

    @Override
    public void resetTask() {
        this.targetPos = null;
        this.creeper.getNavigator().clearPath();
    }

    public EntityCreeper getCreeper() {
        return this.creeper;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }
}