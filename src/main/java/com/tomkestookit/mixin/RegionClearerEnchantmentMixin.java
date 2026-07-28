package com.tomkestookit.mixin;

import com.tomkestookit.regionclearer.RegionClearer;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.EnchantmentScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 附魔台放入区域清除斧 → 无附魔选项。
 *
 * vanilla 的 onContentChanged 会在输入槽变化时算 3 条附魔预览，用 enchantmentPower[i]
 * 和 enchantmentId[i] 存。我们在 TAIL 之后把这三对 Property 都清 0/-1，让附魔按钮全灰。
 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class RegionClearerEnchantmentMixin {

    @Shadow @Final public int[] enchantmentPower;
    @Shadow @Final public int[] enchantmentId;
    @Shadow @Final public int[] enchantmentLevel;

    @Inject(method = "onContentChanged", at = @At("RETURN"))
    private void tomkestookit$blockRegionClearer(Inventory inventory, CallbackInfo ci) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_clearer_use")) return;
        if (!RegionClearer.isTool(inventory.getStack(0))) return;
        for (int i = 0; i < 3; i++) {
            enchantmentPower[i] = 0;
            enchantmentId[i] = -1;
            enchantmentLevel[i] = -1;
        }
    }
}
