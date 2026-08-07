package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.elite.EliteMobRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 精英怪禁止自愈：屏蔽所有非 buff 来源的 heal 调用。
 *
 * 例外：如果 mob 当前有 Regeneration 状态效果，视为"BUFF 触发的回血"放行——
 * 这时的 heal 就是 Regeneration 效果本身。其他自愈路径（原版 mob 自愈逻辑等）
 * 都被拒绝。
 *
 * heal_pulse 技能用 setHealth 直接改血量，不会走这里；已在
 * EliteSkillRegistry 中移除对普通精英怪的适用。
 */
@Mixin(LivingEntity.class)
public abstract class EliteNoHealMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$eliteNoSelfHeal(float amount, CallbackInfo ci) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) return;
        if (!EliteMobRegistry.isElite(mob)) return;
        // BUFF 除外：有 Regeneration 效果时视为 buff 触发，放行
        if (self.hasStatusEffect(StatusEffects.REGENERATION)) return;
        ci.cancel();
    }
}
