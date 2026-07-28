package com.tomkestookit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   - 起始块是蘑菇方块（茎/红帽/棕帽）+ 主手斧头 → 走 mushroom 分支
 *   - 起始块是天然树叶（PERSISTENT=false）+ 主手剪刀 → 走 leaves 分支
 *   - 起始块所在的任意 tag 名以 "_ores" 结尾 + 主手镐 → 走 ore 分支（单次只连锁同种矿）
 */
public final class VeinMineHandler {

    /**
     * 原木/木头/剥皮木 → 对应树苗（下界为菌类）的映射。
     * 覆盖所有 8 种主世界树 + 2 种下界树 * 4 种变体（普通/去皮 log/wood）。
     * 竹子(BAMBOO_BLOCK)不算树，虽然在 BlockTags.LOGS 里，跳过补种。
     */
    private static final Map<Block, Item> LOG_TO_SAPLING = buildSaplingMap();

    private static Map<Block, Item> buildSaplingMap() {
        Map<Block, Item> m = new HashMap<>();
        addSpecies(m, Blocks.OAK_LOG, Blocks.OAK_WOOD,
                     Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD, Items.OAK_SAPLING);
        addSpecies(m, Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD,
                     Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD, Items.SPRUCE_SAPLING);
        addSpecies(m, Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD,
                     Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD, Items.BIRCH_SAPLING);
        addSpecies(m, Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD,
                     Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD, Items.JUNGLE_SAPLING);
        addSpecies(m, Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD,
                     Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD, Items.ACACIA_SAPLING);
        addSpecies(m, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD,
                     Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD, Items.DARK_OAK_SAPLING);
        addSpecies(m, Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD,
                     Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD, Items.MANGROVE_PROPAGULE);
        addSpecies(m, Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD,
                     Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD, Items.CHERRY_SAPLING);
        // 下界树：茎/hyphae → 菌类
        addSpecies(m, Blocks.CRIMSON_STEM, Blocks.CRIMSON_HYPHAE,
                     Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_HYPHAE, Items.CRIMSON_FUNGUS);
        addSpecies(m, Blocks.WARPED_STEM, Blocks.WARPED_HYPHAE,
                     Blocks.STRIPPED_WARPED_STEM, Blocks.STRIPPED_WARPED_HYPHAE, Items.WARPED_FUNGUS);
        return Map.copyOf(m);
    }

    private static void addSpecies(Map<Block, Item> m, Block log, Block wood,
                                    Block strippedLog, Block strippedWood, Item sapling) {
        m.put(log, sapling);
        m.put(wood, sapling);
        m.put(strippedLog, sapling);
        m.put(strippedWood, sapling);
    }

