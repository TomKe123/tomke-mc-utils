package com.tomkestookit.infinitebucket;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 无限水/岩浆桶合成配方判定：
 *
 * 3×3 十字形：
 *   . 铁块 .
 *   铁块 炼药锅 铁块+水桶/岩浆桶
 *   . 铁块 .
 *
 * 实际上是 4 个铁块 + 1 个炼药锅（中心）+ 1 个水桶/岩浆桶（任意一格四周）。
 */
public final class InfiniteBucketRecipe {

    private InfiniteBucketRecipe() {}

    /** 判定 3x3 是否匹配无限桶合成，匹配返回类型，否则 null。*/
    public static InfiniteBucket.BucketType match3x3(RecipeInputInventory input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return null;

        // 中心必须是炼药锅
        ItemStack center = input.getStack(4);
        if (center.getItem() != Items.CAULDRON) return null;

        // 四角必须为空
        int[] corners = {0, 2, 6, 8};
        for (int i : corners) {
            if (!input.getStack(i).isEmpty()) return null;
        }

        // 十字四个位置：必须 3 个铁块 + 1 个水桶或岩浆桶
        int[] cross = {1, 3, 5, 7};
        int ironCount = 0;
        InfiniteBucket.BucketType foundType = null;

        for (int i : cross) {
            ItemStack s = input.getStack(i);
            if (s.isEmpty()) return null;
            if (s.getItem() == Items.IRON_BLOCK) {
                ironCount++;
            } else if (s.getItem() == Items.WATER_BUCKET) {
                if (foundType != null) return null; // 不能有两个桶
                foundType = InfiniteBucket.BucketType.WATER;
            } else if (s.getItem() == Items.LAVA_BUCKET) {
                if (foundType != null) return null;
                foundType = InfiniteBucket.BucketType.LAVA;
            } else {
                return null; // 非法物品
            }
        }

        // 必须恰好 3 个铁块 + 1 个桶
        if (ironCount != 3 || foundType == null) return null;
        return foundType;
    }
}
