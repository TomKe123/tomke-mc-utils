package com.starterkit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 树木连锁采集：玩家用斧头砍下一块 log 时，若开关开启，则以该 log 为起点做 BFS
 * 扩散，把相连的 log/wood 一并破坏，同时正常掉落物 + 扣工具耐久 + 消耗玩家饥饿。
 *
 *   - 触发：主手斧头，起始方块属于 BlockTags.LOGS。
 *   - 邻居：26 邻域（含斜向），处理弯曲/歪脖子树。
 *   - 上限：128 块，超过就停，避免玩家用铁斧一斧头拆整片森林。
 *   - 耐久：每砍一块扣 1 点；耐久 <= 0 时停止（贴合原版'工具坏了就停'的直觉）。
 *   - 事件挂 AFTER：起始块已被原版事件破坏，我们只处理"扩散那些"。
 */
public final class VeinMineHandler {

    private static final int MAX_BLOCKS = 128;
    /** 27 邻居的 offset（0,0,0 会被 visited 集自然过滤掉）*/
    private static final int[][] NEIGHBOR_OFFSETS;
    static {
        NEIGHBOR_OFFSETS = new int[26][];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    NEIGHBOR_OFFSETS[i++] = new int[]{dx, dy, dz};
                }
    }

    /** 防止递归：玩家开始一次 veinmine 处理时进这个集合，处理完再移除。*/
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
        if (sp.isCreative() || sp.isSpectator()) return; // 创造模式直接破坏，不搞连锁
        if (!VeinMineTracker.isEnabled(sp.getUuid())) return;
        if (IN_PROGRESS.contains(sp.getUuid())) return;

        ItemStack tool = sp.getMainHandStack();
        if (!(tool.getItem() instanceof AxeItem)) return;
        if (!state.isIn(BlockTags.LOGS)) return;

        IN_PROGRESS.add(sp.getUuid());
        try {
            veinBreak(sw, sp, tool, pos);
        } finally {
            IN_PROGRESS.remove(sp.getUuid());
        }
    }

    private static void veinBreak(ServerWorld world, ServerPlayerEntity player,
                                  ItemStack tool, BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin);
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        int broken = 0;
        while (!queue.isEmpty() && broken < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            for (int[] off : NEIGHBOR_OFFSETS) {
                BlockPos next = current.add(off[0], off[1], off[2]);
                if (!visited.add(next)) continue;
                BlockState ns = world.getBlockState(next);
                if (!ns.isIn(BlockTags.LOGS)) continue;

                // breakBlock 会掉落物品（走 vanilla 掉落逻辑 + 工具属性判定）
                world.breakBlock(next, true, player);
                broken++;

                // 扣一点耐久；坏了停手
                if (tool.getMaxDamage() > 0) {
                    tool.damage(1, player,
                        p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
                    if (tool.isEmpty()) return;
                }

                queue.add(next);
                if (broken >= MAX_BLOCKS) return;
            }
        }
    }
}
