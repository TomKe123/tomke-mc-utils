package com.tomkestookit.recipe;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.potion.PotionUtil;
import net.minecraft.text.Text;

import java.util.List;

/**
 * "九面包 → 永久饱和喷溅药水" 的两个原语：
 *   - matches3x3(inv)  判定 3x3 是否全部为面包
 *   - buildResult()    构造带 saturation 效果的 SPLASH_POTION
 *
 * 合成路径由 CraftingResultMixin 拦截原版 updateResult 实现，不注册
 * RecipeSerializer——若注册自定义 serializer，vanilla 客户端解析配方同步包时
 * 会因 "Unknown recipe serializer" 崩连。
 */
public final class SaturationPotion {

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

    /** 结果物品：带 "面包精华" 命名 + 永久 saturation 的 SPLASH_POTION。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);

        StatusEffectInstance eff = new StatusEffectInstance(
            StatusEffects.SATURATION, Integer.MAX_VALUE, 0,
            false, false, true);
        PotionUtil.setCustomPotionEffects(stack, List.of(eff));

        stack.setCustomName(Text.literal("面包精华")
            .styled(s -> s.withItalic(false).withBold(true)));

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「九个面包换来一瓶不饿。」",
            "§7麦浪的余韵，永恒的饱足感",
            "§7投掷命中：永久饱和效果",
            "§8面包师看到会哭"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.UNCOMMON,
            "3×3 合成 §8· §79 面包");
        return stack;
    }
}
