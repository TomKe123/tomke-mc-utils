package com.tomkestookit.mixin;

import com.tomkestookit.materials.OriginalWeaponRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
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
 *   - void_blade: 命中后攻击者向前 3 格瞬移
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
                    float heal = amount * 0.15f;
                    attacker.heal(heal);
                }
                break;

            case "abyssal_trident":
                // 额外穿甲伤害（vanilla 已经打了 amount，这里再补一次绕护甲的固定伤害）
                if (attacker.isSubmergedInWater() || victim.isSubmergedInWater()) {
                    // 水下额外 +50% 伤害
                    victim.damage(source, amount * 0.5f);
                }
                // 穿甲：再补 30% amount，绕过护甲
                if (victim.isAlive()) {
                    DamageSource pierce = attacker.getDamageSources().magic();
                    victim.damage(pierce, amount * 0.3f);
                }
                break;

            case "void_blade":
                // 命中后 attacker 向前瞬移 3 格
                Vec3d dir = attacker.getRotationVec(1.0f).normalize();
                Vec3d target = attacker.getPos().add(dir.multiply(3));
                attacker.requestTeleport(target.x, target.y, target.z);
                break;

            case "soul_reaper":
                // 击杀怪物时经验加成由 EliteMobDropMixin.tomkestookit$scaleXp 已放大 tier
                // 这里额外给一次经验，无论是否精英
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
