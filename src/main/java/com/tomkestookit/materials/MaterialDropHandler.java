package com.tomkestookit.materials;

import com.tomkestookit.CarpetBotUtil;
import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.elite.EliteMobRegistry;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.SkeletonEntity;
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
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物专属素材掉落：混合方式。
 *
 * 由 EliteMobDropMixin 在 LivingEntity.drop TAIL 调用（每个怪死时）：
 *   1) 按 vanilla 生物种类骰点低阶材料掉落（低概率，模拟"直接放进 loot table"）
 *   2) 若怪是精英化的，按 tier 提升掉落概率并解锁高阶材料
 *   3) 假人击杀不掉素材（避免自动化农场刷高级素材）
 *
 * 掉落规则表（basic = 非精英 vanilla 概率 / elite mult = 精英倍率）：
 *
 * corrupt_sinew (§7):
 *   zombie/husk/zombie_villager/zoglin/zombified_piglin — 8%
 *   精英 unit 30%
 *
 * abyssal_shard (§a):
 *   drowned 5%, guardian 15%, elder_guardian 30%
 *   精英水系 40%
 *
 * soul_crystal (§a):
 *   wither_skeleton 8%, vex 5%
 *   精英不死 20%
 *
 * stardust (§e):
 *   phantom 12%, enderman 3%
 *   精英飞行/末影 30%
 *
 * blood_moon_essence (§c):
 *   任意精英 15%
 *   boss 100%
 *
 * void_core (§5):
 *   boss 20%, ender_dragon 100%（vanilla 击杀）
 *   精英末影人 5%
 */
public final class MaterialDropHandler {

    private MaterialDropHandler() {}

    /**
     * 由掉落 mixin 在 LivingEntity.drop TAIL 调用。
     * mob 已经死了，attacker 可能为 null（环境伤害）。
     */
    public static void onMobDrop(LivingEntity dying, DamageSource source) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        if (!(dying instanceof MobEntity mob)) return;
        if (!(dying.getWorld() instanceof ServerWorld world)) return;

        // 假人击杀不掉专属素材（防挂机塔）
        if (source.getAttacker() instanceof ServerPlayerEntity sp
            && CarpetBotUtil.isFakePlayer(sp)) return;

        int tier = EliteMobRegistry.getTier(mob);
        boolean isElite = tier > 0;

        // 决定倍率：精英怪概率翻倍 + 数量按 tier 递增
        double eliteMult = isElite ? 1.5 + tier * 0.3 : 1.0;  // T5 精英怪 3.0x
        Vec3d pos = mob.getPos();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1) 腐化肌腱：僵尸系
        if (isZombieLike(mob)) {
            rollDrop(world, pos, "corrupt_sinew", 0.08 * eliteMult, 1, rng);
        }

        // 2) 深邃碎片：水生系
        if (mob instanceof DrownedEntity) {
            rollDrop(world, pos, "abyssal_shard", 0.05 * eliteMult, 1, rng);
        } else if (mob instanceof GuardianEntity) {
            rollDrop(world, pos, "abyssal_shard", 0.15 * eliteMult, 1, rng);
        } else if (mob instanceof ElderGuardianEntity) {
            rollDrop(world, pos, "abyssal_shard", 0.30 * eliteMult, 2, rng);
        }

        // 3) 灵魂结晶：不死系稀有 + vex
        if (mob instanceof WitherSkeletonEntity) {
            rollDrop(world, pos, "soul_crystal", 0.08 * eliteMult, 1, rng);
        } else if (mob instanceof VexEntity) {
            rollDrop(world, pos, "soul_crystal", 0.05 * eliteMult, 1, rng);
        } else if (isElite && isUndead(mob)) {
            rollDrop(world, pos, "soul_crystal", 0.20 * (tier / 3.0), 1, rng);
        }

        // 4) 星尘：飞行/远古
        if (mob instanceof PhantomEntity) {
            rollDrop(world, pos, "stardust", 0.12 * eliteMult, 1, rng);
        } else if (mob instanceof EndermanEntity) {
            rollDrop(world, pos, "stardust", 0.03 * eliteMult, 1, rng);
            // 精英末影人有 5% 直接给虚空之核
            if (isElite && tier >= 4) {
                rollDrop(world, pos, "void_core", 0.05, 1, rng);
            }
        }

        // 5) 血月精华：所有精英怪
        if (isElite) {
            rollDrop(world, pos, "blood_moon_essence", 0.15 + tier * 0.05, 1, rng);
        }

        // 6) 虚空之核 & 高级掉落：Boss 相关
        // Boss 由 BossDropTable 单独处理，这里只处理"精英末影人偶发"，其他 boss 逻辑在 BossDropTable

        // 精英怪的经验放大和额外物品仍由 EliteMobDropMixin 处理
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

    private static boolean isUndead(MobEntity mob) {
        return isZombieLike(mob)
            || mob instanceof SkeletonEntity
            || mob instanceof StrayEntity
            || mob instanceof WitherSkeletonEntity
            || mob instanceof SkeletonHorseEntity;
    }
}
