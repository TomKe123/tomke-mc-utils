package com.tomkestookit.boss;

import com.tomkestookit.CarpetBotUtil;
import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.TomkesTookitMod;
import com.tomkestookit.boss.BossTokenRegistry.BossDef;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Boss 令牌激活处理：右键方块 → 倒计时 → 生成 Boss。
 */
public final class BossSummonHandler {

    private BossSummonHandler() {}

    private static final Map<UUID, PendingSpawn> PENDING = new HashMap<>();

    private static final class PendingSpawn {
        final String bossId;
        final ServerWorld world;
        final BlockPos pos;
        final ServerPlayerEntity summoner;
        int ticksLeft;

        PendingSpawn(String bossId, ServerWorld world, BlockPos pos,
                     ServerPlayerEntity summoner, int ticksLeft) {
            this.bossId = bossId;
            this.world = world;
            this.pos = pos;
            this.summoner = summoner;
            this.ticksLeft = ticksLeft;
        }
    }
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!TomkesTookitConfig.enabled("item.boss_token")) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ItemStack stack = serverPlayer.getMainHandStack();
            if (stack.isEmpty()) return ActionResult.PASS;
            NbtCompound nbt = stack.getNbt();
            if (nbt == null || !nbt.contains("TomkesTookitBossToken")) return ActionResult.PASS;

            String bossId = nbt.getString("TomkesTookitBossToken");
            BossDef def = BossTokenRegistry.byId(bossId);
            if (def == null) return ActionResult.PASS;

            // Validate: not a fake player (carpet bot)
            if (CarpetBotUtil.isFakePlayer(serverPlayer)) {
                return ActionResult.PASS;
            }

            // Cooldown check
            if (!BossCooldownStore.canSummon(serverPlayer.getUuid(), bossId)) {
                long remaining = BossCooldownStore.getRemainingCooldown(serverPlayer.getUuid(), bossId);
                long hours = remaining / 3600000;
                long minutes = (remaining % 3600000) / 60000;
                serverPlayer.sendMessage(
                        Text.literal("§c冷却中：还需 " + hours + " 小时 " + minutes + " 分钟"), false);
                return ActionResult.PASS;
            }

            // Concurrent limit
            if (BossBarManager.getActiveCount() >= 3) {
                serverPlayer.sendMessage(
                        Text.literal("§c同时活跃 Boss 已达上限 (3)，请稍后再试"), false);
                return ActionResult.PASS;
            }

            // Distance from spawn check
            BlockPos spawnPos = ((ServerWorld) world).getSpawnPos();
            BlockPos targetPos = hitResult.getBlockPos().up();
            double distSq = targetPos.getSquaredDistance(spawnPos);
            if (distSq < 200.0 * 200.0) {
                serverPlayer.sendMessage(
                        Text.literal("§c需距离出生点 200 格以上才能召唤"), false);
                return ActionResult.PASS;
            }

            // Target position check: must be air/passable
            if (!world.isAir(targetPos) && !world.getBlockState(targetPos).getCollisionShape(world, targetPos).isEmpty()) {
                serverPlayer.sendMessage(
                        Text.literal("§c目标位置被遮挡，无法召唤"), false);
                return ActionResult.PASS;
            }

            // All checks passed — consume token
            stack.decrement(1);

            // Broadcast summon message
            MinecraftServer server = serverPlayer.getServer();
            if (server != null) {
                server.getPlayerManager().broadcast(
                        Text.literal("§6§l⚡ " + serverPlayer.getName().getString()
                                + " 正在召唤 §c" + def.displayName()), false);
            }

            // Record cooldown
            BossCooldownStore.recordSummon(serverPlayer.getUuid(), bossId);

            // Add pending spawn with 60 tick countdown
            UUID pendingId = UUID.randomUUID();
            PENDING.put(pendingId, new PendingSpawn(bossId, (ServerWorld) world, targetPos, serverPlayer, 60));

            return ActionResult.SUCCESS;
        });

        // Tick handler for pending spawns
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) return;
            Iterator<Map.Entry<UUID, PendingSpawn>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, PendingSpawn> entry = it.next();
                PendingSpawn ps = entry.getValue();
                ps.ticksLeft--;

                // Spawn particles during countdown
                Vec3d particlePos = Vec3d.ofCenter(ps.pos);
                ps.world.spawnParticles(ParticleTypes.FLAME,
                        particlePos.x, particlePos.y, particlePos.z,
                        5, 0.5, 0.5, 0.5, 0.02);

                if (ps.ticksLeft <= 0) {
                    it.remove();
                    spawnBoss(ps);
                }
            }
        });
    }

    private static void spawnBoss(PendingSpawn ps) {
        BossDef def = BossTokenRegistry.byId(ps.bossId);
        if (def == null) return;

        MobEntity mob = (MobEntity) def.mobType().spawn(
                ps.world, null, null, ps.pos, SpawnReason.COMMAND, false, false);
        if (mob == null) {
            TomkesTookitMod.LOGGER.warn("[BossSummon] Failed to spawn boss: {}", ps.bossId);
            if (ps.summoner != null) {
                ps.summoner.sendMessage(Text.literal("§cBoss 生成失败"), false);
            }
            return;
        }

        applyBossStats(mob, def);
        applyEliteIfAvailable(mob);
        BossBarManager.registerBoss(mob.getUuid(), ps.bossId, ps.world, ps.pos, def);

        TomkesTookitMod.LOGGER.info("[BossSummon] Spawned boss {} at {} for {}",
                ps.bossId, ps.pos, ps.summoner != null ? ps.summoner.getName().getString() : "unknown");
    }

    /** 管理员直接召唤：不消耗令牌、不检查冷却/并发/距离。*/
    public static void summonDirect(String bossId, ServerWorld world, BlockPos pos,
                                    ServerPlayerEntity summoner) {
        BossDef def = BossTokenRegistry.byId(bossId);
        if (def == null) {
            if (summoner != null) {
                summoner.sendMessage(Text.literal("§c未知 Boss ID: " + bossId), false);
            }
            return;
        }

        MobEntity mob = (MobEntity) def.mobType().spawn(
                world, null, null, pos, SpawnReason.COMMAND, false, false);
        if (mob == null) {
            if (summoner != null) {
                summoner.sendMessage(Text.literal("§cBoss 生成失败"), false);
            }
            return;
        }

        applyBossStats(mob, def);
        applyEliteIfAvailable(mob);
        BossBarManager.registerBoss(mob.getUuid(), bossId, world, pos, def);

        if (summoner != null) {
            MinecraftServer server = summoner.getServer();
            if (server != null) {
                server.getPlayerManager().broadcast(
                        Text.literal("§6§l⚡ " + summoner.getName().getString()
                                + " 正在召唤 §c" + def.displayName()), false);
            }
        }
    }

    private static void applyBossStats(MobEntity mob, BossDef def) {
        // Custom name
        mob.setCustomName(Text.literal("§c§l" + def.displayName()));
        mob.setCustomNameVisible(true);

        // Health
        var healthAttr = mob.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * def.healthMult());
            mob.setHealth(mob.getMaxHealth());
        }

        // Damage
        var damageAttr = mob.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * def.damageMult());
        }

        // Follow range
        var followAttr = mob.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(def.followRange());
        }

        // Persistence
        mob.setPersistent();

        // NBT marker
        NbtCompound nbt = new NbtCompound();
        mob.writeNbt(nbt);
        nbt.putString("TomkesTookitBoss", def.bossId());
        mob.readNbt(nbt);
    }

    /** 应用 Boss tier 属性。*/
    private static void applyEliteIfAvailable(MobEntity mob) {
        com.tomkestookit.elite.EliteMobHandler.applyElite(mob,
            com.tomkestookit.elite.EliteTierScale.getBossTier());
    }
}

