package com.tomkestookit.elite;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 战斗狂热追踪器：当玩家在 1 分钟内击杀 ≥10 只敌对生物 并 移动 ≥30 格，
 * 大幅提高该玩家附近的精英怪生成概率（2× 倍率，持续 60 秒）。
 *
 * 机制：
 *   - 每位玩家维护一个滑动窗口（60 秒），记录击杀数和移动距离
 *   - 满足条件后激活"狂热"状态，持续 60 秒
 *   - 狂热期间 EliteMobHandler 的 spawnChance 乘以 FRENZY_MULT
 *   - 狂热结束后计数器归零重新统计
 *
 * 生命周期：注册在 ServerTickEvents + ServerLivingEntityEvents.AFTER_DEATH。
 */
public final class CombatFrenzyTracker {

    /** 狂热时精英生成概率倍率。*/
    public static final double FRENZY_MULT = 3.0;

    /** 触发条件：1 分钟内击杀数。*/
    private static final int KILL_THRESHOLD = 10;
    /** 触发条件：1 分钟内移动格数。*/
    private static final double MOVE_THRESHOLD = 30.0;
    /** 窗口/状态持续时间（tick）。*/
    private static final int WINDOW_TICKS = 1200; // 60 秒

    private static final Map<UUID, PlayerFrenzyState> STATES = new HashMap<>();

    private CombatFrenzyTracker() {}

    public static void register() {
        // 击杀计数
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof MobEntity mob)) return;
            if (mob.getType().getSpawnGroup() != SpawnGroup.MONSTER) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
            getState(player.getUuid()).addKill();
        });

        // 每秒统计移动距离 + 状态过渡
        ServerTickEvents.END_SERVER_TICK.register(CombatFrenzyTracker::tick);
    }

    /** EliteMobHandler 调用：获取该玩家的精英生成倍率（1.0 = 正常，3.0 = 狂热）。*/
    public static double getSpawnMult(ServerPlayerEntity player) {
        PlayerFrenzyState state = STATES.get(player.getUuid());
        if (state == null) return 1.0;
        return state.frenzyActive ? FRENZY_MULT : 1.0;
    }

    /** 判断玩家当前是否在狂热状态。*/
    public static boolean isFrenzied(ServerPlayerEntity player) {
        PlayerFrenzyState state = STATES.get(player.getUuid());
        return state != null && state.frenzyActive;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return; // 每秒一次

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || player.isCreative()) continue;
            PlayerFrenzyState state = getState(player.getUuid());

            // 计算本秒移动距离
            Vec3d pos = player.getPos();
            if (state.lastPos != null) {
                double dist = pos.distanceTo(state.lastPos);
                // 忽略传送（单 tick 移动 > 20 格多半是传送）
                if (dist < 20.0) {
                    state.moveDistance += dist;
                }
            }
            state.lastPos = pos;

            // 窗口过期检查
            state.windowTicksElapsed += 20;
            if (state.frenzyActive) {
                // 狂热持续中
                if (state.windowTicksElapsed >= WINDOW_TICKS) {
                    // 狂热结束
                    state.reset();
                    player.sendMessage(
                        net.minecraft.text.Text.literal("§8[狂热结束] 精英怪生成恢复正常。")
                            .styled(s -> s.withItalic(false)), true);
                }
            } else {
                // 检查是否达到触发条件
                if (state.kills >= KILL_THRESHOLD && state.moveDistance >= MOVE_THRESHOLD) {
                    state.frenzyActive = true;
                    state.windowTicksElapsed = 0; // 从现在开始计时 60 秒狂热
                    player.sendMessage(
                        net.minecraft.text.Text.literal("§c§l⚔ 战斗狂热！§7精英怪生成概率大幅提升 60 秒")
                            .styled(s -> s.withItalic(false)), true);
                } else if (state.windowTicksElapsed >= WINDOW_TICKS) {
                    // 1 分钟内没达标，重置窗口
                    state.reset();
                }
            }
        }

        // 清理离线玩家
        if (server.getTicks() % 600 == 0) {
            STATES.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        }
    }

    private static PlayerFrenzyState getState(UUID uuid) {
        return STATES.computeIfAbsent(uuid, k -> new PlayerFrenzyState());
    }

    private static class PlayerFrenzyState {
        int kills = 0;
        double moveDistance = 0;
        int windowTicksElapsed = 0;
        boolean frenzyActive = false;
        Vec3d lastPos = null;

        void addKill() {
            kills++;
        }

        void reset() {
            kills = 0;
            moveDistance = 0;
            windowTicksElapsed = 0;
            frenzyActive = false;
        }
    }
}
