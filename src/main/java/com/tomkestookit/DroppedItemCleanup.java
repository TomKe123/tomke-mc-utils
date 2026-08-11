package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 掉落物 / 经验球清理：三条互相独立的路径。
 *
 *   1) 定时清理（30 分钟）：倒计时最后 1 分钟展示 bossbar，到点直接清理
 *   2) 卡顿清理（items+xp ≥ 1000 且 MSPT ≥ 50）：10 秒警告后直接清理
 *   3) 管理员命令 /tomkestookit cleanup [world|all]：立即清理
 *   4) 玩家投票：由玩家从菜单发起，20 秒投票期，≥ 50% 通过则清理所有维度
 *      - 投票机制独立，不与 1/2/3 共享路径
 *      - 一次只能有一个进行中的投票（全局）
 *      - 冷却：投票结束后 5 分钟才能再发起
 */
public final class DroppedItemCleanup {

    private static final int TIMER_INTERVAL_TICKS = 20 * 60 * 30;   // 30 分钟
    private static final int TIMER_WARNING_TICKS = 20 * 60;         // 1 分钟
    private static final int LAG_WARNING_TICKS = 20 * 10;           // 10 秒
    private static final int LAG_ITEM_THRESHOLD = 1000;
    private static final double LAG_MSPT_THRESHOLD = 50.0;
    private static final int LAG_COOLDOWN_TICKS = 20 * 60 * 5;
    private static final int VOTE_DURATION_TICKS = 20 * 20;         // 20 秒投票期
    private static final int VOTE_COOLDOWN_TICKS = 20 * 60 * 5;     // 5 分钟投票冷却

    private static final Map<RegistryKey<World>, WorldState> STATES = new HashMap<>();

    /** 玩家发起的全局投票（跨维度）。null 时表示无进行中的投票。*/
    private static PlayerVote CURRENT_VOTE = null;
    /**
     * 上一次投票结束时的 tick，用于冷却判定。
     * 初始 -VOTE_COOLDOWN_TICKS 保证首次调用时 `(lastFinish + cooldown) - now` 恰好为 -now（负值 = 无冷却）。
     * 不用 Long.MIN_VALUE：`Long.MIN_VALUE + cooldown - now` 在 now 较大时会下溢 wraparound 成巨大正值，
     * 导致 UI 显示很离谱的冷却时间（此 BUG 的根因）。
     */
    private static long lastVoteFinishTick = -VOTE_COOLDOWN_TICKS;
    /** 全局投票 bossbar。*/
    private static final ServerBossBar VOTE_BAR = new ServerBossBar(
        Text.literal("§b[清理投票]"), BossBar.Color.BLUE, BossBar.Style.NOTCHED_10);
    private static int nextVoteSeq = 1;

    static {
        VOTE_BAR.setDarkenSky(false);
        VOTE_BAR.setDragonMusic(false);
        VOTE_BAR.setThickenFog(false);
    }

