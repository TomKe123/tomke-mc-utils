package com.tomkestookit.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * "大伊万"：中心 TNT，四周 8 格糖 → 一个可投掷的 TNT 物品。
 *
 *   - 爆炸范围 = 普通 TNT 4 倍
 *   - 伤害 = 普通 TNT 4 倍
 *   - Shift + 右键可当投掷物丢出
 *
 * 实现路径：
 *   - matches3x3 / buildResult 两个原语，与其它魔改配方一致
 *   - CraftingResultMixin 拦截 vanilla 空结果时判定这条
 *   - 投掷 / 爆炸行为由 SugarTntHandler 处理（UseItemCallback + 自定义 TntEntity）
 */
public final class SugarTnt {

    public static final String NBT_KEY = "TomkesTookitSugarTnt";
    public static final float POWER_MULTIPLIER = 4.0f;
    public static final String DISPLAY_NAME = "大伊万";

    private SugarTnt() {}

    /** 3x3：中心必须是 TNT；四周 8 格必须是糖。*/
    public static boolean matches3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return false;
        if (inv.getStack(4).getItem() != Items.TNT) return false;
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() != Items.SUGAR) return false;
        }
        return true;
    }

    /** 产物：带 NBT 标记的 TNT 物品，附特殊命名 + lore + 附魔光效。*/
    public static ItemStack buildResult() {
        ItemStack stack = new ItemStack(Items.TNT);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(NBT_KEY, true);

        // 附魔光效：塞一条 unbreaking I，再用 HideFlags 位 0 隐掉附魔条 tooltip。
        NbtList enchantments = new NbtList();
        NbtCompound enchant = new NbtCompound();
        enchant.putString("id", "minecraft:unbreaking");
        enchant.putInt("lvl", 1);
        enchantments.add(enchant);
        nbt.put("Enchantments", enchantments);
        nbt.putInt("HideFlags", 1);

        stack.setCustomName(Text.literal(DISPLAY_NAME)
            .styled(s -> s.withItalic(false).withBold(true).withColor(Formatting.LIGHT_PURPLE)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「糖是战略物资。」§7—— 蔗糖工业协会",
            "§8一硫二硝三木炭，加点白糖大伊万",
            "",
            "§c威力 §f×4 §8半径 §f×4 §8伤害 §f×4",
            "§e右键 §7放地上引信 §8· §eshift + 右键 §7抛出去",
            "§8扔完之后建议看看背包还剩几颗牙"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.RARE,
            "3×3 合成 §8· §71 TNT + 8 白糖");
        return stack;
    }

    public static boolean isSugarTnt(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.TNT) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_KEY);
    }
}
