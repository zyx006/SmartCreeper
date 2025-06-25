package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class SmartExplodeGoal extends Goal {
    private final Creeper creeper;
    private final Level level;
    private BlockPos targetPos = null;
    private boolean isChargedByCat = false;

    public SmartExplodeGoal(Creeper creeper) {
        this.creeper = creeper;
        this.level = creeper.level();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 始终检查猫的交互（无论是否在执行其他任务）
        checkForCat();

        // 查找目标方块
        int range = this.isChargedByCat ?
                Config.CHARGED_SEARCH_RANGE.get() :
                Config.NORMAL_SEARCH_RANGE.get();

        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.creeper, range, 3);
        return this.targetPos != null;
    }

    private void checkForCat() {
        if (!Config.FEARLESS_OF_CATS.get()) return;

        // 检查6格范围内是否有猫
        AABB searchArea = this.creeper.getBoundingBox().inflate(6.0);
        List<Cat> cats = level.getEntitiesOfClass(Cat.class, searchArea);
        List<Ocelot> ocelots = level.getEntitiesOfClass(Ocelot.class, searchArea);

        List<Animal> catsAndOcelots = new ArrayList<>();
        catsAndOcelots.addAll(cats);
        catsAndOcelots.addAll(ocelots);

        if (!catsAndOcelots.isEmpty() && !this.creeper.isPowered()) {
            if (this.level instanceof ServerLevel serverLevel) {
                LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
                lightningBolt.setDamage(0f);
                this.creeper.thunderHit(serverLevel, lightningBolt);
            }
            this.creeper.setHealth(this.creeper.getMaxHealth());
            this.isChargedByCat = true;

            // 重置寻路以响应新状态
            this.creeper.getNavigation().stop();
        }
    }

    @Override
    public boolean canContinueToUse() {
        // 持续检查猫的交互
        checkForCat();
        if (this.targetPos == null || !ContainerTargetUtils.isTargetBlock(this.level, this.targetPos)) {
            return false;
        }

        int range = this.isChargedByCat ?
                Config.CHARGED_SEARCH_RANGE.get() :
                Config.NORMAL_SEARCH_RANGE.get();
        double maxDist = range * range;
        return this.creeper.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5) < maxDist;
    }

    @Override
    public void start() {
        if (this.targetPos != null) {
            // 使用原版速度 (1.0)
            this.creeper.getNavigation().moveTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0);
        }
    }

    @Override
    public void tick() {
        if (this.targetPos != null) {
            // 检查距离 - 使用原版爆炸触发距离
            double distSq = this.creeper.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
            double explodeDist = 9.0; // 原版3格距离的平方

            if (distSq < explodeDist) {
                this.creeper.ignite(); // 点燃
            } else {
                // 继续移动 - 使用原版速度
                this.creeper.getNavigation().moveTo(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        1.0);
            }
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.creeper.getNavigation().stop();
    }

    public Creeper getCreeper() {
        return this.creeper;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }
}