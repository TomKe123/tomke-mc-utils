package com.tomkestookit.mixin;

import com.tomkestookit.infinitebucket.InfiniteBucket;
import com.tomkestookit.infinitebucket.InfiniteBucketHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 无限水/岩浆桶效果：拦截 BucketItem.use 让桶用完不消失。
 *
 * 关键：@At("RETURN") 时**改返回值**里的 ItemStack，而不是直接改 hand。
 * 原因：调用者（Item#use 触发路径）在方法返回后会 player.setStackInHand(hand, result.getValue())，
 *       如果只改 hand 会立刻被覆盖。所以必须让返回值本身携带无限桶。
 */
@Mixin(BucketItem.class)
public abstract class InfiniteBucketUseMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void tomkestookit$saveInfiniteBucket(World world, PlayerEntity player, Hand hand,
                                                  CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.infinite_bucket")) return;
        ItemStack stack = player.getStackInHand(hand);
        if (InfiniteBucket.isInfinite(stack)) {
            ((InfiniteBucketHolder) player).tomkestookit$setInfiniteBucket(stack.copy());
        } else {
            ((InfiniteBucketHolder) player).tomkestookit$setInfiniteBucket(null);
        }
    }

    @Inject(method = "use", at = @At("RETURN"), cancellable = true)
    private void tomkestookit$restoreInfiniteBucket(World world, PlayerEntity player, Hand hand,
                                                     CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("recipe.infinite_bucket")) return;
        ItemStack saved = ((InfiniteBucketHolder) player).tomkestookit$getInfiniteBucket();
        if (saved == null || saved.isEmpty()) return;
        // 用完清 slot 防止重复触发
        ((InfiniteBucketHolder) player).tomkestookit$setInfiniteBucket(null);

        TypedActionResult<ItemStack> original = cir.getReturnValue();
        if (original == null) return;
        ActionResult result = original.getResult();
        // 只有真的成功交互（放液体 / 打水失败也没关系，此时 saved 还在原封不动）才恢复
        if (!result.isAccepted()) return;

        // 直接改回 hand（走 exchangeStack 后原 stack 已 decrement 成空）
        player.setStackInHand(hand, saved);
        // 同时改返回值里的 stack，防止上游 setStackInHand 再次覆盖
        cir.setReturnValue(new TypedActionResult<>(result, saved));
    }
}
