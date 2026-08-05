package com.tomkestookit.elite;

import com.tomkestookit.CarpetBotUtil;
import com.tomkestookit.TomkesTookitConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * 精英怪生成 / 退化（v2）。
 *
 * 生成条件：mob 有仇恨 target = 真人玩家 && 该玩家 tier ≥ 1
 * 退化条件：mob 已是精英但 target 不再是真人（假人 / 消失）
 *
 * 每 20 tick 扫：
 *   1. 已 tracked 精英怪 → target 状态检查 → 需要则退化
 *   2. 真人玩家附近 hostile mob → target 检查 → 需要则精英化
 *
 * NBT 已有 TomkesTookitElite 的 mob 在 ENTITY_LOAD 时重新应用属性（世界重载路径）。
 */
public final class EliteMobHandler {

    private static final UUID BASE_MOD_UUID = UUID.fromString("70b0a5d0-1e2f-4a3b-8c4d-5e6f7a8b9c0d");
    private static final String MOD_NAME_PREFIX = "tomkestookit_elite_";
    private static final int TICK_INTERVAL = 20; // 每秒一次

    /** 保存精英化前的原始 customName（可能是 null），用于退化时还原。*/
    private static final Map<UUID, Text> ORIGINAL_NAMES = new HashMap<>();

    private EliteMobHandler() {}

    public static void register() {
        // 世界加载已有精英怪 → 只重新应用属性/名字，不再骰点
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
            if (!(entity instanceof MobEntity mob)) return;
            NbtCompound nbt = new NbtCompound();
            mob.writeCustomDataToNbt(nbt);
            if (nbt.contains(EliteMobRegistry.NBT_TAG, net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
                NbtCompound tag = nbt.getCompound(EliteMobRegistry.NBT_TAG);
                int tier = tag.getInt("tier");
                applyElite(mob, tier);
            }
        });

