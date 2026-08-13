package com.tomkestookit.infinitebucket;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 无限水/岩浆桶合成配方判定：
 *
 * 3×3 V字形：
 *   .    水桶/岩浆桶  .      槽位 0 1 2
 *   铁块  炼药锅    铁块     槽位 3 4 5
 *   .    铁块       .      槽位 6 7 8
 *
 * 桶在顶部中间（slot 1），炼药锅在中间（slot 4），
 * 左中铁块（slot 3）、右中铁块（slot 5）、底部中间铁块（slot 7），其余全空。
 */
public final class InfiniteBucketRecipe {

    private InfiniteBucketRecipe() {}

    /** 判定 3x3 是否匹配无限桶合成，匹配返回类型，否则 null。*/
    public static InfiniteBucket.BucketType match3x3(RecipeInputInventory input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return null;

        // 顶部中间：水桶或岩浆桶（vanilla 版本，不能是无限桶或附魔桶）
        ItemStack bucketSlot = input.getStack(1);
        if (InfiniteBucket.isInfinite(bucketSlot)) return null;
        if (com.tomkestookit.regionfiller.RegionFiller.isTool(bucketSlot)) return null;
        InfiniteBucket.BucketType type;
        if (bucketSlot.getItem() == Items.WATER_BUCKET) {
            type = InfiniteBucket.BucketType.WATER;
        } else if (bucketSlot.getItem() == Items.LAVA_BUCKET) {
            type = InfiniteBucket.BucketType.LAVA;
        } else {
            return null;
        }

        // 中间：炼药锅
        if (input.getStack(4).getItem() != Items.CAULDRON) return null;

        // V字形铁块：左中（slot 3）、右中（slot 5）、底部中间（slot 7）
        if (input.getStack(3).getItem() != Items.IRON_BLOCK) return null;
        if (input.getStack(5).getItem() != Items.IRON_BLOCK) return null;
        if (input.getStack(7).getItem() != Items.IRON_BLOCK) return null;

        // 其余槽位必须为空：slot 0, 2, 6, 8
        int[] empty = {0, 2, 6, 8};
        for (int i : empty) {
            if (!input.getStack(i).isEmpty()) return null;
        }

        return type;
    }
}
