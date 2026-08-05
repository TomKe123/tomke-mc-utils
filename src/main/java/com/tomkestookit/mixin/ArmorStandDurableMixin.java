package com.tomkestookit.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 加大盔甲架耐久：横扫 / 高攻速 / 高伤害玩家无法秒杀盔甲架。
 *
 * 原版盔甲架：两次连续命中（间隔 ≤5 tick）触发完整破坏序列。
 * 修改：玩家必须在 HIT_DECAY_TICKS 内连续命中 HITS_REQUIRED 次。
 * 同一 tick 的多次攻击（横扫误伤）只计一次。
 *
 * 达到阈值时，预设 lastHitTime 让 vanilla 认为"第二次快速命中"条件成立，
 * 然后不取消当前调用——vanilla 自己执行完整破坏（掉落 + 粒子 + 音效 + kill）。
 * 这样无论玩家伤害多高，都需要连续命中 5 次才能拆掉盔甲架。
 *
 * 不影响创造模式、爆炸等非玩家攻击。
 */
@Mixin(ArmorStandEntity.class)
public abstract class ArmorStandDurableMixin {

    @Unique
    private int tomkestookit$hitCount = 0;
    @Unique
    private long tomkestookit$lastHitTime = 0;
    @Unique
    private long tomkestookit$lastHitTick = -999;

    /** 需要连续攻击多少次才会破坏。*/
    private static final int HITS_REQUIRED = 5;
    /** 上次攻击后多久（tick）重置计数。*/
    private static final long HIT_DECAY_TICKS = 60;

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$armorStandDurable(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!com.tomkestookit.TomkesTookitConfig.enabled("world.armor_stand_durable")) return;
            ArmorStandEntity self = (ArmorStandEntity) (Object) this;

            // marker 盔甲架（Invulnerable=true）或创造模式攻击：走原版
            if (self.isInvulnerable()) return;
            if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity p && p.isCreative()) return;

            // 非玩家攻击（爆炸、仙人掌等）走原版
            if (!(source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity)) return;

            long currentTick = self.getWorld().getTime();
            long timeSinceLastHit = currentTick - tomkestookit$lastHitTime;

            // 超过衰减时间 → 重置计数
            if (timeSinceLastHit > HIT_DECAY_TICKS) {
                tomkestookit$hitCount = 0;
            }

            // 同一 tick 内的重复攻击（横扫）不累加计数
            if (currentTick != tomkestookit$lastHitTick) {
                tomkestookit$hitCount++;
                tomkestookit$lastHitTick = currentTick;
            }
            tomkestookit$lastHitTime = currentTick;

            // 计数未达到要求 → 阻挡伤害，播放音效反馈
            if (tomkestookit$hitCount < HITS_REQUIRED) {
                self.playSound(net.minecraft.sound.SoundEvents.ENTITY_ARMOR_STAND_HIT, 0.5f, 1.0f);
                cir.setReturnValue(true); // true = 横扫可继续传播到背后生物
                return;
            }

            // 计数达标 → 预设 lastHitTime 让 vanilla 认为已有"首次命中"，
            // 不取消调用 → vanilla damage() 看到 (currentTick - lastHitTime <= 5) 为 true → 执行完整破坏
            tomkestookit$hitCount = 0;
            self.lastHitTime = currentTick;
            // 不调用 cir.cancel / cir.setReturnValue → 继续执行 vanilla damage()
        } catch (Throwable ignored) {}
    }
}

