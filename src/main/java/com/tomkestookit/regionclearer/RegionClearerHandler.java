package com.tomkestookit.regionclearer;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 区域清除工具主逻辑：
 *
 *   - AttackBlockCallback：主手是我们的斧 + 左键方块 → 选 A + 消费事件
 *   - UseBlockCallback：主手是我们的斧 + 右键方块 → 选 B + 消费事件
 *   - 两点齐备：扫描区域方块数，弹聊天确认（含 [确认] [取消]），不做 tier 校验
 *   - /tomkestookit regionclearer confirm / cancel：命令入口，聊天里点的就是这个
 *   - ServerTickEvents.END_SERVER_TICK：每 40 tick 推进一次 ClearJob，一批 4096 方块
 */
public final class RegionClearerHandler {

    /** 一维硬边界。*/
    private static final int MAX_EDGE = 5120;
    /** 掉落物上限：超过此数量则清理时不产生掉落，避免卡顿。约 37×37×37 区域。*/
    private static final int MAX_VOLUME_WITH_DROPS = 50_000;
    /** 清除方块数的绝对上限（int 类型上限）。*/
    private static final int MAX_TOTAL_BLOCKS = Integer.MAX_VALUE;
    /** 触发分批的方块数阈值：以下一次执行完；以上分批。*/
    private static final int SYNC_LIMIT = 32768;
    /** 分批：每 N tick 跑一次。*/
    private static final int BATCH_INTERVAL_TICKS = 10;
    /** 每批最多处理这么多方块（带掉落，逐方块 loot 较慢）。*/
    private static final int BATCH_SIZE_DROP = 8192;
    /** 每批最多处理这么多方块（不掉落，纯 setBlockState，约 8× 于带掉落）。*/
    private static final int BATCH_SIZE_NODROP = 65_536;

    private RegionClearerHandler() {}

    // ------------------------------------------------------------------ state

    /** 玩家选点状态。dim 用 registry key 字符串保存，防止跨维度混淆。*/
    private static final class Selection {
        BlockPos a;
        BlockPos b;
        String dim;
    }

    /**
     * 待确认的清除请求。玩家没点确认前，一直保留；再选点会覆盖它。
     * forcedChunks 记录 preview 阶段强制加载的区块，转交给 job 继续持有直到清除完成。
     */
    public static final class Pending {
        public final UUID player;
        public final ServerWorld world;
        public final RegionClearer.ClearerTier tier;
        public final BlockPos min;
        public final BlockPos max;
        public final int count;
        public final boolean dropItems; // 是否产生掉落物
        public final Set<Long> forcedChunks;

        Pending(UUID player, ServerWorld world, RegionClearer.ClearerTier tier,
                BlockPos min, BlockPos max, int count, boolean dropItems, Set<Long> forcedChunks) {
            this.player = player;
            this.world = world;
            this.tier = tier;
            this.min = min;
            this.max = max;
            this.count = count;
            this.dropItems = dropItems;
            this.forcedChunks = forcedChunks;
        }
    }

    /**
     * 正在执行的 job。两条路径二选一：
     *   - drop 路径：预扫描非空气方块入队 → 分批 poll，逐个走 loot table 掉落；
     *   - nodrop 路径：不预扫描、不入队，用游标 (cx,cy,cz) 从 max.y 向 min.y 扫，
     *     一批扫 BATCH_SIZE_NODROP 个位置直接 setBlockState 成空气。
     *     省一次全量扫描 + 一份 Deque 内存（对上亿方块的区域非常重要），
     *     且 setBlockState 用 NOTIFY_LISTENERS，不触发邻居更新。
     */
    private static final class ClearJob {
        final UUID player;
        final ServerWorld world;
        final RegionClearer.ClearerTier tier;
        final boolean dropItems;
        final Set<Long> forcedChunks;

        // drop 路径
        final Deque<BlockPos> queue;

        // nodrop 路径
        final BlockPos min;
        final BlockPos max;
        int cx, cy, cz;      // 下一个要处理的位置；cy 从 max.y 向下推进
        long remaining;      // 还剩多少方块位置未处理

