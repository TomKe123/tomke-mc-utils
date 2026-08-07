package com.tomkestookit.regionclearer;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 3×3 判定 + 结果构造。CraftingResultMixin 只调 matches3x3 + buildResult 两个原语，
 * 不注册 RecipeSerializer（配方同步给 vanilla 客户端会崩），参照 SaturationPotion。
 */
public final class RegionClearerRecipe {

    private RegionClearerRecipe() {}

    /**
     * 中心必须是 iron_pickaxe，8 格四周必须是同一种 tier 材质。
     * 返回匹配到的 tier；不匹配返回 null。
     */
    public static RegionClearer.ClearerTier match3x3(RecipeInputInventory inv) {
        if (inv.getWidth() != 3 || inv.getHeight() != 3) return null;

        ItemStack center = inv.getStack(4);
        if (center.isEmpty() || center.getItem() != Items.IRON_PICKAXE) return null;

        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        RegionClearer.ClearerTier tier = null;
        for (int i : surround) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) return null;
            RegionClearer.ClearerTier t = tierOfMaterial(s);
            if (t == null) return null;
            if (tier == null) tier = t;
            else if (tier != t) return null;
        }
        return tier;
    }

    private static RegionClearer.ClearerTier tierOfMaterial(ItemStack s) {
        if (s.isIn(ItemTags.PLANKS)) return RegionClearer.ClearerTier.WOOD;
        if (s.isOf(Items.COBBLESTONE)) return RegionClearer.ClearerTier.STONE;
        if (s.isOf(Items.IRON_INGOT)) return RegionClearer.ClearerTier.IRON;
        if (s.isOf(Items.GOLD_INGOT)) return RegionClearer.ClearerTier.GOLD;
        if (s.isOf(Items.DIAMOND)) return RegionClearer.ClearerTier.DIAMOND;
        if (s.isOf(Items.ANCIENT_DEBRIS)) return RegionClearer.ClearerTier.NETHERITE;
        return null;
    }

    /**
     * 产出：对应 tier 的 vanilla 斧头 + NBT 标记 + 空 AttributeModifiers（去掉武器加成）
     * + CustomName / Lore。Damage 保持 0（一把新工具），耐久用完 vanilla 自动破碎。
     */
    public static ItemStack buildResult(RegionClearer.ClearerTier tier) {
        ItemStack stack = new ItemStack(tier.baseItem);

        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt(RegionClearer.NBT_TAG, tier.ordinal());
        nbt.putBoolean("Unbreakable", false);
        // 隐藏 attribute modifier tooltip（因为下面清空了，玩家看到“无加成”反而奇怪，直接隐藏）
        nbt.putInt("HideFlags", 2);

        // 清空 attribute：把 AttributeModifiers 设为空数组，覆盖 vanilla 斧头的攻击伤害 / 速度加成
        NbtList attrs = new NbtList();
        nbt.put("AttributeModifiers", attrs);

        stack.setCustomName(Text.literal("区域清除斧 ")
            .formatted(Formatting.AQUA, Formatting.BOLD)
            .append(Text.literal("§7- §f" + tier.zh)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§8「以前挖一格叫作坊，挖一片叫工程学。」",
            "§7左键 §f选 A §7右键 §f选 B §7聊天点击确认",
            "§7框选范围内所有方块 §8一次性清除",
            "§8基岩、屏障、命令方块只消失不掉落",
            "§8慎用于家门口"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        com.tomkestookit.materials.ItemQualityTag.Quality q = switch (tier) {
            case WOOD, STONE -> com.tomkestookit.materials.ItemQualityTag.Quality.UNCOMMON;
            case IRON, GOLD -> com.tomkestookit.materials.ItemQualityTag.Quality.RARE;
            case DIAMOND -> com.tomkestookit.materials.ItemQualityTag.Quality.EPIC;
            case NETHERITE -> com.tomkestookit.materials.ItemQualityTag.Quality.LEGENDARY;
        };
        com.tomkestookit.materials.ItemQualityTag.append(stack, q,
            "3×3 合成 §8· §7铁镐 + 8 " + tier.zh + "材料");
        return stack;
    }
}
