package com.tomkestookit.entitycap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.EnumSet;
import java.util.List;

/**
 * 每玩家 128 格半径内的被动生成实体数量上限，上限根据 MSPT 动态调节。
 *
 * MSPT → 上限映射（线性插值）：
 *   MSPT ≤ 40 ms  → 上限 = CAP_HIGH (200)
 *   MSPT ≥ 55 ms  → 上限 = CAP_LOW  (50)
 *   之间线性插值
 *
 * 触发时机：ServerWorld.addEntity HEAD（通过 mixin）
 * 被动 SpawnReason：NATURAL / CHUNK_GENERATION / SPAWNER / STRUCTURE / PATROL / JOCKEY / EVENT
 * 主动放行：BREEDING / CONVERSION / REINFORCEMENT / MOB_SUMMONED / TRIGGERED / BUCKET /
 *           SPAWN_EGG / COMMAND / DISPENSER
 */
public final class EntityCap {

    /** 检查半径（刷怪距离）。*/
    public static final double RADIUS = 128.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    /** TPS 正常时的上限（MSPT ≤ 40）。*/
    public static final int CAP_HIGH = 1000;
    /** TPS 过载时的最低上限（MSPT ≥ 55）。*/
    public static final int CAP_LOW = 200;

    private static final double MSPT_GOOD = 40.0;
    private static final double MSPT_BAD  = 55.0;

    /** 被视为"被动/世界自发"的 SpawnReason 白名单。*/
    public static final EnumSet<SpawnReason> PASSIVE_REASONS = EnumSet.of(
        SpawnReason.NATURAL,
        SpawnReason.CHUNK_GENERATION,
        SpawnReason.SPAWNER,
        SpawnReason.STRUCTURE,
        SpawnReason.PATROL,
        SpawnReason.JOCKEY,
        SpawnReason.EVENT
    );

    /** 当前生效的上限，由 Sidebar.refresh() 每秒更新。*/
    private static volatile int currentCap = CAP_HIGH;

    private EntityCap() {}

    // ------------------------------------------------------------------ dynamic cap

    /**
     * 由 Sidebar / tick 每秒调用，根据当前 MSPT 重新计算上限。
     * volatile 写保证其他线程可见（spawn 在 server tick thread 上，同一线程，实际无并发问题）。
     */
    public static void updateCap(double mspt) {
        if (mspt <= MSPT_GOOD) {
            currentCap = CAP_HIGH;
        } else if (mspt >= MSPT_BAD) {
            currentCap = CAP_LOW;
        } else {
            double ratio = (mspt - MSPT_GOOD) / (MSPT_BAD - MSPT_GOOD); // 0.0 → 1.0
            currentCap = (int) Math.round(CAP_HIGH - ratio * (CAP_HIGH - CAP_LOW));
        }
    }

    /** 获取当前动态上限（供 Sidebar 读取）。*/
    public static int getCurrentCap() {
        return currentCap;
    }

    // ------------------------------------------------------------------ spawn gate

    /**
     * 判定这个实体是否应该被上限拒绝。
     * @return true = 拒绝生成
     */
    public static boolean shouldReject(ServerWorld world, Entity entity) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("world.entity_cap")) return false;
        if (!(entity instanceof MobEntity mob)) return false;
        if (mob.cannotDespawn()) return false;
        SpawnGroup group = mob.getType().getSpawnGroup();
        if (group == SpawnGroup.MISC) return false;

        SpawnReason reason = ((SpawnReasonHolder) entity).tomkestookit$getSpawnReason();
        if (reason == null) return false;
        if (!PASSIVE_REASONS.contains(reason)) return false;

        ServerPlayerEntity closest = findClosestPlayer(world, entity.getX(), entity.getY(), entity.getZ());
        if (closest == null) return false;

        int count = countPassiveMobsAround(world, closest);
        return count >= currentCap;
    }

    /**
     * 统计指定玩家 128 格内被动生成的 MobEntity 数量（供 Sidebar 显示）。
     */
    public static int countForPlayer(ServerWorld world, ServerPlayerEntity player) {
        return countPassiveMobsAround(world, player);
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayerEntity findClosestPlayer(ServerWorld world, double x, double y, double z) {
        ServerPlayerEntity best = null;
        double bestSq = RADIUS_SQ;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            if (com.tomkestookit.CarpetBotUtil.isFakePlayer(p)) continue;
            double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= bestSq) { bestSq = sq; best = p; }
        }
        return best;
    }

    private static int countPassiveMobsAround(ServerWorld world, ServerPlayerEntity player) {
        Box box = new Box(
            player.getX() - RADIUS, player.getY() - RADIUS, player.getZ() - RADIUS,
            player.getX() + RADIUS, player.getY() + RADIUS, player.getZ() + RADIUS);
        List<MobEntity> nearby = world.getEntitiesByClass(MobEntity.class, box,
            m -> !m.cannotDespawn() && isPassiveSpawn(m));
        return nearby.size();
    }

    private static boolean isPassiveSpawn(MobEntity mob) {
        SpawnReason r = ((SpawnReasonHolder) mob).tomkestookit$getSpawnReason();
        return r != null && PASSIVE_REASONS.contains(r);
    }
}
