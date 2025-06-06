package cn.czyx007.smartcreeper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.*;

public class SmartExplodeGoal extends Goal {
    private final Creeper creeper;
    private final Level level;
    private BlockPos targetPos = null;
    private boolean isChargedByCat = false;

    public SmartExplodeGoal(Creeper creeper) {
        this.creeper = creeper;
        this.level = creeper.level();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 始终检查猫的交互（无论是否在执行其他任务）
        checkForCat();
        // 查找目标方块
        this.targetPos = findNearestTargetBlock();
        return this.targetPos != null;
    }

    private void checkForCat() {
        if (!Config.FEARLESS_OF_CATS.get()) return;

        // 检查6格范围内是否有猫
        AABB searchArea = this.creeper.getBoundingBox().inflate(6.0);
        List<Cat> cats = level.getEntitiesOfClass(Cat.class, searchArea);
        List<Ocelot> ocelots = level.getEntitiesOfClass(Ocelot.class, searchArea);

        List<Animal> catsAndOcelots = new ArrayList<>();
        catsAndOcelots.addAll(cats);
        catsAndOcelots.addAll(ocelots);

        if (!catsAndOcelots.isEmpty() && !this.creeper.isPowered()) {
            if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                LightningBolt lightningBolt = new LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
                lightningBolt.setDamage(0f);
                this.creeper.thunderHit(serverLevel, lightningBolt);
            }
            this.creeper.setHealth(this.creeper.getMaxHealth());
            this.isChargedByCat = true;

            // 重置寻路以响应新状态
            this.creeper.getNavigation().stop();
        }
    }

    /**
     * 寻找最近目标方块
     */
    private BlockPos findNearestTargetBlock() {
        int range = this.isChargedByCat ?
                Config.CHARGED_SEARCH_RANGE.get() :
                Config.NORMAL_SEARCH_RANGE.get();
        BlockPos center = this.creeper.blockPosition();

        BlockPos nearestPos = null;
        double minDistance = Double.MAX_VALUE;

        // 计算需要搜索的区块范围
        int chunkRange = (range + 15) / 16; // 将方块范围转换为区块范围
        ChunkPos centerChunk = new ChunkPos(center);

        // 搜索周围区块
        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                ChunkAccess chunk = this.creeper.level().getChunk(
                        centerChunk.x + dx,
                        centerChunk.z + dz
                );

                // 获取区块内所有方块实体位置
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    // 检查是否在Y轴范围内
                    if (Math.abs(pos.getY() - center.getY()) > 3) {
                        continue;
                    }

                    // 检查是否在搜索范围内
                    if (pos.distSqr(center) > range * range) {
                        continue;
                    }

                    if (isTargetBlock(pos)) {
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


    @Override
    public boolean canContinueToUse() {
        // 持续检查猫的交互
        checkForCat();
        if (this.targetPos == null || !isTargetBlock(this.targetPos)) {
            return false;
        }

        int range = this.isChargedByCat ?
                Config.CHARGED_SEARCH_RANGE.get() :
                Config.NORMAL_SEARCH_RANGE.get();
        double maxDist = range * range;
        return this.creeper.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5) < maxDist;
    }

    @Override
    public void start() {
        if (this.targetPos != null) {
            // 使用原版速度 (1.0)
            this.creeper.getNavigation().moveTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    1.0);
        }
    }

    @Override
    public void tick() {
        if (this.targetPos != null) {
            // 检查距离 - 使用原版爆炸触发距离
            double distSq = this.creeper.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
            double explodeDist = 9.0; // 原版3格距离的平方

            if (distSq < explodeDist) {
                this.creeper.ignite(); // 点燃
            } else {
                // 继续移动 - 使用原版速度
                this.creeper.getNavigation().moveTo(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        1.0);
            }
        }
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.creeper.getNavigation().stop();
    }

    private boolean isTargetBlock(BlockPos pos) {
        Block block = this.level.getBlockState(pos).getBlock();
        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();

        // 1. 首先检查精确指定的方块列表
        if (Config.TARGET_BLOCKS.get().contains(blockId)) {
            return true;
        }

        // 2. 如果启用了针对所有容器的选项
        if (Config.TARGET_ALL_CONTAINERS.get()) {
            if (isContainerBlock(pos)) {
                return true;
            }
        }

        // 3. 检查通配符mod列表
        if (!Config.WILDCARD_MODS.get().isEmpty()) {
            String modId = blockId.split(":")[0].toLowerCase();
            if (Config.WILDCARD_MODS.get().contains(modId)) {
                // 只有是容器类方块才被选中
                return isContainerBlock(pos);
            }
        }

        return false;
    }

    /**
     * 判断方块是否为容器类方块
     * 通过检查BlockEntity是否具有存储相关能力来判断
     */
    private boolean isContainerBlock(BlockPos pos) {
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        // 检查是否具有各种存储能力
        boolean isContainer = false;

        // 物品存储能力
        var itemHandler = this.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (itemHandler != null) {
            isContainer = true;
        }

        // 能量存储能力
        var energyStorage = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energyStorage != null) {
            isContainer = true;
        }

        // 流体存储能力
        var fluidHandler = this.level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluidHandler != null) {
            isContainer = true;
        }

        // 检查是否为原版容器类型
        if (blockEntity instanceof net.minecraft.world.Container) {
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

    public Creeper getCreeper() {
        return this.creeper;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }
}