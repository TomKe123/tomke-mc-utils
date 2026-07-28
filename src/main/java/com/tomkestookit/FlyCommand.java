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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("fly")
                    .requires(source -> true) // 全员可用
                    .executes(FlyCommand::toggle)
            ));

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
                int effLevel = Math.min(20, Math.max(1, level));
                float ratePerTickXp = effLevel / TICKS_PER_MINUTE;

                // 直接对 progress 做小数扣减，客户端每 tick 都能看到条子平滑动。
                // progress 单位是"当前 level 到下一级所需 XP"的比例。
                int nextLevelXp = player.getNextLevelExperience();
                if (nextLevelXp <= 0) nextLevelXp = 1;
                float progressStep = ratePerTickXp / nextLevelXp;

                progress -= progressStep;
                while (progress < 0.0f && level > 0) {
                    level -= 1;
                    progress += 1.0f;
                }
                if (level == 0 && progress < 0.0f) {
                    cutoff(player, "经验用光了，飞不动了。");
                    continue;
                }

                // 三个字段必须保持一致：totalExperience = 累计所有已过等级 xp + progress * 当前级需求。
                // 只改 level/progress 不改 totalExperience → 传送 / 重生的 copyFrom 之后三者
                // 不一致，vanilla 或其他 mod 可能重新用 totalExperience 反推 level/progress，
                // 造成客户端 XP 条错乱。
                player.experienceLevel = level;
                player.experienceProgress = progress;
                player.totalExperience = computeTotalXp(level, progress);
                // 主动发包让客户端每 tick 看到平滑变化；vanilla tick 也会自己发（因为
                // totalExperience 已被我们同步过），两者语义一致，不会闪烁。
                player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(
                        progress, player.totalExperience, level));
            }
        });

        // 重连恢复飞行权限 + 补一次经验条广播（新 handler 需要 fresh sync）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (XP_DEBT.containsKey(player.getUuid())) {
                enableFly(player);
                syncExperience(player);
            }
        });

        // 重生：新的 ServerPlayerEntity 会重置 abilities，重新赋予
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (XP_DEBT.containsKey(newPlayer.getUuid())) {
                enableFly(newPlayer);
                syncExperience(newPlayer);
            }
        });

        // 走传送门（下界/末地）跨维度时 abilities / experience 在客户端会被重置。
        // 补一次同步：sendAbilitiesUpdate + ExperienceBarUpdateS2CPacket。
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> {
                if (XP_DEBT.containsKey(player.getUuid())) {
                    enableFly(player);
                    syncExperience(player);
                }
            });
    }

    /** 主动把当前 level/progress/total 广播给玩家客户端（用于传送/重生后立即同步）。*/
    private static void syncExperience(ServerPlayerEntity player) {
        int lvl = player.experienceLevel;
        float prog = player.experienceProgress;
        // 重算 totalExperience，保证与 level/progress 一致
        player.totalExperience = computeTotalXp(lvl, prog);
        player.networkHandler.sendPacket(
            new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(
                prog, player.totalExperience, lvl));
    }

    /** 从当前 level 升到下一级所需 XP（vanilla 公式）。*/
    private static int xpForLevelUp(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** total_experience = 累计所有已过等级的 xp + 当前进度 * 当前级需求。*/
    private static int computeTotalXp(int level, float progress) {
        int total = 0;
        for (int i = 0; i < level; i++) total += xpForLevelUp(i);
        total += Math.round(progress * xpForLevelUp(level));
        return total;
    }

    private static int toggle(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        if (!TomkesTookitConfig.enabled("cmd.fly")) {
            player.sendMessage(Text.literal("§c[/fly] 该功能已被管理员禁用").formatted(Formatting.RED), false);
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

    /** 管理员关掉 cmd.fly 时的清理：把所有启用 fly 的玩家还原。*/
    public static void disableAll(net.minecraft.server.MinecraftServer server) {
        if (server == null || XP_DEBT.isEmpty()) return;
        for (ServerPlayerEntity p : new java.util.ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (XP_DEBT.remove(p.getUuid()) != null) {
                disableFly(p);
            }
        }
        XP_DEBT.clear();
    }
}
