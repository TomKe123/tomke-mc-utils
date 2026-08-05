package com.tomkestookit.mixin;

import com.tomkestookit.reforge.ReforgeRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 取消悬空挖掘惩罚：
 *
 * vanilla PlayerEntity.getBlockBreakingSpeed 在玩家不在地面时 × (1/5)。
 * 如果玩家主手装备上有"天锻符文" (skyforge_rune) 重铸石，
 * 在 getBlockBreakingSpeed 返回后乘回 5 以抵消惩罚。
 *
 * 只在满足以下条件时补偿：
 *   1. 功能开启 (item.reforge)
 *   2. 玩家不在地面（否则没惩罚，不需要补偿）
 *   3. 玩家主手有 skyforge_rune UUID 的属性
 */
@Mixin(PlayerEntity.class)
public abstract class ReforgeNoAirPenaltyMixin {

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void tomkestookit$noAirPenalty(BlockState state, CallbackInfoReturnable<Float> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.reforge")) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isOnGround()) return; // 在地面没有惩罚，不需要补偿
        if (!(self instanceof ServerPlayerEntity)) return;

        ItemStack mainHand = self.getMainHandStack();
        ReforgeRegistry.ReforgeDef def = ReforgeRegistry.byId("skyforge_rune");
        if (def == null) return;
        if (!ReforgeRegistry.alreadyApplied(mainHand, def)) return;

        // vanilla 悬空时把速度 / 5，我们乘回来
        cir.setReturnValue(cir.getReturnValue() * 5.0f);
    }
}