    private DroppedItemCleanup() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DroppedItemCleanup::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(DroppedItemCleanup::clear);
    }

    public static void clear(MinecraftServer server) {
        STATES.clear();
        CURRENT_VOTE = null;
        VOTE_BAR.clearPlayers();
        VOTE_BAR.setVisible(false);
    }

    // ---------------------------------------------------------------- tick

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();

        // 投票路径：独立处理，与自动/卡顿清理并行
        if (CURRENT_VOTE != null) {
            tickVote(server, now);
        }

        if (!TomkesTookitConfig.enabled("world.item_cleanup_timer")
            && !TomkesTookitConfig.enabled("world.item_cleanup_lag")) {
            STATES.clear();
            return;
        }

        double mspt = averageMspt(server);

        for (ServerWorld world : server.getWorlds()) {
            WorldState state = STATES.computeIfAbsent(world.getRegistryKey(), k -> new WorldState(now));

            if (TomkesTookitConfig.enabled("world.item_cleanup_timer")) {
                tickTimed(server, world, state, now);
            } else {
                state.resetTimed();
            }
            if (TomkesTookitConfig.enabled("world.item_cleanup_lag")) {
                tickLag(server, world, state, now, mspt);
            } else {
                state.resetLag();
            }
        }
    }

    private static void tickTimed(MinecraftServer server, ServerWorld world, WorldState state, long now) {
        long remaining = state.nextTimedCleanupTick - now;
        if (remaining <= 0) {
            // 直接清理，不再走投票
            int removed = cleanupItems(world);
            state.nextTimedCleanupTick = now + TIMER_INTERVAL_TICKS;
            state.timedWarningActive = false;
            state.timedBar.clearPlayers();
            state.timedBar.setVisible(false);
            if (removed > 0) {
                String dim = MenuUiDimShort(world.getRegistryKey().getValue().toString());
                Text msg = Text.literal(String.format(
                    "§e[定时清理] §7维度 §f%s §7· 删除 §f%d §7个掉落物 / 经验球", dim, removed))
                    .styled(s -> s.withItalic(false));
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(msg, false);
                }
                TomkesTookitMod.LOGGER.info("[TomkesTookit] 定时清理 {}：删除 {} 个物品。",
                    world.getRegistryKey().getValue(), removed);
            }
            return;
        }

        if (remaining > TIMER_WARNING_TICKS) {
            state.timedWarningActive = false;
            state.timedBar.clearPlayers();
            state.timedBar.setVisible(false);
            return;
        }

        state.timedWarningActive = true;
        updateBar(state.timedBar, world,
            Text.literal("§e[定时清理] §f" + formatCountdown(remaining) + " 后自动清理"),
            remaining, TIMER_WARNING_TICKS);
    }

    private static void tickLag(MinecraftServer server, ServerWorld world, WorldState state, long now, double mspt) {
        int itemCount = countItems(world);
        boolean shouldWarn = itemCount >= LAG_ITEM_THRESHOLD && mspt >= LAG_MSPT_THRESHOLD;

        if (state.lagCooldownUntilTick > now) {
            state.lagWarningActive = false;
            state.lagBar.clearPlayers();
            state.lagBar.setVisible(false);
            return;
        }

        if (!shouldWarn) {
            if (!state.lagWarningActive) return;
            // 已经开始的倒计时不能取消
        }

        if (!state.lagWarningActive) {
            state.lagWarningActive = true;
            state.lagWarningEndTick = now + LAG_WARNING_TICKS;
        }

        long remaining = state.lagWarningEndTick - now;
        if (remaining <= 0) {
            // 直接清理，不再走投票
            int removed = cleanupItems(world);
            state.lagCooldownUntilTick = now + LAG_COOLDOWN_TICKS;
            state.lagWarningActive = false;
            state.lagBar.clearPlayers();
            state.lagBar.setVisible(false);
            if (removed > 0) {
                String dim = MenuUiDimShort(world.getRegistryKey().getValue().toString());
                Text msg = Text.literal(String.format(
                    "§c[卡顿清理] §7维度 §f%s §7· 删除 §f%d §7个掉落物 / 经验球", dim, removed))
                    .styled(s -> s.withItalic(false));
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(msg, false);
                }
                TomkesTookitMod.LOGGER.info("[TomkesTookit] 卡顿清理 {}：删除 {} 个物品。",
                    world.getRegistryKey().getValue(), removed);
            }
            return;
        }

        updateBar(state.lagBar, world,
            Text.literal("§c[卡顿清理] §f" + formatCountdown(remaining) + " 后自动清理"),
            remaining, LAG_WARNING_TICKS);
        state.lagBar.setName(Text.literal("§c[卡顿清理] §f" + formatCountdown(remaining)
            + " §7· §f" + itemCount + " §7个 · §f" + String.format("%.1f", mspt) + "mspt"));
    }

    // ---------------------------------------------------------------- 玩家投票

    private static void tickVote(MinecraftServer server, long now) {
        PlayerVote v = CURRENT_VOTE;
        long remaining = v.endTick - now;

        if (remaining <= 0) {
            finalizeVote(server);
            return;
        }

        int yes = v.yesVotes.size();
        int no = v.noVotes.size();
        int eligible = countHumanPlayers(server);
        Text title = Text.literal(String.format(
            "§b[清理投票] §f%s §7· §a同意 %d §8· §c拒绝 %d §8· §7在线 %d",
            formatCountdown(remaining), yes, no, eligible));
        updateBarGlobal(VOTE_BAR, server, title, remaining, VOTE_DURATION_TICKS);
    }

    /**
     * 由玩家从菜单发起清理投票。返回结果消息。
     */
    public static Text startPlayerVote(ServerPlayerEntity initiator) {
        if (CarpetBotUtil.isFakePlayer(initiator)) {
            return Text.literal("§c[清理投票] 假人不能发起投票")
                .styled(s -> s.withItalic(false));
        }
        MinecraftServer server = initiator.getServer();
        if (server == null) {
            return Text.literal("§c[清理投票] 服务器状态异常")
                .styled(s -> s.withItalic(false));
        }
        long now = server.getTicks();
        if (CURRENT_VOTE != null) {
            return Text.literal("§c[清理投票] 已有进行中的投票")
                .styled(s -> s.withItalic(false));
        }
        long cooldownRemain = (lastVoteFinishTick + VOTE_COOLDOWN_TICKS) - now;
        if (cooldownRemain > 0) {
            return Text.literal("§c[清理投票] 冷却中，剩余 §f" + formatCountdown(cooldownRemain))
                .styled(s -> s.withItalic(false));
        }

        // 统计所有维度物品总数
        int totalItems = 0;
        for (ServerWorld w : server.getWorlds()) totalItems += countItems(w);

        String voteId = "cleanup_" + (nextVoteSeq++);
        PlayerVote v = new PlayerVote(
            voteId,
            initiator.getUuid(),
            initiator.getGameProfile().getName(),
            now + VOTE_DURATION_TICKS,
            totalItems
        );
        CURRENT_VOTE = v;

        // 广播
        Text banner = Text.literal("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            .styled(s -> s.withItalic(false));
        Text header = Text.literal("§b§l[清理投票] §f")
            .append(Text.literal(v.initiatorName).styled(s -> s.withItalic(false)))
            .append(Text.literal(" §7发起 · 全服清理 §f" + totalItems + " §7个掉落物 / 经验球")
                .styled(s -> s.withItalic(false)));

        Text yesBtn = Text.literal("§a§l[同意]")
            .styled(s -> s.withItalic(false)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/tomkestookit cleanup vote yes " + voteId))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击投同意票"))));
        Text noBtn = Text.literal("§c§l[拒绝]")
            .styled(s -> s.withItalic(false)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/tomkestookit cleanup vote no " + voteId))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Text.literal("点击投拒绝票"))));
        Text row = Text.literal("§7投票时限 §f20 §7秒： ")
            .styled(s -> s.withItalic(false))
            .copy().append(yesBtn).append(Text.literal("  ")).append(noBtn);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (CarpetBotUtil.isFakePlayer(p)) continue;
            p.sendMessage(banner, false);
            p.sendMessage(header, false);
            p.sendMessage(row, false);
            p.sendMessage(banner, false);
        }
        TomkesTookitMod.LOGGER.info("[TomkesTookit] {} 发起玩家清理投票 {}（{} 个物品）。",
            v.initiatorName, voteId, totalItems);

        return Text.literal("§a[清理投票] 已发起，等待其他玩家投票")
            .styled(s -> s.withItalic(false));
    }

    private static void finalizeVote(MinecraftServer server) {
        PlayerVote v = CURRENT_VOTE;
        if (v == null) return;
        CURRENT_VOTE = null;
        lastVoteFinishTick = server.getTicks();
        VOTE_BAR.clearPlayers();
        VOTE_BAR.setVisible(false);

        int yes = v.yesVotes.size();
        int no = v.noVotes.size();
        int total = yes + no;
        boolean approved;
        String detail;

        if (total == 0) {
            approved = false;
            detail = "无人投票，跳过";
        } else {
            approved = yes * 2 >= total;
            detail = String.format("同意 %d / 总 %d (%.0f%%)",
                yes, total, yes * 100.0 / total);
        }

        Text result;
        if (approved) {
            int removed = 0;
            for (ServerWorld w : server.getWorlds()) removed += cleanupItems(w);
            result = Text.literal(String.format(
                "§a§l[清理投票通过] §7%s §8· §7全服删除 §f%d §7个掉落物 / 经验球",
                detail, removed)).styled(s -> s.withItalic(false));
            TomkesTookitMod.LOGGER.info("[TomkesTookit] 投票通过 {}：删除 {} 个物品。{}",
                v.id, removed, detail);
        } else {
            result = Text.literal(String.format(
                "§c§l[清理投票被拒] §7%s §8· §7本轮跳过",
                detail)).styled(s -> s.withItalic(false));
            TomkesTookitMod.LOGGER.info("[TomkesTookit] 投票被拒 {}。{}", v.id, detail);
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (CarpetBotUtil.isFakePlayer(p)) continue;
            p.sendMessage(result, false);
        }
    }

    // ---------------------------------------------------------------- commands

    /** 命令：投票。返回 1 = 记录成功，0 = 失败。*/
    public static int voteCommand(ServerPlayerEntity player, String voteId, boolean yes) {
        if (CarpetBotUtil.isFakePlayer(player)) {
            player.sendMessage(Text.literal("§c[清理投票] 假人不能投票")
                .styled(s -> s.withItalic(false)), true);
            return 0;
        }
        PlayerVote v = CURRENT_VOTE;
        if (v == null || !v.id.equals(voteId)) {
            player.sendMessage(Text.literal("§c[清理投票] 投票已结束或不存在")
                .styled(s -> s.withItalic(false)), true);
            return 0;
        }
        UUID uid = player.getUuid();
        v.yesVotes.remove(uid);
        v.noVotes.remove(uid);
        if (yes) v.yesVotes.add(uid);
        else v.noVotes.add(uid);

        Text msg = Text.literal("§a[清理投票] 已投 " + (yes ? "§a同意" : "§c拒绝")
            + " §7· 同意 §f" + v.yesVotes.size()
            + " §7· 拒绝 §f" + v.noVotes.size())
            .styled(s -> s.withItalic(false));
        player.sendMessage(msg, true);
        return 1;
    }

    /** 命令：管理员立即清理。target = "all" 或维度 id (minecraft:overworld 等)。*/
    public static int forceCleanup(net.minecraft.server.command.ServerCommandSource src, String target) {
        MinecraftServer server = src.getServer();
        int totalRemoved = 0;
        int worldsAffected = 0;
        for (ServerWorld world : server.getWorlds()) {
            String dimId = world.getRegistryKey().getValue().toString();
            if (!"all".equalsIgnoreCase(target) && !dimId.equalsIgnoreCase(target)
                && !dimId.endsWith(":" + target) && !dimId.endsWith(target)) continue;
            int removed = cleanupItems(world);
            totalRemoved += removed;
            worldsAffected++;
            TomkesTookitMod.LOGGER.info("[TomkesTookit] 管理员强制清理 {}：删除 {} 个掉落物 / 经验球。",
                dimId, removed);
        }
        if (worldsAffected == 0) {
            src.sendError(Text.literal("找不到匹配维度：" + target));
            return 0;
        }
        String summary = "§a[清理] 管理员执行：影响 §f" + worldsAffected
            + " §a个维度，删除 §f" + totalRemoved + " §a个掉落物 / 经验球";
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(summary).styled(s -> s.withItalic(false)), false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static int countHumanPlayers(MinecraftServer server) {
        int count = 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!CarpetBotUtil.isFakePlayer(p)) count++;
        }
        return count;
    }

    private static void updateBar(ServerBossBar bar, ServerWorld world, Text title, long remaining, long total) {
        bar.setVisible(true);
        bar.setName(title);
        bar.setPercent(Math.max(0.0f, Math.min(1.0f, remaining / (float) total)));
        bar.clearPlayers();
        for (var player : world.getPlayers()) {
            bar.addPlayer(player);
        }
    }

    private static void updateBarGlobal(ServerBossBar bar, MinecraftServer server,
                                         Text title, long remaining, long total) {
        bar.setVisible(true);
        bar.setName(title);
        bar.setPercent(Math.max(0.0f, Math.min(1.0f, remaining / (float) total)));
        bar.clearPlayers();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (CarpetBotUtil.isFakePlayer(p)) continue;
            bar.addPlayer(p);
        }
    }

    private static int cleanupItems(ServerWorld world) {
        java.util.List<net.minecraft.entity.Entity> toRemove = new java.util.ArrayList<>();
        for (var entity : world.iterateEntities()) {
            if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
                toRemove.add(entity);
            }
        }
        for (var e : toRemove) e.discard();
        return toRemove.size();
    }

    private static int countItems(ServerWorld world) {
        int count = 0;
        for (var entity : world.iterateEntities()) {
            if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) count++;
        }
        return count;
    }

    private static String formatCountdown(long remainingTicks) {
        long seconds = Math.max(0, (remainingTicks + 19) / 20);
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private static double averageMspt(MinecraftServer server) {
        long total = 0;
        int count = 0;
        for (long ns : server.lastTickLengths) {
            if (ns <= 0) continue;
            total += ns;
            count++;
        }
        if (count == 0) return 0.0;
        return (total / (double) count) / 1_000_000.0;
    }

    private static String MenuUiDimShort(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> id;
        };
    }

    // ---------------------------------------------------------------- state

    private static final class PlayerVote {
        final String id;
        final UUID initiator;
        final String initiatorName;
        final long endTick;
        final int snapshotItemCount;
        final Set<UUID> yesVotes = new HashSet<>();
        final Set<UUID> noVotes = new HashSet<>();

        PlayerVote(String id, UUID initiator, String initiatorName, long endTick, int snapshotItemCount) {
            this.id = id;
            this.initiator = initiator;
            this.initiatorName = initiatorName;
            this.endTick = endTick;
            this.snapshotItemCount = snapshotItemCount;
            // 发起者默认投同意票
            this.yesVotes.add(initiator);
        }
    }

    private static final class WorldState {
        long nextTimedCleanupTick;
        boolean timedWarningActive;
        boolean lagWarningActive;
        long lagWarningEndTick;
        long lagCooldownUntilTick;

        final ServerBossBar timedBar = new ServerBossBar(
            Text.literal("§e[定时清理]"), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        final ServerBossBar lagBar = new ServerBossBar(
            Text.literal("§c[卡顿清理]"), BossBar.Color.RED, BossBar.Style.PROGRESS);

        WorldState(long now) {
            this.nextTimedCleanupTick = now + TIMER_INTERVAL_TICKS;
            this.timedBar.setDarkenSky(false);
            this.timedBar.setDragonMusic(false);
            this.timedBar.setThickenFog(false);
            this.lagBar.setDarkenSky(false);
            this.lagBar.setDragonMusic(false);
            this.lagBar.setThickenFog(false);
        }

        void resetTimed() {
            timedWarningActive = false;
            timedBar.clearPlayers();
            timedBar.setVisible(false);
        }

        void resetLag() {
            lagWarningActive = false;
            lagBar.clearPlayers();
            lagBar.setVisible(false);
        }
    }
}
