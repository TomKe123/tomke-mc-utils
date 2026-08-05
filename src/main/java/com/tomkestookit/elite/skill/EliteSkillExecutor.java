package com.tomkestookit.elite.skill;

import com.tomkestookit.CarpetBotUtil;
import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.TomkesTookitMod;
import com.tomkestookit.elite.EliteMobRegistry;
import com.tomkestookit.elite.EliteTierScale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.*;

public final class EliteSkillExecutor {

    private static final Map<UUID, MobSkillState> STATES = new HashMap<>();
    private static final int CHECK_INTERVAL = 10; // ticks
    private static final Random RANDOM = new Random();

    private EliteSkillExecutor() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EliteSkillExecutor::tick);
    }

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        long now = server.getOverworld().getTime();
        // Every tick: pending windup execution + shield expiry
        tickPending(server, now);
        tickShieldExpiry(server, now);
        // Full check every 10 ticks: new skill rolls + state cleanup
        if (now % CHECK_INTERVAL != 0) return;
        tickNewSkills(server, now);
        cleanup();
    }

    private static void tickPending(MinecraftServer server, long now) {
        for (Map.Entry<UUID, MobSkillState> entry : STATES.entrySet()) {
            MobSkillState state = entry.getValue();
            if (!state.hasPending()) continue;
            Iterator<MobSkillState.PendingSkill> it = state.pendingSkills.iterator();
            while (it.hasNext()) {
                MobSkillState.PendingSkill pending = it.next();
                if (pending.executeAtTick() <= now) {
                    try {
                        pending.skill().onExecute().accept(pending.ctx());
                    } catch (Exception e) {
                        TomkesTookitMod.LOGGER.warn("[EliteSkill] Error executing skill {}: {}", pending.skill().id(), e.getMessage());
                    }
                    it.remove();
                }
            }
        }
    }

    private static void tickShieldExpiry(MinecraftServer server, long now) {
        Set<UUID> tracked = EliteMobRegistry.getTrackedMobs();
        for (UUID uuid : tracked) {
            MobSkillState state = STATES.get(uuid);
            if (state == null) continue;
            MobEntity mob = resolveMob(server, uuid);
            if (mob == null) continue;
            NbtCompound nbt = new NbtCompound();
            mob.writeCustomDataToNbt(nbt);
            if (!nbt.contains("TomkesTookitEliteShield")) continue;
            long shieldEnd = nbt.getLong("TomkesTookitEliteShield");
            if (now >= shieldEnd) {
                nbt.remove("TomkesTookitEliteShield");
                mob.readCustomDataFromNbt(nbt);
                Text currentName = mob.getCustomName();
                if (currentName != null) {
                    String nameStr = currentName.getString();
                    String cleaned = nameStr.replace(" §b[🛡]", "").replace("§b[🛡]", "");
                    mob.setCustomName(Text.literal(cleaned));
                }
            }
        }
    }

    private static void tickNewSkills(MinecraftServer server, long now) {
        Set<UUID> tracked = EliteMobRegistry.getTrackedMobs();
        for (UUID uuid : tracked) {
            MobEntity mob = resolveMob(server, uuid);
            if (mob == null || !mob.isAlive()) continue;

            int tier = EliteMobRegistry.getTier(mob);
            if (tier <= 0) continue;

            MobSkillState state = STATES.computeIfAbsent(uuid, k -> new MobSkillState());
            boolean isBoss = tier >= EliteTierScale.getBossTier();
            if (!state.canAddPending(isBoss)) continue;

            ServerWorld world = (ServerWorld) mob.getWorld();
            List<ServerPlayerEntity> targets = world.getPlayers(p ->
                p.squaredDistanceTo(mob) <= 256.0
                && !CarpetBotUtil.isFakePlayer(p)
                && !p.isSpectator()
            );
            if (targets.isEmpty()) continue;

            List<EliteSkill> pool = EliteSkillRegistry.getPoolForTier(tier);
            List<EliteSkill> available = new ArrayList<>();
            for (EliteSkill skill : pool) {
                Long cdEnd = state.cooldownEndTick.get(skill.id());
                if (cdEnd != null && cdEnd > now) continue;
                SkillContext testCtx = new SkillContext(mob, world, targets, tier, now);
                try {
                    if (skill.canTrigger().test(testCtx)) {
                        available.add(skill);
                    }
                } catch (Exception ignored) {}
            }
            if (available.isEmpty()) continue;

            // Roll probability: 0.15 * tier chance per check interval
            if (RANDOM.nextDouble() >= 0.15 * tier) continue;

            EliteSkill chosen = available.get(RANDOM.nextInt(available.size()));
            SkillContext ctx = new SkillContext(mob, world, targets, tier, now);

            // Enter windup
            try {
                chosen.onWindup().accept(ctx);
            } catch (Exception e) {
                TomkesTookitMod.LOGGER.warn("[EliteSkill] Error in windup {}: {}", chosen.id(), e.getMessage());
            }

            long executeAt = now + chosen.windupTicks();
            state.pendingSkills.add(new MobSkillState.PendingSkill(chosen, ctx, executeAt));
            state.cooldownEndTick.put(chosen.id(), now + chosen.cooldownTicks());
        }
    }

    private static void cleanup() {
        Set<UUID> tracked = EliteMobRegistry.getTrackedMobs();
        STATES.keySet().removeIf(uuid -> !tracked.contains(uuid));
    }

    private static MobEntity resolveMob(MinecraftServer server, UUID uuid) {
        for (ServerWorld sw : server.getWorlds()) {
            Entity e = sw.getEntity(uuid);
            if (e instanceof MobEntity mob && mob.isAlive()) {
                return mob;
            }
        }
        return null;
    }
}
