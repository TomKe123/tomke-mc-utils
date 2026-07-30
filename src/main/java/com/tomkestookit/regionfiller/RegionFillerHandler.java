package com.tomkestookit.regionfiller;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
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
 * 附魔水/岩浆桶：与 RegionClearer 交互一致，但只把区域内的**空气位置**填成对应流体。
 * 一次性物品：confirm 时消耗一个。
 */
public final class RegionFillerHandler {

    private static final int MAX_EDGE = 5120;
    private static final int MAX_VOLUME = 5_000_000;
    private static final int SYNC_LIMIT = 32768;
    private static final int BATCH_INTERVAL_TICKS = 40;
    private static final int BATCH_SIZE = 4096;

    private RegionFillerHandler() {}

    private static final class Selection {
        BlockPos a;
        BlockPos b;
        String dim;
    }

    public static final class Pending {
        public final UUID player;
        public final ServerWorld world;
        public final RegionFiller.FillerType type;
        public final BlockPos min;
        public final BlockPos max;
        public final int count;

        Pending(UUID player, ServerWorld world, RegionFiller.FillerType type,
                BlockPos min, BlockPos max, int count) {
            this.player = player;
            this.world = world;
            this.type = type;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }

    private static final class FillJob {
        final UUID player;
        final ServerWorld world;
        final RegionFiller.FillerType type;
        final Deque<BlockPos> queue;
        final int total;
        int done;

        FillJob(UUID player, ServerWorld world, RegionFiller.FillerType type, Deque<BlockPos> queue) {
            this.player = player;
            this.world = world;
            this.type = type;
            this.queue = queue;
            this.total = queue.size();
        }
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, FillJob> RUNNING = new HashMap<>();

    public static void register() {
        AttackBlockCallback.EVENT.register(RegionFillerHandler::onAttackBlock);
        UseBlockCallback.EVENT.register(RegionFillerHandler::onUseBlock);

        ServerTickEvents.END_SERVER_TICK.register(RegionFillerHandler::tick);

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

    private static ActionResult onAttackBlock(PlayerEntity player, net.minecraft.world.World world,
                                              Hand hand, BlockPos pos,
                                              net.minecraft.util.math.Direction direction) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_filler_use")) return ActionResult.PASS;
        ItemStack tool = player.getMainHandStack();
        if (!RegionFiller.isTool(tool)) return ActionResult.PASS;
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            selectPoint(sp, (ServerWorld) world, pos, true);
        }
        return ActionResult.SUCCESS;
    }

    private static ActionResult onUseBlock(PlayerEntity player, net.minecraft.world.World world,
                                           Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_filler_use")) return ActionResult.PASS;
        ItemStack tool = player.getMainHandStack();
        if (!RegionFiller.isTool(tool)) return ActionResult.PASS;
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            selectPoint(sp, (ServerWorld) world, hit.getBlockPos(), false);
        }
        return ActionResult.SUCCESS;
    }

