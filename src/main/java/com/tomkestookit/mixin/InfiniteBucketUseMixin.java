package com.tomkestookit.mixin;

import com.tomkestookit.infinitebucket.InfiniteBucket;
import com.tomkestookit.infinitebucket.InfiniteBucketHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class InfiniteBucketUseMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void tomkestookit$saveInfiniteBucket(World world, PlayerEntity player, Hand hand,
                                                  CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.infinite_bucket")) return;
        ItemStack stack = player.getStackInHand(hand);
        if (InfiniteBucket.isInfinite(stack)) {
            ((InfiniteBucketHolder) player).tomkestookit$setInfiniteBucket(stack.copy());
        }
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void tomkestookit$restoreInfiniteBucket(World world, PlayerEntity player, Hand hand,
                                                     CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.infinite_bucket")) return;
        ItemStack saved = ((InfiniteBucketHolder) player).tomkestookit$getInfiniteBucket();
        if (saved == null || saved.isEmpty()) return;
        ((InfiniteBucketHolder) player).tomkestookit$setInfiniteBucket(null);
        player.setStackInHand(hand, saved);
    }
}
