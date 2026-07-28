package com.tomkestookit.mixin;

import com.tomkestookit.AfkDisplay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 挂机玩家免疫幻翼伤害。取消挂机后自动恢复。
 *
 * hook 点：{@link LivingEntity#damage} HEAD
 *   - 受害者是 ServerPlayerEntity
 *   - 攻击来源是 PhantomEntity
 *   - 玩家当前处于 AFK 状态
 *   → 直接 return false，vanilla 不处理这次伤害
 *
 * 只影响幻翼伤害；玩家非 AFK 时完全原版行为。
 */
@Mixin(LivingEntity.class)
public abstract class AfkPhantomImmuneMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$afkPhantomImmune(DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        try {
            Object self = this;
            //noinspection ConstantValue -- Qodana 看不到 mixin 目标类会被 apply 到子类
            if (!(self instanceof ServerPlayerEntity sp)) return;
            Entity attacker = source.getAttacker();
            if (!(attacker instanceof PhantomEntity)) return;
            if (!AfkDisplay.isAfk(sp)) return;
            cir.setReturnValue(false);
        } catch (Throwable ignored) {}
    }
}
