package com.tomkestookit;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillageGossipType;

import java.util.UUID;

/**
 * gossip 立即同步：把某个玩家的 major/minor positive gossip 立刻广播给附近所有村民，
 * 跳过 vanilla 的慢传播（需要村民 brain tick 才交流）。
 *
 * 场景：
 *   - 僵尸村民被治愈 → 转换完成瞬间广播（+MAJOR_POSITIVE +20, +MINOR_POSITIVE +25）
 *   - 袭击胜利 → 玩家获得 HERO_OF_THE_VILLAGE → 广播（+MAJOR_POSITIVE +20）
 *
 * 广播半径 48 格（对应"英雄村民"传播半径的常见值），只加入 gossip，不覆盖既有值。
 *
 * 与 VillagerBrainFreezeMixin 兼容：这里直接调 VillagerGossips.startGossip，
 * 不依赖村民 brain tick 运行，冻结的村民也能收到 gossip。
 */
public final class GossipBroadcast {

    /** 广播半径 48 格。*/
    private static final double RADIUS = 48.0;

    private GossipBroadcast() {}

    /**
     * 僵尸村民治愈：广播给 RADIUS 内的成年村民。
     * 对应 vanilla ZombieVillagerEntity.finishConversion 里的 MajorPositive 效果。
     */
    public static void broadcastZombieVillagerCured(ServerWorld world, ServerPlayerEntity healer, VillagerEntity newVillager) {
        if (!TomkesTookitConfig.enabled("world.villager_gossip_broadcast")) return;
        broadcast(world, newVillager.getX(), newVillager.getY(), newVillager.getZ(),
            healer.getUuid(), 20, 25);
    }

    /**
     * 袭击胜利：给拿到 HERO_OF_THE_VILLAGE 的玩家周围广播。
     * 对应 vanilla HeroOfTheVillageGossipTask 的效果。
     */
    public static void broadcastHero(ServerWorld world, ServerPlayerEntity hero) {
        if (!TomkesTookitConfig.enabled("world.villager_gossip_broadcast")) return;
        broadcast(world, hero.getX(), hero.getY(), hero.getZ(),
            hero.getUuid(), 20, 0);
    }

    private static void broadcast(ServerWorld world, double x, double y, double z,
                                  UUID playerUuid, int majorAmount, int minorAmount) {
        Box box = new Box(x - RADIUS, y - RADIUS, z - RADIUS,
                          x + RADIUS, y + RADIUS, z + RADIUS);
        for (VillagerEntity v : world.getEntitiesByClass(VillagerEntity.class, box, e -> !e.isBaby() && e.isAlive())) {
            if (majorAmount > 0) v.getGossip().startGossip(playerUuid, VillageGossipType.MAJOR_POSITIVE, majorAmount);
            if (minorAmount > 0) v.getGossip().startGossip(playerUuid, VillageGossipType.MINOR_POSITIVE, minorAmount);
        }
    }
}
