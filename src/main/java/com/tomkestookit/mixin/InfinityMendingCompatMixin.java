package com.tomkestookit.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让"无限"（Infinity）和"经验修补"（Mending）不再冲突。
 *
 * 原版 {@link Enchantment#canCombine(Enchantment)} 会调用 differs 判互斥，
 * 而 Infinity/Mending 各自被显式列为对方的互斥项，只要 Infinity + Mending
 * 出现在任意一次判定里就直接 return false（导致铁砧/附魔时不能同时存在）。
 *
 * 这里在 HEAD 拦截：若参与判定的两方就是 Infinity 与 Mending，返回 true，让 vanilla
 * 认为它们可以共存。其它组合完全不受影响。
 *
 * 影响范围：
 *   - 铁砧合并弓时保留双方附魔
 *   - 已有 Infinity 的弓可以再上 Mending 附魔书
 *   - 已有 Mending 的弓可以再上 Infinity（若来源合法）
 *   - /enchant 命令加上任一方也不再报冲突
 */
@Mixin(Enchantment.class)
public abstract class InfinityMendingCompatMixin {

    @Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$infinityMendingCompat(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("misc.infinity_mending")) return;
        Enchantment self = (Enchantment) (Object) this;
        if ((self == Enchantments.INFINITY && other == Enchantments.MENDING)
            || (self == Enchantments.MENDING && other == Enchantments.INFINITY)) {
            cir.setReturnValue(true);
        }
    }
}
