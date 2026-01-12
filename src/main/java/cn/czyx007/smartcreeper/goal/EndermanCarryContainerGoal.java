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

public class EndermanCarryContainerGoal extends EntityAIBase {
    private final EntityEnderman enderman;
    private final World world;
    private BlockPos targetPos = null;
    private boolean isCarrying = false;
    private IBlockState carriedBlockState = null;
    private NBTTagCompound carriedTileEntityData = null;
    private int actionCooldown = 0;
    private float lastHealth = -1.0F;
    private boolean shouldStopTask = false; // 用于标记是否应该停止任务

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

        // 如果正在携带方块，不再寻找放置位置，只是持续携带
        if (this.isCarrying) {
            return false;
        }

        // 如果末影人已经手持方块（无论是不是这个任务设置的），不再拾取新方块
        if (this.enderman.getHeldBlockState() != null) {
            return false;
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

        // 如果标记应该停止任务，返回false
        if (this.shouldStopTask) {
            return false;
        }

        // 如果正在携带，继续保持任务活跃状态
        if (this.isCarrying) {
            return true;
        }

        return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.world, this.targetPos);
    }

    @Override
    public void startExecuting() {
        if (this.targetPos != null) {
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
        this.lastHealth = this.enderman.getHealth(); // 初始化生命值用于检测伤害

        // 播放音效
        this.world.playSound(null, this.enderman.posX, this.enderman.posY, this.enderman.posZ,
                SoundEvents.ENTITY_ENDERMEN_SCREAM, SoundCategory.HOSTILE, 1.0F, 1.0F);

        // 设置末影人携带方块的外观
        this.enderman.setHeldBlockState(blockState);
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
            this.actionCooldown = 40; // 设置冷却时间，防止立即重新拾取
            this.lastHealth = -1.0F; // 重置生命值追踪
        }

        this.targetPos = null;
        this.shouldStopTask = false; // 重置停止标志
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