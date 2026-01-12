package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;

public class EndermanCarryContainerGoal extends Goal {
    private final EnderMan enderman;
    private final Level level;
    private BlockPos targetPos = null;
    private boolean isCarrying = false;
    private BlockState carriedBlockState = null;
    private CompoundTag carriedBlockEntityData = null;
    private int actionCooldown = 0;
    private float lastHealth = -1.0F;
    private boolean shouldStopTask = false; // 用于标记是否应该停止任务

    public EndermanCarryContainerGoal(EnderMan enderman) {
        this.enderman = enderman;
        this.level = enderman.level();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 检查配置是否启用末影人搬运容器功能
        if (!Config.ENDERMAN_CARRY_CONTAINERS.get()) {
            return false;
        }

        if (this.actionCooldown > 0) {
            this.actionCooldown--;
            return false;
        }

        // 如果正在携带方块，不再寻找放置位置，只是持续携带
        if (this.isCarrying) {
            return false;
        }

        // 如果末影人已经手持方块（无论是不是这个任务设置的），不再拾取新方块
        if (this.enderman.getCarriedBlock() != null) {
            return false;
        }

        // 寻找要搬运的容器
        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.enderman,
                Config.ENDERMAN_SEARCH_RANGE.get(), 3);
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 检查配置是否仍然启用
        if (!Config.ENDERMAN_CARRY_CONTAINERS.get()) {
            return false;
        }

        // 如果标记应该停止任务，返回false
        if (this.shouldStopTask) {
            return false;
        }

        // 如果正在携带，继续保持任务活跃状态
        if (this.isCarrying) {
            return true;
        }

        return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.level, this.targetPos);
    }

    @Override
    public void start() {
        if (this.targetPos != null) {
            // 移动到目标位置
            this.enderman.getNavigation().moveTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0
            );
        }
    }

    @Override
    public void tick() {
        // 检测末影人是否受到伤害
        if (this.isCarrying) {
            float currentHealth = this.enderman.getHealth();
            if (this.lastHealth > 0 && currentHealth < this.lastHealth) {
                // 受到伤害，标记应该停止任务
                this.shouldStopTask = true;
            }
            this.lastHealth = currentHealth;
            // 正在携带时不执行其他动作
            return;
        }

        if (this.targetPos != null) {
            // 尝试搬运方块
            double distSq = this.enderman.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
            if (distSq < 4.0) { // 2格内拿取
                pickupBlock();
            }
        }
    }

    private void pickupBlock() {
        if (!(this.level instanceof ServerLevel)) return;

        BlockState blockState = this.level.getBlockState(this.targetPos);
        if (blockState.isAir()) return;

        // 保存方块实体数据
        BlockEntity blockEntity = this.level.getBlockEntity(this.targetPos);
        if (blockEntity != null) {
            // 创建新的CompoundTag，避免引用原始数据
            this.carriedBlockEntityData = blockEntity.saveWithoutMetadata(this.level.registryAccess()).copy();

            // 从世界中移除方块实体，防止物品掉落
            this.level.removeBlockEntity(this.targetPos);
        }

        this.carriedBlockState = blockState;

        // 移除原方块（不掉落物品）
        this.level.removeBlock(this.targetPos, false);

        this.isCarrying = true;
        this.targetPos = null;
        this.lastHealth = this.enderman.getHealth(); // 初始化生命值用于检测伤害

        // 播放音效
        this.enderman.playSound(SoundEvents.ENDERMAN_SCREAM, 1.0F, 1.0F);

        // 设置末影人携带方块的外观（如果可能的话）
        this.enderman.setCarriedBlock(blockState);
    }

    @Override
    public void stop() {
        this.enderman.getNavigation().stop();

        // 如果停止时还在携带方块，尝试就近放置或丢弃
        if (this.isCarrying && this.carriedBlockState != null) {
            BlockPos nearbyPos = findNearbyPlacePosition();
            if (nearbyPos != null) {
                this.level.setBlock(nearbyPos, this.carriedBlockState, 3);

                if (this.carriedBlockEntityData != null) {
                    BlockEntity blockEntity = this.level.getBlockEntity(nearbyPos);
                    if (blockEntity != null) {
                        this.carriedBlockEntityData.putInt("x", nearbyPos.getX());
                        this.carriedBlockEntityData.putInt("y", nearbyPos.getY());
                        this.carriedBlockEntityData.putInt("z", nearbyPos.getZ());

                        blockEntity.loadWithComponents(this.carriedBlockEntityData, this.level.registryAccess());
                        blockEntity.setChanged();
                    }
                }
            }

            this.enderman.setCarriedBlock(null);
            this.isCarrying = false;
            this.carriedBlockState = null;
            this.carriedBlockEntityData = null;
            this.actionCooldown = 40; // 设置冷却时间，防止立即重新拾取
            this.lastHealth = -1.0F; // 重置生命值追踪
        }

        this.targetPos = null;
        this.shouldStopTask = false; // 重置停止标志
    }

    private BlockPos findNearbyPlacePosition() {
        BlockPos enderPos = this.enderman.blockPosition();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos checkPos = enderPos.offset(x, y, z);
                    if (this.level.getBlockState(checkPos).isAir() &&
                            !this.level.getBlockState(checkPos.below()).isAir()) {
                        return checkPos;
                    }
                }
            }
        }

        return null;
    }
}