package com.tomkestookit.mixin;

import com.tomkestookit.materials.OriginalWeaponRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原创武器命中特效：LivingEntity.damage TAIL 时读攻击者主手 NBT。
 *
 *   - blood_moon_scythe: 伤害的 15% 回血给攻击者
 *   - abyssal_trident: 直接绕过 30% 护甲（在 amount 修改前重打一次伤害）
 *     注：TAIL 修不了 amount，此处只做视觉/额外伤害逻辑（真正穿甲需 ModifyVariable）
 *   - void_blade: 随机触发两个效果（各 20% 独立判定）
 *       虚空裂隙：对目标施加减速II + 凋零II，持续 2 秒（真实伤害模拟）
 *       维度切割：额外补一次 50% 魔法伤害（绕过护甲）
 *
 * 灵魂收割者的经验加成走 EliteMobDropMixin 或 getXpToDrop（本文件不处理）。
 */
@Mixin(LivingEntity.class)
public abstract class OriginalWeaponEffectMixin {

    @Inject(method = "damage", at = @At("TAIL"))
    private void tomkestookit$weaponEffects(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (amount <= 0f) return;
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return;

        ItemStack weapon = attacker.getMainHandStack();
        String weaponId = OriginalWeaponRegistry.getId(weapon);
        if (weaponId == null) return;

        LivingEntity victim = (LivingEntity) (Object) this;

        switch (weaponId) {
            case "blood_moon_scythe":
                // 吸血 15%（不吸魔法/环境伤害）
                if (!source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_ARMOR)
                    && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)
                    && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) {
                    attacker.heal(amount * 0.15f);
                }
                break;

            case "abyssal_trident":
                // 水下额外 +50% 伤害
                if (attacker.isSubmergedInWater() || victim.isSubmergedInWater()) {
                    victim.damage(source, amount * 0.5f);
                }
                // 穿甲：再补 30% amount，绕过护甲
                if (victim.isAlive()) {
                    victim.damage(attacker.getDamageSources().magic(), amount * 0.3f);
                }
                break;

            case "void_blade":
                java.util.Random rng = attacker.getRandom();

                // 虚空裂隙：20% 几率 —— 减速II + 凋零II 持续 2 秒（40 tick）
                if (rng.nextFloat() < 0.20f) {
                    victim.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, 40, 1, false, true));
                    victim.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WITHER, 40, 1, false, true));
                    attacker.sendMessage(
                        net.minecraft.text.Text.literal("§5虚空裂隙").styled(s -> s.withItalic(false)),
                        true);
                }

                // 维度切割：20% 几率 —— 额外 50% 魔法伤害（绕护甲）
                if (rng.nextFloat() < 0.20f && victim.isAlive()) {
                    victim.damage(attacker.getDamageSources().magic(), amount * 0.50f);
                    attacker.sendMessage(
                        net.minecraft.text.Text.literal("§b维度切割").styled(s -> s.withItalic(false)),
                        true);
                }
                break;

            case "soul_reaper":
                // 击杀时额外掉落经验
                if (victim.getHealth() <= 0 && victim instanceof MobEntity) {
                    int bonusXp = 3 + (int)(victim.getMaxHealth() / 4);
                    net.minecraft.entity.ExperienceOrbEntity.spawn(
                        (net.minecraft.server.world.ServerWorld) victim.getWorld(),
                        victim.getPos(), bonusXp);
                }
                break;
        }
    }
}
