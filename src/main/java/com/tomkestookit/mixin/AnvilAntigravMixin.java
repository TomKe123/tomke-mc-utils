package com.tomkestookit.mixin;

import com.tomkestookit.AntigravBootsHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧特殊配方：任意靴子（左槽）+ 反重力重铸石（右槽）→ 该靴子获得反重力属性。
 *
 * 反重力重铸石走 ReforgeRegistry 注册：id=antigrav_boots，
 * 合成由 SpecialCraftingCatalog + ReforgeRegistry.match3x3 处理。
 * 铁砧应用在此 mixin 中拦截，与 AnvilReforgeMixin 互补（Reforge 只处理
 * 属性追加，反重力靴是整体覆盖 AttributeModifiers）。
 */
@Mixin(value = AnvilScreenHandler.class, priority = 1000)
public abstract class AnvilAntigravMixin {

    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("RETURN"))
    private void tomkestookit$antigravAnvil(CallbackInfo ci) {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();

        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.reforge")) return;
        if (!AntigravBootsHandler.isBoots(left)) return;
        com.tomkestookit.reforge.ReforgeRegistry.ReforgeDef def =
            com.tomkestookit.reforge.ReforgeRegistry.byId("antigrav_boots");
        if (def == null) return;
        if (!def.id().equals(com.tomkestookit.reforge.ReforgeRegistry.getId(right))) return;
        if (AntigravBootsHandler.hasAntigrav(left)) return;

        ItemStack result = left.copy();
        AntigravBootsHandler.applyAntigrav(result, false);

        self.getSlot(2).setStack(result);
        this.levelCost.set(def.anvilCost());
        self.sendContentUpdates();
    }
}
