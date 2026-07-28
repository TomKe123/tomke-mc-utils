package com.tomkestookit.mixin;

import com.tomkestookit.BotAnchorTracker;
import com.tomkestookit.CarpetBotUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让被标记「无击退」的 Carpet 假人 takeKnockback 全部被吞。
 * 覆盖普通伤害击退、爆炸击退、拉扯（fishing rod）等所有走 takeKnockback 的通路。
 * 由 BotAnchorHandler 每 tick 拉回位置作双保险，即使某些位移不经过 takeKnockback
 * 也不会飘走。
 */
@Mixin(LivingEntity.class)
public abstract class BotNoKnockbackMixin {

    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$suppressBotKnockback(double strength, double x, double z, CallbackInfo ci) {
        try {
            Object self = this;
            // 只处理 ServerPlayerEntity；且必须是 Carpet 假人子类（不做名称模糊匹配，避免误伤真玩家）
            //noinspection ConstantValue -- Qodana 看不到 mixin 目标类会被 apply 到子类
            if (!(self instanceof ServerPlayerEntity sp)) return;
            if (!CarpetBotUtil.isFakePlayerStrict(sp)) return;
            // 默认 on：Carpet 假人一律取消击退，除非菜单显式关掉
            if (BotAnchorTracker.noKnockback(sp.getUuid())
                || BotAnchorTracker.isAnchored(sp.getUuid())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // 任何异常都不影响 vanilla 击退流程
        }
    }
}
