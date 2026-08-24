package com.tomkestookit.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 盔甲架仅斧子可拆：不是斧子 → 完全挡掉（不掉耐久、不破坏、只播音效）。
 * 是斧子 → 还需连续命中 HITS_REQUIRED 次（同一 tick 只计一次，60 tick 衰减）。
 *
 * 训练假人集成：
 *   - 训练假人不阻挡伤害，让玩家可以攻击测试 DPS
 *   - 但训练假人同样需要斧子连续 5 次才能拆除
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
            ArmorStandEntity self = (ArmorStandEntity) (Object) this;

            // 训练假人：记录伤害后放行（不阻挡），但拆除还是需要斧子 5 连击
            boolean isTrainingDummy = com.tomkestookit.TomkesTookitConfig.enabled("recipe.training_dummy")
                && com.tomkestookit.trainingdummy.TrainingDummyHandler.isTrainingDummyEntity(self);

            if (isTrainingDummy && source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity player) {
                // 获取横扫伤害（从 Mixin 标记判断，这里 amount 就是实际伤害值）
                float sweepDamage = amount; // 传递给 Handler，但现在 Handler 会根据横扫标记自动区分
                com.tomkestookit.trainingdummy.TrainingDummyHandler.onHit(self, player, amount, sweepDamage);

                // 不是斧子 → 不破坏，但接受伤害（让玩家能看到伤害数字）
                if (!(player.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem)) {
                    // 训练假人永不死亡（血量归满）
                    self.setHealth(self.getMaxHealth());
                    cir.setReturnValue(true);
                    return;
                }

                // 是斧子但未连击 5 次 → 不破坏，仍然接受伤害显示
                long currentTick = self.getWorld().getTime();
                if (currentTick - tomkestookit$lastHitTime > HIT_DECAY_TICKS) {
                    tomkestookit$hitCount = 0;
                }
                if (currentTick != tomkestookit$lastHitTick) {
                    tomkestookit$hitCount++;
                    tomkestookit$lastHitTick = currentTick;
                }
                tomkestookit$lastHitTime = currentTick;

                if (tomkestookit$hitCount < HITS_REQUIRED) {
                    self.setHealth(self.getMaxHealth());
                    self.playSound(net.minecraft.sound.SoundEvents.ENTITY_ARMOR_STAND_HIT, 0.5f, 1.0f);
                    cir.setReturnValue(true);
                    return;
                }
                // 5 连击达成 → 放行，允许破坏
                tomkestookit$hitCount = 0;
                return; // 不 cancel，走原版破坏
            }

            // 普通盔甲架保护逻辑
            if (!com.tomkestookit.TomkesTookitConfig.enabled("world.armor_stand_durable")) return;

            // marker 盔甲架（Invulnerable=true）或创造模式攻击：走原版
            if (self.isInvulnerable()) return;
            if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity player && player.isCreative())
                return;

            // 非玩家攻击（爆炸、仙人掌等）走原版
            if (!(source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity player2)) return;

            // 不是斧子 → 完全阻挡
            if (!(player2.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem)) {
                self.playSound(net.minecraft.sound.SoundEvents.BLOCK_ANVIL_PLACE, 0.5f, 1.0f);
                self.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f, 0.8f);
                cir.setReturnValue(true);
                return;
            }

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

            // 计数未达到要求 → 阻挡伤害，播放斧子砍木声反馈
            if (tomkestookit$hitCount < HITS_REQUIRED) {
                self.playSound(net.minecraft.sound.SoundEvents.ENTITY_ARMOR_STAND_HIT, 0.5f, 1.0f);
                cir.setReturnValue(true);
                return;
            }

            // 计数达标 → 放行 vanilla damage() 完成完整破坏
            tomkestookit$hitCount = 0;
            self.lastHitTime = currentTick;
        } catch (Throwable ignored) {}
    }
}

