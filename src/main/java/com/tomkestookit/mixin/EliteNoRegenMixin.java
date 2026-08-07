package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.elite.EliteMobRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 精英怪禁止获得 Regeneration 效果——切断它们通过 buff 自愈的最后路径。
 * 拦截 addStatusEffect(StatusEffectInstance, Entity)：命中 Regeneration 时直接返回 false。
 */
@Mixin(LivingEntity.class)
public abstract class EliteNoRegenMixin {

    @Inject(
        method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void tomkestookit$eliteBlockRegen(StatusEffectInstance effect, @Nullable Entity source,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!TomkesTookitConfig.enabled("world.elite_mobs")) return;
        if (effect.getEffectType() != StatusEffects.REGENERATION) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) return;
        if (!EliteMobRegistry.isElite(mob)) return;
        cir.setReturnValue(false);
    }
}
