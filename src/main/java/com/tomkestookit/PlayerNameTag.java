package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.AbstractTeam;
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
 * 玩家头顶名字附加实时血量 + 等级。
 *
 *   显示格式：{@code 玩家名 §c<HP>❤ §aLv<LV>}
 *
 * 通过 vanilla scoreboard team 的 prefix/suffix 机制实现（每玩家独占一个 team，
 * suffix 每 20 tick 刷新一次）。这个改法只动 team suffix，不改玩家 CustomName /
 * DisplayName，与假人前缀（{@link com.tomkestookit.mixin.CarpetBotTabNameMixin}）
 * 也不冲突（那个只影响 tab 列表，不影响头顶名）。
 */
public final class PlayerNameTag {

    private static final int REFRESH_INTERVAL = 20; // 1 秒
    private static final String TEAM_PREFIX = "tt_hp_"; // 后接玩家 UUID 前 10 位（总长≤16）

    /** 已创建过 team 的玩家 UUID，避免每 tick 重复添加。*/
    private static final Set<UUID> INITIALIZED = new HashSet<>();

    private PlayerNameTag() {}

    public static void register() {
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

    /** 卸载时清理所有 team。*/
    public static void uninstall(MinecraftServer server) {
        if (server == null) return;
        ServerScoreboard board = server.getScoreboard();
        for (Team t : new java.util.ArrayList<>(board.getTeams())) {
            if (t.getName().startsWith(TEAM_PREFIX)) {
                board.removeTeam(t);
            }
        }
        INITIALIZED.clear();
    }

    private static void refreshAll(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            updatePlayer(board, p);
        }
    }

    private static String teamName(ServerPlayerEntity p) {
        // UUID 前 10 个十六进制字符 + 前缀 = 16 字符（vanilla team 名限制）
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

        int hp = (int) Math.ceil(p.getHealth());
        int maxHp = (int) Math.ceil(p.getMaxHealth());
        int lv = p.experienceLevel;

        // 血量颜色随比例变化
        Formatting hpColor = hp >= maxHp * 0.66 ? Formatting.GREEN
            : hp >= maxHp * 0.33 ? Formatting.YELLOW
            : Formatting.RED;

        Text suffix = Text.empty()
            .append(Text.literal(" ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(hp)).formatted(hpColor))
            .append(Text.literal("❤ ").formatted(Formatting.RED))
            .append(Text.literal("Lv").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(lv)).formatted(Formatting.GOLD));

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
