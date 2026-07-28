package com.tomkestookit.menu;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;
import java.util.function.Supplier;

/**
 * "本包配方 → 特殊 tab" 里显示的一条魔改配方。
 *
 * 这些配方不走 datapack，因此不出现在 RecipeManager.values() 里；
 * 由 SpecialRecipeCatalog 统一提供数据源，让菜单能展示它们的做法/描述。
 *
 * 每条包含：
 *   - resultSupplier 构造产物示意 ItemStack（真实结果构造类同款）
 *   - grid[0..8]     3×3 图示，每格一个物品作为 icon（空格填 AIR/玻璃板）
 *   - description    Lore 描述
 */
public record SpecialRecipeEntry(String key,
                                 Supplier<ItemStack> resultSupplier,
                                 net.minecraft.item.Item[] grid,
                                 List<String> description) {
    public SpecialRecipeEntry {
        if (grid.length != 9) throw new IllegalArgumentException("grid must be 9");
    }

    public ItemStack gridStack(int i) {
        if (grid[i] == null || grid[i] == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(grid[i]);
    }
}
