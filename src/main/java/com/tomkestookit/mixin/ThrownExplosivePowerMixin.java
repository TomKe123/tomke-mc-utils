package com.tomkestookit.mixin;

import com.tomkestookit.ThrownExplosiveHandler;
import com.tomkestookit.recipe.ThrownExplosive;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手雷 / 蹦蹦炸弹 引爆逻辑：ExplosionSourceType.NONE → 只造成伤害，不破坏方块。
 *
 * 优先级 900：早于 SugarTntPowerMixin(default 1000)。
 *   - 我们的实体不是大伊万实体（走各自 UUID 追踪，不会冲突）
 *   - 两个 mixin 各自 return 未匹配的实体，互不影响
 */
@Mixin(value = TntEntity.class, priority = 900)
public abstract class ThrownExplosivePowerMixin {

    @Inject(method = "explode()V", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$thrownExplosiveBoom(CallbackInfo ci) {
        try {
            TntEntity self = (TntEntity) (Object) this;
            ThrownExplosive.Type type = ThrownExplosiveHandler.getEntityType(self);
            if (type == null) return;

            World world = self.getWorld();
            // ExplosionSourceType.NONE → 在 1.20.1 里映射到 DestructionType.KEEP
            // 完全不破坏方块，仍造成实体伤害
            world.createExplosion(self,
                self.getX(), self.getBodyY(0.0625), self.getZ(),
                type.power,
                World.ExplosionSourceType.NONE);
            ThrownExplosiveHandler.unregister(self);
            ci.cancel();
        } catch (Throwable ignored) {
            // 出错让 vanilla 走原逻辑
        }
    }
}
