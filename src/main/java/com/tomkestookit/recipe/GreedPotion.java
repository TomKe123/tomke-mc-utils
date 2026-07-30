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
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * "8 绿宝石块 + 1 空玻璃瓶 → 贪婪之瓶（不祥之兆 100 分钟）" 的两个原语。
 *
 *   - matches3x3(inv)  判定 3x3：中心 GLASS_BOTTLE，四周 8 格 EMERALD_BLOCK
 *   - buildResult()    构造带 BAD_OMEN（100 分钟）的 POTION（可饮用普通药水）
 *
 * 合成路径由 CraftingResultMixin 拦截原版 updateResult 实现，与"面包精华"药水共存，
 * 顺序上先判本条，未命中再判面包，避免相互覆盖。
 */
public final class GreedPotion {

    /** 100 分钟 = 100 * 60 * 20 tick。*/
    public static final int DURATION_TICKS = 100 * 60 * 20;

    private GreedPotion() {}

    /** 3x3：中心必须是空玻璃瓶；四周 8 格必须是绿宝石块。*/
    public static boolean matches3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return false;
        if (inv.getStack(4).getItem() != Items.GLASS_BOTTLE) return false;
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != Items.EMERALD_BLOCK) return false;
        }
        return true;
    }

    /** 结果物品：喷溅药水（SPLASH_POTION），带 100 分钟不祥之兆。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);

        StatusEffectInstance eff = new StatusEffectInstance(
            StatusEffects.BAD_OMEN, DURATION_TICKS, 0,
            false, true, true);
        PotionUtil.setCustomPotionEffects(stack, List.of(eff));

        stack.setCustomName(Text.literal("贪婪之瓶")
            .styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.GREEN)));

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「他们说这瓶里装着一个村庄的税收。」",
            "§7绿宝石熬煮而成，散发着甜腻的铜臭",
            "§7投掷命中：§c100 分钟不祥之兆",
            "§8下次进村时，记得回头看看"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        // 客户端 tooltip 上色：让药水显示为绿宝石绿
        nbt.putInt("CustomPotionColor", 0x1CDC4A);
        return stack;
    }
}
