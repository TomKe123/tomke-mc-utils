package com.tomkestookit;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /fly 命令：全员可用。
 *
 *   - 开启后玩家获得 abilities.allowFlying，双击跳跃即可起飞（vanilla 行为）。
 *   - 消耗经验飞行：每 1 XP 可飞 (1/经验等级) 分钟。
 *     换算：每分钟扣 (当前等级) 点 XP → 每 tick 扣 level/1200 点。
 *     因为每 tick 消耗是小数，用 XP_DEBT 累加，凑够整数再一次性扣掉。
 *   - 只在玩家"实际在飞"（abilities.flying == true）时扣。
 *   - 经验归零自动关闭飞行；重连和重生会自动恢复飞行权限；
 *     创造 / 观察者不受影响。
 *
 * 状态存在内存里，服务器重启后需要重新 /fly 开启。
 * XP_DEBT 的 keySet 同时充当"启用集合"。
 */
public final class FlyCommand {

    private static final Map<UUID, Float> XP_DEBT = new ConcurrentHashMap<>();
    private static final float TICKS_PER_MINUTE = 20f * 60f; // = 1200

    private FlyCommand() {}

    /** 供菜单查询：玩家当前是否处于飞行启用状态。*/
    public static boolean isEnabled(UUID uuid) {
        return XP_DEBT.containsKey(uuid);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("fly")
                    .requires(source -> true) // 全员可用
                    .executes(FlyCommand::toggle)
            );
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (XP_DEBT.isEmpty()) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                if (!XP_DEBT.containsKey(uuid)) continue;
                if (player.isCreative() || player.isSpectator()) continue;
                if (!player.getAbilities().flying) continue;

                int level = player.experienceLevel;
                float progress = player.experienceProgress;
                if (level <= 0 && progress <= 0.0f) {
                    cutoff(player, "经验用光了，飞不动了。");
                    continue;
                }

                // 速率：level XP / 分钟；level 0（有余量）时兜底按 1 计；上限 20 XP/分钟
                // （即 1 XP 至少能飞 3 秒，防止高等级瞬秒经验）
                int effLevel = Math.min(20, Math.max(1, level));
                float rate = effLevel / TICKS_PER_MINUTE;
                float debt = XP_DEBT.getOrDefault(uuid, 0.0f) + rate;
                if (debt >= 1.0f) {
                    int amount = (int) debt;
                    debt -= amount;
                    // addExperience 支持负值：会自动跌落等级，clamp 到 0
                    player.addExperience(-amount);
                }
                XP_DEBT.put(uuid, debt);
            }
        });

        // 重连恢复飞行权限
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (XP_DEBT.containsKey(player.getUuid())) {
                enableFly(player);
            }
        });

        // 重生时新的 ServerPlayerEntity 会重置 abilities，重新赋予
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (XP_DEBT.containsKey(newPlayer.getUuid())) {
                enableFly(newPlayer);
            }
        });

        // 走传送门（下界/末地）跨维度时不触发 AFTER_RESPAWN，abilities 在服务端
        // 虽然保留但客户端会在维度切换后重置 flying 状态导致坠落。这里补一次
        // sendAbilitiesUpdate 同步给客户端。
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> {
                if (XP_DEBT.containsKey(player.getUuid())) {
                    enableFly(player);
                }
            });
    }

    private static int toggle(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        if (player.isCreative() || player.isSpectator()) {
            player.sendMessage(Text.literal("创造/观察者已经能飞了，不需要 /fly。").formatted(Formatting.YELLOW), false);
            return 0;
        }

        UUID uuid = player.getUuid();
        if (XP_DEBT.containsKey(uuid)) {
            XP_DEBT.remove(uuid);
            disableFly(player);
            player.sendMessage(Text.literal("飞行已关闭。").formatted(Formatting.YELLOW), false);
        } else {
            if (player.experienceLevel <= 0 && player.experienceProgress <= 0.0f) {
                player.sendMessage(Text.literal("经验不足，无法起飞。").formatted(Formatting.RED), false);
                return 0;
            }
            XP_DEBT.put(uuid, 0.0f);
            enableFly(player);
            player.sendMessage(
                Text.literal("飞行已开启：每 1 点经验可飞 1/等级 分钟（即每分钟扣当前等级点 XP）。").formatted(Formatting.GREEN),
                false
            );
        }
        return 1;
    }

    private static void enableFly(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return;
        player.getAbilities().allowFlying = true;
        player.sendAbilitiesUpdate();
    }

    private static void disableFly(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return;
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
    }

    private static void cutoff(ServerPlayerEntity player, String reason) {
        XP_DEBT.remove(player.getUuid());
        disableFly(player);
        player.sendMessage(Text.literal(reason).formatted(Formatting.RED), false);
    }
}
