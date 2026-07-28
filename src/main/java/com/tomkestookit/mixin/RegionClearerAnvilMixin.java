package com.tomkestookit.mixin;

import com.tomkestookit.regionclearer.RegionClearer;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧禁止对区域清除斧做任何操作：清空输出。
 *
 * 用 RETURN 而非 TAIL：vanilla 遇到不兼容组合会提前 return，只有 RETURN 才能覆盖所有出口。
 */
@Mixin(AnvilScreenHandler.class)
public abstract class RegionClearerAnvilMixin {

    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("RETURN"))
    private void tomkestookit$blockRegionClearer(CallbackInfo ci) {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.region_clearer_use")) return;
        if (!RegionClearer.isTool(left) && !RegionClearer.isTool(right)) return;
        self.getSlot(2).setStack(ItemStack.EMPTY);
        this.levelCost.set(0);
        self.sendContentUpdates();
    }
}
