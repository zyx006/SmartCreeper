package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.init.SoundEvents;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class ZombieBreakContainerGoal extends EntityAIBase {
    private final EntityZombie zombie;
    private final World world;
    private BlockPos targetPos = null;
    private int breakProgress = 0;

    public ZombieBreakContainerGoal(EntityZombie zombie) {
        this.zombie = zombie;
        this.world = zombie.world;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        // 检查配置是否启用僵尸破坏容器功能
        if (!Config.ZOMBIE_BREAK_CONTAINERS) {
            return false;
        }

        // 只有成年僵尸才能破坏容器
        if (this.zombie.isChild()) {
            return false;
        }

        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.zombie,
                Config.ZOMBIE_SEARCH_RANGE, 3);
        return this.targetPos != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 检查配置是否仍然启用
        if (!Config.ZOMBIE_BREAK_CONTAINERS) {
            return false;
        }

        if (this.targetPos == null || !ContainerTargetUtils.isTargetBlock(this.world, this.targetPos)) {
            return false;
        }

        int range = Config.ZOMBIE_SEARCH_RANGE;
        double maxDist = range * range;
        return this.zombie.getDistanceSqToCenter(this.targetPos) < maxDist;
    }

    @Override
    public void startExecuting() {
        this.breakProgress = 0;
        if (this.targetPos != null) {
            this.zombie.getNavigator().tryMoveToXYZ(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0
            );
        }
    }

    @Override
    public void updateTask() {
        if (this.targetPos == null) return;

        double distSq = this.zombie.getDistanceSqToCenter(this.targetPos);

        if (distSq > 4.0) { // 超过2格距离，继续移动
            this.zombie.getNavigator().tryMoveToXYZ(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0
            );
            this.breakProgress = 0; // 重置破坏进度
        } else {
            // 在范围内，开始破坏
            this.zombie.getNavigator().clearPath();

            // 看向目标方块
            this.zombie.getLookHelper().setLookPosition(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY() + 0.5,
                    this.targetPos.getZ() + 0.5,
                    10.0F, 40.0F
            );

            // 破坏进度
            this.breakProgress++;

            // 播放破坏音效和粒子效果
            if (this.breakProgress % 20 == 0) { // 每秒播放一次音效
                this.zombie.playSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_DOOR_WOOD, 0.5F, 1.0F);

                // 在客户端显示破坏粒子
                if (this.world instanceof WorldServer) {
                    WorldServer worldServer = (WorldServer) this.world;
                    // 使用配置的破坏时间计算破坏进度
                    int totalBreakTime = Config.ZOMBIE_BREAK_TIME;
                    worldServer.sendBlockBreakProgress(
                            this.zombie.getEntityId(),
                            this.targetPos,
                            (int) ((float) this.breakProgress / totalBreakTime * 10.0F)
                    );
                }
            }

            // 破坏完成
            if (this.breakProgress >= Config.ZOMBIE_BREAK_TIME) {
                breakBlock();
            }
        }
    }

    private void breakBlock() {
        if (!(this.world instanceof WorldServer)) return;

        IBlockState blockState = this.world.getBlockState(this.targetPos);
        if (blockState.getBlock().isAir(blockState, this.world, this.targetPos)) return;

        // 播放破坏音效
        this.zombie.playSound(SoundEvents.ENTITY_ZOMBIE_BREAK_DOOR_WOOD, 1.0F, 1.0F);

        // 破坏方块，掉落物品
        this.world.destroyBlock(this.targetPos, true);

        // 清除破坏进度显示
        WorldServer worldServer = (WorldServer) this.world;
        worldServer.sendBlockBreakProgress(this.zombie.getEntityId(), this.targetPos, -1);

        // 重置状态
        this.breakProgress = 0;
    }

    @Override
    public void resetTask() {
        BlockPos oldTargetPos = this.targetPos;
        this.targetPos = null;
        this.breakProgress = 0;
        this.zombie.getNavigator().clearPath();

        // 清除破坏进度显示
        if (this.world instanceof WorldServer && oldTargetPos != null) {
            WorldServer worldServer = (WorldServer) this.world;
            worldServer.sendBlockBreakProgress(this.zombie.getEntityId(), oldTargetPos, -1);
        }
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }
}
