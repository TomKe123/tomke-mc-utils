package com.tomkestookit.mixin;

import com.tomkestookit.AutoCrafterHandler;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 自动合成器被破坏时清理持久化位置。*/
@Mixin(AbstractBlock.class)
public class AutoCrafterBlockBreakMixin {
    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void tomkestookit$autoCrafterBreak(BlockState state, World world, BlockPos pos,
                                               BlockState newState, boolean moved, CallbackInfo ci) {
        if (world.isClient || state.isOf(newState.getBlock())) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.auto_crafter_ops")) return;
        if (world instanceof ServerWorld sw) AutoCrafterHandler.onBroken(sw, pos);
    }
}
