package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.elite.EliteMobRegistry;
import com.tomkestookit.reforge.ReforgeRegistry;
import com.tomkestookit.trinket.TrinketRegistry;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 精英怪掉落放大 + 额外稀有掉落。
 *
 *   - drop TAIL：本 tick 死亡位置附近生成的 ItemEntity 数量 × (1 + tier)，跳过 count=1 且属稀有类型的堆
 *   - drop TAIL：按 tier 决定 0..3 个额外掉落 (绿宝石 / 附魔金苹果 / 饰品 / 重铸石)
 *   - getXpToDrop RETURN：经验 × (1 + tier)
 */
@Mixin(LivingEntity.class)
public abstract class EliteMobDropMixin {

    @Inject(method = "drop", at = @At("TAIL"))
    private void tomkestookit$eliteDrops(DamageSource source, CallbackInfo ci) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) return;

        // 无论是否精英，都尝试掉专属素材
        com.tomkestookit.materials.MaterialDropHandler.onMobDrop(self, source);

        int tier = EliteMobRegistry.getTier(mob);
        if (tier <= 0) return;
        if (!(mob.getWorld() instanceof ServerWorld world)) return;

        int mult = 1 + tier;
        Vec3d pos = mob.getPos();
        Box box = new Box(pos.x - 3, pos.y - 3, pos.z - 3,
                          pos.x + 3, pos.y + 3, pos.z + 3);

        // 放大本 tick 附近的 ItemEntity
        List<ItemEntity> nearby = world.getEntitiesByClass(ItemEntity.class, box,
            e -> e.age <= 1);
        for (ItemEntity ie : nearby) {
            ItemStack s = ie.getStack();
            if (s.isEmpty()) continue;
            if (isRareUnique(s)) continue;
            int newCount = Math.min(s.getMaxCount(), s.getCount() * mult);
            if (newCount != s.getCount()) {
                s.setCount(newCount);
                ie.setStack(s);
            }
        }

        // 额外稀有掉落
        rollBonusDrops(world, pos, tier);
    }

    @Inject(method = "getXpToDrop", at = @At("RETURN"), cancellable = true)
    private void tomkestookit$scaleXp(CallbackInfoReturnable<Integer> cir) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) return;
        int tier = EliteMobRegistry.getTier(mob);
        if (tier <= 0) return;
        int orig = cir.getReturnValue();
        long scaled = (long) orig * (1 + tier);
        cir.setReturnValue(scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled);
    }

    /** 数量恒为 1 且属稀有类型的堆不倍增。*/
    private static boolean isRareUnique(ItemStack s) {
        if (s.getCount() != 1) return false;
        var item = s.getItem();
        return item == Items.WITHER_SKELETON_SKULL
            || item == Items.SKELETON_SKULL
            || item == Items.ZOMBIE_HEAD
            || item == Items.CREEPER_HEAD
            || item == Items.PLAYER_HEAD
            || item == Items.ENDER_PEARL
            || item == Items.DRAGON_EGG
            || item == Items.NETHER_STAR
            || item == Items.HEART_OF_THE_SEA
            || item == Items.TOTEM_OF_UNDYING;
    }

    /**
     * 额外掉落：0..(tier+1) 个，池：绿宝石 / 附魔金苹果 / 饰品 / 重铸石。
     * 饰品 / 重铸石概率随 tier 上升。
     */
    private static void rollBonusDrops(ServerWorld world, Vec3d pos, int tier) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rolls = rng.nextInt(tier + 2); // 0..(tier+1)
        double trinketChance = 0.05 + tier * 0.07;   // T1=0.12, T5=0.40
        double reforgeChance = 0.05 + tier * 0.05;   // T1=0.10, T5=0.30

        for (int i = 0; i < rolls; i++) {
            double roll = rng.nextDouble();
            ItemStack drop = null;
            if (roll < trinketChance) {
                List<TrinketRegistry.TrinketDef> ts = TrinketRegistry.all();
                if (!ts.isEmpty()) {
                    TrinketRegistry.TrinketDef pick = ts.get(rng.nextInt(ts.size()));
                    // 排除 boss_ 前缀（专属）
                    if (pick.id() != null && pick.id().startsWith("boss_")) continue;
                    drop = TrinketRegistry.buildStack(pick);
                }
            } else if (roll < trinketChance + reforgeChance) {
                List<ReforgeRegistry.ReforgeDef> rs = ReforgeRegistry.all();
                if (!rs.isEmpty()) {
                    ReforgeRegistry.ReforgeDef pick = rs.get(rng.nextInt(rs.size()));
                    drop = ReforgeRegistry.buildStack(pick);
                }
            } else if (roll < 0.7) {
                int count = 1 + rng.nextInt(tier);
                drop = new ItemStack(Items.EMERALD, count);
            } else if (roll < 0.85) {
                drop = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            } else {
                // 追加经验球
                int xp = 5 + tier * 10 + rng.nextInt(1 + tier * 5);
                ExperienceOrbEntity.spawn(world, pos, xp);
                continue;
            }
            if (drop != null && !drop.isEmpty()) {
                ItemEntity ie = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, drop);
                ie.setPickupDelay(10);
                world.spawnEntity(ie);
            }
        }
    }
}
