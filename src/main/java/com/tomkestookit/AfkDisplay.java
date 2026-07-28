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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 挂机玩家 tab 名后缀 " [AFK]"。
 *
 * 用一个专用 scoreboard team "tomkestookit_afk" + suffix 实现：加入 team 的玩家
 * 名字后自动出现 suffix，退出 team 后恢复。suffix 只影响客户端显示层，不改玩家名字实体。
 *
 * 挂机判定：位置连续 60 秒不动（位移平方 &lt; MOVE_EPS_SQ）。
 * 内建在这个类里，独立于其它性能优化模块。
 */
public final class AfkDisplay {

    private static final String TEAM_NAME = "tomkestookit_afk";

    /** 挂机阈值：60 秒 = 1200 tick */
    private static final int IDLE_TICKS = 1200;
    /** 判定"在动"的最小位移平方（避免浮点抖动）*/
    private static final double MOVE_EPS_SQ = 0.04;

    private static final class Idle {
        double x, y, z;
        int lastMoveTick;
    }
    private static final Map<UUID, Idle> IDLE_MAP = new HashMap<>();

    /** 上一 tick 已标记为 AFK 的玩家（用于跟当前对比，判断 join/leave）。*/
    private static final Set<UUID> AFK_MARKED = new HashSet<>();

    /** 玩家进 AFK team 前的原 team 名字，用于恢复。null = 原本没 team。*/
    private static final Map<UUID, String> PREVIOUS_TEAM = new HashMap<>();

    private AfkDisplay() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AfkDisplay::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uid = handler.player.getUuid();
            IDLE_MAP.remove(uid);
            AFK_MARKED.remove(uid);
            removeFromTeam(server, handler.player);
        });
    }

    public static boolean isAfk(ServerPlayerEntity player) {
        return AFK_MARKED.contains(player.getUuid());
    }

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("world.afk")) {
            // 关闭后清一次已挂机的玩家 team 前缀，防止残留
            if (!AFK_MARKED.isEmpty()) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (AFK_MARKED.remove(p.getUuid())) removeFromTeam(server, p);
                }
                AFK_MARKED.clear();
            }
            return;
        }
        int now = server.getTicks();

        // 每 tick 追踪玩家位置（成本极小）
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Idle idle = IDLE_MAP.computeIfAbsent(p.getUuid(), k -> {
                Idle i = new Idle();
                i.x = p.getX(); i.y = p.getY(); i.z = p.getZ();
                i.lastMoveTick = now;
                return i;
            });
            double dx = p.getX() - idle.x;
            double dy = p.getY() - idle.y;
            double dz = p.getZ() - idle.z;
            if (dx*dx + dy*dy + dz*dz > MOVE_EPS_SQ) {
                idle.x = p.getX(); idle.y = p.getY(); idle.z = p.getZ();
                idle.lastMoveTick = now;
            }
        }

        // 每秒同步一次 tab suffix
        if (now % 20 == 0) {
            ensureTeam(server);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                Idle idle = IDLE_MAP.get(p.getUuid());
                boolean idleNow = idle != null && (now - idle.lastMoveTick >= IDLE_TICKS);
                boolean marked = AFK_MARKED.contains(p.getUuid());
                if (idleNow && !marked) {
                    addToTeam(server, p);
                    AFK_MARKED.add(p.getUuid());
                } else if (!idleNow && marked) {
                    removeFromTeam(server, p);
                    AFK_MARKED.remove(p.getUuid());
                }
            }
        }
    }

    private static void ensureTeam(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.addTeam(TEAM_NAME);
            team.setSuffix(Text.literal(" [AFK]").formatted(Formatting.GRAY));
            team.setCollisionRule(AbstractTeam.CollisionRule.ALWAYS);
        }
    }

    private static void addToTeam(MinecraftServer server, ServerPlayerEntity player) {
        ServerScoreboard board = server.getScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) return;
        String playerName = player.getGameProfile().getName();
        Team current = board.getPlayerTeam(playerName);
        if (current != null && current != team) {
            PREVIOUS_TEAM.put(player.getUuid(), current.getName());
        }
        board.addPlayerToTeam(playerName, team);
    }

    /** 管理员关掉 world.afk 时的清理：把所有已标记的玩家从 team 移出。*/
    public static void flushAll(MinecraftServer server) {
        if (server == null || AFK_MARKED.isEmpty()) return;
        for (ServerPlayerEntity p : new java.util.ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (AFK_MARKED.remove(p.getUuid())) removeFromTeam(server, p);
        }
        AFK_MARKED.clear();
        IDLE_MAP.clear();
    }

    private static void removeFromTeam(MinecraftServer server, ServerPlayerEntity player) {
        ServerScoreboard board = server.getScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) return;
        String playerName = player.getGameProfile().getName();
        Team current = board.getPlayerTeam(playerName);
        if (current == team) {
            board.removePlayerFromTeam(playerName, team);
        }
        String previousName = PREVIOUS_TEAM.remove(player.getUuid());
        if (previousName != null) {
            Team prev = board.getTeam(previousName);
            if (prev != null) board.addPlayerToTeam(playerName, prev);
        }
    }
}