        final long total;    // 总计要处理的方块数（drop=队列大小；nodrop=区域体积）
        long done;

        /** drop 路径构造。*/
        ClearJob(UUID player, ServerWorld world, RegionClearer.ClearerTier tier,
                 Deque<BlockPos> queue, Set<Long> forcedChunks) {
            this.player = player;
            this.world = world;
            this.tier = tier;
            this.dropItems = true;
            this.forcedChunks = forcedChunks;
            this.queue = queue;
            this.min = null;
            this.max = null;
            this.total = queue.size();
            this.remaining = queue.size();
        }

        /** nodrop 路径构造。*/
        ClearJob(UUID player, ServerWorld world, RegionClearer.ClearerTier tier,
                 BlockPos min, BlockPos max, long volume, Set<Long> forcedChunks) {
            this.player = player;
            this.world = world;
            this.tier = tier;
            this.dropItems = false;
            this.forcedChunks = forcedChunks;
            this.queue = null;
            this.min = min;
            this.max = max;
            this.total = volume;
            this.remaining = volume;
            this.cx = min.getX();
            this.cy = max.getY(); // 从顶向下
            this.cz = min.getZ();
        }
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, ClearJob> RUNNING = new HashMap<>();

    // ------------------------------------------------------------------ registration

    public static void register() {
        AttackBlockCallback.EVENT.register(RegionClearerHandler::onAttackBlock);
        UseBlockCallback.EVENT.register(RegionClearerHandler::onUseBlock);

        ServerTickEvents.END_SERVER_TICK.register(RegionClearerHandler::tick);

        // 断线 / 换维度：清空 pending + 选点。job 保留（世界仍在，玩家上线后继续）。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            SELECTIONS.remove(id);
            Pending pending = PENDING.remove(id);
            if (pending != null) releaseChunks(pending.world, pending.forcedChunks);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            UUID id = player.getUuid();
            SELECTIONS.remove(id);
            Pending pending = PENDING.remove(id);
            if (pending != null) releaseChunks(pending.world, pending.forcedChunks);
        });
    }

    // ------------------------------------------------------------------ input

    private static ActionResult onAttackBlock(PlayerEntity player, net.minecraft.world.World world,
                                              Hand hand, BlockPos pos,
                                              net.minecraft.util.math.Direction direction) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_clearer_use")) return ActionResult.PASS;
        ItemStack tool = player.getMainHandStack();
        if (!RegionClearer.isTool(tool)) return ActionResult.PASS;
        // 服务端负责真正逻辑；客户端也返回 SUCCESS 阻断 vanilla 挖方块动画
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            selectPoint(sp, (ServerWorld) world, pos, true);
        }
        return ActionResult.SUCCESS;
    }

    private static ActionResult onUseBlock(PlayerEntity player, net.minecraft.world.World world,
                                           Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_clearer_use")) return ActionResult.PASS;
        ItemStack tool = player.getMainHandStack();
        if (!RegionClearer.isTool(tool)) return ActionResult.PASS;
        // 服务端负责真正逻辑；客户端也返回 SUCCESS 阻断 vanilla 剥皮/去蜡等动画
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            selectPoint(sp, (ServerWorld) world, hit.getBlockPos(), false);
        }
        return ActionResult.SUCCESS;
    }

    private static void selectPoint(ServerPlayerEntity player, ServerWorld world, BlockPos pos, boolean isA) {
        UUID id = player.getUuid();
        // 换维度：整个选择清空
        String dim = world.getRegistryKey().getValue().toString();
        Selection sel = SELECTIONS.computeIfAbsent(id, k -> new Selection());
        if (sel.dim != null && !sel.dim.equals(dim)) {
            sel.a = null;
            sel.b = null;
        }
        sel.dim = dim;
        if (isA) sel.a = pos.toImmutable();
        else sel.b = pos.toImmutable();

        player.sendMessage(Text.literal(String.format(
            "§7[区域清除] §f已选取 %s §7(%d, %d, %d)",
            isA ? "A" : "B", pos.getX(), pos.getY(), pos.getZ())), false);

        if (sel.a != null && sel.b != null) {
            preview(player, world, sel);
        }
    }

    // ------------------------------------------------------------------ preview / confirm

    /** 扫描区域并把结果发给玩家。若可执行则设置 pending。*/
    private static void preview(ServerPlayerEntity player, ServerWorld world, Selection sel) {
        RegionClearer.ClearerTier tier = RegionClearer.getTier(player.getMainHandStack());
        if (tier == null) {
            player.sendMessage(Text.literal("§c[区域清除] 主手需要拿区域清除斧。"), false);
            return;
        }

        BlockPos min = new BlockPos(
            Math.min(sel.a.getX(), sel.b.getX()),
            Math.min(sel.a.getY(), sel.b.getY()),
            Math.min(sel.a.getZ(), sel.b.getZ()));
        BlockPos max = new BlockPos(
            Math.max(sel.a.getX(), sel.b.getX()),
            Math.max(sel.a.getY(), sel.b.getY()),
            Math.max(sel.a.getZ(), sel.b.getZ()));

        long dx = (long) max.getX() - min.getX() + 1;
        long dy = (long) max.getY() - min.getY() + 1;
        long dz = (long) max.getZ() - min.getZ() + 1;
        if (dx > MAX_EDGE || dy > MAX_EDGE || dz > MAX_EDGE) {
            player.sendMessage(Text.literal(String.format(
                "§c[区域清除] 区域边长 %d×%d×%d 超过 %d，已取消。", dx, dy, dz, MAX_EDGE)), false);
            PENDING.remove(player.getUuid());
            return;
        }
        long volume = dx * dy * dz;
        if (volume > MAX_TOTAL_BLOCKS) {
            player.sendMessage(Text.literal(String.format(
                "§c[区域清除] 区域体积 %d 超过绝对上限 %d，已取消。", volume, MAX_TOTAL_BLOCKS)), false);
            PENDING.remove(player.getUuid());
            return;
        }

        // 覆盖旧的 pending：先释放它持有的区块，避免叠加
        Pending oldPending = PENDING.remove(player.getUuid());
        if (oldPending != null) releaseChunks(oldPending.world, oldPending.forcedChunks);

        // 超过 50K 体积的区域：跳过精确预扫描（太慢），不产生掉落物
        boolean dropItems = volume <= MAX_VOLUME_WITH_DROPS;
        int total;

        // 强制加载区域涉及的区块（预扫描和后续清除都需要）
        // 小区域走同步加载，保证扫描 / 逐方块清除路径能拿到玩家视距外的区块数据；
        // 大区域走 fill 路径，fill 命令自己会同步加载子区块，这里不必一次性把所有区块拉进内存
        Set<Long> forcedChunks = forceLoadChunks(world, min, max, dropItems);

        if (!dropItems) {
            // 大区域：不做预扫描，估算为 volume 本身（上限 int）
            total = volume > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume;
            player.sendMessage(Text.literal(
                "§e[区域清除] §c区域体积超过 50K，此次清理不会掉落任何物品。§7(避免卡顿)"), false);
        } else {
            // 正常区域：精确预扫描（区块已同步加载，直接读即可）
            total = 0;
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        m.set(x, y, z);
                        if (!world.getBlockState(m).isAir()) total++;
                    }
                }
            }
        }

        Pending pending = new Pending(player.getUuid(), world, tier, min, max, total, dropItems, forcedChunks);
        PENDING.put(player.getUuid(), pending);

        // 预估分批数：drop 路径按预扫描出的 total 分批；nodrop 路径按整体积分批
        int batchSize = dropItems ? BATCH_SIZE_DROP : BATCH_SIZE_NODROP;
        long unit = dropItems ? total : volume;
        long batches = (unit + batchSize - 1) / batchSize;
        long seconds = unit <= SYNC_LIMIT ? 0 : (batches * BATCH_INTERVAL_TICKS / 20);

        MutableText line1 = Text.literal(String.format(
            "§7[区域清除] 区域 §f(%d,%d,%d) → (%d,%d,%d)§7 · 方块 §f%d",
            min.getX(), min.getY(), min.getZ(),
            max.getX(), max.getY(), max.getZ(),
            total));
        MutableText line2 = seconds == 0
            ? Text.literal("§7一次性执行")
            : Text.literal(String.format("§7分 §f%d§7 批，约 §f%ds", batches, seconds));

        MutableText confirm = Text.literal("[§a§l确认§r§7]")
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/tomkestookit regionclearer confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击确认执行").formatted(Formatting.GREEN))));
        MutableText cancel = Text.literal("  [§c§l取消§r§7]")
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/tomkestookit regionclearer cancel"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击取消").formatted(Formatting.RED))));

        player.sendMessage(line1, false);
        player.sendMessage(line2, false);
        player.sendMessage(Text.empty().append(confirm).append(cancel), false);
    }

    // ------------------------------------------------------------------ commands

    /** 玩家点了 [确认]。*/
    public static int confirm(ServerPlayerEntity player) {
        Pending p = PENDING.remove(player.getUuid());
        if (p == null) {
            player.sendMessage(Text.literal("§7[区域清除] 没有待确认的区域。"), false);
            return 0;
        }
        if (RUNNING.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§c[区域清除] 上一次清除还未完成。"), false);
            PENDING.put(player.getUuid(), p); // 恢复
            return 0;
        }
        ItemStack tool = player.getMainHandStack();
        RegionClearer.ClearerTier tier = RegionClearer.getTier(tool);
        if (tier == null || tier != p.tier) {
            player.sendMessage(Text.literal("§c[区域清除] 主手 tier 已变化，请重新选取。"), false);
            return 0;
        }

        // 先扣耐久：一次 confirm 消耗 perUseDamage。就算工具断裂也不影响 job 跑完，
        // 因为 job 本身持有 tier 快照，不再依赖玩家手上的工具。
        int perUse = tier.perUseDamage();
        tool.damage(perUse, player, pl -> pl.sendToolBreakStatus(Hand.MAIN_HAND));
        if (tool.isEmpty()) {
            player.sendMessage(Text.literal("§7[区域清除] 工具已耗尽。"), false);
        }

        // 复用 preview 阶段已加载的区块（job 完成后统一释放）
        Set<Long> forcedChunks = p.forcedChunks != null ? p.forcedChunks : new HashSet<>();

        ClearJob job;
        long expectedCount;

        if (p.dropItems) {
            // drop 路径：预扫描非空气方块入队 → 分批 poll 走 loot table
            // 从上往下入队：Deque 从队首 poll，先清顶层，避免上方水/岩浆流回已清空的下层
            Deque<BlockPos> queue = new ArrayDeque<>(Math.min(p.count, 1 << 20));
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int y = p.max.getY(); y >= p.min.getY(); y--) {
                for (int x = p.min.getX(); x <= p.max.getX(); x++) {
                    for (int z = p.min.getZ(); z <= p.max.getZ(); z++) {
                        m.set(x, y, z);
                        if (!p.world.getBlockState(m).isAir()) {
                            queue.add(m.toImmutable());
                        }
                    }
                }
            }
            expectedCount = queue.size();
            job = new ClearJob(player.getUuid(), p.world, tier, queue, forcedChunks);
        } else {
            // nodrop 路径：不预扫描、不入队。游标扫描整个体积，直接 setBlockState 抹平
            long dx = (long) p.max.getX() - p.min.getX() + 1;
            long dy = (long) p.max.getY() - p.min.getY() + 1;
            long dz = (long) p.max.getZ() - p.min.getZ() + 1;
            long volume = dx * dy * dz;
            expectedCount = volume;
            job = new ClearJob(player.getUuid(), p.world, tier, p.min, p.max, volume, forcedChunks);
        }

        int batchSize = job.dropItems ? BATCH_SIZE_DROP : BATCH_SIZE_NODROP;
        if (expectedCount <= SYNC_LIMIT) {
            // 一次性跑完
            runBatch(job, (int) expectedCount);
            releaseChunks(p.world, forcedChunks);
            player.sendMessage(Text.literal(String.format(
                "§a[区域清除] 完成，共清 §f%d§a 方块。", job.done)), false);
        } else {
            RUNNING.put(player.getUuid(), job);
            long batches = (expectedCount + batchSize - 1) / batchSize;
            player.sendMessage(Text.literal(String.format(
                "§a[区域清除] 已开始，共 §f%d§a 方块，分 §f%d§a 批推进%s。",
                expectedCount, batches, job.dropItems ? "" : "（无掉落·加速）")), false);
        }
        // 清空选点
        SELECTIONS.remove(player.getUuid());
        return 1;
    }

    /** 玩家点了 [取消]。*/
    public static int cancel(ServerPlayerEntity player) {
        Pending removed = PENDING.remove(player.getUuid());
        SELECTIONS.remove(player.getUuid());
        if (removed == null) {
            player.sendMessage(Text.literal("§7[区域清除] 没有待确认的区域。"), false);
            return 0;
        }
        // 释放 preview 阶段加载的区块
        releaseChunks(removed.world, removed.forcedChunks);
        player.sendMessage(Text.literal("§7[区域清除] 已取消。"), false);
        return 1;
    }

    // ------------------------------------------------------------------ tick / job

    /**
     * 强制加载区域涉及的所有区块，返回已加载的区块集合（ChunkPos.toLong 编码）。
     *
     * setChunkForced 只是加 ForcedTicket，实际加载走 ChunkTicketManager 异步流程，
     * 立即 isChunkLoaded 通常仍为 false。当 syncLoad=true 时，追加一次 getChunk(cx,cz)
     * 同步加载到 FULL 状态，保证紧接着的方块扫描能拿到真实数据（否则玩家视距外的区块
     * 会被当成"未加载"跳过，最终清理时漏掉这些位置）。
     *
     * 对于超大区域（走 nodrop 路径），可传 syncLoad=false：nodrop 路径不做预扫描，
     * 区块由 setBlockState 按需同步加载，分摊到多批 tick 中，不会一口气卡爆服务器。
     */
    private static Set<Long> forceLoadChunks(ServerWorld world, BlockPos min, BlockPos max,
                                             boolean syncLoad) {
        Set<Long> forced = new HashSet<>();
        int minCx = min.getX() >> 4, maxCx = max.getX() >> 4;
        int minCz = min.getZ() >> 4, maxCz = max.getZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                world.setChunkForced(cx, cz, true);
                if (syncLoad) {
                    // 同步加载到 FULL 状态；玩家视距外 / 从未加载过的区块会被从磁盘或生成器载入
                    world.getChunk(cx, cz);
                }
                forced.add(net.minecraft.util.math.ChunkPos.toLong(cx, cz));
            }
        }
        return forced;
    }

    /** 取消区域涉及的区块的强制加载。*/
    private static void releaseChunks(ServerWorld world, Set<Long> forcedChunks) {
        if (forcedChunks == null) return;
        for (long packed : forcedChunks) {
            int cx = net.minecraft.util.math.ChunkPos.getPackedX(packed);
            int cz = net.minecraft.util.math.ChunkPos.getPackedZ(packed);
            world.setChunkForced(cx, cz, false);
        }
        forcedChunks.clear();
    }

    private static void tick(MinecraftServer server) {
        // 每 10 tick 给 pending 玩家撒红石粒子边框
        if (server.getTicks() % 10 == 0) {
            for (Pending p : PENDING.values()) {
                ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(p.player);
                if (viewer == null || viewer.getServerWorld() != p.world) continue;
                drawBorderParticles(viewer, p.world, p.min, p.max);
            }
        }

        if (RUNNING.isEmpty()) return;
        if (server.getTicks() % BATCH_INTERVAL_TICKS != 0) return;

        RUNNING.entrySet().removeIf(entry -> {
            ClearJob job = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.player);
            if (player == null) return false; // 玩家离线：保留 job，等回来

            int batchSize = job.dropItems ? BATCH_SIZE_DROP : BATCH_SIZE_NODROP;
            runBatch(job, batchSize);

            if (job.remaining <= 0) {
                releaseChunks(job.world, job.forcedChunks);
                player.sendMessage(Text.literal(String.format(
                    "§a[区域清除] 完成，共清 §f%d§a / §f%d§a 方块%s。",
                    job.done, job.total, job.dropItems ? "" : "（无掉落）")), false);
                return true;
            }
            // 每批发个进度（actionbar）
            long batches = (job.remaining + batchSize - 1) / batchSize;
            player.sendMessage(Text.literal(String.format(
                "§7[区域清除] §f%d§7 / §f%d§7 方块，剩余 §f%d§7 批",
                job.done, job.total, batches)), true);
            return false;
        });
    }

    /**
     * 沿 AABB 的 12 条边发送红石粒子给玩家（仅玩家自己可见）。
     * 每条边按方块间隔 0.5 撒点，粒子颜色红色，count=1, offset=0。
     */
    private static void drawBorderParticles(ServerPlayerEntity viewer, ServerWorld world,
                                            BlockPos min, BlockPos max) {
        net.minecraft.particle.DustParticleEffect dust = new net.minecraft.particle.DustParticleEffect(
            new org.joml.Vector3f(1.0f, 0.0f, 0.0f), 1.0f);

        double x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        double x1 = max.getX() + 1.0, y1 = max.getY() + 1.0, z1 = max.getZ() + 1.0;
        // 步长 0.5：密集但不至于卡；每条边限制 200 个点上限
        double step = 0.5;

        // 4 条平行 X 轴的边（y/z 固定于 8 个组合中的 4 个）
        drawLine(viewer, world, dust, x0, y0, z0, x1, y0, z0, step);
        drawLine(viewer, world, dust, x0, y0, z1, x1, y0, z1, step);
        drawLine(viewer, world, dust, x0, y1, z0, x1, y1, z0, step);
        drawLine(viewer, world, dust, x0, y1, z1, x1, y1, z1, step);
        // 4 条平行 Y 轴
        drawLine(viewer, world, dust, x0, y0, z0, x0, y1, z0, step);
        drawLine(viewer, world, dust, x1, y0, z0, x1, y1, z0, step);
        drawLine(viewer, world, dust, x0, y0, z1, x0, y1, z1, step);
        drawLine(viewer, world, dust, x1, y0, z1, x1, y1, z1, step);
        // 4 条平行 Z 轴
        drawLine(viewer, world, dust, x0, y0, z0, x0, y0, z1, step);
        drawLine(viewer, world, dust, x1, y0, z0, x1, y0, z1, step);
        drawLine(viewer, world, dust, x0, y1, z0, x0, y1, z1, step);
        drawLine(viewer, world, dust, x1, y1, z0, x1, y1, z1, step);
    }

    private static void drawLine(ServerPlayerEntity viewer, ServerWorld world,
                                 net.minecraft.particle.ParticleEffect effect,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1, double step) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return;
        int steps = Math.min(200, Math.max(2, (int) Math.ceil(len / step)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double px = x0 + dx * t;
            double py = y0 + dy * t;
            double pz = z0 + dz * t;
            // 仅发给该玩家：spawnParticles 的 overload（player, particle, force, x,y,z, count, dx,dy,dz, speed）
            world.spawnParticles(viewer, effect, true, px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 处理一批方块。分派到 drop / nodrop 两条实现，均使用 NOTIFY_LISTENERS 标志位，
     * 不触发邻居更新，不影响清除区域外的方块。
     */
    private static void runBatch(ClearJob job, int n) {
        if (job.dropItems) runBatchDrop(job, n);
        else runBatchNoDrop(job, n);
    }

    /** drop 路径：从预扫描队列里 poll 至多 n 个方块，逐个走 loot table 掉落。*/
    private static void runBatchDrop(ClearJob job, int n) {
        ServerWorld world = job.world;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(job.player);
        // loot table 用的 tool：用 tier 对应的干净 vanilla 斧头，语义等同于纯净镐子挖，
        // 不需要玩家手上还有工具（可能已断裂）。
        ItemStack toolForDrop = new ItemStack(job.tier.baseItem);

        for (int i = 0; i < n; i++) {
            BlockPos pos = job.queue.poll();
            if (pos == null) break;
            job.remaining--;
            // 区块在 preview 阶段已 setChunkForced，job 完成前不会卸载；
            // 若极端情况仍未加载，getBlockState 会同步加载，不再跳过（跳过会漏清）
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            // 掉落策略：
            //   - 基岩：不掉落
            //   - 流体（水 / 岩浆 / 其它 FluidBlock）：不掉落，直接抹掉
            //   - 其他 hardness < 0 的方块（命令方块 / 屏障 / 门框等）：不掉落
            //   - 其余：按 vanilla loot table 计算掉落，然后汇入垃圾桶（不生成 ItemEntity）
            // waterlogged 方块（楼梯 / 栅栏泡水）本身是可掉落 block，不算流体；
            // 只有 FluidBlock 本体（水源 / 岩浆源及流动）视为流体不掉落
            boolean shouldDrop;
            boolean isFluid = state.getBlock() instanceof FluidBlock;
            if (state.isOf(Blocks.BEDROCK) || isFluid) {
                shouldDrop = false;
            } else {
                float hardness;
                try {
                    hardness = state.getHardness(null, null);
                } catch (Throwable t) {
                    hardness = 0f; // 读取失败按可挖处理
                }
                shouldDrop = hardness >= 0f;
            }
            if (shouldDrop) {
                BlockEntity be = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
                java.util.List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, player, toolForDrop);
                for (ItemStack drop : drops) {
                    com.tomkestookit.menu.TrashCanMenu.deposit(drop);
                }
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            job.done++;
        }
    }

    /**
     * nodrop 路径：游标扫描 n 个位置，非空气直接 setBlockState 成空气。
     * 不做 loot table、不建队列、不 isAir 前预扫，速度接近纯 setBlockState 的极限。
     * 仍用 NOTIFY_LISTENERS，只把变更推给客户端，不触发邻居更新。
     *
     * 掉落物汇入垃圾桶：不生成 ItemEntity（这本来就是"不掉落"路径的初衷 —— 避免大区域
     * 生成海量掉落物卡服），但把方块本体的物品形式（Block.asItem）单条累加到全服垃圾桶。
     * 用简化路径而非 loot table，避免 loot 采样在千万级方块上拖垮 tick。
     */
    private static void runBatchNoDrop(ClearJob job, int n) {
        ServerWorld world = job.world;
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        int minX = job.min.getX(), maxX = job.max.getX();
        int minY = job.min.getY();
        int minZ = job.min.getZ(), maxZ = job.max.getZ();

        int cx = job.cx, cy = job.cy, cz = job.cz;
        int processed = 0;
        while (processed < n && cy >= minY) {
            m.set(cx, cy, cz);
            BlockState state = world.getBlockState(m);
            if (!state.isAir()) {
                // 汇入垃圾桶：跳过流体 / 基岩 / hardness<0 的方块，跟 drop 路径策略一致
                boolean isFluid = state.getBlock() instanceof FluidBlock;
                if (!state.isOf(Blocks.BEDROCK) && !isFluid) {
                    net.minecraft.item.Item asItem = state.getBlock().asItem();
                    if (asItem != null && asItem != net.minecraft.item.Items.AIR) {
                        com.tomkestookit.menu.TrashCanMenu.deposit(new ItemStack(asItem));
                    }
                }
                world.setBlockState(m, air, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                job.done++;
            }
            processed++;

            // 从上往下扫：z 先走完 → x → y 递减
            cz++;
            if (cz > maxZ) {
                cz = minZ;
                cx++;
                if (cx > maxX) {
                    cx = minX;
                    cy--;
                }
            }
        }
        job.cx = cx;
        job.cy = cy;
        job.cz = cz;
        job.remaining -= processed;
        if (cy < minY) job.remaining = 0; // 到底了，钳到 0
    }
}
