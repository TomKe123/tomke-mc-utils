package com.tomkestookit.menu;

import com.tomkestookit.FlyCommand;
import com.tomkestookit.KitCommand;
import com.tomkestookit.KitTracker;
import com.tomkestookit.landmark.LandmarkStore;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 主菜单构造与打开入口。
 *
 * 布局 9x6：
 *
 *   槽位:  0  1  2  3  4  5  6  7  8
 *          ■  ■  ■  ■  ✧  ■  ■  ■  ■     row 0  4=玩家信息卡
 *          ■  ■  ■  ■  ■  ■  ■  ■  ■     row 1  装饰
 *          9  ■ 11  ■ 13  ■ 15  ■ 17     row 2  9/11/13/15/17=动作类（5）
 *          ■  ■  ■  ■  ■  ■  ■  ■  ■     row 3  装饰
 *          ■ 37  ■ 39  ■ 41  ■ 43  ■     row 4  37/39/41/43=开关/浏览类（4）
 *          ■  ■  ■  ■  ■  ■  ■  ■ 53     row 5  53=关闭
 *
 * 按钮:
 *   4  - 玩家信息卡(玩家头颅) - 顶部中央（无 action）
 *   9  - 返回死亡地点(骨头, 无记录时灰显)
 *   11 - 出生点(床)
 *   13 - 领取 Kit (箱子)
 *   15 - 在线玩家(头颅)
 *   17 - 服务器地标(地图)
 *   37 - 飞行切换(羽毛, 开启时发光)
 *   39 - 连锁采集(木斧, 开启时发光)
 *   41 - 实体抓取(刷怪蛋, 开启时发光)
 *   43 - 本包配方(工作台)
 *   53 - 关闭(屏障)
 *
 * 空槽全部用灰色玻璃板铺满，避免"透视格"感。
 */
public final class MenuUi {

    private MenuUi() {}

