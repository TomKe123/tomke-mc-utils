package com.tomkestookit.regionfiller;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 3×3 判定 + 结果构造：9 格必须全是同一种桶（WATER_BUCKET 或 LAVA_BUCKET）。
 */
public final class RegionFillerRecipe {

    private RegionFillerRecipe() {}

    public static RegionFiller.FillerType match3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;
        // 首格决定类型
        ItemStack first = inv.getStack(0);
        RegionFiller.FillerType type;
        if (first.isOf(Items.WATER_BUCKET)) type = RegionFiller.FillerType.WATER;
        else if (first.isOf(Items.LAVA_BUCKET)) type = RegionFiller.FillerType.LAVA;
        else return null;
        // 其余 8 格必须与首格一致
        for (int i = 1; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isOf(type.baseItem)) return null;
        }
        return type;
    }

    public static ItemStack buildResult(RegionFiller.FillerType type) {
        ItemStack stack = new ItemStack(type.baseItem);

        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt(RegionFiller.NBT_TAG, type.ordinal());

        // 附魔光效：塞一个 Unbreaking 附魔，实际对桶无效，只是产生 glint
        NbtList enchList = new NbtList();
        NbtCompound fakeEnch = new NbtCompound();
        fakeEnch.putString("id", Registries.ENCHANTMENT.getId(Enchantments.UNBREAKING).toString());
        fakeEnch.putInt("lvl", 1);
        enchList.add(fakeEnch);
        nbt.put("Enchantments", enchList);
        // 隐藏附魔 tooltip（1 = enchantments）
        nbt.putInt("HideFlags", 1);

        stack.setCustomName(Text.literal("附魔" + type.zh + "桶")
            .formatted(Formatting.AQUA, Formatting.BOLD));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「九个桶浓缩成一片海。」",
            "§7左键 §f选 A §7右键 §f选 B §7聊天点击确认",
            "§7只填空气格 §8已有方块原封不动",
            "§8一次性物品，倒完就扔",
            "§8岩浆桶版请勿在木屋附近使用"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);
        com.tomkestookit.materials.ItemQualityTag.append(stack,
            com.tomkestookit.materials.ItemQualityTag.Quality.RARE,
            "3×3 合成 §8· §79 " + type.zh + "桶（一次性）");
        return stack;
    }
}
