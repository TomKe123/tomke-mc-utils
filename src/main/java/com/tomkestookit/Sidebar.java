package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 右侧 SIDEBAR 计分板：每秒刷新 TPS / MSPT / 游戏内时钟。
 *
 *   - 一个 dummy 类型的 objective 挂在 SIDEBAR 显示槽。
 *   - 显示条目通过 fake player name 作为"行文本"，行的 score 值决定纵向顺序：
 *     score 越大越靠上。给三条固定的 3/2/1，游戏时钟居中 (2)，MSPT 最下 (1)。
 *   - 每秒刷新一次；先 reset 掉旧的"行"再写新的。
 */
public final class Sidebar {

    private static final String OBJECTIVE_ID = "tomkestookit_sidebar";
    private static final int REFRESH_INTERVAL = 20; // 20 ticks = 1s
    private static ScoreboardObjective objective;

    private Sidebar() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Sidebar::install);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % REFRESH_INTERVAL != 0) return;
            if (!TomkesTookitConfig.enabled("ui.sidebar")) return;
            refresh(server);
        });
    }

    /** 管理员关掉 ui.sidebar 时的清理：把 SIDEBAR 上的 objective 卸掉。*/
    public static void uninstall(MinecraftServer server) {
        if (server == null) return;
        ServerScoreboard board = server.getScoreboard();
        ScoreboardObjective existing = board.getNullableObjective(OBJECTIVE_ID);
        if (existing != null) board.removeObjective(existing);
        objective = null;
    }

    private static void install(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        // 移除旧版本残留（同名 objective）
        ScoreboardObjective existing = board.getNullableObjective(OBJECTIVE_ID);
        if (existing != null) board.removeObjective(existing);

        objective = board.addObjective(
            OBJECTIVE_ID,
            ScoreboardCriterion.DUMMY,
            Text.literal("§b§l服务器状态"),
            ScoreboardCriterion.RenderType.INTEGER
        );
        board.setObjectiveSlot(Scoreboard.SIDEBAR_DISPLAY_SLOT_ID, objective);
    }

    private static void refresh(MinecraftServer server) {
        if (objective == null) return;
        ServerScoreboard board = server.getScoreboard();

        double mspt = averageMspt(server);
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 1e-6));

        // 更新 EntityCap 动态上限（每秒一次，与 sidebar 刷新同频）
        if (com.tomkestookit.TomkesTookitConfig.enabled("world.entity_cap")) {
            com.tomkestookit.entitycap.EntityCap.updateCap(mspt);
        }

        String tpsLine  = "§7TPS §f"  + colorTps(tps)   + String.format("%.1f", tps);
        String msptLine = "§7MSPT §f" + colorMspt(mspt) + String.format("%.1f ms", mspt);
        String timeLine = "§7时间 §f" + formatMcTime(server);

        // 全服总实体数行（统计所有维度）
        int totalEntities = 0;
        for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
            totalEntities += world.iterateEntities().spliterator().getExactSizeIfKnown();
        }
        String totalLine = "§7总实体 §f" + colorEntityCount(totalEntities) + totalEntities;

        // 实体上限行（仅单人或取第一个玩家，多人显示第一个真人玩家）
        String capLine = null;
        if (com.tomkestookit.TomkesTookitConfig.enabled("world.entity_cap")) {
            int cap = com.tomkestookit.entitycap.EntityCap.getCurrentCap();
            // 找主世界第一个真人玩家来统计实体数
            net.minecraft.server.network.ServerPlayerEntity sample = null;
            for (net.minecraft.server.network.ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!p.isSpectator() && !com.tomkestookit.CarpetBotUtil.isFakePlayer(p)) {
                    sample = p;
                    break;
                }
            }
            if (sample != null && sample.getServerWorld() != null) {
                int count = com.tomkestookit.entitycap.EntityCap.countForPlayer(
                    sample.getServerWorld(), sample);
                String countColor = count >= cap ? "§c"
                    : count >= cap * 0.8 ? "§e"
                    : "§a";
                capLine = "§7范围实体 " + countColor + count + "§7/" + cap;
            }
        }

        java.util.Set<String> wanted = capLine != null
            ? java.util.Set.of(tpsLine, timeLine, msptLine, totalLine, capLine)
            : java.util.Set.of(tpsLine, timeLine, msptLine, totalLine);

        // 清掉不在新集合的旧行
        for (ScoreboardPlayerScore old : new java.util.ArrayList<>(board.getAllPlayerScores(objective))) {
            if (!wanted.contains(old.getPlayerName())) {
                board.resetPlayerScore(old.getPlayerName(), objective);
            }
        }

        board.getPlayerScore(tpsLine,  objective).setScore(5);
        board.getPlayerScore(timeLine, objective).setScore(4);
        board.getPlayerScore(msptLine, objective).setScore(3);
        board.getPlayerScore(totalLine, objective).setScore(2);
        if (capLine != null) board.getPlayerScore(capLine, objective).setScore(1);
    }

    private static String colorEntityCount(int count) {
        if (count <= 3000) return "" + Formatting.GREEN;
        if (count <= 5000) return "" + Formatting.YELLOW;
        return "" + Formatting.RED;
    }

    // ------------------------------------------------------------------ metrics

    private static double averageMspt(MinecraftServer server) {
        long total = 0;
        int count = 0;
        for (long ns : server.lastTickLengths) {
            if (ns <= 0) continue; // 服务器刚启动，还没跑满 100 tick
            total += ns;
            count++;
        }
        if (count == 0) return 0.0;
        return (total / (double) count) / 1_000_000.0; // ns -> ms
    }

    /** MC 世界时间 0=6:00，24000=第二天 6:00。用主世界的时间。*/
    private static String formatMcTime(MinecraftServer server) {
        long time = server.getOverworld().getTimeOfDay() % 24000L;
        // MC hour = (time / 1000 + 6) % 24
        int hours = (int) ((time / 1000L + 6L) % 24L);
        int minutes = (int) (((time % 1000L) * 60L) / 1000L);
        String daytime;
        if (hours >= 6 && hours < 18) {
            daytime = "§e☀"; // 白天
        } else {
            daytime = "§9☾"; // 夜晚
        }
        return String.format("%s §f%02d:%02d", daytime, hours, minutes);
    }

    private static String colorTps(double tps) {
        if (tps >= 19.0) return "" + Formatting.GREEN;
        if (tps >= 15.0) return "" + Formatting.YELLOW;
        return "" + Formatting.RED;
    }

    private static String colorMspt(double mspt) {
        if (mspt <= 45.0) return "" + Formatting.GREEN;
        if (mspt <= 55.0) return "" + Formatting.YELLOW;
        return "" + Formatting.RED;
    }
}
