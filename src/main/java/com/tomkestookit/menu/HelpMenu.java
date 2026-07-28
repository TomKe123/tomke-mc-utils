package com.tomkestookit.menu;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 帮助与说明菜单。9x6 布局：
 *
 *   row 0: [tab 功能 0]  [tab 配方 1]  ...  [返回 8]
 *   row 1-5: 36 个条目槽（9..53）
 *
 * 每个条目一个 ItemStack + name + 说明 lore。数据静态定义在下面 ENTRIES 里。
 */
public final class HelpMenu {

    private static final int BACK_SLOT = 8;
    private static final int FIRST_SLOT = 9;
    private static final Text TITLE = Text.literal("§e§l§n帮助与说明");

    private enum Tab {
        FEATURES("功能玩法", Items.WRITABLE_BOOK),
        RECIPES("特殊配方", Items.CRAFTING_TABLE);

        final String label;
        final Item icon;
        Tab(String label, Item icon) { this.label = label; this.icon = icon; }
    }

    private HelpMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, Tab.FEATURES);
    }

    private static void open(ServerPlayerEntity viewer, Tab tab) {
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        inv.setStack(0, tabButton(Tab.FEATURES, tab == Tab.FEATURES));
        actions.put(0, p -> open(p, Tab.FEATURES));

        inv.setStack(1, tabButton(Tab.RECIPES, tab == Tab.RECIPES));
        actions.put(1, p -> open(p, Tab.RECIPES));

        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, MenuUi::open);

        List<HelpEntry> entries = (tab == Tab.FEATURES) ? FEATURES : RECIPES;
        int max = Math.min(entries.size(), 45); // 9..53
        for (int i = 0; i < max; i++) {
            HelpEntry e = entries.get(i);
            inv.setStack(FIRST_SLOT + i, e.build());
            // 条目本身不响应点击
        }

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    private static ItemStack tabButton(Tab tab, boolean selected) {
        ItemStack stack = MenuButtons.button(tab.icon, "§f§l" + tab.label,
            List.of(selected ? "§a● 当前" : "§8○ 点击切换"));
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    // ------------------------------------------------------------------ data

    private record HelpEntry(Item icon, String name, List<String> lore) {
        ItemStack build() {
            return MenuButtons.button(icon, name, lore);
        }
    }

    private static final List<HelpEntry> FEATURES = List.of(
        new HelpEntry(Items.CLOCK, "§e§l主菜单",
            List.of("§7Shift + F §8/ §f/cd §7打开",
                "§7右键“菜单时钟”也可打开")),
        new HelpEntry(Items.BONE, "§e§l返回死亡点",
            List.of("§7死亡后聊天弹链接",
                "§7命令：§f/back",
                "§7菜单点击“返回死亡地点”")),
        new HelpEntry(Items.RED_BED, "§e§l返回出生点",
            List.of("§7菜单点击“返回出生点”",
                "§7无重生点时到世界出生点")),
        new HelpEntry(Items.CHEST, "§e§l新手 Kit",
            List.of("§7首次登录聊天领取",
                "§7命令：§f/tomkestookit give",
                "§7菜单可反复领取")),
        new HelpEntry(Items.FEATHER, "§e§l飞行",
            List.of("§7消耗经验飞行",
                "§7每分钟扣当前等级点 XP",
                "§7命令：§f/fly §7 或菜单切换")),
        new HelpEntry(Items.WOODEN_AXE, "§e§l连锁采集",
            List.of("§7Shift + 左键 触发连锁",
                "§7  §f树/叶/矿§7 最多 128",
                "§7  §f白名单方块§7 单次≤32",
                "§7命令 §f/veinmine§7 或菜单切换",
                "§7大丛林 / 大云杉 / 大黑橡 补种 4 苗")),
        new HelpEntry(Items.PIG_SPAWN_EGG, "§e§l实体抓取",
            List.of("§7Shift + 空手右键 生物",
                "§7  → 收进对应刷怪蛋",
                "§7保留 NBT（交易/驯服/命名等）",
                "§7成年/幼年状态原样保留")),
        new HelpEntry(Items.LEATHER_BOOTS, "§e§l反重力皮靴",
            List.of("§7新手 Kit 附带",
                "§7100% 跳跃加成，摔落 -75%",
                "§7铁砧转移属性到其他鞋子",
                "§7  皮靴 + 反重力皮靴 = 反重力靴")),
        new HelpEntry(Items.MAP, "§e§l地标",
            List.of("§7命令 §f/landmark add \"<名字>\"",
                "§7右键主菜单地标按钮 快捷添加",
                "§7列表左键传送 / 右键详细页",
                "§7详细页可改名 / 换图标 / 删除")),
        new HelpEntry(Items.PLAYER_HEAD, "§e§l在线玩家",
            List.of("§7列出所有在线玩家",
                "§7左键传送到该玩家",
                "§7按距离排序，跨维度置底")),
        new HelpEntry(Items.WATER_BUCKET, "§e§l附魔水/岩浆桶",
            List.of("§7左右键选点区域",
                "§7聊天确认后填充空气",
                "§8一次性物品，用完消失")),
        new HelpEntry(Items.DIAMOND_AXE, "§e§l区域清除斧",
            List.of("§7左右键选点，聊天确认",
                "§7下界合金 tier 可清基岩",
                "§7区域内不可挖的方块也会被清")),
        new HelpEntry(Items.CAULDRON, "§e§l药水锅",
            List.of("§7右键锅注药水，一瓶 1 层",
                "§7同锅可注不同药水，效果合并",
                "§7右键箭 → 药水箭；玻璃瓶 → 取药水",
                "§8锅顶悬浮字显示效果/时长/层数")),
        new HelpEntry(Items.OAK_SIGN, "§e§l命令告示牌",
            List.of("§7主手拿告示牌 + Shift + F",
                "§7  第 1 步 聊天输入命令",
                "§7  第 2 步 聊天输入自定义文字",
                "§7放下后自动蜡封，右键执行命令",
                "§7第 1 行 §f[命令告示牌]",
                "§7第 2 行 玩家自定义文字",
                "§8允许命令：landmark / back / cd / fly",
                "§8         tp / seed / locate",
                "§8         carpet / tick / player 等全套")),
        new HelpEntry(Items.LODESTONE, "§e§l物流终端",
            List.of("§7成对木桶，两端物品实时同步",
                "§7需两端都在加载区块",
                "§7上方盔甲架显示状态",
                "§8合成见“特殊配方”页")),
        new HelpEntry(Items.BREAD, "§e§l村民折扣共享",
            List.of("§7全服共享正面 gossip",
                "§7一位玩家治愈僵尸村民",
                "§7  → 全服玩家享同等折扣")),
        new HelpEntry(Items.IRON_INGOT, "§e§l铁砧上限提升",
            List.of("§7“过于昂贵”阈值提升到 §f100")),
        new HelpEntry(Items.ECHO_SHARD, "§e§l监守者战利品",
            List.of("§7击杀监守者可掉落：",
                "§7必掉 §f回响碎片 §71-3、§f幽匿催发体 §71-2",
                "§735% §7音乐类：唱片残片 / cat / 13 / otherside",
                "§720% §7迅捷潜行附魔书 I-III",
                "§712% §7监守 / 幽静 盔甲纹饰",
                "§715% §7附魔金苹果 §71-2"))
    );

    private static final List<HelpEntry> RECIPES = List.of(
        new HelpEntry(Items.GOLDEN_APPLE, "§e§l附魔金苹果",
            List.of("§f3×3§7 全金块 + 中央苹果",
                "§7  §8G G G",
                "§7  §8G A G",
                "§7  §8G G G",
                "§8vanilla 已移除，这里恢复",
                "§7吃下可获得成就 §f君临天下")),
        new HelpEntry(Items.SPLASH_POTION, "§e§l面包精华（喷溅药水）",
            List.of("§f3×3§7 全 9 个面包",
                "§7永久滋养效果",
                "§8需装载 Farmer's Delight")),
        new HelpEntry(Items.EXPERIENCE_BOTTLE, "§e§l附魔之瓶",
            List.of("§f切石§7：§f青金石块§7 → 经验瓶",
                "§8低价刷经验瓶")),
        new HelpEntry(Items.DEEPSLATE, "§e§l石头 → 深板岩",
            List.of("§f切石§7：石头 → 深板岩",
                "§7所有矿石变体皆可切",
                "§8圆石 / 各种矿石 → 深板岩版")),
        new HelpEntry(Items.WATER_BUCKET, "§e§l附魔水桶",
            List.of("§f3×3§7 全 9 个水桶",
                "§7区域批量填水，一次性",
                "§7左右键选点，聊天确认")),
        new HelpEntry(Items.LAVA_BUCKET, "§e§l附魔岩浆桶",
            List.of("§f3×3§7 全 9 个岩浆桶",
                "§7区域批量填岩浆，一次性")),
        new HelpEntry(Items.NETHERITE_AXE, "§e§l区域清除斧",
            List.of("§f3×3§7 中间铁镐，四周同种材质：",
                "§7  木板 → 木斧（1 次）",
                "§7  圆石 → 石斧（1 次）",
                "§7  铁锭 → 铁斧（3 次）",
                "§7  金锭 → 金斧（3 次）",
                "§7  钻石 → 钻石斧（5 次）",
                "§7  远古残骸 → 下界合金斧（10 次）",
                "§8可挖对应级别方块；下界合金可清基岩")),
        new HelpEntry(Items.BARREL, "§e§l物流终端",
            List.of("§f3×3§7 中间磁石，四周铁块",
                "§7  §8I I I",
                "§7  §8I L I",
                "§7  §8I I I",
                "§7一次得到 2 个成对木桶",
                "§8两端 chunk 都加载时实时同步")),
        new HelpEntry(Items.CLOCK, "§e§l菜单时钟",
            List.of("§7新手 Kit 附带",
                "§7右键打开主菜单",
                "§8也可通过 §f/cd §8命令")),
        new HelpEntry(Items.DIAMOND, "§e§l钻石回收（自动压缩）",
            List.of("§7工作盆 + 压局机组合",
                "§7放入 1 个钻石制品即压缩为钻石",
                "§7支持：块 / 头盔 / 胸甲 / 护腿 / 靴",
                "§7支持：剑 / 镐 / 斧 / 锹 / 锄",
                "§7支持：附魔台 / 唱片机",
                "§8需装载 Create"))
    );
}
