package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家头顶名字附加实时血量 + 等级 + 身份标签 —— 分两行显示。
 *
 * 布局：
 *   第一行：{@code [玩家]/[假人] 玩家名 §c<HP>❤}   ← team prefix + name + suffix
 *   第二行：{@code <LV> §6Lv}                    ← belowName scoreboard 显示等级
 *
 * 实现细节：
 *   - 身份 走 team prefix（[假人] 红加粗 / [玩家] 绿加粗）
 *   - HP 走 team suffix，每 20 tick 手动刷新（vanilla 无 HP=整数 auto-sync 到 suffix）
 *   - Level 走 vanilla {@code LEVEL} criterion + BELOW_NAME_DISPLAY_SLOT_ID，自动同步
 *   - 玩家 team 名 {@code tt_hp_<uuid前10位>}，vanilla team 名长度 ≤16 限制
 *
 * 兼容性：
 *   - 只改 team prefix/suffix + 一个 belowName objective，不改 DisplayName / CustomName
 *   - 与 CarpetBotTabNameMixin（只影响 tab 列表）不冲突（那里前缀走 getPlayerListName）
 *   - 与 TabHealth（LIST 槽 hearts 渲染）不冲突（不同 slot）
 */
public final class PlayerNameTag {

    private static final int REFRESH_INTERVAL = 20; // 1 秒
    private static final String TEAM_PREFIX = "tt_hp_"; // 后接玩家 UUID 前 10 位（总长 ≤16）
    private static final String LEVEL_OBJECTIVE = "tomkestookit_level";

    /** 已创建过 team 的玩家 UUID，避免每 tick 重复添加。*/
    private static final Set<UUID> INITIALIZED = new HashSet<>();
    private static ScoreboardObjective levelObjective;

    private PlayerNameTag() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerNameTag::installLevelObjective);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % REFRESH_INTERVAL != 0) return;
            if (!TomkesTookitConfig.enabled("ui.player_nametag")) return;
            refreshAll(server);
        });
        // 玩家离线时清理 team（避免 team 长期堆积）
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (server == null) return;
            removeTeam(server, handler.player);
        });
    }

    /** 卸载时清理所有 team + 移除 belowName objective。*/
    public static void uninstall(MinecraftServer server) {
        if (server == null) return;
        ServerScoreboard board = server.getScoreboard();
        for (Team t : new java.util.ArrayList<>(board.getTeams())) {
            if (t.getName().startsWith(TEAM_PREFIX)) {
                board.removeTeam(t);
            }
        }
        ScoreboardObjective existing = board.getNullableObjective(LEVEL_OBJECTIVE);
        if (existing != null) board.removeObjective(existing);
        INITIALIZED.clear();
        levelObjective = null;
    }

    // ------------------------------------------------------------------ setup

    private static void installLevelObjective(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        ScoreboardObjective existing = board.getNullableObjective(LEVEL_OBJECTIVE);
        if (existing != null) board.removeObjective(existing);

        levelObjective = board.addObjective(
            LEVEL_OBJECTIVE,
            ScoreboardCriterion.LEVEL,           // 内置只读，vanilla 每 tick 同步玩家等级
            Text.literal("§6Lv"),                 // 显示在等级数字右侧
            ScoreboardCriterion.RenderType.INTEGER
        );
        board.setObjectiveSlot(Scoreboard.BELOW_NAME_DISPLAY_SLOT_ID, levelObjective);
    }

    // ------------------------------------------------------------------ refresh

    private static void refreshAll(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            updatePlayer(board, p);
        }
    }

    private static String teamName(ServerPlayerEntity p) {
        String u = p.getUuidAsString().replace("-", "");
        return TEAM_PREFIX + u.substring(0, Math.min(10, u.length()));
    }

    private static void updatePlayer(ServerScoreboard board, ServerPlayerEntity p) {
        String name = teamName(p);
        Team team = board.getTeam(name);
        if (team == null) {
            try {
                team = board.addTeam(name);
                team.setShowFriendlyInvisibles(true);
                team.setCollisionRule(AbstractTeam.CollisionRule.ALWAYS);
                board.addPlayerToTeam(p.getName().getString(), team);
                INITIALIZED.add(p.getUuid());
            } catch (Exception ignored) {
                return; // team 冲突（同名已存在但不属于我们），跳过
            }
        } else if (!INITIALIZED.contains(p.getUuid())) {
            board.addPlayerToTeam(p.getName().getString(), team);
            INITIALIZED.add(p.getUuid());
        }

        // 身份前缀：[假人] / [玩家]（每 tick 重设是幂等的，写入 team 内部字段无副作用）
        boolean isBot = com.tomkestookit.CarpetBotUtil.isFakePlayer(p);
        Text prefix = isBot
            ? Text.literal("[假人] ").formatted(Formatting.RED, Formatting.BOLD)
            : Text.literal("[玩家] ").formatted(Formatting.GREEN, Formatting.BOLD);
        team.setPrefix(prefix);

        int hp = (int) Math.ceil(p.getHealth());
        int maxHp = (int) Math.ceil(p.getMaxHealth());

        // 血量颜色随比例变化
        Formatting hpColor = hp >= maxHp * 0.66 ? Formatting.GREEN
            : hp >= maxHp * 0.33 ? Formatting.YELLOW
            : Formatting.RED;

        Text suffix = Text.empty()
            .append(Text.literal(" ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(hp)).formatted(hpColor))
            .append(Text.literal("❤").formatted(Formatting.RED));

        team.setSuffix(suffix);
    }

    private static void removeTeam(MinecraftServer server, ServerPlayerEntity p) {
        if (p == null) return;
        ServerScoreboard board = server.getScoreboard();
        Team t = board.getTeam(teamName(p));
        if (t != null) board.removeTeam(t);
        INITIALIZED.remove(p.getUuid());
    }
}
