package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * 每 tick 把被锁定的 Carpet 假人拉回锚点。
 *
 *   - 锚点存在于同一维度：velocity 清零 + requestTeleport 回原位（含 yaw/pitch）
 *   - 假人不在同一维度：跨维度 teleport（Carpet 假人跨维度往往需要 executeCommand）
 *   - 玩家断线：清理该玩家所有锚点/无击退记录
 *   - 只处理被 CarpetBotUtil.isFakePlayer 判定为假人的目标
 */
public final class BotAnchorHandler {

    private BotAnchorHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BotAnchorHandler::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BotAnchorTracker.onDisconnect(handler.player.getUuid()));
    }

    /** 位置漂移超过这个阈值（方块）才拉回。太小会每 tick 触发传送、打断攻击。*/
    private static final double DRIFT_EPS_SQ = 0.25 * 0.25;

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try {
                java.util.UUID uid = p.getUuid();
                BotAnchorTracker.Anchor a = BotAnchorTracker.anchor(uid);
                BotAnchorTracker.TargetMode mode = BotAnchorTracker.targetMode(uid);
                boolean hasAnchor = a != null;
                boolean hasTarget = mode != BotAnchorTracker.TargetMode.NONE;
                if (!hasAnchor && !hasTarget) continue;   // 快速跳过：绝大多数玩家没配任何东西
                if (!CarpetBotUtil.isFakePlayerStrict(p)) continue;
                if (hasTarget) handleTargeting(p, mode);
                if (!hasAnchor) continue;

                ServerWorld target = resolveWorld(server, a.dim());
                if (target == null) continue;

                // 跨维度：必须走完整 teleport；视角保留假人当前朝向，不用锚点里的
                if (p.getWorld() != target) {
                    p.teleport(target, a.x(), a.y(), a.z(), p.getYaw(), p.getPitch());
                    p.setVelocity(0, 0, 0);
                    p.velocityModified = true;
                    p.fallDistance = 0;
                    continue;
                }

                // 同维度：只锁位置，不锁视角（否则假人无法瞄准目标造成伤害）
                double dx = p.getX() - a.x();
                double dy = p.getY() - a.y();
                double dz = p.getZ() - a.z();
                if (dx * dx + dy * dy + dz * dz > DRIFT_EPS_SQ) {
                    // 用玩家自己当前视角 refresh，不覆盖 yaw/pitch，避免打断 attack raycast
                    p.networkHandler.requestTeleport(a.x(), a.y(), a.z(), p.getYaw(), p.getPitch());
                    p.setVelocity(0, 0, 0);
                    p.velocityModified = true;
                }
                p.fallDistance = 0;
            } catch (Throwable ignored) {
                // 单个假人的处理出错不影响其他假人和真玩家
            }
        }
    }

    private static void handleTargeting(ServerPlayerEntity bot, BotAnchorTracker.TargetMode mode) {
        Entity target = findTarget(bot, mode);
        if (target == null) return;

        double dx = target.getX() - bot.getX();
        double dy = target.getEyeY() - bot.getEyeY();
        double dz = target.getZ() - bot.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        bot.headYaw = yaw;
        bot.bodyYaw = yaw;
    }

    private static Entity findTarget(ServerPlayerEntity bot, BotAnchorTracker.TargetMode mode) {
        // 半径 6：足够覆盖攻击距离 + 缓冲；expand(8) 会多扫 78% 的空间
        Box box = bot.getBoundingBox().expand(6.0);
        java.util.List<Entity> candidates = bot.getWorld().getOtherEntities(bot, box, e -> isValidTarget(bot, e, mode));
        if (candidates.isEmpty()) return null;
        // 只对最近的做 canSee raycast（贵）；不满足退第二近
        candidates.sort(java.util.Comparator.comparingDouble(bot::squaredDistanceTo));
        int limit = Math.min(candidates.size(), 3);
        for (int i = 0; i < limit; i++) {
            Entity e = candidates.get(i);
            if (bot.canSee(e)) return e;
        }
        return null;
    }

    private static boolean isValidTarget(ServerPlayerEntity bot, Entity e, BotAnchorTracker.TargetMode mode) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof ServerPlayerEntity) return false; // 永远不索敌玩家（真玩家/假人都不打）
        return switch (mode) {
            case ARMOR_STAND -> e instanceof ArmorStandEntity;
            case PASSIVE -> e instanceof PassiveEntity;
            case HOSTILE -> e instanceof HostileEntity;
            default -> false;
        };
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String dim) {
        return com.tomkestookit.logistics.LogisticsMarker.resolveWorld(server, dim);
    }
}
