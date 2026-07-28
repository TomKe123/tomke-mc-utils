package com.tomkestookit.mixin;

import com.tomkestookit.AutoCrafterHandler;
import net.minecraft.block.CraftingTableBlock;
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

/** 自动合成器工作台打开时切到自定义箱子 UI。*/
@Mixin(CraftingTableBlock.class)
public class AutoCrafterBarrelOpenMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$openAutoCrafter(BlockState state, World world, BlockPos pos,
                                              PlayerEntity player, Hand hand, BlockHitResult hit,
                                              CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.auto_crafter_ops")) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (AutoCrafterHandler.onUse(sw, pos, sp)) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