    private static void selectPoint(ServerPlayerEntity player, ServerWorld world, BlockPos pos, boolean isA) {
        UUID id = player.getUuid();
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
            "§7[区域填充] §f已选取 %s §7(%d, %d, %d)",
            isA ? "A" : "B", pos.getX(), pos.getY(), pos.getZ())), false);

        if (sel.a != null && sel.b != null) {
            preview(player, world, sel);
        }
    }

    private static void preview(ServerPlayerEntity player, ServerWorld world, Selection sel) {
        RegionFiller.FillerType type = RegionFiller.getType(player.getMainHandStack());
        if (type == null) {
            player.sendMessage(Text.literal("§c[区域填充] 主手需要拿附魔水桶或附魔岩浆桶。"), false);
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
                "§c[区域填充] 区域边长 %d×%d×%d 超过 %d，已取消。", dx, dy, dz, MAX_EDGE)), false);
            PENDING.remove(player.getUuid());
            return;
        }
        long volume = dx * dy * dz;
        if (volume > MAX_VOLUME) {
            player.sendMessage(Text.literal(String.format(
                "§c[区域填充] 区域体积 %d 超过上限 %d，已取消。", volume, MAX_VOLUME)), false);
            PENDING.remove(player.getUuid());
            return;
        }

        // 数可填位置：空气 + 同类流动流体（后者会被凝成源方块）。未加载 chunk 跳过并汇总。
        int fillCount = 0;
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
                    if (RegionFiller.isFillable(world.getBlockState(m), type)) fillCount++;
                }
            }
        }

        if (unloadedChunks > 0) {
            player.sendMessage(Text.literal(String.format(
                "§c[区域填充] 区域内有 %d 个未加载方块位置，请靠近再选取，已取消。", unloadedChunks)), false);
            PENDING.remove(player.getUuid());
            return;
        }

        if (fillCount == 0) {
            player.sendMessage(Text.literal("§7[区域填充] 区域内无可填位置，无需填充。"), false);
            PENDING.remove(player.getUuid());
            return;
        }

        Pending pending = new Pending(player.getUuid(), world, type, min, max, fillCount);
        PENDING.put(player.getUuid(), pending);

        int batches = (fillCount + BATCH_SIZE - 1) / BATCH_SIZE;
        int seconds = fillCount <= SYNC_LIMIT ? 0 : (batches * BATCH_INTERVAL_TICKS / 20);

        MutableText line1 = Text.literal(String.format(
            "§7[区域填充] 区域 §f(%d,%d,%d) → (%d,%d,%d)§7 · 可填 §f%d§7 位置",
            min.getX(), min.getY(), min.getZ(),
            max.getX(), max.getY(), max.getZ(),
            fillCount));
        MutableText line2 = seconds == 0
            ? Text.literal("§7一次性执行")
            : Text.literal(String.format("§7分 §f%d§7 批，约 §f%ds", batches, seconds));

        MutableText confirm = Text.literal("[§a§l确认§r§7]")
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/tomkestookit regionfiller confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击确认执行").formatted(Formatting.GREEN))));
        MutableText cancel = Text.literal("  [§c§l取消§r§7]")
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/tomkestookit regionfiller cancel"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击取消").formatted(Formatting.RED))));

        player.sendMessage(line1, false);
        player.sendMessage(line2, false);
        player.sendMessage(Text.empty().append(confirm).append(cancel), false);
    }

    public static int confirm(ServerPlayerEntity player) {
        Pending p = PENDING.remove(player.getUuid());
        if (p == null) {
            player.sendMessage(Text.literal("§7[区域填充] 没有待确认的区域。"), false);
            return 0;
        }
        if (RUNNING.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§c[区域填充] 上一次填充还未完成。"), false);
            PENDING.put(player.getUuid(), p);
            return 0;
        }
        ItemStack tool = player.getMainHandStack();
        RegionFiller.FillerType type = RegionFiller.getType(tool);
        if (type != p.type) {
            player.sendMessage(Text.literal("§c[区域填充] 主手工具已变化，请重新选取。"), false);
            return 0;
        }

        // 一次性：消耗一个
        tool.decrement(1);

        Deque<BlockPos> queue = new ArrayDeque<>(Math.min(p.count, 1 << 20));
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = p.min.getY(); y <= p.max.getY(); y++) {
            for (int x = p.min.getX(); x <= p.max.getX(); x++) {
                for (int z = p.min.getZ(); z <= p.max.getZ(); z++) {
                    m.set(x, y, z);
                    if (!p.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    if (RegionFiller.isFillable(p.world.getBlockState(m), p.type)) {
                        queue.add(m.toImmutable());
                    }
                }
            }
        }

        FillJob job = new FillJob(player.getUuid(), p.world, p.type, queue);
        if (queue.size() <= SYNC_LIMIT) {
            runBatch(job, queue.size());
            player.sendMessage(Text.literal(String.format(
                "§a[区域填充] 完成，共填 §f%d§a 方块。", job.done)), false);
        } else {
            RUNNING.put(player.getUuid(), job);
            player.sendMessage(Text.literal(String.format(
                "§a[区域填充] 已开始，共 §f%d§a 位置，分批推进。", queue.size())), false);
        }
        SELECTIONS.remove(player.getUuid());
        return 1;
    }

    public static int cancel(ServerPlayerEntity player) {
        Pending removed = PENDING.remove(player.getUuid());
        SELECTIONS.remove(player.getUuid());
        if (removed == null) {
            player.sendMessage(Text.literal("§7[区域填充] 没有待确认的区域。"), false);
            return 0;
        }
        player.sendMessage(Text.literal("§7[区域填充] 已取消。"), false);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) return;
        if (server.getTicks() % BATCH_INTERVAL_TICKS != 0) return;

        RUNNING.entrySet().removeIf(entry -> {
            FillJob job = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.player);
            if (player == null) return false;
            runBatch(job, BATCH_SIZE);
            if (job.queue.isEmpty()) {
                player.sendMessage(Text.literal(String.format(
                    "§a[区域填充] 完成，共填 §f%d§a / §f%d§a 位置。", job.done, job.total)), false);
                return true;
            }
            player.sendMessage(Text.literal(String.format(
                "§7[区域填充] §f%d§7 / §f%d§7 位置，剩余 §f%d§7 批",
                job.done, job.total, (job.queue.size() + BATCH_SIZE - 1) / BATCH_SIZE)), true);
            return false;
        });
    }

    private static void runBatch(FillJob job, int n) {
        ServerWorld world = job.world;
        for (int i = 0; i < n; i++) {
            BlockPos pos = job.queue.poll();
            if (pos == null) break;
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            // 分批期间可能有别的方块流进/被打破，dynamic 查最新状态。
            // 允许覆盖：空气 + 同类流动流体（后者被覆盖为源方块，等于凝流水为水源）。
            if (!RegionFiller.isFillable(world.getBlockState(pos), job.type)) continue;
            world.setBlockState(pos, job.type.fluidBlock.getDefaultState(),
                Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            job.done++;
        }
    }
}
