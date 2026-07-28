package com.tomkestookit.mixin;

import com.tomkestookit.GossipBroadcast;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家获得 HERO_OF_THE_VILLAGE 效果时（袭击胜利），立即广播 gossip 给附近村民，
 * 让 vanilla HeroOfTheVillageGossipTask 不再依赖村民 brain 慢传播。
 *
 * hook 点：{@link LivingEntity#onStatusEffectApplied} —— 效果第一次被加时触发。
 */
@Mixin(LivingEntity.class)
public abstract class HeroOfVillageGossipMixin {

    @Inject(method = "onStatusEffectApplied", at = @At("HEAD"))
    private void tomkestookit$heroGossipBroadcast(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        try {
            if (effect.getEffectType() != StatusEffects.HERO_OF_THE_VILLAGE) return;
            Object self = this;
            //noinspection ConstantValue -- Qodana 看不到 mixin 目标类会被 apply 到子类
            if (!(self instanceof ServerPlayerEntity sp)) return;
            if (!(sp.getWorld() instanceof ServerWorld sw)) return;
            GossipBroadcast.broadcastHero(sw, sp);
        } catch (Throwable ignored) {}
    }
}
