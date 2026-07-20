package com.starterkit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 树木连锁采集：玩家用斧头砍一块 log 时，先判断这块 log 是否属于一棵**自然树**，
 * 若是则把整棵树的所有 log 一起破坏。
 *
 *   - 判定"自然树"：以起始 log 为中心 BFS 收集所有相连的 log；对这批 log
 *     的 6 邻居检查——是否存在至少一片 `LeavesBlock` 且 `PERSISTENT=false`。
 *     PERSISTENT=false 只出现于世界生成/树苗长大时的天然叶子；玩家用剪刀
 *     采集后放置的叶子 PERSISTENT=true。这样能干净地区分"天然森林"和
 *     "玩家自建的木屋墙壁"。
 *   - 触发：主手斧头，起始方块属于 BlockTags.LOGS，非创造/观察者。
 *   - 邻居：26 邻域用于连通性；判定叶子只看 6 邻居，避免误判斜下方的其它
 *     树的叶子。
 *   - 上限：128 块（安全阀，正常巨型丛林树也就 100+）。
 *   - 耐久：每砍一块扣 1 点；耐久归零立即停。
 *   - 事件挂 AFTER：起始块已被原版事件破坏。若判定为树，破坏剩下的连通 log。
 */
public final class VeinMineHandler {

    private static final int MAX_BLOCKS = 128;

    /** 26 邻居 offset（含斜向），用于扫连通的 log。*/
    private static final int[][] NEIGHBOR_26;
    /** 6 邻居 offset（上下前后左右），用于判"旁边有没有叶子"。*/
    private static final int[][] NEIGHBOR_6 = {
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

    /** 防止 breakBlock 触发的 AFTER 再次进这里造成递归。*/
    private static final Set<java.util.UUID> IN_PROGRESS = new HashSet<>();

    private VeinMineHandler() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(VeinMineHandler::onBlockBroken);
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos,
                                      BlockState state, net.minecraft.block.entity.BlockEntity be) {
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        if (!VeinMineTracker.isEnabled(sp.getUuid())) return;
        if (IN_PROGRESS.contains(sp.getUuid())) return;

        ItemStack tool = sp.getMainHandStack();
        if (!(tool.getItem() instanceof AxeItem)) return;
        if (!state.isIn(BlockTags.LOGS)) return;

        IN_PROGRESS.add(sp.getUuid());
        try {
            List<BlockPos> logs = collectConnectedLogs(sw, pos);
            if (!isTree(sw, logs)) return; // 不是树，什么都不做
            breakAll(sw, sp, tool, logs);
        } finally {
            IN_PROGRESS.remove(sp.getUuid());
        }
    }

    /**
     * BFS 收集连通的 log。不包含起始块（起始块已被原版事件破坏）。
     * 硬上限 MAX_BLOCKS 防止一斧头扫掉巨型森林。
     */
    private static List<BlockPos> collectConnectedLogs(ServerWorld world, BlockPos origin) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin.toImmutable());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            for (int[] off : NEIGHBOR_26) {
                BlockPos next = current.add(off[0], off[1], off[2]);
                if (!visited.add(next.toImmutable())) continue;
                BlockState ns = world.getBlockState(next);
                if (!ns.isIn(BlockTags.LOGS)) continue;
                result.add(next.toImmutable());
                queue.add(next);
                if (result.size() >= MAX_BLOCKS) break;
            }
        }
        return result;
    }

    /**
     * 判定"这团 log 是否属于一棵自然树"：
     *   6 邻居内存在至少一片叶子 且 PERSISTENT == false。
     *
     * PERSISTENT=false → 世界生成或从树苗长大的天然叶子；
     * PERSISTENT=true  → 玩家用剪刀采回放置的叶子。
     * 玩家自建的木屋墙壁上方即使有叶子，也几乎都是 PERSISTENT=true 的。
     */
    private static boolean isTree(ServerWorld world, List<BlockPos> logs) {
        for (BlockPos log : logs) {
            for (int[] off : NEIGHBOR_6) {
                BlockPos leafPos = log.add(off[0], off[1], off[2]);
                BlockState s = world.getBlockState(leafPos);
                if (!(s.getBlock() instanceof LeavesBlock)) continue;
                if (!s.get(LeavesBlock.PERSISTENT)) return true;
            }
        }
        return false;
    }

    /** 逐块 breakBlock，走 vanilla 掉落逻辑，同时扣工具耐久，坏了停手。*/
    private static void breakAll(ServerWorld world, ServerPlayerEntity player,
                                 ItemStack tool, List<BlockPos> logs) {
        for (BlockPos p : logs) {
            world.breakBlock(p, true, player);
            if (tool.getMaxDamage() > 0) {
                tool.damage(1, player, pl -> pl.sendToolBreakStatus(Hand.MAIN_HAND));
                if (tool.isEmpty()) return;
            }
        }
    }
}
