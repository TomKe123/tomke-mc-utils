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
        java.util.Map.entry("saturation_potion",    "recipe.saturation_potion"),
        java.util.Map.entry("infinite_bucket_water","recipe.infinite_bucket"),
        java.util.Map.entry("infinite_bucket_lava", "recipe.infinite_bucket"),
        java.util.Map.entry("auto_crafter",         "recipe.auto_crafter"),
        java.util.Map.entry("logistics",            "recipe.logistics"),
        java.util.Map.entry("region_clearer_iron",  "recipe.region_clearer"),
        java.util.Map.entry("region_filler_water",  "recipe.region_filler"),
        java.util.Map.entry("region_filler_lava",   "recipe.region_filler"),
        java.util.Map.entry("reforge_fury_shard",       "item.reforge"),
        java.util.Map.entry("reforge_berserker_core",   "item.reforge"),
        java.util.Map.entry("reforge_swift_fang",       "item.reforge"),
        java.util.Map.entry("reforge_iron_bark",        "item.reforge"),
        java.util.Map.entry("reforge_diamond_shell",    "item.reforge"),
        java.util.Map.entry("reforge_featherweight_gem","item.reforge"),
        java.util.Map.entry("reforge_titan_plate",      "item.reforge"),
        java.util.Map.entry("reforge_lucky_clover",     "item.reforge"),
        java.util.Map.entry("reforge_mending_moss",     "item.reforge"),
        java.util.Map.entry("reforge_knockback_crystal","item.reforge"),
        java.util.Map.entry("reforge_skyforge_rune",    "item.reforge"),
        java.util.Map.entry("reforge_soul_mend",        "item.reforge"),
        java.util.Map.entry("reforge_antigrav_boots",  "item.reforge"),
        java.util.Map.entry("reforge_inferno_crystal",    "item.reforge"),
        java.util.Map.entry("reforge_wardwood_bark",      "item.reforge"),
        java.util.Map.entry("reforge_bedrock_bulwark",    "item.reforge"),
        java.util.Map.entry("reforge_dragon_scale_plate", "item.reforge"),
        java.util.Map.entry("reforge_titan_heart",        "item.reforge"),
        java.util.Map.entry("reforge_cosmic_essence",     "item.reforge"),
        java.util.Map.entry("reforge_abyssal_might",      "item.reforge"),
        java.util.Map.entry("reforge_soul_edge",          "item.reforge"),
        java.util.Map.entry("reforge_blood_moon_fury",    "item.reforge"),
        java.util.Map.entry("reforge_void_pierce",        "item.reforge"),
        java.util.Map.entry("reforge_stardust_weight",    "item.reforge"),
        java.util.Map.entry("weapon_blood_moon_scythe",   "item.original_weapon"),
        java.util.Map.entry("weapon_soul_reaper",         "item.original_weapon"),
        java.util.Map.entry("weapon_abyssal_trident",     "item.original_weapon"),
        java.util.Map.entry("weapon_void_blade",          "item.original_weapon"),
        java.util.Map.entry("boss_infernal_warden",       "item.boss_token"),
        java.util.Map.entry("boss_abyssal_guardian",      "item.boss_token"),
        java.util.Map.entry("boss_frost_lich",            "item.boss_token"),
        java.util.Map.entry("boss_void_dragon_child",     "item.boss_token")
    );

    public enum Category { CRAFTING, TRINKET, REFORGE, WEAPON, BOSS }

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
        if (e.key().startsWith("trinket_")
            && !com.tomkestookit.TomkesTookitConfig.enabled("recipe.trinket")) return false;
        return true;
    }

    private static Category categoryOf(SpecialRecipeEntry e) {
        if (e.key().startsWith("trinket_")) return Category.TRINKET;
        if (e.key().startsWith("reforge_")) return Category.REFORGE;
        if (e.key().startsWith("weapon_")) return Category.WEAPON;
        if (e.key().startsWith("boss_")) return Category.BOSS;
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

        // ---- 重铸石 ----
        // 3×3 合成：中心=recipeCenter，四周=recipeSurround；合成后拿去铁砧敲装备
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
        // 材料是 MobMaterialRegistry 的 NBT 素材，展示 grid 用素材对应的 vanilla icon
        for (com.tomkestookit.boss.BossTokenRegistry.BossDef bdef :
                com.tomkestookit.boss.BossTokenRegistry.ALL) {
            com.tomkestookit.materials.MobMaterialRegistry.MaterialDef centerMat =
                com.tomkestookit.materials.MobMaterialRegistry.byId(bdef.craftingCenter());
            com.tomkestookit.materials.MobMaterialRegistry.MaterialDef surroundMat =
                com.tomkestookit.materials.MobMaterialRegistry.byId(bdef.craftingSurround());
            if (centerMat == null || surroundMat == null) continue;

            Item bCenter = centerMat.icon();
            Item bSurround = surroundMat.icon();

            List<String> bossLore = new ArrayList<>();
            bossLore.add("§8「击败精英怪，凝聚为召唤令牌。」");
            bossLore.add("§7中心：§f" + centerMat.rarity() + centerMat.name());
            bossLore.add("§7外围：§f" + surroundMat.rarity() + surroundMat.name() + " ×8");
            bossLore.add("§c§lBoss 令牌 §8- 右键地面 3s 召唤");
            bossLore.add("§8生命 ×" + fmt(bdef.healthMult()) + "  伤害 ×" + fmt(bdef.damageMult()));
            bossLore.add("§8同服上限 3 只 · 距出生点 200 格以上");
            bossLore.add("§7适用于：§f召唤 §c" + bdef.displayName());

            list.add(new SpecialRecipeEntry(
                "boss_" + bdef.bossId(),
                () -> com.tomkestookit.boss.BossTokenRegistry.buildTokenStack(bdef),
                new Item[] {
                    bSurround, bSurround, bSurround,
                    bSurround, bCenter,   bSurround,
                    bSurround, bSurround, bSurround
                },
                bossLore
            ));
        }

        // ---- 原创武器 ----
        for (com.tomkestookit.materials.OriginalWeaponRegistry.WeaponDef wd :
                com.tomkestookit.materials.OriginalWeaponRegistry.all()) {
            Item wCenter = wd.centerItem();
            Item wSurround = wd.surroundItem();
            list.add(new SpecialRecipeEntry(
                "weapon_" + wd.id(),
                () -> com.tomkestookit.materials.OriginalWeaponRegistry.buildStack(wd),
                new Item[] {
                    wSurround, wSurround, wSurround,
                    wSurround, wCenter,   wSurround,
                    wSurround, wSurround, wSurround
                },
                wd.lore()
            ));
        }

        // ---- 饰品 ----
        // 升级链的展示顺序：把每条链（base → advanced1 → advanced2 …）合并到一起，
        // 而不是"所有基础在前，进阶在后"的默认注册顺序。
        for (com.tomkestookit.trinket.TrinketRegistry.TrinketDef def : orderTrinketsByChain()) {
            Item s = def.surround();
            Item c = def.center();
            String recipeKey = "trinket_" + def.id();
            if (def.prereqTrinketId() != null) {
                // 进阶饰品：中心用前置饰品的 icon 物品做展示 + 注册中心点击跳转
                com.tomkestookit.trinket.TrinketRegistry.TrinketDef prereq =
                    com.tomkestookit.trinket.TrinketRegistry.byId(def.prereqTrinketId());
                Item centerIcon = prereq != null ? prereq.icon() : Items.BARRIER;
                list.add(new SpecialRecipeEntry(
                    recipeKey,
                    () -> com.tomkestookit.trinket.TrinketRegistry.buildStack(def),
                    new Item[] { s, s, s, s, centerIcon, s, s, s, s },
                    def.lore()
                ));
                // 中心 slot（3×3 index=4）点击跳转到前置饰品配方
                SpecialRecipeEntry.registerLink(recipeKey, 4, "trinket_" + def.prereqTrinketId());
            } else {
                list.add(new SpecialRecipeEntry(
                    recipeKey,
                    () -> com.tomkestookit.trinket.TrinketRegistry.buildStack(def),
                    new Item[] { s, s, s, s, c, s, s, s, s },
                    def.lore()
                ));
            }
        }

        return list;
    }

    /**
     * 把饰品按升级链排序：每条链的根放前面，后续递进紧随其后。
     * 没有升级链的独立饰品按注册顺序排。
     *
     * 例：iron_arm → blood_fury 紧邻展示，haste_charm_1 → 2 → 3 紧邻展示。
     */
    private static List<com.tomkestookit.trinket.TrinketRegistry.TrinketDef> orderTrinketsByChain() {
        List<com.tomkestookit.trinket.TrinketRegistry.TrinketDef> all =
            com.tomkestookit.trinket.TrinketRegistry.all();

        // 构建 id → def 映射
        java.util.Map<String, com.tomkestookit.trinket.TrinketRegistry.TrinketDef> byId = new java.util.LinkedHashMap<>();
        for (var def : all) byId.put(def.id(), def);

        // 找出被当作前置的 id 集合
        java.util.Set<String> hasChild = new java.util.HashSet<>();
        for (var def : all) {
            if (def.prereqTrinketId() != null) hasChild.add(def.prereqTrinketId());
        }

        // 找链的根：没有 prereq 的 def
        // 或者，prereq 自身也没有 prereq 的 def（链根）
        // 对于 "独立不成链" 的也算一个单节点链

        // 构建从 id → 其后继（prereqTrinketId == id 的 def）
        java.util.Map<String, com.tomkestookit.trinket.TrinketRegistry.TrinketDef> childOf = new java.util.LinkedHashMap<>();
        for (var def : all) {
            if (def.prereqTrinketId() != null) {
                childOf.put(def.prereqTrinketId(), def);
            }
        }

        java.util.Set<String> visited = new java.util.HashSet<>();
        List<com.tomkestookit.trinket.TrinketRegistry.TrinketDef> result = new ArrayList<>();

        for (var def : all) {
            if (visited.contains(def.id())) continue;
            // 只从链根开始展开
            if (def.prereqTrinketId() != null) continue;

            // 展开链
            var current = def;
            while (current != null) {
                if (!visited.add(current.id())) break;
                result.add(current);
                current = childOf.get(current.id());
            }
        }

        // 保险：如果有遗漏的（不应该），追加
        for (var def : all) {
            if (visited.add(def.id())) result.add(def);
        }

        return result;
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.valueOf(v);
    }
}
