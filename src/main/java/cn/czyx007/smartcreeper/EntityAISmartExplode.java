package cn.czyx007.smartcreeper;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.List;

public class EntityAISmartExplode extends EntityAIBase {
    private final EntityCreeper creeper;
    private final World world;
    private BlockPos targetPos = null;
    private int searchCooldown = 0;
    private boolean isChargedByCat = false;

    // 用于优化搜索的缓存
    private Set<BlockPos> searchedPositions = new HashSet<>();
    private int lastSearchTick = 0;

    public EntityAISmartExplode(EntityCreeper creeper) {
        this.creeper = creeper;
        this.world = creeper.world;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        // 始终检查猫的交互（无论是否在执行其他任务）
        checkForCat();

        // 冷却时间检查
        if (this.searchCooldown > 0) {
            this.searchCooldown--;
            return this.targetPos != null; // 如果已有目标则继续执行
        }

        // 查找目标方块
        this.targetPos = findNearestTargetBlock();

        if (this.targetPos != null) {
            this.searchCooldown = 20; // 1秒冷却，减少性能消耗
            return true;
        }

        return false;
    }

    private void checkForCat() {
        if (!SmartCreeper.fearlessOfCats) return;

        // 检查6格范围内是否有猫
        List<EntityOcelot> cats = this.world.getEntitiesWithinAABB(EntityOcelot.class,
                this.creeper.getEntityBoundingBox().grow(6.0));

        if (!cats.isEmpty() && !this.creeper.getPowered()) {
            this.creeper.onStruckByLightning(null); // 变成高压苦力怕
            this.creeper.setHealth(this.creeper.getMaxHealth());
            this.isChargedByCat = true;

            // 重置寻路以立即响应新状态
            this.creeper.getNavigator().clearPath();
            this.searchCooldown = 0; // 立即重新搜索
            this.searchedPositions.clear(); // 清除搜索缓存
        }
    }

    /**
     * 使用广度优先搜索算法优化寻找最近目标方块的效率
     */
    private BlockPos findNearestTargetBlock() {
        // 确定搜索范围
        int range = this.isChargedByCat ? SmartCreeper.chargedSearchRange : SmartCreeper.normalSearchRange;
        BlockPos center = new BlockPos(this.creeper);

        // 如果与上次搜索位置相同且时间间隔较短，使用缓存
        int currentTick = this.creeper.ticksExisted;
        if (Math.abs(currentTick - this.lastSearchTick) < 20 && this.searchedPositions.contains(center)) {
            return null; // 避免频繁搜索同一区域
        }

        this.lastSearchTick = currentTick;

        // 使用BFS进行搜索，优先找到最近的目标
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.offer(center);
        visited.add(center);

        // 定义6个方向的搜索（上下左右前后）
        int[][] directions = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // 检查当前位置是否是目标方块
            if (isTargetBlock(current)) {
                this.searchedPositions.add(center);
                return current;
            }

            // 扩展搜索到邻近位置
            for (int[] dir : directions) {
                BlockPos neighbor = current.add(dir[0], dir[1], dir[2]);

                // 检查是否在搜索范围内
                if (Math.abs(neighbor.getX() - center.getX()) > range ||
                        Math.abs(neighbor.getY() - center.getY()) > 3 ||
                        Math.abs(neighbor.getZ() - center.getZ()) > range) {
                    continue;
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }

            // 限制搜索深度以避免性能问题
            if (visited.size() > range * range * 6) {
                break;
            }
        }

        this.searchedPositions.add(center);
        return null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 持续检查猫的交互
        checkForCat();

        if (this.targetPos == null || !isTargetBlock(this.targetPos)) {
            return false;
        }

        // 使用原版的判断距离（约8-10格）
        double maxDist = 100.0; // 10格距离
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
                if (isContainerBlock(pos)) {
                    return true;
                }
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
}