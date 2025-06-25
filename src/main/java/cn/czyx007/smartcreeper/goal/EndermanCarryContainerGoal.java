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
import java.util.Random;

public class EndermanCarryContainerGoal extends Goal {
    private final EnderMan enderman;
    private final Level level;
    private BlockPos targetPos = null;
    private BlockPos placePos = null;
    private boolean isCarrying = false;
    private BlockState carriedBlockState = null;
    private CompoundTag carriedBlockEntityData = null;
    private int actionCooldown = 0;

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

        // 如果正在携带方块，寻找放置位置
        if (this.isCarrying) {
            this.placePos = findPlacePosition();
            return this.placePos != null;
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

        if (this.isCarrying) {
            return this.placePos != null && this.level.getBlockState(this.placePos).isAir();
        }

        return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.level, this.targetPos);
    }

    @Override
    public void start() {
        if (this.isCarrying && this.placePos != null) {
            // 移动到放置位置
            this.enderman.getNavigation().moveTo(
                    this.placePos.getX() + 0.5,
                    this.placePos.getY(),
                    this.placePos.getZ() + 0.5,
                    1.0
            );
        } else if (this.targetPos != null) {
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
        if (this.isCarrying && this.placePos != null) {
            // 正在携带方块，尝试放置
            double distSq = this.enderman.distanceToSqr(this.placePos.getX() + 0.5, this.placePos.getY(), this.placePos.getZ() + 0.5);
            if (distSq < 4.0) { // 2格内放置
                placeBlock();
            }
        } else if (this.targetPos != null) {
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

        // 播放音效
        this.enderman.playSound(SoundEvents.ENDERMAN_SCREAM, 1.0F, 1.0F);

        // 设置末影人携带方块的外观（如果可能的话）
        if (this.enderman.getCarriedBlock() == null) {
            this.enderman.setCarriedBlock(blockState);
        }
    }

    private void placeBlock() {
        if (!(this.level instanceof ServerLevel) || this.carriedBlockState == null) return;

        // 放置方块
        this.level.setBlock(this.placePos, this.carriedBlockState, 3);

        // 恢复方块实体数据
        if (this.carriedBlockEntityData != null) {
            // 确保我们使用的是数据的副本
            CompoundTag blockEntityData = this.carriedBlockEntityData.copy();

            // 更新位置
            blockEntityData.putInt("x", this.placePos.getX());
            blockEntityData.putInt("y", this.placePos.getY());
            blockEntityData.putInt("z", this.placePos.getZ());

            // 创建新的方块实体
            BlockEntity newBlockEntity = BlockEntity.loadStatic(
                    this.placePos,
                    this.carriedBlockState,
                    blockEntityData,
                    this.level.registryAccess());

            if (newBlockEntity != null) {
                this.level.setBlockEntity(newBlockEntity);
                newBlockEntity.setChanged();
            }
        }

        // 重置状态
        this.isCarrying = false;
        this.carriedBlockState = null;
        this.carriedBlockEntityData = null;
        this.placePos = null;
        this.actionCooldown = 200; // 10秒冷却

        // 清除末影人携带的方块外观
        this.enderman.setCarriedBlock(null);

        // 播放音效
        this.enderman.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
    }

    private BlockPos findPlacePosition() {
        Random random = (Random) this.enderman.getRandom();
        BlockPos enderPos = this.enderman.blockPosition();

        // 在16格外的随机位置寻找合适的放置点
        for (int attempt = 0; attempt < 20; attempt++) {
            int distance = Config.ENDERMAN_PLACE_MIN_DISTANCE.get() + random.nextInt(16); // 配置的最小距离 + 0-16格
            double angle = random.nextDouble() * 2 * Math.PI;

            int x = enderPos.getX() + (int) (Math.cos(angle) * distance);
            int z = enderPos.getZ() + (int) (Math.sin(angle) * distance);

            // 寻找合适的Y坐标
            for (int y = enderPos.getY() - 5; y <= enderPos.getY() + 5; y++) {
                BlockPos checkPos = new BlockPos(x, y, z);

                if (this.level.getBlockState(checkPos).isAir() &&
                        !this.level.getBlockState(checkPos.below()).isAir() &&
                        this.level.getBlockState(checkPos.above()).isAir()) {
                    return checkPos;
                }
            }
        }

        return null;
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
        }

        this.targetPos = null;
        this.placePos = null;
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