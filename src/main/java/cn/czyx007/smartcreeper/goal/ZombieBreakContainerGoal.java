package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class ZombieBreakContainerGoal extends Goal {
    private final Zombie zombie;
    private final Level level;
    private BlockPos targetPos = null;
    private int breakProgress = 0;

    public ZombieBreakContainerGoal(Zombie zombie) {
        this.zombie = zombie;
        this.level = zombie.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 检查配置是否启用僵尸破坏容器功能
        if (!Config.ZOMBIE_BREAK_CONTAINERS.get()) {
            return false;
        }

        // 只有成年僵尸才能破坏容器
        if (this.zombie.isBaby()) {
            return false;
        }

        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.zombie,
                Config.ZOMBIE_SEARCH_RANGE.get(), 3);
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 检查配置是否仍然启用
        if (!Config.ZOMBIE_BREAK_CONTAINERS.get()) {
            return false;
        }

        if (this.targetPos == null || !ContainerTargetUtils.isTargetBlock(this.level, this.targetPos)) {
            return false;
        }

        Integer range = Config.ZOMBIE_SEARCH_RANGE.get();
        double maxDist = range * range;
        return this.zombie.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5) < maxDist;
    }

    @Override
    public void start() {
        this.breakProgress = 0;
        if (this.targetPos != null) {
            this.zombie.getNavigation().moveTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0
            );
        }
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;

        double distSq = this.zombie.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);

        if (distSq > 4.0) { // 超过2格距离，继续移动
            this.zombie.getNavigation().moveTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0
            );
            this.breakProgress = 0; // 重置破坏进度
        } else {
            // 在范围内，开始破坏
            this.zombie.getNavigation().stop();

            // 看向目标方块
            this.zombie.getLookControl().setLookAt(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY() + 0.5,
                    this.targetPos.getZ() + 0.5
            );

            // 破坏进度
            this.breakProgress++;

            // 播放破坏音效和粒子效果
            if (this.breakProgress % 20 == 0) { // 每秒播放一次音效
                this.zombie.playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.5F, 1.0F);

                // 在客户端显示破坏粒子
                if (this.level instanceof ServerLevel serverLevel) {
                    // 使用配置的破坏时间计算破坏进度
                    int totalBreakTime = Config.ZOMBIE_BREAK_TIME.get();
                    serverLevel.destroyBlockProgress(
                            this.zombie.getId(),
                            this.targetPos,
                            (int) ((float) this.breakProgress / totalBreakTime * 10.0F)
                    );
                }
            }

            // 破坏完成
            if (this.breakProgress >= Config.ZOMBIE_BREAK_TIME.get()) {
                breakBlock();
            }
        }
    }

    private void breakBlock() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;

        BlockState blockState = this.level.getBlockState(this.targetPos);
        if (blockState.isAir()) return;

        // 播放破坏音效
        this.zombie.playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.0F, 1.0F);

        // 破坏方块，掉落物品
        this.level.destroyBlock(this.targetPos, true);

        // 清除破坏进度显示
        serverLevel.destroyBlockProgress(this.zombie.getId(), this.targetPos, -1);

        // 重置状态
        this.breakProgress = 0;
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.breakProgress = 0;
        this.zombie.getNavigation().stop();

        // 清除破坏进度显示
        if (this.level instanceof ServerLevel serverLevel && this.targetPos != null) {
            serverLevel.destroyBlockProgress(this.zombie.getId(), this.targetPos, -1);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}