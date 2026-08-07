package com.tomkestookit.menu;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * "本包配方 → 特殊 tab" 里显示的一条魔改配方。
 *
 * 这些配方不走 datapack，因此不出现在 RecipeManager.values() 里；
 * 由 SpecialRecipeCatalog 统一提供数据源，让菜单能展示它们的做法/描述。
 *
 * 每条包含：
 *   - resultSupplier 构造产物示意 ItemStack（真实结果构造类同款）
 *   - grid[0..8]     3×3 图示，每格一个 ItemStack（支持 NBT 自定义物品）
 *   - description    Lore 描述
 *
 * 链式跳转（gridLinks）：
 *   进阶饰品的配方中心是「另一个配方」的产物。把该格 slot 映射到目标 key，
 *   RecipeMenu.openSpecialDetail 会：
 *     - 用目标配方的产物 ItemStack 作为该格 icon（带 NBT/附魔光效）
 *     - 添加点击 action → 跳到目标配方的详情
 */
public record SpecialRecipeEntry(String key,
                                 Supplier<ItemStack> resultSupplier,
                                 ItemStack[] grid,
                                 List<String> description) {
    public SpecialRecipeEntry {
        if (grid.length != 9) throw new IllegalArgumentException("grid must be 9");
    }

    /** 便捷构造：从 Item[] 自动转为 ItemStack[]（向后兼容普通配方）。*/
    public SpecialRecipeEntry(String key, Supplier<ItemStack> resultSupplier,
                              Item[] items, List<String> description) {
        this(key, resultSupplier, toStacks(items), description);
    }

    public ItemStack gridStack(int i) {
        if (grid[i] == null || grid[i].isEmpty()) return ItemStack.EMPTY;
        return grid[i].copy();
    }

    private static ItemStack[] toStacks(Item[] items) {
        ItemStack[] stacks = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            stacks[i] = (items[i] == null || items[i] == Items.AIR)
                ? ItemStack.EMPTY : new ItemStack(items[i]);
        }
        return stacks;
    }

    // ---- 静态 side-map：<recipe key, <slot idx, target key>> ----
    private static final Map<String, Map<Integer, String>> LINKS = new HashMap<>();

    /** 注册一个跳转链接：clicking gridSlot in `sourceKey` opens `targetKey`。*/
    public static void registerLink(String sourceKey, int gridSlot, String targetKey) {
        LINKS.computeIfAbsent(sourceKey, k -> new HashMap<>()).put(gridSlot, targetKey);
    }

    /** 查询该 slot 是否有跳转目标。*/
    public String gridLinkTarget(int slot) {
        Map<Integer, String> m = LINKS.get(key);
        return m == null ? null : m.get(slot);
    }
}
