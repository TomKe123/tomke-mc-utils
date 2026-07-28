package com.tomkestookit.mixin;

import com.tomkestookit.SugarTntHandler;
import com.tomkestookit.recipe.SugarTnt;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 大伊万 TntEntity 引爆时用 4× power + 4× 伤害。
 *
 * vanilla {@code TntEntity#explode()} 只有一句 world.createExplosion(...) 用 power=4.0f。
 * 我们在 HEAD 拦截：若被标记为大伊万，先自己发一次 4×power 的爆炸，然后 cancel 原方法。
 *
 * Vanilla explosion 伤害是 (7×power) × distanceFactor（近爆点最高），
 * power 变 4× 后半径也 4×，覆盖体积和伤害衰减范围都会自然放大——
 * 具体伤害倍率略高于 4×（因为距离衰减在更远才起效），符合"伤害 × 4"的目标。
 */
@Mixin(TntEntity.class)
public abstract class SugarTntPowerMixin {

    @Inject(method = "explode()V", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$sugarBoom(CallbackInfo ci) {
        try {
            //noinspection DataFlowIssue -- mixin 标准写法，运行时 this 就是 TntEntity 实例
            TntEntity self = (TntEntity) (Object) this;
            if (!SugarTntHandler.isSugarTntEntity(self)) return;
            World world = self.getWorld();
            // vanilla 是 4.0f；我们放大 4 倍 = 16.0f
            world.createExplosion(self,
                self.getX(), self.getBodyY(0.0625), self.getZ(),
                4.0f * SugarTnt.POWER_MULTIPLIER,
                World.ExplosionSourceType.TNT);
            SugarTntHandler.unregisterEntity(self);
            ci.cancel();
        } catch (Throwable ignored) {
            // 出错让 vanilla 走原逻辑
        }
    }
}
