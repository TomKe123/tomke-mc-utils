package com.tomkestookit.elite;

import com.tomkestookit.CarpetBotUtil;
import com.tomkestookit.TomkesTookitConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精英怪光环减益系统。
 * 4 格范围内对真实玩家施加减益，概率取决于精英层级。
 */
public final class EliteDebuffAura {

    private static final double AURA_RANGE = 4.0;
    private static final int COOLDOWN_TICKS = 20;

    /** key = mobUuid.hashCode()*31 + playerUuid.hashCode(), value = lastApplyTick */
    private static final Map<Long, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();

    private EliteDebuffAura() {}

    // --- Debuff table ---

    private record DebuffEntry(StatusEffect effect, int amplifier) {}

    private static final Map<Integer, List<DebuffEntry>> DEBUFF_TABLE = new HashMap<>();

    static {
        DEBUFF_TABLE.put(1, List.of(
            new DebuffEntry(StatusEffects.SLOWNESS, 0)
        ));
        DEBUFF_TABLE.put(2, List.of(
            new DebuffEntry(StatusEffects.SLOWNESS, 0),
            new DebuffEntry(StatusEffects.MINING_FATIGUE, 0)
        ));
        DEBUFF_TABLE.put(3, List.of(
            new DebuffEntry(StatusEffects.WEAKNESS, 0),
            new DebuffEntry(StatusEffects.SLOWNESS, 1),
            new DebuffEntry(StatusEffects.MINING_FATIGUE, 1)
        ));
        DEBUFF_TABLE.put(4, List.of(
            new DebuffEntry(StatusEffects.WEAKNESS, 1),
            new DebuffEntry(StatusEffects.POISON, 0),
            new DebuffEntry(StatusEffects.SLOWNESS, 1)
        ));
        DEBUFF_TABLE.put(5, List.of(
            new DebuffEntry(StatusEffects.WEAKNESS, 2),
            new DebuffEntry(StatusEffects.POISON, 1),
            new DebuffEntry(StatusEffects.WITHER, 0)
        ));
        DEBUFF_TABLE.put(EliteTierScale.getBossTier(), List.of(
            new DebuffEntry(StatusEffects.WITHER, 1),
            new DebuffEntry(StatusEffects.SLOWNESS, 2),
            new DebuffEntry(StatusEffects.MINING_FATIGUE, 2)
        ));
    }

    // --- Registration ---

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
            if (server.getTicks() % 20 != 0) return;

            long currentTick = server.getOverworld().getTime();
            Set<UUID> tracked = EliteMobRegistry.getTrackedMobs();

            for (UUID mobUuid : tracked) {
                for (ServerWorld sw : server.getWorlds()) {
                    Entity entity = sw.getEntity(mobUuid);
                    if (entity == null || !entity.isAlive()) continue;
                    if (!(entity instanceof MobEntity mob)) continue;

                    int tier = readTier(mob);
                    if (tier <= 0) continue;

                    EliteTierScale.TierData tierData = EliteTierScale.get(tier);
                    Box aura = new Box(mob.getBlockPos()).expand(AURA_RANGE);
                    List<ServerPlayerEntity> players = sw.getEntitiesByClass(
                        ServerPlayerEntity.class, aura, p -> !CarpetBotUtil.isFakePlayer(p)
                    );

                    for (ServerPlayerEntity target : players) {
                        applyDebuffs(mob, target, tier, tierData, currentTick);
                    }
                    break; // found entity in this world, skip other worlds
                }
            }

            // Periodically clean stale cooldown entries (every 10 seconds)
            if (server.getTicks() % 200 == 0) {
                COOLDOWN_MAP.entrySet().removeIf(e -> currentTick - e.getValue() > 200);
            }
        });
    }

    /**
     * 当精英怪攻击玩家时也触发减益（用于近战命中）。
     */
    public static void onEliteAttack(MobEntity attacker, ServerPlayerEntity victim, int tier) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        if (CarpetBotUtil.isFakePlayer(victim)) return;
        if (tier <= 0) return;

        EliteTierScale.TierData tierData = EliteTierScale.get(tier);
        long currentTick = attacker.getWorld().getTime();
        applyDebuffs(attacker, victim, tier, tierData, currentTick);
    }

    // --- Internal ---

    private static void applyDebuffs(MobEntity mob, ServerPlayerEntity target,
                                     int tier, EliteTierScale.TierData tierData, long currentTick) {
        // Cooldown check
        long cooldownKey = (long) mob.getUuid().hashCode() * 31 + target.getUuid().hashCode();
        Long lastApply = COOLDOWN_MAP.get(cooldownKey);
        if (lastApply != null && currentTick - lastApply < COOLDOWN_TICKS) return;

        // Probability roll
        if (RANDOM.nextDouble() >= tierData.debuffChance()) return;

        // Apply debuffs
        List<DebuffEntry> debuffs = DEBUFF_TABLE.get(tier);
        if (debuffs == null) return;

        for (DebuffEntry entry : debuffs) {
            StatusEffectInstance existing = target.getStatusEffect(entry.effect());
            // Only apply if target doesn't have a higher-duration instance
            if (existing != null && existing.getDuration() > tierData.debuffDurationTicks()) continue;

            target.addStatusEffect(new StatusEffectInstance(
                entry.effect(),
                tierData.debuffDurationTicks(),
                entry.amplifier(),
                false, // ambient
                true,  // showParticles
                true   // showIcon
            ));
        }

        COOLDOWN_MAP.put(cooldownKey, currentTick);
    }

    private static int readTier(MobEntity mob) {
        NbtCompound nbt = new NbtCompound();
        mob.writeCustomDataToNbt(nbt);
        if (!nbt.contains(EliteMobRegistry.NBT_TAG, NbtElement.COMPOUND_TYPE)) return 0;
        return nbt.getCompound(EliteMobRegistry.NBT_TAG).getInt("tier");
    }
}
