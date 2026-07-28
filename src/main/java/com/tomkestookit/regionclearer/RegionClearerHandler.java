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
import java.util.Map;
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
    /** 体积硬边界：预扫描是同步的，太大会卡主线程。5M 大约相当于 172³。*/
    private static final int MAX_VOLUME = 5_000_000;
    /** 触发分批的方块数阈值：以下一次执行完；以上分批。*/
    private static final int SYNC_LIMIT = 32768;
    /** 分批：每 40 tick（2 秒）跑一次。*/
    private static final int BATCH_INTERVAL_TICKS = 40;
    /** 每批最多处理这么多方块。*/
    private static final int BATCH_SIZE = 4096;

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
     */
    public static final class Pending {
        public final UUID player;
        public final ServerWorld world;
        public final RegionClearer.ClearerTier tier;
        public final BlockPos min;
        public final BlockPos max;
        public final int count;

        Pending(UUID player, ServerWorld world, RegionClearer.ClearerTier tier,
                BlockPos min, BlockPos max, int count) {
            this.player = player;
            this.world = world;
            this.tier = tier;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }

    /** 正在执行的 job。存 Deque<BlockPos> 分批 poll。*/
    private static final class ClearJob {
        final UUID player;
        final ServerWorld world;
        final RegionClearer.ClearerTier tier;
        final Deque<BlockPos> queue;
        final int total;
        int done;

        ClearJob(UUID player, ServerWorld world, RegionClearer.ClearerTier tier,
                 Deque<BlockPos> queue) {
            this.player = player;
            this.world = world;
            this.tier = tier;
            this.queue = queue;
            this.total = queue.size();
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
            PENDING.remove(id);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            UUID id = player.getUuid();
            SELECTIONS.remove(id);
            PENDING.remove(id);
        });
    }

    // ------------------------------------------------------------------ input

    private static ActionResult onAttackBlock(PlayerEntity player, net.minecraft.world.World world,
                                              Hand hand, BlockPos pos,
                                              net.minecraft.util.math.Direction direction) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
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
        if (volume > MAX_VOLUME) {
            player.sendMessage(Text.literal(String.format(
                "§c[区域清除] 区域体积 %d 超过上限 %d，已取消。", volume, MAX_VOLUME)), false);
            PENDING.remove(player.getUuid());
            return;
        }

        // 只做未加载 chunk 检查；不再验证方块能否被本 tier 挖 —— 区域内一切非空气方块都会被清除。
        int total = 0;
        int unloadedChunks = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    m.set(x, y, z);
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        unloadedChunks++;
                        continue;
                    }
                    if (!world.getBlockState(m).isAir()) total++;
                }
            }
        }

        if (unloadedChunks > 0) {
            player.sendMessage(Text.literal(String.format(
                "§c[区域清除] 区域内有 %d 个未加载方块位置，请靠近再选取，已取消。", unloadedChunks)), false);
            PENDING.remove(player.getUuid());
            return;
        }

        Pending pending = new Pending(player.getUuid(), world, tier, min, max, total);
        PENDING.put(player.getUuid(), pending);

        int batches = (total + BATCH_SIZE - 1) / BATCH_SIZE;
        int seconds = total <= SYNC_LIMIT ? 0 : (batches * BATCH_INTERVAL_TICKS / 20);

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

        Deque<BlockPos> queue = new ArrayDeque<>(Math.min(p.count, 1 << 20));
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = p.min.getY(); y <= p.max.getY(); y++) {
            for (int x = p.min.getX(); x <= p.max.getX(); x++) {
                for (int z = p.min.getZ(); z <= p.max.getZ(); z++) {
                    m.set(x, y, z);
                    if (!p.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    if (!p.world.getBlockState(m).isAir()) {
                        queue.add(m.toImmutable());
                    }
                }
            }
        }

        ClearJob job = new ClearJob(player.getUuid(), p.world, tier, queue);
        if (queue.size() <= SYNC_LIMIT) {
            // 一次性跑完
            runBatch(job, queue.size());
            player.sendMessage(Text.literal(String.format(
                "§a[区域清除] 完成，共清 §f%d§a 方块。", job.done)), false);
        } else {
            RUNNING.put(player.getUuid(), job);
            player.sendMessage(Text.literal(String.format(
                "§a[区域清除] 已开始，共 §f%d§a 方块，分批推进。", queue.size())), false);
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
        player.sendMessage(Text.literal("§7[区域清除] 已取消。"), false);
        return 1;
    }

    // ------------------------------------------------------------------ tick / job

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
            // 玩家在线就继续推进；job 已扣耐久，工具是否还完好不再影响 job 完成。
            runBatch(job, BATCH_SIZE);
            if (job.queue.isEmpty()) {
                player.sendMessage(Text.literal(String.format(
                    "§a[区域清除] 完成，共清 §f%d§a / §f%d§a 方块。", job.done, job.total)), false);
                return true;
            }
            // 每批发个进度
            player.sendMessage(Text.literal(String.format(
                "§7[区域清除] §f%d§7 / §f%d§7 方块，剩余 §f%d§7 批",
                job.done, job.total, (job.queue.size() + BATCH_SIZE - 1) / BATCH_SIZE)), true);
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

    /** 从 queue 里 poll 至多 n 个方块清掉。*/
    private static void runBatch(ClearJob job, int n) {
        ServerWorld world = job.world;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(job.player);
        // loot table 用的 tool：用 tier 对应的干净 vanilla 斧头，语义等同于纯净镐子挖，
        // 不需要玩家手上还有工具（可能已断裂）。
        ItemStack toolForDrop = new ItemStack(job.tier.baseItem);

        for (int i = 0; i < n; i++) {
            BlockPos pos = job.queue.poll();
            if (pos == null) break;
            // chunk 卸载了就跳过，避免同步等区块
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            // 掉落策略：
            //   - 基岩：不掉落
            //   - 流体（水 / 岩浆 / 粉雪 / 其它 FluidBlock）：不掉落，直接抹掉
            //   - 其他 hardness < 0 的方块（命令方块 / 屏障 / 门框等）：不掉落
            //   - 其余：按 vanilla loot table 掉落（工具用 tier 对应干净斧头）
            // waterlogged 方块（楼梯 / 栅栏泡水）本身是可掉落 block，不算流体；
            // 只有 FluidBlock 本体（水源 / 岩浆源及流动）视为流体不掉落
            boolean isFluid = state.getBlock() instanceof FluidBlock;
            boolean shouldDrop;
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
                Block.dropStacks(state, world, pos, be, player, toolForDrop);
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            job.done++;
        }
    }
}
