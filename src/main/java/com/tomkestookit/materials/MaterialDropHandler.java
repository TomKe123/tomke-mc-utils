package com.tomkestookit.materials;

import com.tomkestookit.CarpetBotUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物专属素材掉落（精英怪系统取消后独立运作）。
 *
 * 死亡时按 vanilla 生物种类骰点低阶材料，低概率掉落；假人击杀跳过。
 * 通过 Fabric ServerLivingEntityEvents.AFTER_DEATH 独立注册。
 */
public final class MaterialDropHandler {

    private MaterialDropHandler() {}

    /** 注册怪物死亡掉落素材事件。*/
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            onMobDrop(entity, source);
        });
    }

    /**
     * 死亡时按 vanilla 生物种类掉落素材。
     * @param dying 死亡的实体
     * @param source 伤害来源（可能 null）
     */
    public static void onMobDrop(LivingEntity dying, DamageSource source) {
        if (!(dying instanceof MobEntity mob)) return;
        if (!(dying.getWorld() instanceof ServerWorld world)) return;

        // 假人击杀不掉专属素材（防挂机塔）
        if (source.getAttacker() instanceof ServerPlayerEntity sp
            && CarpetBotUtil.isFakePlayer(sp)) return;

        Vec3d pos = mob.getPos();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1) 腐化肌腱：僵尸系
        if (isZombieLike(mob)) {
            rollDrop(world, pos, "corrupt_sinew", 0.08, 1, rng);
        }

        // 2) 深邃碎片：水生系
        if (mob instanceof DrownedEntity) {
            rollDrop(world, pos, "abyssal_shard", 0.05, 1, rng);
        } else if (mob instanceof GuardianEntity) {
            rollDrop(world, pos, "abyssal_shard", 0.15, 1, rng);
        } else if (mob instanceof ElderGuardianEntity) {
            rollDrop(world, pos, "abyssal_shard", 0.30, 2, rng);
        }

        // 3) 灵魂结晶：不死系稀有 + vex
        if (mob instanceof WitherSkeletonEntity) {
            rollDrop(world, pos, "soul_crystal", 0.08, 1, rng);
        } else if (mob instanceof VexEntity) {
            rollDrop(world, pos, "soul_crystal", 0.05, 1, rng);
        }

        // 4) 星尘：飞行/远古
        if (mob instanceof PhantomEntity) {
            rollDrop(world, pos, "stardust", 0.12, 1, rng);
        } else if (mob instanceof EndermanEntity) {
            rollDrop(world, pos, "stardust", 0.03, 1, rng);
        }
    }

    private static void rollDrop(ServerWorld world, Vec3d pos, String matId,
                                  double chance, int maxCount, ThreadLocalRandom rng) {
        chance = Math.min(1.0, chance);
        if (rng.nextDouble() >= chance) return;
        MobMaterialRegistry.MaterialDef def = MobMaterialRegistry.byId(matId);
        if (def == null) return;
        int count = 1 + rng.nextInt(Math.max(1, maxCount));
        ItemEntity ie = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z,
            MobMaterialRegistry.build(def, count));
        ie.setPickupDelay(10);
        world.spawnEntity(ie);
    }

    private static boolean isZombieLike(MobEntity mob) {
        return mob instanceof ZombieEntity
            || mob instanceof HuskEntity
            || mob instanceof ZombieVillagerEntity
            || mob instanceof DrownedEntity
            || mob instanceof ZoglinEntity
            || mob instanceof ZombifiedPiglinEntity;
    }
}
