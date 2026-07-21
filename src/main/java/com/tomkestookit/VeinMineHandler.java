package com.tomkestookit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MushroomBlock;
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
 *   - 起始块是 MushroomBlock + 主手斧头 → 走 mushroom 分支
 *   - 起始块所在的任意 tag 名以 "_ores" 结尾 + 主手镐 → 走 ore 分支（单次只连锁同种矿）
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
            if (tool.getItem() instanceof AxeItem) {
                if (state.isIn(BlockTags.LOGS)) {
                    handleTree(sw, sp, tool, pos);
                } else if (state.getBlock() instanceof MushroomBlock) {
                    handleMushroom(sw, sp, tool, pos, state);
                }
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
        List<BlockPos> logs = VeinMine.collect(world, origin, null,
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

    // ------------------------------------------------------------------ mushroom

    /**
     * 大蘑菇连锁：BFS 收集所有相连的 MushroomBlock（茎 + 蘑菇帽），
     * 自然检测：连通图中同时存在茎（MUSHROOM_STEM）和帽（RED/BROWN_MUSHROOM_BLOCK）才触发。
     */
    private static void handleMushroom(ServerWorld world, ServerPlayerEntity player,
                                       ItemStack tool, BlockPos origin, BlockState originState) {
        List<BlockPos> blocks = VeinMine.collect(world, origin, null,
            (o, n) -> n.getBlock() instanceof MushroomBlock);
        if (!isNaturalMushroom(world, origin, originState, blocks)) return;
        VeinMine.breakAll(world, player, tool, blocks);
    }

    /**
     * 自然大蘑菇判定：起始块 + BFS 结果中，同时包含茎和帽才视为一棵自然蘑菇。
     * 玩家单独放置的蘑菇方块不触发。
     */
    private static boolean isNaturalMushroom(ServerWorld world, BlockPos origin,
                                             BlockState originState, List<BlockPos> blocks) {
        boolean hasStem = isStem(originState);
        boolean hasCap = isCap(originState);
        for (BlockPos p : blocks) {
            if (hasStem && hasCap) return true;
            BlockState s = world.getBlockState(p);
            if (!hasStem && isStem(s)) hasStem = true;
            if (!hasCap && isCap(s)) hasCap = true;
        }
        return hasStem && hasCap;
    }

    private static boolean isStem(BlockState state) {
        return state.isOf(Blocks.MUSHROOM_STEM);
    }

    private static boolean isCap(BlockState state) {
        return state.isOf(Blocks.RED_MUSHROOM_BLOCK) || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    // ------------------------------------------------------------------ ore

    /**
     * 矿物连锁：BFS 连通"共享至少一个特定 ore tag"的方块（跨石头/深板岩变体一起吃）。
     * 使用 isSpecificOreTag 而非 isOreTag 做 BFS 匹配，排除 "ores" 宽泛 tag，
     * 确保单次只连锁同一种矿物。
     */
    private static void handleOre(ServerWorld world, ServerPlayerEntity player,
                                  ItemStack tool, BlockPos origin, BlockState originState) {
        List<BlockPos> ores = VeinMine.collect(world, origin, originState,
            (o, n) -> VeinMine.sharesTag(o, n, VeinMineHandler::isSpecificOreTag));
        if (ores.isEmpty()) return;
        VeinMine.breakAll(world, player, tool, ores);
    }

    /** 判定方块是否属于某种矿：任意 tag 的 path 匹配 ore 命名规则。*/
    private static boolean isOreState(BlockState state) {
        return state.streamTags().anyMatch(VeinMineHandler::isOreTag);
    }

    /**
     * 宽泛 ore tag 检测（用于"这个方块是不是矿"的初始判定）。
     * 包含 _ores / ores/ / ores 三种模式。
     */
    private static boolean isOreTag(TagKey<Block> tag) {
        String path = tag.id().getPath();
        return path.endsWith("_ores") || path.startsWith("ores/") || path.equals("ores");
    }

    /**
     * 具体 ore tag 检测（用于 BFS 同类匹配）。
     * 排除宽泛的 "ores" tag（所有矿物都在里面），只保留 _ores 和 ores/* 这类
     * 细分 tag，保证单次只连锁一种矿。
     */
    private static boolean isSpecificOreTag(TagKey<Block> tag) {
        String path = tag.id().getPath();
        return path.endsWith("_ores") || path.startsWith("ores/");
    }
}
