package com.tomkestookit.mixin;

import com.tomkestookit.AutoCrafterHandler;
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

/** 放置带自动合成器 NBT 的木桶时注册位置。*/
@Mixin(BlockItem.class)
public class AutoCrafterBarrelPlaceMixin {
    @Inject(method = "postPlacement", at = @At("HEAD"))
    private void tomkestookit$autoCrafterPlace(BlockPos pos, World world, PlayerEntity player,
                                               ItemStack stack, BlockState state,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.auto_crafter_ops")) return;
        if (!(world instanceof ServerWorld sw)) return;
        AutoCrafterHandler.onPlaced(sw, pos, stack);
    }
}
