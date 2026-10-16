package com.tomkestookit.mixin;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.tomkestookit.reforge.ReforgeRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 已撤销的重铸属性在读取处直接失效。
 *
 * ReforgeRefreshHandler 只扫在线玩家的物品栏 / 末影箱；箱子、盔甲架、掉落物、
 * 被怪物捡起穿上的装备里仍留着旧的 "tomkestookit:reforge_*" modifier。
 * 这里在 getAttributeModifiers 返回前把不在 ReforgeRegistry 里的重铸 modifier 过滤掉，
 * 无论物品在哪、被谁装备都不再生效；NBT 本身仍由 ReforgeRefreshHandler 慢慢清理。
 *
 * 快路径：没有 APPLIED_TAG 的物品（绝大多数 vanilla 物品）直接返回，不分配新 Multimap。
 */
@Mixin(ItemStack.class)
public abstract class ReforgeModifierFilterMixin {

    private static final String PREFIX = "tomkestookit:reforge_";

    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void tomkestookit$dropRemovedReforges(EquipmentSlot slot,
            CallbackInfoReturnable<Multimap<EntityAttribute, EntityAttributeModifier>> cir) {
        NbtCompound nbt = ((ItemStack) (Object) this).getNbt();
        if (nbt == null || !nbt.contains(ReforgeRegistry.APPLIED_TAG)) return;

        Multimap<EntityAttribute, EntityAttributeModifier> original = cir.getReturnValue();
        boolean any = false;
        for (EntityAttributeModifier m : original.values()) {
            if (isRemoved(m)) { any = true; break; }
        }
        if (!any) return;

        Multimap<EntityAttribute, EntityAttributeModifier> filtered = LinkedHashMultimap.create();
        original.forEach((attr, m) -> { if (!isRemoved(m)) filtered.put(attr, m); });
        cir.setReturnValue(filtered);
    }

    private static boolean isRemoved(EntityAttributeModifier m) {
        String name = m.getName();
        return name.startsWith(PREFIX) && ReforgeRegistry.byId(name.substring(PREFIX.length())) == null;
    }
}
