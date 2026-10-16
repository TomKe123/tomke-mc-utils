package com.tomkestookit.menu;

import com.tomkestookit.AutoCrafterHandler;
import com.tomkestookit.logistics.LogisticsHandler;
import com.tomkestookit.recipe.GreedPotion;
import com.tomkestookit.recipe.SaturationPotion;
import com.tomkestookit.reforge.ReforgeRegistry;
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
        java.util.Map.entry("grenade",              "recipe.thrown_explosive"),
        java.util.Map.entry("bouncy_bomb",          "recipe.thrown_explosive"),
        java.util.Map.entry("saturation_potion",    "recipe.saturation_potion"),
        java.util.Map.entry("infinite_bucket_water","recipe.infinite_bucket"),
        java.util.Map.entry("infinite_bucket_lava", "recipe.infinite_bucket"),
        java.util.Map.entry("auto_crafter",         "recipe.auto_crafter"),
        java.util.Map.entry("antigrav_boots",       "recipe.antigrav_boots"),
        java.util.Map.entry("logistics_terminal",   "recipe.logistics"),
        java.util.Map.entry("region_clearer_iron",  "recipe.region_clearer"),
        java.util.Map.entry("region_filler_water",  "recipe.region_filler"),
        java.util.Map.entry("region_filler_lava",   "recipe.region_filler"),
        java.util.Map.entry("reforge_soul_mend",        "item.reforge"),
        java.util.Map.entry("weapon_blood_moon_scythe",   "item.original_weapon"),
        java.util.Map.entry("weapon_soul_reaper",         "item.original_weapon"),
        java.util.Map.entry("weapon_abyssal_trident",     "item.original_weapon"),
        java.util.Map.entry("weapon_void_blade",          "item.original_weapon")
        // Boss 系统已取消
        // java.util.Map.entry("boss_infernal_warden",       "item.boss_token"),
        // java.util.Map.entry("boss_abyssal_guardian",      "item.boss_token"),
        // java.util.Map.entry("boss_frost_lich",            "item.boss_token"),
        // java.util.Map.entry("boss_void_dragon_child",     "item.boss_token")
    );

    public enum Category { CRAFTING, REFORGE, WEAPON }

    /** 展示给菜单的配方目录；管理员关掉的自动过滤。*/
    public static List<SpecialRecipeEntry> all() {
        List<SpecialRecipeEntry> out = new ArrayList<>();
        for (SpecialRecipeEntry e : raw()) {
            if (!isEnabled(e)) continue;
            out.add(e);
        }
        return out;
    }

    /** 按分类过滤。*/
    public static List<SpecialRecipeEntry> byCategory(Category cat) {
        List<SpecialRecipeEntry> out = new ArrayList<>();
        for (SpecialRecipeEntry e : raw()) {
            if (!isEnabled(e)) continue;
            if (categoryOf(e) != cat) continue;
            out.add(e);
        }
        return out;
    }

    private static boolean isEnabled(SpecialRecipeEntry e) {
        String cfg = KEY_TO_CONFIG.get(e.key());
        if (cfg != null && !com.tomkestookit.TomkesTookitConfig.enabled(cfg)) return false;
        return true;
    }

    private static Category categoryOf(SpecialRecipeEntry e) {
        if (e.key().startsWith("reforge_")) return Category.REFORGE;
        if (e.key().startsWith("weapon_")) return Category.WEAPON;
        // Boss 系统已取消
        // if (e.key().startsWith("boss_")) return Category.BOSS;
        return Category.CRAFTING;
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

        // 手雷
        list.add(new SpecialRecipeEntry(
            "grenade",
            () -> com.tomkestookit.recipe.ThrownExplosive.buildResult(
                com.tomkestookit.recipe.ThrownExplosive.Type.GRENADE),
            new Item[] {
                Items.TNT,          null, null,
                Items.WATER_BUCKET, null, null,
                null,               null, null
            },
            List.of(
                "§8「拉环、五秒、扔出去。」",
                "§71 TNT + 1 水桶 §8（任意 2 格）",
                "§e右键 §7手持抛出",
                "§c爆炸只造成伤害，不破坏方块",
                "§8无限桶 / 附魔桶 §7不消耗"
            )
        ));

        // 蹦蹦炸弹
        list.add(new SpecialRecipeEntry(
            "bouncy_bomb",
            () -> com.tomkestookit.recipe.ThrownExplosive.buildResult(
                com.tomkestookit.recipe.ThrownExplosive.Type.BOUNCY_BOMB),
            new Item[] {
                Items.TNT,          null, null,
                Items.WATER_BUCKET, null, null,
                null,               null, null
            },
            List.of(
                "§8「弹一弹、再弹一弹，砰。」",
                "§71 大伊万 + 1 水桶 §8（任意 2 格）",
                "§e右键 §7手持抛出，落地 §d弹跳",
                "§c爆炸只造成伤害，不破坏方块",
                "§8无限桶 / 附魔桶 §7不消耗"
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
                "§7投掷命中：永久饱和效果",
                "§8依赖 vanilla 饱和效果"
            )
        ));

        // 无限水桶
        list.add(new SpecialRecipeEntry(
            "infinite_bucket_water",
            () -> com.tomkestookit.infinitebucket.InfiniteBucket.build(
                com.tomkestookit.infinitebucket.InfiniteBucket.BucketType.WATER),
            new Item[] {
                null,            Items.IRON_BLOCK,   null,
                Items.IRON_BLOCK, Items.CAULDRON, Items.WATER_BUCKET,
                null,            Items.IRON_BLOCK,   null
            },
            List.of(
                "§8「有了它，你再也不用跑回水源了。」",
                "§7十字形：3铁块+炼药锅+水桶",
                "§7倒水后自动补满，无限使用",
                "§8桶的位置上下左右任意"
            )
        ));

        // 无限岩浆桶
        list.add(new SpecialRecipeEntry(
            "infinite_bucket_lava",
            () -> com.tomkestookit.infinitebucket.InfiniteBucket.build(
                com.tomkestookit.infinitebucket.InfiniteBucket.BucketType.LAVA),
            new Item[] {
                null,            Items.IRON_BLOCK,   null,
                Items.IRON_BLOCK, Items.CAULDRON, Items.LAVA_BUCKET,
                null,            Items.IRON_BLOCK,   null
            },
            List.of(
                "§8「岩浆也有无限续杯？服务员！」",
                "§7十字形：3铁块+炼药锅+岩浆桶",
                "§7倒岩浆后自动补满，无限使用",
                "§8桶的位置上下左右任意"
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

        // 反重力皮靴
        list.add(new SpecialRecipeEntry(
            "antigrav_boots",
            com.tomkestookit.AntigravBootsHandler::buildDisplayStack,
            new Item[] {
                Items.PHANTOM_MEMBRANE, Items.PHANTOM_MEMBRANE, Items.PHANTOM_MEMBRANE,
                Items.PHANTOM_MEMBRANE, Items.LEATHER_BOOTS,    Items.PHANTOM_MEMBRANE,
                Items.PHANTOM_MEMBRANE, Items.PHANTOM_MEMBRANE, Items.PHANTOM_MEMBRANE
            },
            List.of(
                "§8「把幻翼的灵魂绑在鞋底，大地就够不着你了。」",
                "§7中心皮靴，四周 8 幻翼膜",
                "§7跳跃 II + 速度 ×3 + 摔落减免 75%",
                "§7手工合成保留皮靴原有附魔 / 染色 / 命名",
                "§8自动合成器只接受白板皮靴"
            )
        ));

        // ---- 重铸石 ----
        // 3×3 合成：中心=recipeCenter，四周=recipeSurround；合成后拿去铁砧敲装备
        // 怪物素材系列重铸石现在用 vanilla 物品替代，直接展示 Item 类型即可
        for (ReforgeRegistry.ReforgeDef def : ReforgeRegistry.all()) {
            Item center = def.recipeCenter();
            Item surround = def.recipeSurround();
            list.add(new SpecialRecipeEntry(
                "reforge_" + def.id(),
                () -> ReforgeRegistry.buildStack(def),
                new Item[] {
                    surround, surround, surround,
                    surround, center,   surround,
                    surround, surround, surround
                },
                def.lore()
            ));
        }

        // ---- Boss 令牌 ----
        // Boss 系统已取消
        /*
        for (com.tomkestookit.boss.BossTokenRegistry.BossDef bdef :
                com.tomkestookit.boss.BossTokenRegistry.ALL) {
            List<String> bossLore = new ArrayList<>();
            bossLore.add("§8「击败精英怪，凝聚为召唤令牌。」");
            bossLore.add("§7中心：§f" + bdef.craftingCenter().getName().getString());
            bossLore.add("§7外围：§f" + bdef.craftingSurround().getName().getString() + " ×8");
            bossLore.add("§c§lBoss 令牌 §8- 右键地面 3s 召唤");
            bossLore.add("§8生命 ×" + fmt(bdef.healthMult()) + "  伤害 ×" + fmt(bdef.damageMult()));
            bossLore.add("§8同服上限 3 只 · 距出生点 200 格以上");
            bossLore.add("§7适用于：§f召唤 §c" + bdef.displayName());

            list.add(new SpecialRecipeEntry(
                "boss_" + bdef.bossId(),
                () -> com.tomkestookit.boss.BossTokenRegistry.buildTokenStack(bdef),
                new Item[] {
                    bdef.craftingSurround(), bdef.craftingSurround(), bdef.craftingSurround(),
                    bdef.craftingSurround(), bdef.craftingCenter(),   bdef.craftingSurround(),
                    bdef.craftingSurround(), bdef.craftingSurround(), bdef.craftingSurround()
                },
                bossLore
            ));
        }
        */

        // ---- 原创武器 ----
        // 合成中心现在是 vanilla 物品，不需要 buildMobMatStack
        for (com.tomkestookit.materials.OriginalWeaponRegistry.WeaponDef wd :
                com.tomkestookit.materials.OriginalWeaponRegistry.all()) {
            list.add(new SpecialRecipeEntry(
                "weapon_" + wd.id(),
                () -> com.tomkestookit.materials.OriginalWeaponRegistry.buildStack(wd),
                new Item[] {
                    wd.surroundItem(), wd.surroundItem(), wd.surroundItem(),
                    wd.surroundItem(), wd.centerItem(),   wd.surroundItem(),
                    wd.surroundItem(), wd.surroundItem(), wd.surroundItem()
                },
                wd.lore()
            ));
        }

        return list;
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.valueOf(v);
    }
}
