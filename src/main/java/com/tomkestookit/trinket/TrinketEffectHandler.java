package com.tomkestookit.trinket;

import com.tomkestookit.TomkesTookitConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 饰品效果分发：每秒扫在线玩家的饰品袋，触发对应 applier。
 * 特殊效果（摔落免疫 / 掉落物吸附）通过独立 hook 实现。
 *
 * 性能：每 tick 只调一次 TrinketStore.read()（内存缓存命中 = O(1)），
 * 然后在本地扫 capacity 范围内的 id set。不再反复反序列化 SNBT。
 */
public final class TrinketEffectHandler {

    private static final int REFRESH_INTERVAL = 20; // 1 秒

    private TrinketEffectHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TrinketEffectHandler::tick);

        // 摔落免疫 (cats_eye)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!TomkesTookitConfig.enabled("item.trinket")) return true;
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!isFallDamage(source)) return true;
            return !hasTrinket(player, "cats_eye");
        });

        // 图腾碎片：拦截致死伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!TomkesTookitConfig.enabled("item.trinket")) return true;
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (player.getHealth() - amount > 0) return true;
            if (hasTrinket(player, "totem_splinter") || hasTrinket(player, "totem_heart")) {
                if (TrinketRegistry.tryTotemSplinter(player)) return false;
            }
            return true;
        });

        // 死亡保留经验 (ender_anchor)
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!TomkesTookitConfig.enabled("item.trinket")) return;
            if (alive) return;
            if (!hasTrinket(oldPlayer, "ender_anchor")) return;
            newPlayer.experienceLevel = oldPlayer.experienceLevel;
            newPlayer.experienceProgress = oldPlayer.experienceProgress;
            newPlayer.totalExperience = oldPlayer.totalExperience;
        });
    }

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("item.trinket")) return;
        int ticks = server.getTicks();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // 一次性读缓存 + 算出拥有的 trinket id set
            Set<String> owned = ownedTrinketsCached(player);

            // 每 tick 效果
            if (owned.contains("lodestar_pin")) {
                TrinketRegistry.TrinketDef def = TrinketRegistry.byId("lodestar_pin");
                if (def != null) def.applier().accept(player);
            }
            if (owned.contains("void_pearl")) {
                TrinketRegistry.trackGround(player);
                TrinketRegistry.TrinketDef def = TrinketRegistry.byId("void_pearl");
                if (def != null) def.applier().accept(player);
            }
            if (owned.contains("soul_lantern_charm")) {
                clearNearbyAggro(player);
            }
            if (owned.contains("magma_strider")) {
                TrinketRegistry.TrinketDef def = TrinketRegistry.byId("magma_strider");
                if (def != null) def.applier().accept(player);
            }

            // 其余效果每秒一次
            if (ticks % REFRESH_INTERVAL == 0) {
                for (String id : owned) {
                    if ("lodestar_pin".equals(id)) continue;
                    if ("void_pearl".equals(id)) continue;
                    if ("soul_lantern_charm".equals(id)) continue;
                    if ("magma_strider".equals(id)) continue;
                    TrinketRegistry.TrinketDef def = TrinketRegistry.byId(id);
                    if (def != null) def.applier().accept(player);
                }
            }
        }
    }

    /** 检查玩家饰品袋里是否有指定 trinket（仅计已解锁格）。*/
    public static boolean hasTrinket(ServerPlayerEntity player, String trinketId) {
        UUID uuid = player.getUuid();
        int capacity = TrinketStore.getCapacity(uuid);
        ItemStack[] bag = TrinketStore.read(uuid); // 缓存命中 = O(1)
        for (int i = 0; i < capacity && i < bag.length; i++) {
            if (trinketId.equals(TrinketRegistry.getId(bag[i]))) return true;
        }
        return false;
    }

    /** 读缓存并返回玩家已解锁格中的 trinket id 集合。*/
    private static Set<String> ownedTrinketsCached(ServerPlayerEntity player) {
        Set<String> set = new HashSet<>();
        UUID uuid = player.getUuid();
        int capacity = TrinketStore.getCapacity(uuid);
        ItemStack[] bag = TrinketStore.read(uuid);
        for (int i = 0; i < capacity && i < bag.length; i++) {
            String id = TrinketRegistry.getId(bag[i]);
            if (id != null) set.add(id);
        }
        return set;
    }

    private static boolean isFallDamage(DamageSource source) {
        return source.isIn(DamageTypeTags.IS_FALL);
    }

    /** 灵魂灯笼：清除 8 格内怪物对该玩家的仇恨目标。*/
    private static void clearNearbyAggro(ServerPlayerEntity player) {
        net.minecraft.server.world.ServerWorld world = player.getServerWorld();
        net.minecraft.util.math.Box box = player.getBoundingBox().expand(8.0);
        for (net.minecraft.entity.mob.MobEntity mob : world.getEntitiesByClass(
                net.minecraft.entity.mob.MobEntity.class, box, e -> true)) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
    }
}
