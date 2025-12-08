package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SkeletonShootContainerGoal extends EntityAIBase {
    private final AbstractSkeleton skeleton;
    private final World world;
    private BlockPos targetPos = null;
    private int shootCooldown = 0;
    private int lastShootTime = 0;

    public SkeletonShootContainerGoal(AbstractSkeleton skeleton) {
        this.skeleton = skeleton;
        this.world = skeleton.world;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        // 检查配置是否启用骷髅射击容器功能
        if (!Config.SKELETON_TARGET_CONTAINERS) {
            return false;
        }

        // 每隔一段时间才重新搜索目标
        if (this.skeleton.ticksExisted - this.lastShootTime < 20) { // 1秒间隔
            return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.world, this.targetPos);
        }

        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.skeleton,
                Config.SKELETON_SEARCH_RANGE, 4);
        return this.targetPos != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 检查配置是否仍然启用
        if (!Config.SKELETON_TARGET_CONTAINERS) {
            return false;
        }
        return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.world, this.targetPos);
    }

    @Override
    public void startExecuting() {
        this.shootCooldown = 0;
    }

    @Override
    public void updateTask() {
        if (this.targetPos == null) return;

        double distance = this.skeleton.getDistanceSq(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
        double maxDistance = Config.SKELETON_SEARCH_RANGE * Config.SKELETON_SEARCH_RANGE;

        // 如果不在射程内，移动到射程内
        if (distance > maxDistance) {
            this.skeleton.getNavigator().tryMoveToXYZ(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    0.8
            );
        } else {
            // 如果在射程内，停止移动并保持距离
            this.skeleton.getNavigator().clearPath();
        }

        // 看向目标
        this.skeleton.getLookHelper().setLookPosition(
                this.targetPos.getX() + 0.5,
                this.targetPos.getY() + 0.5,
                this.targetPos.getZ() + 0.5,
                30.0F, 30.0F
        );

        // 射击冷却
        if (this.shootCooldown > 0) {
            this.shootCooldown--;
        }

        // 射击（必须射程内 + 有视线）
        if (this.shootCooldown <= 0 && distance <= maxDistance) {
            Vec3d skeletonEye = new Vec3d(
                    this.skeleton.posX,
                    this.skeleton.posY + this.skeleton.getEyeHeight(),
                    this.skeleton.posZ
            );
            Vec3d targetCenter = new Vec3d(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY() + 0.5,
                    this.targetPos.getZ() + 0.5
            );

            if (hasLineOfSight(skeletonEye, targetCenter)) {
                shootAtTarget();
                this.shootCooldown = Config.SKELETON_SHOOT_COOLDOWN;
                this.lastShootTime = this.skeleton.ticksExisted;
            } else {
                this.skeleton.getNavigator().tryMoveToXYZ(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        0.8
                );
            }
        }
    }

    /**
     * 判断 from → to 是否无方块阻挡
     */
    private boolean hasLineOfSight(Vec3d from, Vec3d to) {
        // 使用 RAY_TRACE_BLOCKS 参数，只检测方块阻挡
        RayTraceResult result = this.world.rayTraceBlocks(from, to, false, true, false);

        if (result == null) {
            return true; // 无阻挡
        }

        if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
            // 允许命中的方块就是目标方块（例如直接射箱子）
            return result.getBlockPos().equals(this.targetPos);
        }

        // 其他情况视为阻挡
        return false;
    }

    private void shootAtTarget() {
        Vec3d targetCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
        Vec3d skeletonPos = new Vec3d(this.skeleton.posX, this.skeleton.posY + this.skeleton.getEyeHeight(), this.skeleton.posZ);

        Vec3d direction = targetCenter.subtract(skeletonPos).normalize();

        // 创建箭矢
        EntityArrow arrow = new EntityTippedArrow(this.world, this.skeleton);
        arrow.setPosition(skeletonPos.x, skeletonPos.y, skeletonPos.z);

        // 设置箭矢速度和方向
        double speed = 1.6; // 箭矢速度
        arrow.motionX = direction.x * speed;
        arrow.motionY = direction.y * speed;
        arrow.motionZ = direction.z * speed;

        // 标记这支箭是针对容器的
        arrow.getEntityData().setString("smartcreeper_target", "container");
        arrow.getEntityData().setLong("target_pos", this.targetPos.toLong());

        this.world.spawnEntity(arrow);

        // 播放射击音效
        this.skeleton.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.skeleton.getRNG().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public void resetTask() {
        this.targetPos = null;
        this.skeleton.getNavigator().clearPath();
    }
}