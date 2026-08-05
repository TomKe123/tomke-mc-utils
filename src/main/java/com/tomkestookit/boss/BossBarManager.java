package com.tomkestookit.boss;

import com.tomkestookit.TomkesTookitMod;
import com.tomkestookit.boss.BossTokenRegistry.BossDef;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 活跃 Boss Bar 管理：跟踪存活 Boss、更新血条、处理死亡/超时。
 */
public final class BossBarManager {

    private BossBarManager() {}

    public static final class ActiveBoss {
        public final ServerBossBar bar;
        public final UUID mobUuid;
        public final String bossId;
        public final ServerWorld world;
        public long lastCombatTick;
        public final BlockPos spawnPos;

        ActiveBoss(ServerBossBar bar, UUID mobUuid, String bossId,
                   ServerWorld world, long lastCombatTick, BlockPos spawnPos) {
            this.bar = bar;
            this.mobUuid = mobUuid;
            this.bossId = bossId;
            this.world = world;
            this.lastCombatTick = lastCombatTick;
            this.spawnPos = spawnPos;
        }
    }
    public static final Map<UUID, ActiveBoss> ACTIVE = new HashMap<>();

    private static final double VISIBLE_RADIUS_SQ = 100.0 * 100.0;
    /** 20 秒未受伤 → 治疗到满血；60 秒未受伤 → 消失。*/
    private static final long AFK_HEAL_TICKS = 20L * 20;
    private static final long AFK_DESPAWN_TICKS = 20L * 60;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BossBarManager::tick);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActiveBoss>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveBoss> entry = it.next();
            ActiveBoss active = entry.getValue();

            Entity mob = active.world.getEntity(active.mobUuid);
            if (!(mob instanceof LivingEntity living) || living.isRemoved() || living.isDead()) {
                onBossDead(active, server);
                it.remove();
                continue;
            }

            // Update health bar
            float pct = Math.max(0f, Math.min(1f, living.getHealth() / living.getMaxHealth()));
            active.bar.setPercent(pct);

            // Detect combat: if the boss was recently hurt (hurtTime > 0), refresh combat tick
            if (living.hurtTime > 0) {
                active.lastCombatTick = server.getTicks();
            }

            // Add/remove players within visible radius
            Vec3d mobPos = living.getPos();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getWorld() != active.world) {
                    active.bar.removePlayer(player);
                    continue;
                }
                double distSq = player.getPos().squaredDistanceTo(mobPos);
                if (distSq <= VISIBLE_RADIUS_SQ) {
                    if (!active.bar.getPlayers().contains(player)) {
                        active.bar.addPlayer(player);
                    }
                } else {
                    active.bar.removePlayer(player);
                }
            }

            // Anti-AFK: if not recently damaged, heal, then despawn
            long ticksSinceCombat = server.getTicks() - active.lastCombatTick;
            if (ticksSinceCombat > AFK_DESPAWN_TICKS) {
                // Silent despawn: no drops
                active.bar.setVisible(false);
                active.bar.clearPlayers();
                living.discard();
                it.remove();
                TomkesTookitMod.LOGGER.info("[BossBar] Boss {} despawned due to inactivity", active.bossId);
                continue;
            } else if (ticksSinceCombat > AFK_HEAL_TICKS) {
                if (living.getHealth() < living.getMaxHealth()) {
                    living.setHealth(living.getMaxHealth());
                }
            }
        }
    }

    private static void onBossDead(ActiveBoss active, MinecraftServer server) {
        active.bar.setVisible(false);
        active.bar.clearPlayers();

        BossDef def = BossTokenRegistry.byId(active.bossId);
        if (def == null) return;

        // Find last damager as killer (best-effort)
        ServerPlayerEntity killer = null;
        Entity mob = active.world.getEntity(active.mobUuid);
        Vec3d deathPos;
        if (mob instanceof LivingEntity living) {
            deathPos = living.getPos();
            if (living.getAttacker() instanceof ServerPlayerEntity sp) {
                killer = sp;
            } else if (living.getPrimeAdversary() instanceof ServerPlayerEntity sp) {
                killer = sp;
            }
        } else {
            deathPos = Vec3d.ofCenter(active.spawnPos);
        }

        String killerName = killer != null ? killer.getName().getString() : "未知";
        server.getPlayerManager().broadcast(
                Text.literal("§6§l⚡ §c" + def.displayName()
                        + " §7被 §f" + killerName + " §7击杀！"), false);

        spawnFirework(active.world, deathPos);

        try {
            BossDropTable.drop(def, active.world, deathPos, killer);
        } catch (Throwable t) {
            TomkesTookitMod.LOGGER.error("[BossBar] Drop failed for boss {}", active.bossId, t);
        }
    }

    private static void spawnFirework(ServerWorld world, Vec3d pos) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        NbtCompound fireworks = new NbtCompound();
        NbtList explosions = new NbtList();
        NbtCompound explosion = new NbtCompound();
        explosion.putByte("Type", (byte) 1);
        explosion.putBoolean("Flicker", true);
        explosion.putBoolean("Trail", true);
        explosion.putIntArray("Colors", new int[]{0xFF0000, 0xFFAA00, 0xFFFF55});
        explosion.putIntArray("FadeColors", new int[]{0xFFFFFF});
        explosions.add(explosion);
        fireworks.put("Explosions", explosions);
        fireworks.putByte("Flight", (byte) 1);
        NbtCompound tag = rocket.getOrCreateNbt();
        tag.put("Fireworks", fireworks);

        FireworkRocketEntity fw = new FireworkRocketEntity(world, pos.x, pos.y + 1, pos.z, rocket);
        world.spawnEntity(fw);
    }

    /** 注册一只新 Boss 的血条：立即让所有在世玩家看见（后续 tick 按距离过滤）。*/
    public static void registerBoss(UUID mobUuid, String bossId, ServerWorld world,
                                    BlockPos spawnPos, BossDef def) {
        ServerBossBar bar = new ServerBossBar(
                Text.literal("§c§l" + def.displayName()),
                def.barColor(),
                def.barStyle());
        bar.setPercent(1.0f);

        MinecraftServer server = world.getServer();
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world) {
                    bar.addPlayer(player);
                }
            }
        }

        ActiveBoss active = new ActiveBoss(bar, mobUuid, bossId, world,
                server != null ? server.getTicks() : 0L, spawnPos);
        ACTIVE.put(mobUuid, active);
    }

    /** 战斗信号：刷新最近受击 tick，防被判为 AFK。*/
    public static void notifyCombat(UUID mobUuid) {
        ActiveBoss active = ACTIVE.get(mobUuid);
        if (active == null) return;
        MinecraftServer server = active.world.getServer();
        if (server != null) {
            active.lastCombatTick = server.getTicks();
        }
    }

    public static int getActiveCount() {
        return ACTIVE.size();
    }
}

