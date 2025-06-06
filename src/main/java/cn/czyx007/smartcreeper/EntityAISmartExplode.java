package cn.czyx007.smartcreeper;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.List;
import java.util.Map;

public class EntityAISmartExplode extends EntityAIBase {
    private final EntityCreeper creeper;
    private final World world;
    private BlockPos targetPos = null;
    private boolean isChargedByCat = false;

    public EntityAISmartExplode(EntityCreeper creeper) {
        this.creeper = creeper;
        this.world = creeper.world;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        // 始终检查猫的交互（无论是否在执行其他任务）
        checkForCat();

        // 查找目标方块
        this.targetPos = findNearestTargetBlock();
        return this.targetPos != null;
    }

    private void checkForCat() {
        if (!SmartCreeper.fearlessOfCats) return;

        // 检查6格范围内是否有猫
        List<EntityOcelot> cats = this.world.getEntitiesWithinAABB(EntityOcelot.class,
                this.creeper.getEntityBoundingBox().grow(6.0));

        if (!cats.isEmpty() && !this.creeper.getPowered()) {
            this.creeper.onStruckByLightning(null); // 变成高压苦力怕
            this.creeper.setHealth(this.creeper.getMaxHealth());
            this.creeper.isDead = false;
            this.creeper.deathTime = 0;
            this.creeper.hurtResistantTime = 0;
            this.isChargedByCat = true;

            // 重置寻路以立即响应新状态
            this.creeper.getNavigator().clearPath();
        }
    }

    /**
     * 使用广度优先搜索算法优化寻找最近目标方块的效率
     */
    private BlockPos findNearestTargetBlock() {
        // 确定搜索范围
        int range = this.isChargedByCat ? SmartCreeper.chargedSearchRange : SmartCreeper.normalSearchRange;
        BlockPos center = new BlockPos(this.creeper);

        BlockPos nearestPos = null;
        double minDistance = Double.MAX_VALUE;

        // 计算需要搜索的区块范围
        int chunkRange = (range + 15) / 16; // 将方块范围转换为区块范围
        ChunkPos centerChunk = new ChunkPos(center);

        // 搜索周围区块
        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                Chunk chunk = this.creeper.world.getChunk(
                        centerChunk.x + dx,
                        centerChunk.z + dz
                );

                // 获取区块内所有方块实体位置
                Map<BlockPos, TileEntity> chunkTileEntities = chunk.getTileEntityMap();
                for (Map.Entry<BlockPos, TileEntity> entry : chunkTileEntities.entrySet()) {
                    BlockPos pos = entry.getKey();

                    // 检查是否在Y轴范围内
                    if (Math.abs(pos.getY() - center.getY()) > 3) {
                        continue;
                    }

                    // 检查是否在搜索范围内
                    if (pos.distanceSq(center.getX(), center.getY(), center.getZ()) > range * range) {
                        continue;
                    }

                    if (isTargetBlock(pos)) {
                        double distance = pos.distanceSq(center.getX(), center.getY(), center.getZ());
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestPos = pos;
                        }
                    }
                }
            }
        }
        return nearestPos;
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 持续检查猫的交互
        checkForCat();

        if (this.targetPos == null || !isTargetBlock(this.targetPos)) {
            return false;
        }

        int range = this.isChargedByCat ? SmartCreeper.chargedSearchRange : SmartCreeper.normalSearchRange;
        double maxDist = range * range;
        return this.creeper.getDistanceSqToCenter(this.targetPos) < maxDist;
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
            double distSq = this.creeper.getDistanceSqToCenter(this.targetPos);
            double explodeDist = 9.0; // 原版3格距离的平方

            if (distSq < explodeDist) {
                this.creeper.setCreeperState(1); // 点燃
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

    private boolean isTargetBlock(BlockPos pos) {
        Block block = this.world.getBlockState(pos).getBlock();
        String blockId = block.getRegistryName().toString();

        // 1. 首先检查精确指定的方块列表
        if (SmartCreeper.targetBlocks.contains(blockId)) {
            return true;
        }

        // 2. 如果启用了针对所有容器的选项
        if (SmartCreeper.targetAllContainers) {
            if (isContainerBlock(pos)) {
                return true;
            }
        }

        // 3. 检查通配符mod列表
        if (!SmartCreeper.wildcardMods.isEmpty()) {
            String modId = blockId.split(":")[0].toLowerCase();
            if (SmartCreeper.wildcardMods.contains(modId)) {
                // 只有是容器类方块才被选中
                return isContainerBlock(pos);
            }
        }

        return false;
    }

    /**
     * 判断方块是否为容器类方块
     * 通过检查TileEntity是否实现了存储相关接口来判断
     */
    private boolean isContainerBlock(BlockPos pos) {
        TileEntity tileEntity = this.world.getTileEntity(pos);
        if (tileEntity == null) {
            return false;
        }

        // 检查是否实现了各种存储接口
        boolean isContainer = false;

        // 原版物品容器接口
        if (tileEntity instanceof IInventory) {
            isContainer = true;
        }

        // Forge物品处理接口
        if (tileEntity.hasCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            isContainer = true;
        }

        // 能量存储接口
        if (tileEntity.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, null)) {
            isContainer = true;
        }

        // 流体存储接口
        if (tileEntity.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            isContainer = true;
        }

        // 特殊情况：一些mod的特殊方块类型检测
        String className = tileEntity.getClass().getSimpleName().toLowerCase();
        if (className.contains("machine") ||
                className.contains("generator") ||
                className.contains("storage") ||
                className.contains("controller") ||
                className.contains("interface") ||
                className.contains("terminal") ||
                className.contains("drive") ||
                className.contains("cell") ||
                className.contains("chest") ||
                className.contains("furnace") ||
                className.contains("processor") ||
                className.contains("assembler") ||
                className.contains("crafter") ||
                className.contains("tank")) {
            isContainer = true;
        }

        return isContainer;
    }

    public EntityCreeper getCreeper() {
        return this.creeper;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }
}