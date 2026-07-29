package com.tomkestookit;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员面板可开关的功能目录。
 *
 * key 与 {@link TomkesTookitConfig#enabled(String)} 里各特性早退判断使用的字符串一一对应；
 * zhName / icon / desc 只给菜单展示用。
 *
 * group 决定它出现在哪个分组按钮下（{@link Group#label}）。
 *
 * 目录本身是硬编码的常量列表。config 文件里只存"哪些 key 被关了"。
 */
public final class AdminFeature {

    public enum Group {
        RECIPE("§d§l特殊配方", Items.CRAFTING_TABLE, "魔改 3×3 合成路径的开关"),
        ITEM("§b§l物品交互", Items.STICK, "右键 / 左键 / 使用物品的行为"),
        COMMAND("§e§l命令", Items.COMMAND_BLOCK_MINECART, "/命令族与聊天前缀"),
        WORLD("§a§l生物 &amp; 世界", Items.WHEAT, "村民、假人、AFK、传送保护等"),
        UI("§9§l界面", Items.OAK_SIGN, "侧栏、tab 血量条"),
        MISC("§7§l杂项", Items.REDSTONE, "铁砧、附魔、入服提示等");

        public final String label;
        public final Item icon;
        public final String desc;
        Group(String label, Item icon, String desc) { this.label = label; this.icon = icon; this.desc = desc; }
    }

    public final String key;
    public final String zhName;
    public final Item icon;
    public final Group group;
    public final String desc;

    private AdminFeature(String key, String zhName, Item icon, Group group, String desc) {
        this.key = key;
        this.zhName = zhName;
        this.icon = icon;
        this.group = group;
        this.desc = desc;
    }

    private static final List<AdminFeature> ALL = new ArrayList<>();

    private static void add(String key, String zhName, Item icon, Group group, String desc) {
        ALL.add(new AdminFeature(key, zhName, icon, group, desc));
    }

    static {
        // ---------- 配方 ----------
        add("recipe.greed_potion",       "贪婪之瓶",         Items.EMERALD_BLOCK, Group.RECIPE, "8 绿宝石块 + 1 空瓶 → 100 分钟 BAD_OMEN");
        add("recipe.sugar_tnt",          "大伊万",           Items.TNT,           Group.RECIPE, "8 糖 + 中心 TNT → 4× 威力附魔 TNT");
        add("recipe.saturation_potion",  "面包精华",         Items.BREAD,         Group.RECIPE, "9 面包 → 永久滋养喷溅药水");
        add("recipe.region_clearer",     "区域清除斧配方",   Items.IRON_PICKAXE,  Group.RECIPE, "中心铁镐 + 8 材质 → 对应 tier 清除斧");
        add("recipe.region_filler",      "附魔水/岩浆桶配方",Items.WATER_BUCKET,  Group.RECIPE, "9 水桶 / 9 岩浆桶 → 附魔桶");
        add("recipe.logistics",          "物流终端配方",     Items.LODESTONE,     Group.RECIPE, "8 铁块 + 中心磁石 → 一对物流桶");
        add("recipe.auto_crafter",       "自动合成器配方",   Items.CRAFTING_TABLE,Group.RECIPE, "中心工作台 + 4 铁 + 4 红石 → 自动合成器");

        // ---------- 物品交互 ----------
        add("item.antigrav_boots",       "反重力靴",         Items.LEATHER_BOOTS, Group.ITEM, "跳跃 II、摔落减伤 75%、速度 ×3、铁砧转移");
        add("item.sugar_tnt_boom",       "大伊万爆炸增强",   Items.TNT,           Group.ITEM, "关闭后已合成的大伊万走 vanilla 威力");
        add("item.region_clearer_use",   "区域清除斧使用",   Items.NETHERITE_AXE, Group.ITEM, "左右键选点、聊天确认后分批清除");
        add("item.region_filler_use",    "附魔桶使用",       Items.LAVA_BUCKET,   Group.ITEM, "9 格附魔桶一次性填空气");
        add("item.logistics_ops",        "物流终端运行",     Items.BARREL,        Group.ITEM, "同步库存、状态标签、破坏保护");
        add("item.auto_crafter_ops",     "自动合成器运行",   Items.CRAFTING_TABLE,Group.ITEM, "tick 合成、开 UI、放置、破坏、漏斗对接");
        add("item.potion_cauldron",      "药水锅",           Items.CAULDRON,      Group.ITEM, "空/水锅装药水、浸箭、复用瓶子、顶上飘字");
        add("item.shulker_handheld",     "潜影盒手持打开",   Items.SHULKER_BOX,   Group.ITEM, "shift+右键手持潜影盒开 UI");
        add("item.portable_ender_chest", "随身末影箱",       Items.ENDER_CHEST,   Group.ITEM, "/echest 或菜单按钮打开末影箱");
        add("item.portable_workbench",   "随身工作台",       Items.CARTOGRAPHY_TABLE, Group.ITEM, "/crafting 或菜单按钮打开工作台");
        add("item.sign_command",         "告示牌绑命令",     Items.OAK_SIGN,      Group.ITEM, "shift+F 手持告示牌 → 聊天输入命令 → 放下即封");
        add("item.veinmine",             "连锁采集",         Items.DIAMOND_PICKAXE,Group.ITEM,"shift+挖 → BFS 连锁，补树苗");
        add("item.entity_capture",       "生物收纳",         Items.EGG,           Group.ITEM, "shift+右键生物装进对应刷怪蛋");

        // ---------- 命令 ----------
        add("cmd.fly",                   "/fly 飞行",        Items.FEATHER,       Group.COMMAND, "消耗经验换飞行时间");
        add("cmd.back",                  "/back 死亡返回",   Items.BONE,          Group.COMMAND, "记死亡位置 + 传送 + 5 秒无敌");
        add("cmd.landmark",              "/landmark 地标",   Items.COMPASS,       Group.COMMAND, "add/remove/rename/icon/tp/list");
        add("cmd.veinmine_toggle",       "/veinmine 命令",   Items.NAME_TAG,      Group.COMMAND, "toggle 与白名单管理");
        add("cmd.gamemode_shortcut",     "!c / !s 聊天前缀", Items.PAPER,         Group.COMMAND, "旁观 ↔ 生存 快捷切换");
        add("cmd.menu",                  "/cd 与 shift+F",   Items.CLOCK,         Group.COMMAND, "菜单入口");

        // ---------- 生物 &amp; 世界 ----------
        add("world.bot_anchor",          "假人相关",         Items.ARMOR_STAND,   Group.WORLD, "锚点、无击退、免伤真玩家、tab 前缀、交互菜单");
        add("world.villager_gossip_share","村民 gossip 共享",Items.EMERALD,       Group.WORLD, "正面折扣全服共享");
        add("world.villager_gossip_broadcast","治愈/英雄广播",Items.GOLDEN_APPLE, Group.WORLD, "治愈瞬间 / Hero 效果即时广播 gossip");
        add("world.afk",                 "AFK 显示与免幻翼", Items.CLOCK,         Group.WORLD, "60 秒不动加 [AFK]，幻翼伤害归零");
        add("world.sleep_percentage",    "1 人睡觉跳夜",     Items.RED_BED,       Group.WORLD, "启服写 gamerule；关掉后需要 /gamerule 手动改回");
        add("world.spawn_protection",    "传送/重生无敌",    Items.SHIELD,        Group.WORLD, "5 秒 RESISTANCE IV + 防火");
        add("world.per_player_spawn",    "刷怪每人独立",     Items.ZOMBIE_HEAD,   Group.WORLD, "每个玩家享受独立 vanilla 刷怪上限，多人服体验≈单机");

        // ---------- UI ----------
        add("ui.sidebar",                "侧栏 TPS/MSPT",    Items.OAK_SIGN,      Group.UI, "每秒刷新");
        add("ui.tab_health",             "tab 血量红心",     Items.APPLE,         Group.UI, "关闭后需重登才彻底消失");

        // ---------- 杂项 ----------
        add("misc.anvil_cost_cap",       "铁砧上限 100",     Items.ANVIL,         Group.MISC, "把 vanilla 40 级 \"过于昂贵\" 提到 100");
        add("misc.infinity_mending",     "无限 + 经验修补",  Items.BOW,           Group.MISC, "允许弓同时上 Infinity 与 Mending");
        add("misc.kit_on_join",          "入服礼包提示",     Items.WRITABLE_BOOK, Group.MISC, "关闭后新玩家不会自动收到 [Kit] 消息，仍可用 /tomkestookit give");
    }

    public static List<AdminFeature> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<AdminFeature> byGroup(Group group) {
        List<AdminFeature> out = new ArrayList<>();
        for (AdminFeature f : ALL) if (f.group == group) out.add(f);
        return out;
    }

    /** 按 group 顺序统计每组多少条，用于主 panel 展示。*/
    public static Map<Group, Integer> groupCounts() {
        Map<Group, Integer> out = new LinkedHashMap<>();
        for (Group g : Group.values()) out.put(g, 0);
        for (AdminFeature f : ALL) out.merge(f.group, 1, Integer::sum);
        return out;
    }
}