    private VeinMineHandler() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(VeinMineHandler::onBlockBroken);
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos,
                                      BlockState state, net.minecraft.block.entity.BlockEntity be) {
        if (world.isClient) return;
        if (!TomkesTookitConfig.enabled("item.veinmine")) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        if (!VeinMineTracker.isEnabled(sp.getUuid())) return;
        if (!sp.isSneaking()) return;
        if (VeinMine.IN_PROGRESS.contains(sp.getUuid())) return;

        ItemStack tool = sp.getMainHandStack();
        // 区域清除斧走它自己的选点逻辑，不走连锁
        if (com.tomkestookit.regionclearer.RegionClearer.isTool(tool)) return;

        VeinMine.IN_PROGRESS.add(sp.getUuid());
        try {
            boolean handled = false;
            if (tool.getItem() instanceof AxeItem) {
                if (state.isIn(BlockTags.LOGS)) {
                    handled = handleTree(sw, sp, tool, pos, state);
                } else if (isMushroom(state)) {
                    handled = handleMushroom(sw, sp, tool, pos);
                }
            } else if (tool.isOf(Items.SHEARS)) {
                // The origin block has already been broken by the AFTER event.
                // Shears chaining only handles natural leaves.
                if (state.getBlock() instanceof LeavesBlock && !state.get(LeavesBlock.PERSISTENT)) {
                    handled = handleLeaves(sw, sp, tool, pos);
                }
            } else if (isOreState(state) && tool.getItem() instanceof PickaxeItem) {
                handled = handleOre(sw, sp, tool, pos, state);
            }

            if (!handled) {
                handleConfiguredBlock(sw, sp, tool, pos, state);
            }
        } finally {
            VeinMine.IN_PROGRESS.remove(sp.getUuid());
        }
    }

    // ------------------------------------------------------------------ tree

    /**
     * 树木连锁：BFS 连通 log → 判定"是否有天然树叶/菌类附着"
     * → 是则批量破坏，然后尝试补种一棵。
     * originState 是玩家砍下的起始块（AFTER 事件传入，此时块已被破坏成 AIR），
     * 用来查 LOG_TO_SAPLING 决定补种什么。
     */
    private static boolean handleTree(ServerWorld world, ServerPlayerEntity player,
                                     ItemStack tool, BlockPos origin, BlockState originState) {
        List<BlockPos> logs = VeinMine.collect(world, origin, null,
            (o, n) -> n.isIn(BlockTags.LOGS));
        if (!hasNaturalFoliage(world, origin, logs)) return false;
        if (logs.isEmpty()) {
            replantSapling(world, player, origin, logs, originState);
            return true;
        }
        VeinMine.breakAll(world, player, tool, logs);
        replantSapling(world, player, origin, logs, originState);
        return true;
    }

    /**
     * 在被砍树的最低点自动补种一棵对应树苗（下界菌类同理）。
     * 不消耗玩家物品——连锁砍树的 QoL：自动在树基位置生成树苗。
     *
     * 补种规则：
     *   - 树苗种类：originState 查 LOG_TO_SAPLING（起始块类型决定）
     *   - 位置：(origin + logs) 里 Y 最小的位置（树基）
     *   - 合法性：sapling.canPlaceAt 判定支撑方块（草/土/菌岩...）
     *     不满足则跳过（如空中的浮木）
     */
    private static void replantSapling(ServerWorld world, ServerPlayerEntity player,
                                        BlockPos origin, List<BlockPos> logs,
                                        BlockState originState) {
        Item saplingItem = LOG_TO_SAPLING.get(originState.getBlock());
        if (saplingItem == null) return; // 未映射的 log（如竹子）

        if (!(saplingItem instanceof BlockItem bi)) return;
        BlockState saplingState = bi.getBlock().getDefaultState();

        // 找底层：origin + logs 里最小 Y
        int minY = origin.getY();
        for (BlockPos p : logs) {
            if (p.getY() < minY) minY = p.getY();
        }
        // 收集底层所有 log 的 xz（供 2×2 检测），并挑 origin 所在底层做备选
        java.util.Set<Long> bottomXZ = new java.util.HashSet<>();
        if (origin.getY() == minY) bottomXZ.add(BlockPos.asLong(origin.getX(), 0, origin.getZ()));
        for (BlockPos p : logs) {
            if (p.getY() == minY) bottomXZ.add(BlockPos.asLong(p.getX(), 0, p.getZ()));
        }

        // 大丛林 / 大云杉 / 大黑橡：树基是 2×2 时四格都补树苗
        Block block = originState.getBlock();
        boolean canBe2x2 = block == Blocks.SPRUCE_LOG || block == Blocks.SPRUCE_WOOD
            || block == Blocks.STRIPPED_SPRUCE_LOG || block == Blocks.STRIPPED_SPRUCE_WOOD
            || block == Blocks.JUNGLE_LOG || block == Blocks.JUNGLE_WOOD
            || block == Blocks.STRIPPED_JUNGLE_LOG || block == Blocks.STRIPPED_JUNGLE_WOOD
            || block == Blocks.DARK_OAK_LOG || block == Blocks.DARK_OAK_WOOD
            || block == Blocks.STRIPPED_DARK_OAK_LOG || block == Blocks.STRIPPED_DARK_OAK_WOOD;

        if (canBe2x2) {
            BlockPos corner = find2x2Corner(bottomXZ, minY);
            if (corner != null) {
                int planted = 0;
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        BlockPos p = corner.add(dx, 0, dz);
                        if (tryPlant(world, p, saplingState)) planted++;
                    }
                }
                if (planted > 0) {
                    world.playSound(null, corner,
                        net.minecraft.sound.SoundEvents.BLOCK_GRASS_PLACE,
                        SoundCategory.BLOCKS, 0.8f, 1.0f);
                }
                return;
            }
        }

        // 单株：origin 所在的（或最矮的）单个位置补 1 棵
        BlockPos plantAt = origin.getY() == minY ? origin : lowestBottom(logs, minY, origin);
        if (tryPlant(world, plantAt, saplingState)) {
            world.playSound(null, plantAt,
                net.minecraft.sound.SoundEvents.BLOCK_GRASS_PLACE,
                SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
    }

    /** 找底层是否形成 2×2 正方形；返回西北角（最小 x/z），否则 null。*/
    private static BlockPos find2x2Corner(java.util.Set<Long> bottomXZ, int y) {
        for (Long key : bottomXZ) {
            BlockPos base = BlockPos.fromLong(key);
            int bx = base.getX(), bz = base.getZ();
            // 尝试以 base 为西北角
            if (bottomXZ.contains(BlockPos.asLong(bx + 1, 0, bz))
                && bottomXZ.contains(BlockPos.asLong(bx, 0, bz + 1))
                && bottomXZ.contains(BlockPos.asLong(bx + 1, 0, bz + 1))) {
                return new BlockPos(bx, y, bz);
            }
        }
        return null;
    }

    /** 从底层里挑一个（找不到返回 origin）。*/
    private static BlockPos lowestBottom(List<BlockPos> logs, int minY, BlockPos origin) {
        for (BlockPos p : logs) {
            if (p.getY() == minY) return p;
        }
        return origin;
    }

    /** 试着在 pos 种树苗（空气 + 支撑合法）。返回是否成功种下。*/
    private static boolean tryPlant(ServerWorld world, BlockPos pos, BlockState saplingState) {
        if (!world.getBlockState(pos).isAir()) return false;
        if (!saplingState.canPlaceAt(world, pos)) return false;
        world.setBlockState(pos, saplingState, Block.NOTIFY_ALL);
        return true;
    }

    /**
     * 起始块 + 所有已 BFS 的 log 的 6 邻居中，是否有天然树叶或下界疣块。
     * 主世界树：LeavesBlock 且 PERSISTENT=false
     * 下界树：NETHER_WART_BLOCK / WARPED_WART_BLOCK / SHROOMLIGHT
     */
    private static boolean hasNaturalFoliage(ServerWorld world, BlockPos origin, List<BlockPos> logs) {
        if (checkFoliageNeighbors(world, origin)) return true;
        for (BlockPos p : logs) if (checkFoliageNeighbors(world, p)) return true;
        return false;
    }

    private static boolean checkFoliageNeighbors(ServerWorld world, BlockPos center) {
        for (int[] off : VeinMine.NEIGHBOR_6) {
            BlockPos p = center.add(off[0], off[1], off[2]);
            BlockState s = world.getBlockState(p);
            // 主世界树叶（非玩家放置）
            if (s.getBlock() instanceof LeavesBlock) {
                if (!s.get(LeavesBlock.PERSISTENT)) return true;
                continue;
            }
            // 下界树的"树叶"：疣块和菌光体
            if (s.isOf(Blocks.NETHER_WART_BLOCK)
                || s.isOf(Blocks.WARPED_WART_BLOCK)
                || s.isOf(Blocks.SHROOMLIGHT)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ mushroom

    /**
     * 大蘑菇连锁：BFS 收集所有相连的蘑菇方块（茎 / 红帽 / 棕帽），直接批量破坏。
     * 无"自然"检测——玩家想链接就链接，反正需要 shift+斧头，是主动动作；
     * 单个孤立蘑菇块 BFS 为空，无副作用。
     */
    private static boolean handleMushroom(ServerWorld world, ServerPlayerEntity player,
                                          ItemStack tool, BlockPos origin) {
        List<BlockPos> blocks = VeinMine.collect(world, origin, null,
            (o, n) -> isMushroom(n));
        if (blocks.isEmpty()) return false;
        VeinMine.breakAll(world, player, tool, blocks);
        return true;
    }

    /** 检测方块是否为大蘑菇方块（三种：茎 / 红帽 / 棕帽）。*/
    private static boolean isMushroom(BlockState state) {
        return state.isOf(Blocks.MUSHROOM_STEM)
            || state.isOf(Blocks.RED_MUSHROOM_BLOCK)
            || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    // ------------------------------------------------------------------ leaves

    /**
     * 树叶连锁：BFS 收集所有相连的天然树叶（PERSISTENT=false），批量破坏。
     * 允许跨叶种类连锁（不同品种的树叶挨在一起也算同一片林冠）。
     * 玩家自己放置的树叶（PERSISTENT=true）不参与，避免误伤装饰。
     */
    private static boolean handleLeaves(ServerWorld world, ServerPlayerEntity player,
                                        ItemStack tool, BlockPos origin) {
        List<BlockPos> leaves = VeinMine.collect(world, origin, null, (o, n) -> {
            if (!(n.getBlock() instanceof LeavesBlock)) return false;
            return !n.get(LeavesBlock.PERSISTENT);
        });
        if (leaves.isEmpty()) return false;
        VeinMine.breakAll(world, player, tool, leaves);
        return true;
    }

    // ------------------------------------------------------------------ ore

    /**
     * 矿物连锁：BFS 连通"共享至少一个特定 ore tag"的方块（跨石头/深板岩变体一起吃）。
     * 使用 isSpecificOreTag 而非 isOreTag 做 BFS 匹配，排除 "ores" 宽泛 tag，
     * 确保单次只连锁同一种矿物。
     */
    private static boolean handleOre(ServerWorld world, ServerPlayerEntity player,
                                     ItemStack tool, BlockPos origin, BlockState originState) {
        List<BlockPos> ores = VeinMine.collect(world, origin, originState,
            (o, n) -> VeinMine.sharesTag(o, n, VeinMineHandler::isSpecificOreTag));
        if (ores.isEmpty()) return false;
        VeinMine.breakAll(world, player, tool, ores);
        return true;
    }

    /** Configured same-block chaining: explicitly allowlisted blocks mine up to their per-break limit. */
    private static boolean handleConfiguredBlock(ServerWorld world, ServerPlayerEntity player,
                                                 ItemStack tool, BlockPos origin,
                                                 BlockState originState) {
        if (!VeinMineBlockConfig.isConfigured(originState.getBlock())) return false;
        int limit = VeinMineBlockConfig.getLimit(originState.getBlock());
        List<BlockPos> blocks = VeinMine.collectUpToTotal(world, origin, originState,
            (o, n) -> n.isOf(o.getBlock()), limit);
        if (blocks.isEmpty()) return false;
        VeinMine.breakAll(world, player, tool, blocks);
        return true;
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
