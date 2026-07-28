package com.tomkestookit;

import com.tomkestookit.logistics.LogisticsHandler;
import com.tomkestookit.recipe.GreedPotion;
import com.tomkestookit.recipe.SaturationPotion;
import com.tomkestookit.recipe.SugarTnt;
import com.tomkestookit.regionclearer.RegionClearer;
import com.tomkestookit.regionclearer.RegionClearerRecipe;
import com.tomkestookit.regionfiller.RegionFiller;
import com.tomkestookit.regionfiller.RegionFillerRecipe;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 所有"mixin 塞进 CraftingResultInventory 的魔改配方"的统一判定入口。
 *
 * 集中在一处，让玩家手工合成台（{@code CraftingResultMixin}）和自动合成器
 * （{@code AutoCrafterHandler.craftOnce}）能走同一套规则；此前合成器只调
 * {@code RecipeManager.getFirstMatch}，找不到 mixin 魔改配方 → 报"异常"。
 *
 * 该链的顺序 = 判定优先级；先命中先返回。
 */
public final class SpecialCraftingCatalog {

    private SpecialCraftingCatalog() {}

    /** 返回匹配到的魔改配方产物；均未命中返回 EMPTY。
     *  同一份 config 特性 key 与 CraftingResultMixin / 其它 crafting mixin 共用，
     *  确保玩家手工合成与自动合成器行为一致。*/
    public static ItemStack tryMatch(RecipeInputInventory input) {
        if (TomkesTookitConfig.enabled("recipe.greed_potion")
            && GreedPotion.matches3x3(input)) return GreedPotion.buildResult();
        if (TomkesTookitConfig.enabled("recipe.sugar_tnt")
            && SugarTnt.matches3x3(input)) return SugarTnt.buildResult();
        if (TomkesTookitConfig.enabled("recipe.saturation_potion")
            && SaturationPotion.matches3x3(input)) return SaturationPotion.buildResult();
        // 自动合成器（工作台 + 4 铁锭 + 4 红石）
        if (TomkesTookitConfig.enabled("recipe.auto_crafter")
            && matchesAutoCrafter(input)) return AutoCrafterHandler.buildItem();
        // 物流终端（磁石 + 8 铁块）
        if (TomkesTookitConfig.enabled("recipe.logistics")
            && matchesLogistics(input)) return LogisticsHandler.buildResultPair();
        // 区域清除斧（铁镐 + 8 材料）
        if (TomkesTookitConfig.enabled("recipe.region_clearer")) {
            RegionClearer.ClearerTier tier = RegionClearerRecipe.match3x3(input);
            if (tier != null) return RegionClearerRecipe.buildResult(tier);
        }
        // 附魔水/岩浆桶（9 水/岩浆桶）
        if (TomkesTookitConfig.enabled("recipe.region_filler")) {
            RegionFiller.FillerType type = RegionFillerRecipe.match3x3(input);
            if (type != null) return RegionFillerRecipe.buildResult(type);
        }
        return ItemStack.EMPTY;
    }

    private static boolean matchesAutoCrafter(RecipeInputInventory input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return false;
        if (input.getStack(4).getItem() != Items.CRAFTING_TABLE) return false;
        int[] corners = {0, 2, 6, 8};
        int[] edges = {1, 3, 5, 7};
        for (int i : corners) if (input.getStack(i).getItem() != Items.IRON_INGOT) return false;
        for (int i : edges) if (input.getStack(i).getItem() != Items.REDSTONE) return false;
        return true;
    }

    private static boolean matchesLogistics(RecipeInputInventory input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return false;
        if (input.getStack(4).getItem() != Items.LODESTONE) return false;
        int[] surround = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i : surround) if (input.getStack(i).getItem() != Items.IRON_BLOCK) return false;
        return true;
    }
}
