package com.tomkestookit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 连锁采集分派器。
 *
 * 触发通用条件（除起始块与工具外的公共约束）：
 *   - 玩家开关开启（VeinMineTracker）
 *   - 玩家 shift 按住
 *   - 玩家非创造/观察者
 *   - 事件挂 AFTER：起始块已被原版事件破坏
 *
 * 分派规则：
 *   - 起始块属于 BlockTags.LOGS + 主手斧头 → 走 tree 分支
 *   - 起始块所在的任意 tag 名以 "_ores" 结尾 + 主手镐 → 走 ore 分支
 *
 * 每个分支各自做二次判定（树：是否自然树；矿：连片同类矿）。
 */
public final class VeinMineHandler {

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
        if (!sp.isSneaking()) return;
        if (VeinMine.IN_PROGRESS.contains(sp.getUuid())) return;

        ItemStack tool = sp.getMainHandStack();

        VeinMine.IN_PROGRESS.add(sp.getUuid());
        try {
            if (state.isIn(BlockTags.LOGS) && tool.getItem() instanceof AxeItem) {
                handleTree(sw, sp, tool, pos);
            } else if (isOreState(state) && tool.getItem() instanceof PickaxeItem) {
                handleOre(sw, sp, tool, pos, state);
            }
        } finally {
            VeinMine.IN_PROGRESS.remove(sp.getUuid());
        }
    }

    // ------------------------------------------------------------------ tree

    /**
     * 树木连锁：BFS 连通 log → 判定"是否有天然叶子（PERSISTENT=false）附着"
     * → 是则批量破坏。玩家自建的木屋墙壁不触发。
     */
    private static void handleTree(ServerWorld world, ServerPlayerEntity player,
                                   ItemStack tool, BlockPos origin) {
        BlockState originState = world.getBlockState(origin); // AIR，起始已破
        List<BlockPos> logs = VeinMine.collect(world, origin, originState,
            (o, n) -> n.isIn(BlockTags.LOGS));
        if (!hasNaturalLeaf(world, origin, logs)) return;
        VeinMine.breakAll(world, player, tool, logs);
    }

    /** 起始块 + 所有已 BFS 的 log 的 6 邻居中，是否有至少一片 PERSISTENT=false 叶子。*/
    private static boolean hasNaturalLeaf(ServerWorld world, BlockPos origin, List<BlockPos> logs) {
        if (checkLeafNeighbors(world, origin)) return true;
        for (BlockPos p : logs) if (checkLeafNeighbors(world, p)) return true;
        return false;
    }

    private static boolean checkLeafNeighbors(ServerWorld world, BlockPos center) {
        for (int[] off : VeinMine.NEIGHBOR_6) {
            BlockPos p = center.add(off[0], off[1], off[2]);
            BlockState s = world.getBlockState(p);
            if (!(s.getBlock() instanceof LeavesBlock)) continue;
            if (!s.get(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ore

    /**
     * 矿物连锁：BFS 连通"共享至少一个 _ores tag"的方块（跨石头/深板岩变体一起吃）。
     * tag 发现法自动兼容 Forge #forge:ores/* 和 mod 新增的 mymod:*_ores。
     */
    private static void handleOre(ServerWorld world, ServerPlayerEntity player,
                                  ItemStack tool, BlockPos origin, BlockState originState) {
        List<BlockPos> ores = VeinMine.collect(world, origin, originState,
            (o, n) -> VeinMine.sharesTag(o, n, VeinMineHandler::isOreTag));
        if (ores.isEmpty()) return;
        VeinMine.breakAll(world, player, tool, ores);
    }

    /** 判定方块是否属于某种矿：任意 tag 的 registry key 路径以 "_ores" 结尾。*/
    private static boolean isOreState(BlockState state) {
        return state.streamTags().anyMatch(VeinMineHandler::isOreTag);
    }

    /** tag 的 path 以 "_ores" 结尾即视为矿类 tag，例如 minecraft:coal_ores、forge:ores/iron。*/
    private static boolean isOreTag(TagKey<net.minecraft.block.Block> tag) {
        String path = tag.id().getPath();
        return path.endsWith("_ores") || path.startsWith("ores/") || path.equals("ores");
    }
}
