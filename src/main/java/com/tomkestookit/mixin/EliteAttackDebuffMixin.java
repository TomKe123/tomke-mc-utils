package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.elite.EliteDebuffAura;
import com.tomkestookit.elite.EliteMobRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 精英怪命中真人玩家 → 骰点施加 debuff。
 *
 * TAIL 位置注入：确保伤害已生效再触发 debuff，避免玩家实际未受伤时错触发。
 * 假人过滤在 EliteDebuffAura.onEliteAttack 内部处理。
 */
@Mixin(LivingEntity.class)
public abstract class EliteAttackDebuffMixin {

    @Inject(method = "damage", at = @At("TAIL"))
    private void tomkestookit$eliteHitDebuff(DamageSource source, float amount,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        if (!cir.getReturnValue()) return;
        if (amount <= 0f) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity victim)) return;

        if (!(source.getAttacker() instanceof MobEntity attacker)) return;
        int tier = EliteMobRegistry.getTier(attacker);
        if (tier <= 0) return;

        EliteDebuffAura.onEliteAttack(attacker, victim, tier);
    }
}
