package com.starterkit.recipe;

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
 * "九面包 → 永久回复喷溅药水" 的两个原语：
 *   - matches3x3(inv) 判定 3x3 是否全部为面包
 *   - buildResult()   构造带 REGENERATION 效果的 SPLASH_POTION
 *
 * 用 REGENERATION 而不是 SATURATION，因为 SATURATION 是 InstantStatusEffect，
 * duration 对 instant 效果无效——喝了/被溅到只会瞬间回饱和度一次。
 * REGENERATION 是持续 effect，Integer.MAX_VALUE duration 直接生效，实际观感
 * 就是"永久回血"（每 25 tick 恢复 1 生命）。
 *
 * 合成路径由 CraftingResultMixin 拦截原版 updateResult 实现，不注册
 * RecipeSerializer——因为服务端 mod 若注册自定义 serializer，vanilla 客户端
 * 解析配方同步包时会因 "Unknown recipe serializer" 崩连。
 */
public final class SaturationPotion {

    private SaturationPotion() {}

    /** 3x3 输入格子必须全部是 minecraft:bread，且没有多余的物品。*/
    public static boolean matches3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != Items.BREAD) return false;
        }
        return true;
    }

    /** 结果物品：带 "面包精华" 命名 + 永久 REGENERATION 效果的 SPLASH_POTION。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);

        // Integer.MAX_VALUE = 永久（客户端 tooltip 显示 **:**）
        // REGENERATION 是持续 effect，duration 生效；amplifier=0 = 每 50 tick 回 1 心
        StatusEffectInstance regen = new StatusEffectInstance(
            StatusEffects.REGENERATION, Integer.MAX_VALUE, 0,
            false, false, true);
        PotionUtil.setCustomPotionEffects(stack, List.of(regen));

        stack.setCustomName(Text.literal("面包精华")
            .styled(s -> s.withItalic(false).withBold(true)));

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§7面粉与劳动的浓缩")
                .styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        return stack;
    }
}
