package com.starterkit.recipe;

import com.starterkit.StarterKitMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * "九面包 → 永久滋养喷溅药水" 的两个原语：
 *   - matches3x3(inv)  判定 3x3 是否全部为面包
 *   - buildResult()    构造带 farmersdelight:nourishment 效果的 SPLASH_POTION
 *
 * 依赖 Farmer's Delight 已装载：通过 Registries.STATUS_EFFECT.get 运行时查找
 * "farmersdelight:nourishment"，不做静态类引用，避免没装 FD 时编译期 mod
 * 崩掉——事实上 build.gradle 也没加 FD 依赖。
 *
 * 若查找不到（服务器没装 FD），fallback 到不带效果的普通喷溅药水+一条提示
 * lore，避免出货为 null。
 *
 * 合成路径由 CraftingResultMixin 拦截原版 updateResult 实现，不注册
 * RecipeSerializer——若注册自定义 serializer，vanilla 客户端解析配方同步包时
 * 会因 "Unknown recipe serializer" 崩连。
 */
public final class SaturationPotion {

    private static final Identifier NOURISHMENT_ID = new Identifier("farmersdelight", "nourishment");

    private SaturationPotion() {}

    /** 3x3 输入格子必须全部是 minecraft:bread。*/
    public static boolean matches3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != Items.BREAD) return false;
        }
        return true;
    }

    /** 结果物品：带 "面包精华" 命名 + 永久 nourishment 的 SPLASH_POTION。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);

        StatusEffect nourishment = Registries.STATUS_EFFECT.get(NOURISHMENT_ID);
        if (nourishment != null) {
            // Integer.MAX_VALUE 就是"永久"，客户端 tooltip 会显示 **:**
            StatusEffectInstance eff = new StatusEffectInstance(
                nourishment, Integer.MAX_VALUE, 0,
                false, false, true);
            PotionUtil.setCustomPotionEffects(stack, List.of(eff));
        } else {
            StarterKitMod.LOGGER.warn("[StarterKit] farmersdelight:nourishment 未注册，" +
                "'面包精华' 药水将不带效果。请确认已装载 Farmer's Delight。");
        }

        stack.setCustomName(Text.literal("面包精华")
            .styled(s -> s.withItalic(false).withBold(true)));

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§7面粉与劳动的浓缩")
                .styled(s -> s.withItalic(false)))));
        if (nourishment == null) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§c需要 Farmer's Delight 加载")
                    .styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        return stack;
    }
}
