package com.tomkestookit.mixin;

import com.tomkestookit.logistics.LogisticsHandler;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BarrelBlock.onUse HEAD：如果是物流终端桶，检查对端可用性；不可用则拒绝打开 GUI。
 */
@Mixin(BarrelBlock.class)
public abstract class LogisticsBarrelOpenMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$blockOnUnavailable(BlockState state, World world, BlockPos pos,
                                                 PlayerEntity player, Hand hand, BlockHitResult hit,
                                                 CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.logistics_ops")) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (!LogisticsHandler.canOpenLocal(sw, pos, sp)) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
