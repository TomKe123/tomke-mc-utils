package com.tomkestookit.menu;

import com.tomkestookit.AutoCrafterHandler;
import com.tomkestookit.logistics.LogisticsHandler;
import com.tomkestookit.recipe.GreedPotion;
import com.tomkestookit.recipe.SaturationPotion;
import com.tomkestookit.regionclearer.RegionClearer;
import com.tomkestookit.regionclearer.RegionClearerRecipe;
import com.tomkestookit.regionfiller.RegionFiller;
import com.tomkestookit.regionfiller.RegionFillerRecipe;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 本包 5 个 mixin 塞进 CraftingScreenHandler 的"魔改配方"数据源。
 * RecipeMenu 里的"特殊 tab"从这里读，让玩家能在菜单里看到做法。
 */
public final class SpecialRecipeCatalog {

    private SpecialRecipeCatalog() {}

    /** SpecialRecipeEntry.key → 对应 admin config feature key。*/
    private static final java.util.Map<String, String> KEY_TO_CONFIG = java.util.Map.ofEntries(
        java.util.Map.entry("greed_potion",         "recipe.greed_potion"),
        java.util.Map.entry("sugar_tnt",            "recipe.sugar_tnt"),
        java.util.Map.entry("saturation_potion",    "recipe.saturation_potion"),
        java.util.Map.entry("auto_crafter",         "recipe.auto_crafter"),
        java.util.Map.entry("logistics",            "recipe.logistics"),
        java.util.Map.entry("region_clearer_iron",  "recipe.region_clearer"),
        java.util.Map.entry("region_filler_water",  "recipe.region_filler"),
        java.util.Map.entry("region_filler_lava",   "recipe.region_filler")
    );

    /** 展示给菜单的配方目录；管理员关掉的自动过滤。*/
    public static List<SpecialRecipeEntry> all() {
        List<SpecialRecipeEntry> out = new ArrayList<>();
        for (SpecialRecipeEntry e : raw()) {
            String cfg = KEY_TO_CONFIG.get(e.key());
            if (cfg != null && !com.tomkestookit.TomkesTookitConfig.enabled(cfg)) continue;
            out.add(e);
        }
        return out;
    }

