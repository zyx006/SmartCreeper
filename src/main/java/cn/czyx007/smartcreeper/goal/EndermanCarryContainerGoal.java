package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.state.IBlockState;

import java.util.Random;

public class EndermanCarryContainerGoal extends EntityAIBase {
    private final EntityEnderman enderman;
    private final World world;
    private BlockPos targetPos = null;
    private BlockPos placePos = null;
    private boolean isCarrying = false;
    private IBlockState carriedBlockState = null;
    private NBTTagCompound carriedTileEntityData = null;
    private int actionCooldown = 0;

    public EndermanCarryContainerGoal(EntityEnderman enderman) {
        this.enderman = enderman;
        this.world = enderman.world;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        // 检查配置是否启用末影人搬运容器功能
        if (!Config.ENDERMAN_CARRY_CONTAINERS) {
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
                Config.ENDERMAN_SEARCH_RANGE, 3);
        return this.targetPos != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 检查配置是否仍然启用
        if (!Config.ENDERMAN_CARRY_CONTAINERS) {
            return false;
        }

        if (this.isCarrying) {
            return this.placePos != null && this.world.getBlockState(this.placePos).getBlock().isAir(this.world.getBlockState(this.placePos), this.world, this.placePos);
        }

        return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.world, this.targetPos);
    }

    @Override
    public void startExecuting() {
        if (this.isCarrying && this.placePos != null) {
            // 移动到放置位置
            this.enderman.getNavigator().tryMoveToXYZ(
                    this.placePos.getX() + 0.5,
                    this.placePos.getY(),
                    this.placePos.getZ() + 0.5,
                    1.0
            );
        } else if (this.targetPos != null) {
            // 移动到目标位置
            this.enderman.getNavigator().tryMoveToXYZ(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0
            );
        }
    }

    @Override
    public void updateTask() {
        if (this.isCarrying && this.placePos != null) {
            // 正在携带方块，尝试放置
            double distSq = this.enderman.getDistanceSq(this.placePos.getX() + 0.5, this.placePos.getY(), this.placePos.getZ() + 0.5);
            if (distSq < 4.0) { // 2格内放置
                placeBlock();
            }
        } else if (this.targetPos != null) {
            // 尝试搬运方块
            double distSq = this.enderman.getDistanceSq(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
            if (distSq < 4.0) { // 2格内拿取
                pickupBlock();
            }
        }
    }

    private void pickupBlock() {
        if (this.world.isRemote) return;

        IBlockState blockState = this.world.getBlockState(this.targetPos);
        if (blockState.getBlock().isAir(blockState, this.world, this.targetPos)) return;

        // 保存TileEntity数据
        TileEntity tileEntity = this.world.getTileEntity(this.targetPos);
        if (tileEntity != null) {
            // 创建新的NBTTagCompound，避免引用原始数据
            this.carriedTileEntityData = tileEntity.writeToNBT(new NBTTagCompound()).copy();
        }

        this.carriedBlockState = blockState;

        // 移除原方块
        // 先清空容器内容，再移除方块
        if (tileEntity instanceof IInventory) {
            IInventory inventory = (IInventory) tileEntity;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            }
            inventory.markDirty();
        }
        this.world.destroyBlock(this.targetPos, false);

        this.isCarrying = true;
        this.targetPos = null;

        // 播放音效
        this.world.playSound(null, this.enderman.posX, this.enderman.posY, this.enderman.posZ,
                SoundEvents.ENTITY_ENDERMEN_SCREAM, SoundCategory.HOSTILE, 1.0F, 1.0F);

        // 设置末影人携带方块的外观
        if (this.enderman.getHeldBlockState() == null) {
            this.enderman.setHeldBlockState(blockState);
        }
    }

    private void placeBlock() {
        if (this.world.isRemote || this.carriedBlockState == null) return;

        // 放置方块
        this.world.setBlockState(this.placePos, this.carriedBlockState, 3);

        // 恢复TileEntity数据
        if (this.carriedTileEntityData != null) {
            // 确保使用的是数据的副本
            NBTTagCompound tileEntityData = this.carriedTileEntityData.copy();

            // 更新位置
            tileEntityData.setInteger("x", this.placePos.getX());
            tileEntityData.setInteger("y", this.placePos.getY());
            tileEntityData.setInteger("z", this.placePos.getZ());

            // 获取新的TileEntity并加载数据
            TileEntity newTileEntity = this.world.getTileEntity(this.placePos);
            if (newTileEntity != null) {
                newTileEntity.readFromNBT(tileEntityData);
                newTileEntity.markDirty();
            }
        }

        // 重置状态
        this.isCarrying = false;
        this.carriedBlockState = null;
        this.carriedTileEntityData = null;
        this.placePos = null;
        this.actionCooldown = 200; // 10秒冷却

        // 清除末影人携带的方块外观
        this.enderman.setHeldBlockState(null);

        // 播放音效
        this.world.playSound(null, this.enderman.posX, this.enderman.posY, this.enderman.posZ,
                SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
    }

    private BlockPos findPlacePosition() {
        Random random = this.enderman.getRNG();
        BlockPos enderPos = new BlockPos(this.enderman);

        // 在配置距离外的随机位置寻找合适的放置点
        for (int attempt = 0; attempt < 20; attempt++) {
            int distance = Config.ENDERMAN_PLACE_MIN_DISTANCE + random.nextInt(16); // 配置的最小距离 + 0-16格
            double angle = random.nextDouble() * 2 * Math.PI;

            int x = enderPos.getX() + (int) (Math.cos(angle) * distance);
            int z = enderPos.getZ() + (int) (Math.sin(angle) * distance);

            // 寻找合适的Y坐标
            for (int y = enderPos.getY() - 5; y <= enderPos.getY() + 5; y++) {
                BlockPos checkPos = new BlockPos(x, y, z);
                IBlockState checkState = this.world.getBlockState(checkPos);
                IBlockState belowState = this.world.getBlockState(checkPos.down());
                IBlockState aboveState = this.world.getBlockState(checkPos.up());

                if (checkState.getBlock().isAir(checkState, this.world, checkPos) &&
                        !belowState.getBlock().isAir(belowState, this.world, checkPos.down()) &&
                        aboveState.getBlock().isAir(aboveState, this.world, checkPos.up())) {
                    return checkPos;
                }
            }
        }

        return null;
    }

    @Override
    public void resetTask() {
        this.enderman.getNavigator().clearPath();

        // 如果停止时还在携带方块，尝试就近放置或丢弃
        if (this.isCarrying && this.carriedBlockState != null) {
            BlockPos nearbyPos = findNearbyPlacePosition();
            if (nearbyPos != null) {
                this.world.setBlockState(nearbyPos, this.carriedBlockState, 3);

                if (this.carriedTileEntityData != null) {
                    TileEntity tileEntity = this.world.getTileEntity(nearbyPos);
                    if (tileEntity != null) {
                        NBTTagCompound data = this.carriedTileEntityData.copy();
                        data.setInteger("x", nearbyPos.getX());
                        data.setInteger("y", nearbyPos.getY());
                        data.setInteger("z", nearbyPos.getZ());

                        tileEntity.readFromNBT(data);
                        tileEntity.markDirty();
                    }
                }
            }

            this.enderman.setHeldBlockState(null);
            this.isCarrying = false;
            this.carriedBlockState = null;
            this.carriedTileEntityData = null;
        }

        this.targetPos = null;
        this.placePos = null;
    }

    private BlockPos findNearbyPlacePosition() {
        BlockPos enderPos = new BlockPos(this.enderman);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos checkPos = enderPos.add(x, y, z);
                    IBlockState checkState = this.world.getBlockState(checkPos);
                    IBlockState belowState = this.world.getBlockState(checkPos.down());

                    if (checkState.getBlock().isAir(checkState, this.world, checkPos) &&
                            !belowState.getBlock().isAir(belowState, this.world, checkPos.down())) {
                        return checkPos;
                    }
                }
            }
        }

        return null;
    }
}