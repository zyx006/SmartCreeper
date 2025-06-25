package cn.czyx007.smartcreeper.util;

import cn.czyx007.smartcreeper.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ContainerTargetUtils {

    /**
     * 寻找最近的目标方块
     * @param entity 搜索的实体
     * @param searchRange 搜索范围
     * @param verticalRange Y轴搜索范围
     * @return 最近的目标方块位置，如果没有找到则返回null
     */
    public static BlockPos findNearestTargetBlock(Entity entity, int searchRange, int verticalRange) {
        Level level = entity.level();
        BlockPos center = entity.blockPosition();

        BlockPos nearestPos = null;
        double minDistance = Double.MAX_VALUE;

        // 计算需要搜索的区块范围
        int chunkRange = (searchRange + 15) / 16; // 将方块范围转换为区块范围
        ChunkPos centerChunk = new ChunkPos(center);

        // 搜索周围区块
        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                ChunkAccess chunk = level.getChunk(
                        centerChunk.x + dx,
                        centerChunk.z + dz
                );

                // 获取区块内所有方块实体位置
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    // 检查是否在Y轴范围内
                    if (Math.abs(pos.getY() - center.getY()) > verticalRange) {
                        continue;
                    }

                    // 检查是否在搜索范围内
                    if (pos.distSqr(center) > searchRange * searchRange) {
                        continue;
                    }

                    if (isTargetBlock(level, pos)) {
                        double distance = pos.distSqr(center);
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestPos = pos.immutable();
                        }
                    }
                }
            }
        }
        return nearestPos;
    }

    /**
     * 判断方块是否为目标方块
     */
    public static boolean isTargetBlock(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

        // 1. 首先检查精确指定的方块列表
        if (Config.TARGET_BLOCKS.get().contains(blockId)) {
            return true;
        }

        // 2. 如果启用了针对所有容器的选项
        if (Config.TARGET_ALL_CONTAINERS.get()) {
            if (isContainerBlock(level, pos)) {
                return true;
            }
        }

        // 3. 检查通配符mod列表
        if (!Config.WILDCARD_MODS.get().isEmpty()) {
            String modId = blockId.split(":")[0].toLowerCase();
            if (Config.WILDCARD_MODS.get().contains(modId)) {
                // 只有是容器类方块才被选中
                return isContainerBlock(level, pos);
            }
        }

        return false;
    }

    /**
     * 判断方块是否为容器类方块
     * 通过检查BlockEntity是否具有存储相关能力来判断
     */
    public static boolean isContainerBlock(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        // 检查是否具有各种存储能力
        boolean isContainer = false;

        // 物品存储能力
        var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (itemHandler != null) {
            isContainer = true;
        }

        // 能量存储能力
        var energyStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energyStorage != null) {
            isContainer = true;
        }

        // 流体存储能力
        var fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluidHandler != null) {
            isContainer = true;
        }

        // 检查是否为原版容器类型
        if (blockEntity instanceof Container) {
            isContainer = true;
        }

        // 特殊情况：通过类名检测一些mod的特殊方块类型
        String className = blockEntity.getClass().getSimpleName().toLowerCase();
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