package cn.czyx007.smartcreeper.goal;

import cn.czyx007.smartcreeper.Config;
import cn.czyx007.smartcreeper.util.ContainerTargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SkeletonShootContainerGoal extends Goal {
    private final AbstractSkeleton skeleton;
    private final Level level;
    private BlockPos targetPos = null;
    private int shootCooldown = 0;
    private int lastShootTime = 0;

    public SkeletonShootContainerGoal(AbstractSkeleton skeleton) {
        this.skeleton = skeleton;
        this.level = skeleton.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 检查配置是否启用骷髅射击容器功能
        if (!Config.SKELETON_TARGET_CONTAINERS.get()) {
            return false;
        }

        // 每隔一段时间才重新搜索目标
        if (this.skeleton.tickCount - this.lastShootTime < 20) { // 1秒间隔
            return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.level, this.targetPos);
        }

        this.targetPos = ContainerTargetUtils.findNearestTargetBlock(this.skeleton,
                Config.SKELETON_SEARCH_RANGE.get(), 4);
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 检查配置是否仍然启用
        if (!Config.SKELETON_TARGET_CONTAINERS.get()) {
            return false;
        }
        return this.targetPos != null && ContainerTargetUtils.isTargetBlock(this.level, this.targetPos);
    }

    @Override
    public void start() {
        this.shootCooldown = 0;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;

        double distance = this.skeleton.distanceToSqr(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
        double maxDistance = Config.SKELETON_SEARCH_RANGE.get() * Config.SKELETON_SEARCH_RANGE.get();

        // 如果不在射程内，移动到射程内
        if (distance > maxDistance) {
            this.skeleton.getNavigation().moveTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    0.8
            );
        } else {
            // 如果在射程内，停止移动并保持距离
            this.skeleton.getNavigation().stop();
        }

        // 看向目标
        this.skeleton.getLookControl().setLookAt(
                this.targetPos.getX() + 0.5,
                this.targetPos.getY() + 0.5,
                this.targetPos.getZ() + 0.5
        );

        // 射击冷却
        if (this.shootCooldown > 0) {
            this.shootCooldown--;
        }

        // 射击（只要在射程内）
        if (this.shootCooldown <= 0 && distance <= maxDistance) {
            shootAtTarget();
            this.shootCooldown = Config.SKELETON_SHOOT_COOLDOWN.get();
            this.lastShootTime = this.skeleton.tickCount;
        }
    }

    private void shootAtTarget() {
        Vec3 targetCenter = Vec3.atCenterOf(this.targetPos);
        Vec3 skeletonPos = this.skeleton.getEyePosition();

        Vec3 direction = targetCenter.subtract(skeletonPos).normalize();

        // 创建箭矢
        Arrow arrow = new Arrow(this.level, this.skeleton, new ItemStack(Items.ARROW), null);
        arrow.setPos(skeletonPos);

        // 设置箭矢速度和方向
        double speed = 1.6; // 箭矢速度
        arrow.shoot(direction.x, direction.y, direction.z, (float) speed, 1.0F);

        // 标记这支箭是针对容器的
        arrow.getPersistentData().putString("smartcreeper_target", "container");
        arrow.getPersistentData().putLong("target_pos", this.targetPos.asLong());

        this.level.addFreshEntity(arrow);

        // 播放射击音效
        this.skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.skeleton.getNavigation().stop();
    }
}