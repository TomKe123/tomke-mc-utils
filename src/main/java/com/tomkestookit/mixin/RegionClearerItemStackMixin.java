package com.tomkestookit.mixin;

import com.tomkestookit.regionclearer.RegionClearer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 保底禁附魔：ItemStack.isEnchantable 直接返回 false。
 * 附魔台 / 铁砧 / 附魔书组合 / mod 的自定义附魔逻辑都通常会先查这个。
 */
@Mixin(ItemStack.class)
public abstract class RegionClearerItemStackMixin {

    @Inject(method = "isEnchantable", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$notEnchantable(CallbackInfoReturnable<Boolean> cir) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_clearer_use")) return;
        ItemStack self = (ItemStack) (Object) this;
        if (RegionClearer.isTool(self)) {
            cir.setReturnValue(false);
        }
    }
}
