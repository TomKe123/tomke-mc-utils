package com.tomkestookit.mixin;

import com.tomkestookit.entitycap.EntityCap;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 每玩家 128 格实体上限的注入点。
 *
 * ServerWorld.addEntity 是 spawnEntity / tryLoadEntity 的私有汇聚方法，
 * 覆盖它相当于同时覆盖 2 个 public 入口。HEAD + cancellable + setReturnValue(false)：
 * 拒绝 = 实体不入世界。
 */
@Mixin(ServerWorld.class)
public abstract class EntityCapServerWorldMixin {

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$entityCap(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            ServerWorld self = (ServerWorld) (Object) this;
            if (EntityCap.shouldReject(self, entity)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 兜底：任何异常都放行，不因功能崩溃阻断 vanilla spawn
        }
    }
}
