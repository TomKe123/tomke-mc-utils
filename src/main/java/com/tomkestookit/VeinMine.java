package com.tomkestookit;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * 连锁采集通用逻辑：BFS 收集 + 顺序破坏。
 *
 * 让两个"具体规则"（树木、矿物）共用同一套扫描/破坏骨架。上下文相关的判定
 * 由调用方提供 predicate 决定。
 */
public final class VeinMine {

    public static final int MAX_BLOCKS = 128;

    /** 26 邻居 offset（含斜向），用于连通性 BFS。*/
    public static final int[][] NEIGHBOR_26;
    /** 6 邻居 offset，用于判断相邻状态（不含斜向）。*/
    public static final int[][] NEIGHBOR_6 = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1},
    };

    static {
        NEIGHBOR_26 = new int[26][];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    NEIGHBOR_26[i++] = new int[]{dx, dy, dz};
                }
    }

    /** 防止 breakBlock 触发的 AFTER 递归。共享给所有连锁 handler。*/
    public static final Set<UUID> IN_PROGRESS = new HashSet<>();

    private VeinMine() {}

    /**
     * BFS 收集"与起始块连通、且 accept 判定为同类"的所有方块，最多 MAX_BLOCKS-1 个额外块。
     * 总破坏量（含起始块）最多 128。
     * 不包含起始块（起始已由原版事件破坏）。
     */
    public static List<BlockPos> collect(ServerWorld world, BlockPos origin, BlockState originState,
                                         BiPredicate<BlockState, BlockState> accept) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin.toImmutable());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        int maxAdditionalBlocks = MAX_BLOCKS - 1; // include the already-broken origin in the 128 total
        while (!queue.isEmpty() && result.size() < maxAdditionalBlocks) {
            BlockPos current = queue.poll();
            for (int[] off : NEIGHBOR_26) {
                BlockPos next = current.add(off[0], off[1], off[2]);
                if (!visited.add(next.toImmutable())) continue;
                BlockState ns = world.getBlockState(next);
                if (!accept.test(originState, ns)) continue;
                result.add(next.toImmutable());
                queue.add(next);
                if (result.size() >= maxAdditionalBlocks) break;
            }
        }
        return result;
    }

    /**
     * BFS 收集最多 maxTotalBlocks-1 个额外块。总破坏量包含起始块。
     * 用于白名单方块的“单次挖掘数量限制”：连通域再大也只连锁本次上限内的块。
     */
    public static List<BlockPos> collectUpToTotal(ServerWorld world, BlockPos origin,
                                                  BlockState originState,
                                                  BiPredicate<BlockState, BlockState> accept,
                                                  int maxTotalBlocks) {
        List<BlockPos> result = new ArrayList<>();
        if (maxTotalBlocks <= 1) return result;
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin.toImmutable());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        int maxAdditionalBlocks = maxTotalBlocks - 1;
        while (!queue.isEmpty() && result.size() < maxAdditionalBlocks) {
            BlockPos current = queue.poll();
            for (int[] off : NEIGHBOR_26) {
                BlockPos next = current.add(off[0], off[1], off[2]);
                if (!visited.add(next.toImmutable())) continue;
                BlockState ns = world.getBlockState(next);
                if (!accept.test(originState, ns)) continue;
                result.add(next.toImmutable());
                queue.add(next);
                if (result.size() >= maxAdditionalBlocks) break;
            }
        }
        return result;
    }

    /**
     * Collects the complete connected component and returns it only when the total
     * size, including the already-broken origin block, is at most maxTotalBlocks.
     * If the component is larger, returns an empty list so terrain is not partially chained.
     */
    public static List<BlockPos> collectIfTotalAtMost(ServerWorld world, BlockPos origin,
                                                      BlockState originState,
                                                      BiPredicate<BlockState, BlockState> accept,
                                                      int maxTotalBlocks) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin.toImmutable());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        int totalAccepted = 1; // include the already-broken origin block
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int[] off : NEIGHBOR_26) {
                BlockPos next = current.add(off[0], off[1], off[2]);
                if (!visited.add(next.toImmutable())) continue;
                BlockState ns = world.getBlockState(next);
                if (!accept.test(originState, ns)) continue;

                totalAccepted++;
                if (totalAccepted > maxTotalBlocks) {
                    return List.of();
                }

                result.add(next.toImmutable());
                queue.add(next);
            }
        }
        return result;
    }

    /**
     * 逐块破坏，掉落按工具附魔计算（精准采集/时运生效）。
     * 每块扣工具 1 点耐久，坏了停手。
     *
     * 实现细节：vanilla 的 world.breakBlock(pos, true, player) 内部调用
     * Block.dropStacks(..., ItemStack.EMPTY) —— 空工具，附魔无效。所以拆两步：
     *   1) breakBlock(pos, false, player) —— 保留破坏动画/音效/更新，不掉落
     *   2) 手动 Block.dropStacks(state, world, pos, be, player, tool)
     *      —— 按实际工具解析 loot table，精准/时运/掉落 XP 全部生效
     */
    public static void breakAll(ServerWorld world, ServerPlayerEntity player,
                                ItemStack tool, List<BlockPos> positions) {
        for (BlockPos p : positions) {
            BlockState state = world.getBlockState(p);
            if (state.isAir()) continue;
            BlockEntity be = state.hasBlockEntity() ? world.getBlockEntity(p) : null;

            // 破坏前先算好 tool 副本用于 dropStacks，避免耐久归零后 dropStacks
            // 拿到 EMPTY 导致精准/时运丢失
            ItemStack toolForDrop = tool.copy();

            if (world.breakBlock(p, false, player)) {
                Block.dropStacks(state, world, p, be, player, toolForDrop);
            }

            if (tool.getMaxDamage() > 0) {
                tool.damage(1, player, pl -> pl.sendToolBreakStatus(Hand.MAIN_HAND));
                if (tool.isEmpty()) return;
            }
        }
    }

    /**
     * 判两个 BlockState 是否共享至少一个 tag（stream 版）。
     * 用于矿物"同类判定"：两个矿共享至少一个 _ores tag 即算同类。
     */
    public static boolean sharesTag(BlockState a, BlockState b,
                                    java.util.function.Predicate<TagKey<net.minecraft.block.Block>> filter) {
        Set<TagKey<net.minecraft.block.Block>> aTags = new HashSet<>();
        a.streamTags().forEach(t -> { if (filter.test(t)) aTags.add(t); });
        return b.streamTags().anyMatch(t -> filter.test(t) && aTags.contains(t));
    }
}