        // 每 20 tick 扫描 target 状态
        ServerTickEvents.END_SERVER_TICK.register(EliteMobHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        if (server.getTicks() % TICK_INTERVAL != 0) return;

        // 1) 已精英化 mob 的退化检查
        Set<UUID> tracked = new HashSet<>(EliteMobRegistry.getTrackedMobs());
        for (UUID uuid : tracked) {
            MobEntity mob = resolveMob(server, uuid);
            if (mob == null || !mob.isAlive()) {
                EliteMobRegistry.remove(uuid);
                ORIGINAL_NAMES.remove(uuid);
                continue;
            }
            // Boss 不退化（Boss 有独立管理器 BossBarManager 负责生命周期）
            if (isBossMob(mob)) continue;

            LivingEntity target = mob.getTarget();
            boolean validTarget = target instanceof ServerPlayerEntity sp
                                  && !CarpetBotUtil.isFakePlayer(sp)
                                  && !sp.isSpectator()
                                  && sp.isAlive();
            if (!validTarget) {
                removeElite(mob);
            }
        }

        // 2) 真人玩家附近 hostile mob 的精英化检查
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (CarpetBotUtil.isFakePlayer(player)) continue;
                if (player.isSpectator()) continue;

                int playerTier = ThreatRating.getTier(player);
                if (playerTier <= 0) continue;

                Box box = new Box(player.getBlockPos()).expand(32);
                List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, box, m ->
                    m.isAlive()
                    && !m.isPersistent()
                    && m.getType().getSpawnGroup() == SpawnGroup.MONSTER
                    && !EliteMobRegistry.isElite(m)
                    && m.getTarget() == player
                );
                for (MobEntity mob : mobs) {
                    // 随机在 [tier-1, tier+1] 挑选 elite tier
                    int minT = Math.max(1, playerTier - 1);
                    int maxT = Math.min(5, playerTier + 1);
                    int chosen = minT + world.random.nextInt(maxT - minT + 1);
                    applyElite(mob, chosen);
                }
            }
        }
    }

    private static MobEntity resolveMob(MinecraftServer server, UUID uuid) {
        for (ServerWorld sw : server.getWorlds()) {
            Entity e = sw.getEntity(uuid);
            if (e instanceof MobEntity m) return m;
        }
        return null;
    }

    /** 判断 mob 是否为 Boss（有 TomkesTookitBoss NBT 标记）*/
    private static boolean isBossMob(MobEntity mob) {
        NbtCompound nbt = new NbtCompound();
        mob.writeCustomDataToNbt(nbt);
        return nbt.contains("TomkesTookitBoss");
    }

    // ---- Elite 应用 ----

    public static void applyElite(MobEntity mob, int tier) {
        EliteTierScale.TierData data = EliteTierScale.get(tier);

        applyModifier(mob, EntityAttributes.GENERIC_MAX_HEALTH,
            modUuid(0, tier), data.healthMult() - 1.0, EntityAttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE,
            modUuid(1, tier), data.damageMult() - 1.0, EntityAttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, EntityAttributes.GENERIC_ARMOR,
            modUuid(2, tier), data.armorMult() * 4.0, EntityAttributeModifier.Operation.ADDITION);
        applyModifier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED,
            modUuid(3, tier), data.speedMult() - 1.0, EntityAttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, EntityAttributes.GENERIC_FOLLOW_RANGE,
            modUuid(4, tier), data.followRangeMult(), EntityAttributeModifier.Operation.ADDITION);

        EntityAttributeInstance healthAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            mob.setHealth((float) healthAttr.getValue());
        }

        // 保存原始名字（只在首次精英化时保存）
        if (!ORIGINAL_NAMES.containsKey(mob.getUuid())) {
            ORIGINAL_NAMES.put(mob.getUuid(), mob.getCustomName());
        }

        String mobName = mob.getName().getString();
        // 如果之前已被精英化过，getName 会带前缀 → 用 default name 更保险
        Text origName = ORIGINAL_NAMES.get(mob.getUuid());
        if (origName == null) {
            mobName = mob.getType().getName().getString();
        }
        String displayName;
        if (tier <= 2) {
            displayName = "§d§l⚔ 精英" + mobName + " [T" + tier + "]";
        } else if (tier <= 4) {
            displayName = "§6§l⚔ 精英" + mobName + " [T" + tier + "]";
        } else {
            displayName = "§6§l§k|§r§6§l⚔ 精英" + mobName + " [T" + tier + "]§6§l§k|";
        }
        mob.setCustomName(Text.literal(displayName));
        mob.setCustomNameVisible(true);

        NbtCompound eliteTag = new NbtCompound();
        eliteTag.putInt("tier", tier);
        eliteTag.putLong("appliedAt", mob.getWorld().getTime());
        NbtCompound mobNbt = new NbtCompound();
        mob.writeCustomDataToNbt(mobNbt);
        mobNbt.put(EliteMobRegistry.NBT_TAG, eliteTag);
        mob.readCustomDataFromNbt(mobNbt);

        EliteMobRegistry.register(mob.getUuid(), tier);
    }

    /** 退化：移除精英属性、名字、NBT、追踪注册。*/
    public static void removeElite(MobEntity mob) {
        UUID uuid = mob.getUuid();
        NbtCompound mobNbt = new NbtCompound();
        mob.writeCustomDataToNbt(mobNbt);
        int oldTier = 0;
        if (mobNbt.contains(EliteMobRegistry.NBT_TAG, net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            oldTier = mobNbt.getCompound(EliteMobRegistry.NBT_TAG).getInt("tier");
        }

        // 移除属性 modifier（每个属性都试一遍，用当前 tier 的 UUID）
        removeModifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, modUuid(0, oldTier));
        removeModifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, modUuid(1, oldTier));
        removeModifier(mob, EntityAttributes.GENERIC_ARMOR, modUuid(2, oldTier));
        removeModifier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, modUuid(3, oldTier));
        removeModifier(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, modUuid(4, oldTier));

        // clamp 血量到新 maxHealth
        EntityAttributeInstance h = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (h != null && mob.getHealth() > h.getValue()) {
            mob.setHealth((float) h.getValue());
        }

        // 恢复原始名字
        Text orig = ORIGINAL_NAMES.remove(uuid);
        mob.setCustomName(orig);
        mob.setCustomNameVisible(orig != null);

        // 清 NBT 标记
        mobNbt.remove(EliteMobRegistry.NBT_TAG);
        mobNbt.remove("TomkesTookitEliteShield");
        mob.readCustomDataFromNbt(mobNbt);

        EliteMobRegistry.remove(uuid);
    }

    // ---- helpers ----

    private static UUID modUuid(int attributeIndex, int tier) {
        long msb = BASE_MOD_UUID.getMostSignificantBits() + attributeIndex;
        long lsb = BASE_MOD_UUID.getLeastSignificantBits() + tier;
        return new UUID(msb, lsb);
    }

    private static void applyModifier(MobEntity mob,
                                       net.minecraft.entity.attribute.EntityAttribute attribute,
                                       UUID uuid, double value,
                                       EntityAttributeModifier.Operation operation) {
        EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
        if (instance == null) return;
        EntityAttributeModifier existing = instance.getModifier(uuid);
        if (existing != null) instance.removeModifier(existing);
        instance.addPersistentModifier(new EntityAttributeModifier(
            uuid, MOD_NAME_PREFIX + attribute.getTranslationKey(), value, operation
        ));
    }

    private static void removeModifier(MobEntity mob,
                                        net.minecraft.entity.attribute.EntityAttribute attribute,
                                        UUID uuid) {
        EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
        if (instance == null) return;
        EntityAttributeModifier existing = instance.getModifier(uuid);
        if (existing != null) instance.removeModifier(existing);
    }
}
