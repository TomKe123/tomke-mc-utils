package com.tomkestookit.mixin;

import com.tomkestookit.logistics.LogisticsHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockItem.postPlacement HEAD：如果 stack 是物流终端木桶（有 pairId NBT），
 * 就在放下的位置注册 pair 端点、写 BE NBT、生成 marker。
 *
 * 对普通 BlockItem 无影响；只在 stack 上有我们的 NBT 时才处理。
 */
@Mixin(BlockItem.class)
public abstract class LogisticsBarrelPlaceMixin {

    @Inject(method = "postPlacement", at = @At("HEAD"))
    private void tomkestookit$logisticsPlace(BlockPos pos, World world, PlayerEntity player,
                                             ItemStack stack, BlockState state,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.logistics_ops")) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!LogisticsHandler.isLogisticsBarrelStack(stack)) return;
        LogisticsHandler.onPlaced(sw, pos, stack);
    }
}
