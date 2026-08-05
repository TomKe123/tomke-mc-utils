package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.trinket.TrinketRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 熔岩行者饰品：取消岩浆内移动减速。
 *
 * 原版 travel() 在 isInLava() == true 时进入岩浆移动分支，
 * 使用 0.02f 作为速度系数（正常约 0.1），并施加 0.5x 速度衰减。
 * 本 mixin 在玩家装备 magma_strider 饰品时让 isInLava() 返回 false，
 * 使 travel() 走正常的地面/空中移动逻辑——配合饰品已有的防火效果，
 * 实现"岩浆冲浪"体验。
 *
 * 注意：字节码中方法引用的 owner 是 LivingEntity（不是 Entity），
 * target 必须写 Lnet/minecraft/entity/LivingEntity;isInLava()Z。
 */
@Mixin(LivingEntity.class)
public abstract class MagmaStriderMovementMixin {

    @Redirect(
        method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInLava()Z")
    )
    private boolean tomkestookit$skipLavaSlowdown(LivingEntity self) {
        if (!TomkesTookitConfig.enabled("item.trinket")) return self.isInLava();
        if (self instanceof ServerPlayerEntity player) {
            if (TrinketRegistry.hasTrinketInBag(player, "magma_strider")) {
                return false; // 跳过岩浆移动分支 → 使用正常移动速度
            }
        }
        return self.isInLava();
    }
}
