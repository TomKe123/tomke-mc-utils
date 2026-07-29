package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家独立的自然刷怪配额。
 *
 * 目标：在多人服上让每个玩家周围看到的怪物数量像单人一样。默认 vanilla 走
 * SpawnHelper$Info.isBelowCap，通过 SpawnDensityCapper 对"这个 chunk 的所有观察玩家"
 * 共享一份 per-group 密度上限——玩家越多整体密度反而越低。本类把判定改成：
 * 拿"离这个刷怪 chunk 最近的、且在其模拟距离内的玩家"作为归属，只查该玩家周围
 * 模拟距离范围内的同群组怪物数量，跟 vanilla 的 {@link SpawnGroup#getCapacity()} 比。
 *
 * 归属规则 = "最近有效玩家"：
 *   - 谁离这个 chunk 最近，这只未来会刷出来的怪就算谁的
 *   - "有效"= 玩家在自己的模拟距离内能看见 chunk（chebyshev &lt;= simulation distance）
 *   - 没有任何有效玩家 → 直接返回 false（vanilla 里没有玩家也不会刷这个 chunk，
 *     所以这个 case 一般不会到；兜底避免"归属不确定就放行"）
 *
 * 计数窗口 = "玩家周围模拟距离范围内的同群组 MobEntity 数"：
 *   - 与 vanilla getCapacity() 的语义匹配（vanilla 的分子是"整世界同群组数"，
 *     分母 = getCapacity() * spawningChunkCount / 289；单人服 spawningChunkCount
 *     基本就是模拟距离内 chunk 数，比值和"cap per player"数量级一致）
 *   - 遍历世界所有实体一次，按 owner 分组统计，同一 tick 内后续查询命中缓存
 *
 * 与其他 mod 的兼容：
 *   - 我们通过 mixin priority = 2000 + HEAD cancellable setReturnValue 直接短路，
 *     其他 mod 对 isBelowCap 的 @Inject 都不会走到；相当于最高优先级
 *   - 关掉 {@code world.per_player_spawn} 开关时立即回落到 vanilla，别的 mod 各自
 *     的逻辑恢复正常，没有持久化副作用
 *
 * World 反查：{@link net.minecraft.world.SpawnHelper.Info} 内部没有 world 引用。
 * 之前尝试过在 SpawnHelper.spawn 上再挂一个 mixin 用 ThreadLocal 传，但这个版本组合的
 * sponge-mixin (0.17.3 / mixin 0.8.7 + loom 1.9) 对 SpawnHelper.spawn 的 @Inject
 * 有 target 找不到的 apply crash（refmap 里描述符 remap 正确，但运行时 target 校验
 * 阶段读到的仍是 yarn 名，稳定复现）。绕开：{@link ServerChunkManager#getSpawnInfo}
 * 在 world tick 期间返回当前 Info，遍历 {@link MinecraftServer#getWorlds()} 找到
 * 那个 spawnInfo == this 的 world 即为归属。server 引用在 ServerLifecycleEvents 里存。
 */
public final class PerPlayerSpawnCap {

    /** 由 {@link #register} 在 STARTED 事件里存下，用于世界反查；STOPPED 清空。*/
    private static MinecraftServer server;

    /** 按 world 做 tick 级别的缓存：同一 tick 内不重复扫实体。*/
    private static final Map<ServerWorld, Cache> CACHES = new HashMap<>();

    private PerPlayerSpawnCap() {}

    /** 由 TomkesTookitMod.onInitialize 调用一次，登记生命周期回调。*/
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; CACHES.clear(); });
    }

    /**
     * Vanilla SpawnHelper$Info.isBelowCap 的替代实现。
     * @param info 调用方（SpawnHelperInfoMixin）传入 (SpawnHelper.Info) this
     * @return true = 该 SpawnGroup 可以继续在这个 chunk 尝试刷怪
     */
    public static boolean isBelowCap(SpawnHelper.Info info, SpawnGroup group, ChunkPos chunkPos) {
        ServerWorld world = findWorldFor(info);
        if (world == null) return true;   // 兜底：拿不到 world 就不阻止 vanilla 逻辑

        int simDistChunks = simulationDistanceChunks(world);
        ServerPlayerEntity owner = findClosestPlayer(world, chunkPos, simDistChunks);
        if (owner == null) return false;  // 没归属就不刷，避免"无主怪"堆积

        Cache cache = getCache(world);
        int count = cache.countFor(owner, group, simDistChunks);
        return count < group.getCapacity();
    }

    /**
     * 反查这个 Info 属于哪个 world：遍历所有 world，谁 chunkManager.getSpawnInfo() == info 就是它。
     * vanilla ServerChunkManager.tickChunks 在调 SpawnHelper.spawn 前会把新 Info putfield 到自己身上，
     * 整个 spawn 循环期间不变。所以 isBelowCap 触发时反查一定命中。
     */
    private static ServerWorld findWorldFor(SpawnHelper.Info info) {
        MinecraftServer s = server;
        if (s == null) return null;
        for (ServerWorld w : s.getWorlds()) {
            if (w.getChunkManager().getSpawnInfo() == info) return w;
        }
        return null;
    }

    /** 玩家离开 / 世界卸载时的兜底：不主动清，Cache 每 tick 自动过期。这里保留个入口。*/
    public static void invalidate(ServerWorld world) {
        CACHES.remove(world);
    }

    // ------------------------------------------------------------------ internals

    private static int simulationDistanceChunks(ServerWorld world) {
        // simulation distance 是玩家单位，最低为 1 chunk；vanilla 默认 10
        int d = world.getServer().getPlayerManager().getSimulationDistance();
        return Math.max(1, d);
    }

    private static ServerPlayerEntity findClosestPlayer(ServerWorld world, ChunkPos chunkPos, int simDistChunks) {
        ServerPlayerEntity best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            int d = new ChunkPos(p.getBlockPos()).getChebyshevDistance(chunkPos);
            if (d > simDistChunks) continue;
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static Cache getCache(ServerWorld world) {
        long tick = world.getTime();
        Cache c = CACHES.get(world);
        if (c == null || c.tick != tick) {
            c = new Cache(tick);
            CACHES.put(world, c);
        }
        return c;
    }

    /**
     * 单 tick 内的实体分组计数缓存。
     * 首次 countFor 会 O(N) 扫一遍全世界的 MobEntity 并按 (owner-uuid, group) 聚合；
     * 后续同 tick 查询 O(1)。
     */
    private static final class Cache {
        final long tick;
        /** owner 玩家 UUID → 该玩家名下每个 SpawnGroup 的当前存活数。*/
        private Map<UUID, int[]> byOwner;
        /** cache 参数一旦决定就固定：simDistChunks 影响归属，一 tick 内认为不变。*/
        private int simDistChunks = -1;

        Cache(long tick) { this.tick = tick; }

        int countFor(ServerPlayerEntity owner, SpawnGroup group, int simDistChunks) {
            ensure(owner.getServerWorld(), simDistChunks);
            int[] arr = byOwner.get(owner.getUuid());
            return arr == null ? 0 : arr[group.ordinal()];
        }

        private void ensure(ServerWorld world, int simDistChunks) {
            if (byOwner != null && this.simDistChunks == simDistChunks) return;
            this.simDistChunks = simDistChunks;
            byOwner = new HashMap<>();

            // 预取玩家一次，按玩家扫时避免每只怪都遍历一次玩家列表
            java.util.List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) return;

            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof MobEntity mob)) continue;
                // 只算"自然刷怪 cap 里应该被算的" —— vanilla 的 Info.run 也只统计
                // 非持久化怪（!cannotDespawn），我们贴近这个语义避免把命名怪、boss 也算进去
                if (mob.cannotDespawn()) continue;

                SpawnGroup g = mob.getType().getSpawnGroup();
                if (g == SpawnGroup.MISC) continue;

                ChunkPos cp = new ChunkPos(mob.getBlockPos());
                ServerPlayerEntity closest = null;
                int bestDist = Integer.MAX_VALUE;
                for (ServerPlayerEntity p : players) {
                    if (p.isSpectator()) continue;
                    int d = new ChunkPos(p.getBlockPos()).getChebyshevDistance(cp);
                    if (d > simDistChunks) continue;
                    if (d < bestDist) {
                        bestDist = d;
                        closest = p;
                    }
                }
                if (closest == null) continue;

                int[] arr = byOwner.computeIfAbsent(closest.getUuid(),
                    k -> new int[SpawnGroup.values().length]);
                arr[g.ordinal()]++;
            }
        }
    }
}