    public static void open(ServerPlayerEntity player) {
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        Map<Integer, Consumer<ServerPlayerEntity>> rightActions = new HashMap<>();

        // 顶部中央：玩家信息卡（不响应点击，仅展示）
        inv.setStack(4, playerInfoCard(player));

        // 动作行 (row 2)：5 个横向均分按钮
        inv.setStack(9, backButton(player));
        actions.put(9, MenuUi::tpDeath);

        inv.setStack(11, spawnButton(player));
        actions.put(11, MenuUi::tpSpawn);

        inv.setStack(13, kitButton(player));
        actions.put(13, MenuUi::giveKit);

        inv.setStack(15, playerListButton(player));
        actions.put(15, PlayerListMenu::open);

        inv.setStack(17, landmarkButton());
        actions.put(17, LandmarkMenu::open);
        rightActions.put(17, p -> LandmarkMenu.promptAdd(p, "", 0));

        // 开关/浏览行 (row 4)：4 个横向均分按钮
        inv.setStack(37, flyButton(player));
        actions.put(37, MenuUi::toggleFly);

        inv.setStack(39, veinMineButton(player));
        actions.put(39, MenuUi::toggleVeinMine);
        rightActions.put(39, VeinMineMenu::open);

        inv.setStack(41, entityCaptureButton(player));
        actions.put(41, MenuUi::toggleEntityCapture);

        inv.setStack(43, recipeButton(player));
        actions.put(43, RecipeMenu::open);

        // 帮助 / 说明按钮
        inv.setStack(45, helpButton());
        actions.put(45, HelpMenu::open);

        // 魔改配方直达
        inv.setStack(46, specialRecipeButton());
        actions.put(46, RecipeMenu::openSpecial);

        // 垃圾桶
        inv.setStack(47, trashCanButton());
        actions.put(47, com.tomkestookit.menu.TrashCanMenu::open);

        // 随身末影箱（受 item.portable_ender_chest 控制，关掉时按钮仍显示但点击后收到红色提示）
        inv.setStack(48, enderChestButton());
        actions.put(48, p -> com.tomkestookit.PortableWorkstations.openEnderChest(p));

        // 随身工作台
        inv.setStack(50, portableWorkbenchButton());
        actions.put(50, p -> com.tomkestookit.PortableWorkstations.openCraftingTable(p));

        // 发起清理投票（全服掉落物/经验球，5 分钟冷却）
        inv.setStack(51, voteCleanupButton());
        actions.put(51, MenuUi::startCleanupVote);

        // 管理员面板入口（仅 OP 可见；非 OP 该槽会被 fillBackground 用玻璃板遮住）
        if (player.hasPermissionLevel(2)) {
            inv.setStack(52, MenuButtons.button(Items.COMMAND_BLOCK,
                "§c§l管理员面板",
                List.of("§7逐项开关 mod 功能",
                    "§7自定义新手礼包",
                    "",
                    "§eOP only")));
            actions.put(52, AdminPanelMenu::open);
        }

        // 关闭按钮
        inv.setStack(53, MenuButtons.closeButton());
        actions.put(53, ServerPlayerEntity::closeHandledScreen);

        // 剩下的空位铺满玻璃板
        MenuButtons.fillBackground(inv);

        // 标题带玩家名
        Text title = Text.literal("§b§l§n" + player.getGameProfile().getName() + " 的菜单");
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions, rightActions, null),
            title
        ));
    }

    // ------------------------------------------------------------------ buttons

    private static ItemStack specialRecipeButton() {
        int count = com.tomkestookit.menu.SpecialRecipeCatalog.all().size();
        return MenuButtons.button(Items.NETHER_STAR, "§d§l魔改配方",
            List.of(
                "§7独门 3×3 合成，共 §f" + count + " §7条",
                "§8贪婪之瓶 · 大伊万 · 面包精华",
                "§8自动合成器 · 物流终端",
                "§8区域清除斧 · 附魔水/岩浆桶",
                "",
                "§a▶ 点击查看做法"));
    }

    private static ItemStack helpButton() {
        return MenuButtons.button(Items.KNOWLEDGE_BOOK, "§e§l帮助与说明",
            List.of(
                "§7点击查看：",
                "§7· 所有功能玩法",
                "§7· 特殊合成配方",
                "§7· 快捷键与命令"
            ));
    }

    private static ItemStack enderChestButton() {
        return MenuButtons.button(Items.ENDER_CHEST, "§5§l随身末影箱",
            List.of("§7在任何地方打开你的末影箱",
                "§8命令：§f/echest"));
    }

    private static ItemStack voteCleanupButton() {
        return MenuButtons.button(Items.BLAZE_POWDER, "§6§l发起清理投票",
            List.of("§7全服广播投票，20 秒投票期",
                "§7同意 ≥ 50% 通过 → 清理所有维度",
                "§8· 掉落物 + 经验球",
                "§8· 与定时/卡顿清理独立",
                "§8· 5 分钟冷却",
                "",
                "§a▶ 点击发起"));
    }

    private static void startCleanupVote(ServerPlayerEntity player) {
        Text result = com.tomkestookit.DroppedItemCleanup.startPlayerVote(player);
        player.closeHandledScreen();
        player.sendMessage(result, false);
    }

    private static ItemStack trashCanButton() {
        return MenuButtons.button(Items.LAVA_BUCKET, "§c§l垃圾桶",
            List.of("§7全服共享 · 每 10 分钟清空",
                "§7自动/卡顿/区域清除的物品",
                "§7会汇总到这里（角标为过量数）",
                "§7点条目可选择提取数量",
                "§8* 全部 · = 填满 · 空 取消 · 支持 3*64",
                "",
                "§a▶ 点击打开"));
    }

    private static ItemStack portableWorkbenchButton() {
        return MenuButtons.button(Items.CARTOGRAPHY_TABLE, "§6§l随身工作台",
            List.of("§7在任何地方打开 3×3 合成",
                "§8命令：§f/crafting"));
    }

    private static ItemStack playerInfoCard(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        String dim = dimShort(player.getWorld().getRegistryKey().getValue().toString());
        List<String> lore = List.of(
            "§7等级 §f" + player.experienceLevel + " §8· §7经验 §f" + player.totalExperience,
            "§7生命 §c" + (int) Math.ceil(player.getHealth()) + "§7/§c" + (int) Math.ceil(player.getMaxHealth()),
            "§7位置 §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " §7@ §f" + dim,
            "",
            "§8点击其他槽位使用菜单"
        );
        return MenuButtons.skull(player.getGameProfile(),
            "§e§l" + player.getGameProfile().getName(), lore);
    }

    private static ItemStack backButton(ServerPlayerEntity player) {
        com.tomkestookit.DeathLocationTracker.DeathLocation loc =
            com.tomkestookit.DeathLocationTracker.get(player.getUuid());
        List<String> lore;
        if (loc == null) {
            lore = List.of(
                "§7暂无死亡记录",
                "§7死亡后会记录坐标",
                "",
                "§8点击无效"
            );
        } else {
            String dim = dimShort(loc.dimension);
            lore = List.of(
                "§7死亡坐标 §f" + (int) Math.floor(loc.x) + " " + (int) Math.floor(loc.y) + " " + (int) Math.floor(loc.z),
                "§7维度 §f" + dim,
                "",
                "§a▶ 点击返回"
            );
        }
        return MenuButtons.button(Items.BONE, "§f§l返回死亡地点", lore);
    }

    private static ItemStack spawnButton(ServerPlayerEntity player) {
        BlockPos spawn = player.getSpawnPointPosition();
        List<String> lore;
        if (spawn != null) {
            String dim = dimShort(player.getSpawnPointDimension().getValue().toString());
            lore = List.of(
                "§7重生点 §f" + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ(),
                "§7维度 §f" + dim,
                "",
                "§a▶ 点击传送"
            );
        } else {
            lore = List.of(
                "§7未设置重生点",
                "§7将前往世界出生点",
                "",
                "§a▶ 点击传送"
            );
        }
        return MenuButtons.button(Items.RED_BED, "§f§l返回出生点", lore);
    }

    private static ItemStack flyButton(ServerPlayerEntity player) {
        boolean enabled = FlyCommand.isEnabled(player.getUuid());
        String status = enabled ? "§a已开启" : "§c已关闭";
        List<String> lore = List.of(
            "§7当前 " + status,
            "§7等级 §f" + player.experienceLevel + " §8(消耗经验飞行)",
            "",
            "§a▶ 点击切换"
        );
        ItemStack stack = MenuButtons.button(Items.FEATHER, "§f§l飞行开关", lore);
        if (enabled) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack kitButton(ServerPlayerEntity player) {
        long left = KitTracker.remainingCooldown(player.getUuid());
        List<String> lore = List.of(
            left > 0 ? "§7冷却中 §c" + KitTracker.formatRemaining(left) : "§7状态 §a可领取",
            "§7每 24 小时可领取一次",
            "",
            "§a▶ 点击领取一套"
        );
        return MenuButtons.button(Items.CHEST, "§f§l新手 Kit", lore);
    }

    private static ItemStack playerListButton(ServerPlayerEntity player) {
        int online = player.getServer().getPlayerManager().getPlayerList().size();
        List<String> lore = List.of(
            "§7在线人数 §f" + online,
            "",
            "§a▶ 点击浏览并传送"
        );
        return MenuButtons.button(Items.PLAYER_HEAD, "§f§l在线玩家", lore);
    }

    private static ItemStack landmarkButton() {
        int count = LandmarkStore.all().size();
        List<String> lore = List.of(
            "§7地标总数 §f" + count,
            "",
            "§a左键 §7→ 浏览并传送",
            "§b右键 §7→ 在当前位置快捷添加"
        );
        return MenuButtons.button(Items.MAP, "§f§l服务器地标", lore);
    }

    private static ItemStack recipeButton(ServerPlayerEntity player) {
        int total = 0;
        for (var r : player.getServer().getRecipeManager().values()) {
            if (com.tomkestookit.TomkesTookitMod.MOD_ID.equals(r.getId().getNamespace())) total++;
        }
        List<String> lore = List.of(
            "§7本包配方 §f" + total + " §7条",
            "§7包含合成 / 切石",
            "",
            "§a▶ 点击查看"
        );
        return MenuButtons.button(Items.CRAFTING_TABLE, "§f§l本包配方", lore);
    }

    private static ItemStack entityCaptureButton(ServerPlayerEntity player) {
        boolean enabled = com.tomkestookit.EntityCaptureTracker.isEnabled(player.getUuid());
        String status = enabled ? "§a已开启" : "§c已关闭";
        List<String> lore = List.of(
            "§7当前 " + status,
            "§7§oshift + 右键§7 空手抓取生物",
            "§7  → 收进对应刷怪蛋",
            "§8保留 NBT（交易/驯服/命名/装备）",
            "",
            "§a▶ 点击切换"
        );
        ItemStack stack = MenuButtons.button(Items.PIG_SPAWN_EGG, "§f§l实体抓取", lore);
        if (enabled) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack veinMineButton(ServerPlayerEntity player) {
        boolean enabled = com.tomkestookit.VeinMineTracker.isEnabled(player.getUuid());
        String status = enabled ? "§a已开启" : "§c已关闭";
        List<String> lore = List.of(
            "§7当前 " + status,
            "§7§oshift + 左键§7 触发连锁",
            "§7  斧砍自然树 §8→ §7整棵原木/大蘑菇",
            "§7  剪刀砍树叶 §8→ §7整片林冠",
            "§7  镐挖矿石 §8→ §7同类连片",
            "§7  白名单方块 §8→ §7单次最多 32 块",
            "§a左键切换 §8/ §b右键详细设置",
            "§8指令：/veinmine blocks",
            "",
            "§a▶ 点击切换"
        );
        ItemStack stack = MenuButtons.button(Items.WOODEN_AXE, "§f§l连锁采集", lore);
        if (enabled) MenuButtons.withGlow(stack);
        return stack;
    }

    // ------------------------------------------------------------------ actions

    private static void tpDeath(ServerPlayerEntity player) {
        boolean ok = com.tomkestookit.BackCommand.teleportToDeath(player);
        if (ok) player.closeHandledScreen();
    }

    private static void tpSpawn(ServerPlayerEntity player) {
        BlockPos spawn = player.getSpawnPointPosition();
        ServerWorld target;
        if (spawn != null) {
            target = player.getServer().getWorld(player.getSpawnPointDimension());
            if (target == null) target = player.getServer().getOverworld();
        } else {
            target = player.getServer().getOverworld();
            spawn = target.getSpawnPos();
        }
        float yaw = player.getSpawnAngle();
        com.tomkestookit.PlayerTeleport.teleport(player, target,
            spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, yaw, 0);
        com.tomkestookit.SpawnProtection.grant(player, "返回出生点");
        player.sendMessage(Text.literal("已传送到出生点").formatted(Formatting.GREEN), true);
        player.closeHandledScreen();
    }

    private static void toggleFly(ServerPlayerEntity player) {
        player.getServer().getCommandManager().executeWithPrefix(
            player.getCommandSource(), "fly");
        player.closeHandledScreen();
    }

    private static void toggleEntityCapture(ServerPlayerEntity player) {
        boolean now = com.tomkestookit.EntityCaptureTracker.toggle(player.getUuid());
        player.sendMessage(now
            ? Text.literal("实体抓取已 §a开启").formatted(Formatting.GREEN)
            : Text.literal("实体抓取已 §c关闭").formatted(Formatting.YELLOW),
            true);
        player.closeHandledScreen();
    }

    private static void toggleVeinMine(ServerPlayerEntity player) {
        boolean now = com.tomkestookit.VeinMineTracker.toggle(player.getUuid());
        player.sendMessage(now
            ? Text.literal("连锁采集已 §a开启").formatted(Formatting.GREEN)
            : Text.literal("连锁采集已 §c关闭").formatted(Formatting.YELLOW),
            true);
        player.closeHandledScreen();
    }

    private static void giveKit(ServerPlayerEntity player) {
        KitCommand.tryClaim(player, true);
        player.closeHandledScreen();
    }

    // ------------------------------------------------------------------ helpers

    static String dimShort(String key) {
        return com.tomkestookit.landmark.LandmarkCommand.dimShort(key);
    }
}
