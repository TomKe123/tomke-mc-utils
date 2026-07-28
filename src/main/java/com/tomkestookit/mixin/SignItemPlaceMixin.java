package com.tomkestookit.mixin;

import com.tomkestookit.SignCommandHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockItem.postPlacement HEAD：如果 stack 是带 pending 命令的告示牌物品，
 * 把命令写到 SignBlockEntity NBT 并自动蜡封。
 *
 * 用 BlockItem 而不是 SignItem 覆盖，是为了同时包含 HangingSignItem
 * （其父类是 VerticallyAttachableBlockItem extends BlockItem）。
 */
@Mixin(BlockItem.class)
public abstract class SignItemPlaceMixin {

    @Inject(method = "postPlacement", at = @At("HEAD"))
    private void tomkestookit$bindSignCommand(BlockPos pos, World world, PlayerEntity player,
                                              ItemStack stack, BlockState state,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.sign_command")) return;
        if (!SignCommandHandler.isSignItem(stack)) return;
        SignCommandHandler.bindOnPlace(world, pos, stack);
    }
}
