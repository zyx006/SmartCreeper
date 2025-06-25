package cn.czyx007.smartcreeper.handler;

import cn.czyx007.smartcreeper.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;

public class ContainerHitHandler {
    private static final ConcurrentHashMap<BlockPos, HitData> hitMap = new ConcurrentHashMap<>();

    private static int getHitExpiryTime() {
        return Config.CONTAINER_HIT_EXPIRY_TIME.get();
    }

    private static int getRequiredHits() {
        return Config.CONTAINER_REQUIRED_HITS.get();
    }

    public static class HitData {
        public int hitCount;
        public long lastHitTime;

        public HitData() {
            this.hitCount = 1;
            this.lastHitTime = System.currentTimeMillis();
        }

        public void addHit() {
            this.hitCount++;
            this.lastHitTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - this.lastHitTime > getHitExpiryTime() * 50; // 转换为毫秒
        }
    }

    /**
     * 记录容器被箭矢击中
     *
     * @param level 世界
     * @param pos   容器位置
     * @return 如果达到爆炸条件返回true
     */
    public static boolean recordHit(Level level, BlockPos pos) {
        // 清理过期数据
        cleanupExpiredHits();

        HitData hitData = hitMap.get(pos);
        if (hitData == null) {
            hitMap.put(pos, new HitData());
            hitData = hitMap.get(pos);
        } else {
            if (hitData.isExpired()) {
                hitMap.put(pos, new HitData());
                hitData = hitMap.get(pos);
            } else {
                hitData.addHit();
            }
        }

        // 播放击中音效
        level.playSound(null, pos, SoundEvents.ARROW_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 检查是否达到爆炸条件
        if (hitData.hitCount >= getRequiredHits()) {
            explodeContainer(level, pos);
            hitMap.remove(pos);
            return true;
        }

        return false;
    }

    /**
     * 爆炸容器
     */
    private static void explodeContainer(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return;

        // 创建小型爆炸效果（不破坏其他方块）
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1.5F, Level.ExplosionInteraction.NONE);

        // 强制破坏方块
        level.destroyBlock(pos, false);

        // 播放爆炸音效
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
    }

    /**
     * 清理过期的击中记录
     */
    private static void cleanupExpiredHits() {
        hitMap.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 获取指定位置的击中次数
     */
    public static int getHitCount(BlockPos pos) {
        HitData hitData = hitMap.get(pos);
        if (hitData == null || hitData.isExpired()) {
            return 0;
        }
        return hitData.hitCount;
    }

    /**
     * 清除所有击中记录
     */
    public static void clearAllHits() {
        hitMap.clear();
    }
}