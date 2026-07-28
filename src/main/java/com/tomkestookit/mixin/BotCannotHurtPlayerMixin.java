package com.tomkestookit.mixin;

import com.tomkestookit.CarpetBotUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 禁止 Carpet 假人对真实玩家造成任何伤害。*/
@Mixin(LivingEntity.class)
public abstract class BotCannotHurtPlayerMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$blockBotDamageToPlayers(DamageSource source, float amount,
                                                      CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        //noinspection ConstantValue -- Qodana 看不到 mixin 目标类会被 apply 到子类
        if (!(self instanceof ServerPlayerEntity target)) return;
        // 玩家目标若本身是假人，不拦；只保护真实玩家
        if (CarpetBotUtil.isFakePlayerStrict(target)) return;
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity sp && CarpetBotUtil.isFakePlayerStrict(sp)) {
            cir.setReturnValue(false);
        }
    }
}
