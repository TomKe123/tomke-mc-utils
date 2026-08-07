package com.tomkestookit.elite.skill;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class EliteSkillRegistry {

    private static final List<EliteSkill> ALL = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private EliteSkillRegistry() {}

    static {
        // 1. knockback_burst (T1, cooldown 160t, windup 10t)
        ALL.add(new EliteSkill("knockback_burst", 160, 10, 1,
            ctx -> !ctx.targets().isEmpty(),
            ctx -> {
                ServerWorld world = ctx.world();
                MobEntity mob = ctx.attacker();
                world.spawnParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 1, mob.getZ(), 5, 0.5, 0.5, 0.5, 0.02);
                for (ServerPlayerEntity target : ctx.targets()) {
                    target.sendMessage(Text.literal("§e⚡ 精英怪蓄力中"), true);
                }
            },
            ctx -> {
                MobEntity mob = ctx.attacker();
                for (ServerPlayerEntity target : ctx.targets()) {
                    if (target.squaredDistanceTo(mob) > 16.0) continue; // 4 blocks
                    Vec3d dir = target.getPos().subtract(mob.getPos()).normalize();
                    target.setVelocity(dir.x * 2.5, 0.6, dir.z * 2.5);
                    target.velocityModified = true;
                }
            }
        ));

        // 2. slow_field (T2, cooldown 200t, windup 0t)
        ALL.add(new EliteSkill("slow_field", 200, 0, 2,
            ctx -> !ctx.targets().isEmpty(),
            ctx -> { /* no windup */ },
            ctx -> {
                MobEntity mob = ctx.attacker();
                for (ServerPlayerEntity target : ctx.targets()) {
                    if (target.squaredDistanceTo(mob) > 64.0) continue; // 8 blocks
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2, false, true, true));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 80, 1, false, true, true));
                }
            }
        ));

        // 3. summon_minions (T3, cooldown 400t, windup 20t)
        ALL.add(new EliteSkill("summon_minions", 400, 20, 3,
            ctx -> {
                MobEntity mob = ctx.attacker();
                ServerWorld world = ctx.world();
                long count = world.getEntitiesByType(mob.getType(), mob.getBoundingBox().expand(32), e -> true).size();
                return count < 12;
            },
            ctx -> {
                ServerWorld world = ctx.world();
                MobEntity mob = ctx.attacker();
                world.spawnParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 1.0, 1.0, 1.0, 0.5);
            },
            ctx -> {
                MobEntity mob = ctx.attacker();
                ServerWorld world = ctx.world();
                EntityType<?> type = mob.getType();
                int count = 2 + RANDOM.nextInt(3); // 2-4
                for (int i = 0; i < count; i++) {
                    double ox = (RANDOM.nextDouble() - 0.5) * 10.0;
                    double oz = (RANDOM.nextDouble() - 0.5) * 10.0;
                    BlockPos spawnPos = BlockPos.ofFloored(mob.getX() + ox, mob.getY(), mob.getZ() + oz);
                    if (!world.getBlockState(spawnPos).isAir()) continue;
                    Entity spawned = type.create(world);
                    if (spawned == null) continue;
                    spawned.setPosition(mob.getX() + ox, mob.getY(), mob.getZ() + oz);
                    if (spawned instanceof MobEntity spawnedMob) {
                        NbtCompound nbt = new NbtCompound();
                        spawnedMob.writeCustomDataToNbt(nbt);
                        nbt.putBoolean("TomkesTookitMinion", true);
                        spawnedMob.readCustomDataFromNbt(nbt);
                    }
                    world.spawnEntity(spawned);
                }
            }
        ));

        // heal_pulse 已删除：精英怪不允许自愈（除 BUFF 外）

        // 5. blink_strike (T4, cooldown 240t, windup 6t)
        ALL.add(new EliteSkill("blink_strike", 240, 6, 4,
            ctx -> {
                MobEntity mob = ctx.attacker();
                for (ServerPlayerEntity t : ctx.targets()) {
                    if (t.squaredDistanceTo(mob) > 9.0) return true; // > 3 blocks
                }
                return false;
            },
            ctx -> {
                ServerWorld world = ctx.world();
                MobEntity mob = ctx.attacker();
                world.spawnParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.5, 1.0, 0.5, 0.5);
            },
            ctx -> {
                MobEntity mob = ctx.attacker();
                ServerWorld world = ctx.world();
                if (ctx.targets().isEmpty()) return;
                ServerPlayerEntity target = ctx.targets().get(0);
                Vec3d behindTarget = target.getPos().subtract(target.getRotationVec(1f).normalize().multiply(2));
                BlockPos behindPos = BlockPos.ofFloored(behindTarget.x, behindTarget.y, behindTarget.z);
                if (world.getBlockState(behindPos).isAir()) {
                    mob.teleport(behindTarget.x, behindTarget.y, behindTarget.z);
                    mob.tryAttack(target);
                }
            }
        ));

        // 6. fire_nova (T4, cooldown 300t, windup 8t)
        ALL.add(new EliteSkill("fire_nova", 300, 8, 4,
            ctx -> !ctx.targets().isEmpty(),
            ctx -> {
                ServerWorld world = ctx.world();
                MobEntity mob = ctx.attacker();
                world.spawnParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 1.5, 0.5, 1.5, 0.05);
            },
            ctx -> {
                MobEntity mob = ctx.attacker();
                ServerWorld world = ctx.world();
                for (ServerPlayerEntity target : ctx.targets()) {
                    if (target.squaredDistanceTo(mob) > 36.0) continue; // 6 blocks
                    target.setOnFireFor(4);
                    target.damage(world.getDamageSources().onFire(), 4.0f);
                }
            }
        ));

        // 7. lightning_strike (T5, cooldown 400t, windup 16t)
        ALL.add(new EliteSkill("lightning_strike", 400, 16, 5,
            ctx -> !ctx.targets().isEmpty(),
            ctx -> {
                ServerWorld world = ctx.world();
                if (ctx.targets().isEmpty()) return;
                ServerPlayerEntity target = ctx.targets().get(0);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 2, target.getZ(), 15, 1.0, 0.5, 1.0, 0.1);
                for (ServerPlayerEntity t : ctx.targets()) {
                    t.sendMessage(Text.literal("§e⚡ 闪电！"), true);
                }
            },
            ctx -> {
                ServerWorld world = ctx.world();
                if (ctx.targets().isEmpty()) return;
                ServerPlayerEntity target = ctx.targets().get(0);
                int strikes = 1 + RANDOM.nextInt(3); // 1-3
                for (int i = 0; i < strikes; i++) {
                    double ox = (RANDOM.nextDouble() - 0.5) * 4.0;
                    double oz = (RANDOM.nextDouble() - 0.5) * 4.0;
                    LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                    if (lightning == null) continue;
                    lightning.setPosition(target.getX() + ox, target.getY(), target.getZ() + oz);
                    world.spawnEntity(lightning);
                }
            }
        ));

        // 8. shield_stance (T5, cooldown 800t, windup 6t)
        ALL.add(new EliteSkill("shield_stance", 800, 6, 5,
            ctx -> true,
            ctx -> {
                ServerWorld world = ctx.world();
                MobEntity mob = ctx.attacker();
                world.spawnParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.5, 1.0, 0.5, 0.5);
            },
            ctx -> {
                MobEntity mob = ctx.attacker();
                long shieldEnd = ctx.currentTick() + 160; // 8 seconds
                NbtCompound nbt = new NbtCompound();
                mob.writeCustomDataToNbt(nbt);
                nbt.putLong("TomkesTookitEliteShield", shieldEnd);
                mob.readCustomDataFromNbt(nbt);
                // Append shield indicator to custom name（短标记）
                Text currentName = mob.getCustomName();
                if (currentName != null) {
                    String nameStr = currentName.getString();
                    if (!nameStr.contains("🛡")) {
                        mob.setCustomName(Text.literal(nameStr + " §b🛡"));
                    }
                }
            }
        ));
    }

    public static List<EliteSkill> getPoolForTier(int tier) {
        List<EliteSkill> pool = new ArrayList<>();
        for (EliteSkill skill : ALL) {
            if (skill.minTierRequired() <= tier) {
                pool.add(skill);
            }
        }
        return pool;
    }

    public static List<EliteSkill> all() {
        return ALL;
    }
}
