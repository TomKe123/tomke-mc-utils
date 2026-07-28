package com.tomkestookit.mixin;

import com.tomkestookit.regionfiller.RegionFiller;
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

/**
 * 附魔水桶 / 附魔岩浆桶 禁止 vanilla 右键倒液体行为。
 *
 * vanilla BucketItem.use 会：
 *   - 水桶：raycast 找空气 / 可注水方块 → 放置水
 *   - 岩浆桶：raycast 找空气 → 放置岩浆
 * 玩家右键空气（未命中方块）时 UseBlockCallback 不触发，BucketItem.use 直接跑，
 * 我们的“选点 B”流程被跳过；同时会把液体倒出来。这里在 HEAD 直接 pass，阻断。
 * 命中方块的选点走 UseBlockCallback，正常处理。
 */
@Mixin(BucketItem.class)
public abstract class RegionFillerBucketUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$blockVanillaBucketUse(World world, PlayerEntity player, Hand hand,
                                                    CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_filler_use")) return;
        ItemStack stack = player.getStackInHand(hand);
        if (RegionFiller.isTool(stack)) {
            cir.setReturnValue(TypedActionResult.pass(stack));
        }
    }
}
