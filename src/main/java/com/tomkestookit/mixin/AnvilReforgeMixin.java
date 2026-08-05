package com.tomkestookit.mixin;

import com.tomkestookit.reforge.ReforgeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧重铸：装备(左) + 重铸石(右) → 装备追加属性。
 *
 * 兼容性：
 *   - 不覆盖已有属性，追加到 AttributeModifiers 列表末尾
 *   - 每块重铸石通过 UUID 去重，同一块不能打两次
 *   - 对任意 mod 装备生效（只要通过 canApply 判定）
 *   - priority = 1100，在 AnvilAntigravMixin(1000) 之后运行
 */
@Mixin(value = AnvilScreenHandler.class, priority = 1100)
public abstract class AnvilReforgeMixin {

    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("RETURN"))
    private void tomkestookit$reforgeAnvil(CallbackInfo ci) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.reforge")) return;

        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();

        if (left.isEmpty() || right.isEmpty()) return;

        String reforgeId = ReforgeRegistry.getId(right);
        if (reforgeId == null) return;

        ReforgeRegistry.ReforgeDef def = ReforgeRegistry.byId(reforgeId);
        if (def == null) return;

        // 检查装备是否适用
        if (!def.canApply().test(left)) return;

        // 检查是否已应用
        if (ReforgeRegistry.alreadyApplied(left, def)) return;

        ItemStack result = ReforgeRegistry.applyReforge(left, def);

        self.getSlot(2).setStack(result);
        this.levelCost.set(def.anvilCost());
        self.sendContentUpdates();
    }
}
