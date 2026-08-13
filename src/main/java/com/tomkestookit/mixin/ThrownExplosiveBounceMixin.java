package com.tomkestookit.mixin;

import com.tomkestookit.ThrownExplosiveHandler;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 蹦蹦炸弹在地面弹跳：TntEntity.tick() TAIL 拦截，检测 onGround + 有下落趋势时给一次
 * 向上冲量，保持水平速度不消散。
 *
 * vanilla TntEntity.tick 已经做了触地 y*=-0.5 的小反弹，但衰减太快只能弹一次；
 * 我们在此基础上给一个补偿冲量，让炸弹能连续弹跳 2~3 次直到引信到期。
 */
@Mixin(TntEntity.class)
public abstract class ThrownExplosiveBounceMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void tomkestookit$bounce(CallbackInfo ci) {
        try {
            TntEntity self = (TntEntity) (Object) this;
            if (!ThrownExplosiveHandler.isBouncy(self)) return;
            if (!self.isOnGround()) return;

            Vec3d v = self.getVelocity();
            // 落地瞬间 vanilla 会把 y 从负数翻成小正数（*-0.5），此时补一次向上冲量
            // 只有在垂直速度较小时补偿，避免持续加速
            if (v.y < 0.25) {
                // 保留水平速度（vanilla 会 *0.7 衰减，我们乘 1.3 抵消回来）
                self.setVelocity(v.x * 1.3, 0.42, v.z * 1.3);
                self.velocityModified = true;
            }
        } catch (Throwable ignored) {}
    }
}
