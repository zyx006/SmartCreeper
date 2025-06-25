package cn.czyx007.smartcreeper.util;

import cn.czyx007.smartcreeper.Config;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.Map;

public class ContainerTargetUtils {

    /**
     * 寻找最近的目标方块
     * @param entity 搜索的实体
     * @param searchRange 搜索范围
     * @param verticalRange Y轴搜索范围
     * @return 最近的目标方块位置，如果没有找到则返回null
     */
    public static BlockPos findNearestTargetBlock(Entity entity, int searchRange, int verticalRange) {
        World world = entity.world;
        BlockPos center = entity.getPosition();

        BlockPos nearestPos = null;
        double minDistance = Double.MAX_VALUE;

        // 计算需要搜索的区块范围
        int chunkRange = (searchRange + 15) / 16; // 将方块范围转换为区块范围
        ChunkPos centerChunk = new ChunkPos(center);

        // 搜索周围区块
        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                Chunk chunk = world.getChunk(
                        centerChunk.x + dx,
                        centerChunk.z + dz
                );

                // 获取区块内所有方块实体位置
                Map<BlockPos, TileEntity> chunkTileEntities = chunk.getTileEntityMap();
                for (Map.Entry<BlockPos, TileEntity> entry : chunkTileEntities.entrySet()) {
                    BlockPos pos = entry.getKey();

                    // 检查是否在Y轴范围内
                    if (Math.abs(pos.getY() - center.getY()) > verticalRange) {
                        continue;
                    }

                    // 检查是否在搜索范围内
                    if (pos.distanceSq(center.getX(), center.getY(), center.getZ()) > searchRange * searchRange) {
                        continue;
                    }

                    if (isTargetBlock(world, pos)) {
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

    /**
     * 判断方块是否为目标方块
     */
    public static boolean isTargetBlock(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        String blockId = block.getRegistryName().toString();

        // 1. 首先检查精确指定的方块列表
        for (String targetBlock : Config.TARGET_BLOCKS) {
            if (targetBlock.equals(blockId)) {
                return true;
            }
        }

        // 2. 如果启用了针对所有容器的选项
        if (Config.TARGET_ALL_CONTAINERS) {
            if (isContainerBlock(world, pos)) {
                return true;
            }
        }

        // 3. 检查通配符mod列表
        if (Config.WILDCARD_MODS.length > 0) {
            String modId = blockId.split(":")[0].toLowerCase();
            for (String wildcardMod : Config.WILDCARD_MODS) {
                if (wildcardMod.equals(modId)) {
                    // 只有是容器类方块才被选中
                    return isContainerBlock(world, pos);
                }
            }
        }

        return false;
    }

    /**
     * 判断方块是否为容器类方块
     * 通过检查TileEntity是否具有存储相关能力来判断
     */
    public static boolean isContainerBlock(World world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity == null) {
            return false;
        }

        // 检查是否具有各种存储能力
        boolean isContainer = false;

        // 物品存储能力
        if (tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            isContainer = true;
        }

        // 能量存储能力
        if (tileEntity.hasCapability(CapabilityEnergy.ENERGY, null)) {
            isContainer = true;
        }

        // 流体存储能力
        if (tileEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            isContainer = true;
        }

        // 检查是否为原版容器类型
        if (tileEntity instanceof IInventory) {
            isContainer = true;
        }

        // 特殊情况：通过类名检测一些mod的特殊方块类型
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