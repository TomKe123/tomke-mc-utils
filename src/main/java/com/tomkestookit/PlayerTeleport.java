package com.tomkestookit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 统一的玩家传送入口 + 传送后 HUD 兜底。
 *
 * 1.20.1 已知客户端 bug：跨维度 teleport 之后经验条 HUD 会显示为空/异常，
 * 直到玩家下一次自然地获得或消耗经验才恢复。
 *
 * 兜底思路：让 vanilla 自己去发经验包。
 *   ServerPlayerEntity.playerTick 每 tick 检查 {@code totalExperience != syncedExperience}，
 *   不等就发一次 {@code ExperienceBarUpdateS2CPacket}（参数由 vanilla 自己拼，绝对正确）。
 *   我们传送完调 {@code addExperience(1)}：totalExperience 涨 1 → 下一 tick vanilla 自
 *   动重发包 → HUD 修好。20 tick 后再 {@code addExperience(-1)} 精确抵消。
 *
 * 副作用：
 *   - 玩家的死亡分数（addScore）会 +1 再 -1 → 净零；只在传送瞬间死掉的极端情况下
 *     score 可能落在 +1，可接受
 *   - 20 tick 内玩家如果获得了别的经验，我们的 -1 依然只减 1，不干扰其他 XP 变动
 *   - 同一玩家 20 tick 内被反复传送：待回退量按次累加、到期时间刷新到最后一次+20
 *   - 玩家 20 tick 内下线：{@link net.minecraft.server.PlayerManager#getPlayer} 返回 null
 *     时跳过（经验持久化在 NBT，+1 已落盘；下次上线看到 +1 不回退。数值差异 1 点，可接受）
 */
public final class PlayerTeleport {

    private static final int REVERT_DELAY_TICKS = 20;

    /** uuid → {expireTick, 累积待回退的经验量}。*/
    private static final Map<UUID, int[]> PENDING = new HashMap<>();

    private PlayerTeleport() {}

    /** 由 {@link TomkesTookitMod#onInitialize} 调一次，注册 tick handler。*/
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayerTeleport::onTick);
    }

    /** 传送并触发经验条 HUD 兜底同步。参数与 {@link ServerPlayerEntity#teleport} 一致。*/
    public static void teleport(ServerPlayerEntity player, ServerWorld world,
                                double x, double y, double z, float yaw, float pitch) {
        player.teleport(world, x, y, z, yaw, pitch);
        player.addExperience(1);

        int expireAt = player.getServer().getTicks() + REVERT_DELAY_TICKS;
        int[] rec = PENDING.get(player.getUuid());
        if (rec == null) {
            PENDING.put(player.getUuid(), new int[]{expireAt, 1});
        } else {
            rec[0] = expireAt;  // 到期刷到最后一次传送+20，避免早期 -1 干扰后续传送
            rec[1] += 1;
        }
    }

    private static void onTick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        int now = server.getTicks();
        Iterator<Map.Entry<UUID, int[]>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, int[]> e = it.next();
            int[] rec = e.getValue();
            if (now < rec[0]) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p != null) p.addExperience(-rec[1]);
            it.remove();
        }
    }
}