    /** 未过滤的完整目录（内部用，或供 admin 面板"忽略开关"预览）。*/
    private static List<SpecialRecipeEntry> raw() {
        List<SpecialRecipeEntry> list = new ArrayList<>();

        // 贪婪之瓶
        list.add(new SpecialRecipeEntry(
            "greed_potion",
            GreedPotion::buildResult,
            new Item[] {
                Items.EMERALD_BLOCK, Items.EMERALD_BLOCK, Items.EMERALD_BLOCK,
                Items.EMERALD_BLOCK, Items.GLASS_BOTTLE,  Items.EMERALD_BLOCK,
                Items.EMERALD_BLOCK, Items.EMERALD_BLOCK, Items.EMERALD_BLOCK
            },
            List.of(
                "§8「他们说这瓶里装着一个村庄的税收。」",
                "§7中心空瓶，四周绿宝石块",
                "§7投掷命中：§c100 分钟不祥之兆",
                "§8下次进村时，记得回头看看"
            )
        ));

        // 大伊万
        list.add(new SpecialRecipeEntry(
            "sugar_tnt",
            com.tomkestookit.recipe.SugarTnt::buildResult,
            new Item[] {
                Items.SUGAR, Items.SUGAR, Items.SUGAR,
                Items.SUGAR, Items.TNT,   Items.SUGAR,
                Items.SUGAR, Items.SUGAR, Items.SUGAR
            },
            List.of(
                "§8「一硫二硝三木炭，加点白糖大伊万」",
                "§7中心 TNT，四周 8 格糖",
                "§c威力 §f×4 §8半径 §f×4 §8伤害 §f×4",
                "§e右键 §7放地上引信 §8· §eshift + 右键 §7抛出去",
                "§8扔完之后建议看看背包还剩几颗牙"
            )
        ));

        // 面包精华
        list.add(new SpecialRecipeEntry(
            "saturation_potion",
            SaturationPotion::buildResult,
            new Item[] {
                Items.BREAD, Items.BREAD, Items.BREAD,
                Items.BREAD, Items.BREAD, Items.BREAD,
                Items.BREAD, Items.BREAD, Items.BREAD
            },
            List.of(
                "§8「九个面包换来一瓶不饿。」",
                "§79 面包压成一瓶麦浪",
                "§7投掷命中：永久滋养效果",
                "§8依赖 Farmer's Delight 加载才有效果"
            )
        ));

        // 自动合成器
        list.add(new SpecialRecipeEntry(
            "auto_crafter",
            AutoCrafterHandler::buildItem,
            new Item[] {
                Items.IRON_INGOT, Items.REDSTONE,       Items.IRON_INGOT,
                Items.REDSTONE,   Items.CRAFTING_TABLE, Items.REDSTONE,
                Items.IRON_INGOT, Items.REDSTONE,       Items.IRON_INGOT
            },
            List.of(
                "§8「工作台通电了。」",
                "§7铁 + 红石 + 工作台，从此双手解放",
                "§7右键打开：设配方、放材料、看输出",
                "§7红石 §f持续 §7/ §f脉冲 §7/ §f无红石 §7三档",
                "§8上方喂料 §8· §8下方抽货 §8· §8满时礼貌停下"
            )
        ));

        // 物流终端
        list.add(new SpecialRecipeEntry(
            "logistics_terminal",
            LogisticsHandler::buildResultPair,
            new Item[] {
                Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK,
                Items.IRON_BLOCK, Items.LODESTONE,  Items.IRON_BLOCK,
                Items.IRON_BLOCK, Items.IRON_BLOCK, Items.IRON_BLOCK
            },
            List.of(
                "§8「磁石不会说谎——它们知道彼此在哪。」",
                "§7一炉出 2 个成对木桶",
                "§7两端放下后物品实时同步",
                "§8区块不加载，货物就打瞌睡"
            )
        ));

        // 区域清除斧（示例：铁级）
        list.add(new SpecialRecipeEntry(
            "region_clearer_iron",
            () -> RegionClearerRecipe.buildResult(RegionClearer.ClearerTier.IRON),
            new Item[] {
                Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
                Items.IRON_INGOT, Items.IRON_PICKAXE, Items.IRON_INGOT,
                Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT
            },
            List.of(
                "§8「以前挖一格叫作坊，挖一片叫工程学。」",
                "§7中心铁镐，四周材质决定 tier",
                "§7木板 / 圆石 / 铁 / 金 / 钻石 / 远古残骸",
                "§7左 §fA §7右 §fB §7聊天点击确认",
                "§8基岩屏障命令方块只消失不掉落"
            )
        ));

        // 区域填充：附魔水桶
        list.add(new SpecialRecipeEntry(
            "region_filler_water",
            () -> RegionFillerRecipe.buildResult(RegionFiller.FillerType.WATER),
            new Item[] {
                Items.WATER_BUCKET, Items.WATER_BUCKET, Items.WATER_BUCKET,
                Items.WATER_BUCKET, Items.WATER_BUCKET, Items.WATER_BUCKET,
                Items.WATER_BUCKET, Items.WATER_BUCKET, Items.WATER_BUCKET
            },
            List.of(
                "§8「九个桶浓缩成一片海。」",
                "§79 水桶合成一次性附魔水桶",
                "§7左 §fA §7右 §fB §7聊天点击确认",
                "§8只填空气格 §8· §8一次性物品"
            )
        ));

        // 区域填充：附魔岩浆桶
        list.add(new SpecialRecipeEntry(
            "region_filler_lava",
            () -> RegionFillerRecipe.buildResult(RegionFiller.FillerType.LAVA),
            new Item[] {
                Items.LAVA_BUCKET, Items.LAVA_BUCKET, Items.LAVA_BUCKET,
                Items.LAVA_BUCKET, Items.LAVA_BUCKET, Items.LAVA_BUCKET,
                Items.LAVA_BUCKET, Items.LAVA_BUCKET, Items.LAVA_BUCKET
            },
            List.of(
                "§8「九个桶浓缩成一片岩浆湖。」",
                "§79 岩浆桶合成一次性附魔岩浆桶",
                "§7左 §fA §7右 §fB §7聊天点击确认",
                "§c慎用于木屋 / 森林 / 家门口"
            )
        ));

        return list;
    }
}
