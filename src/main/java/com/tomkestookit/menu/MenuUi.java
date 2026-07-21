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
 *         36  ■ 38  ■ 40  ■ 42  ■ 44     row 4  36/38/40/42/44=开关/浏览类（5）
 *          ■  ■  ■  ■  ■  ■  ■  ■ 53     row 5  53=关闭
 *
 * 按钮:
 *   4  - 玩家信息卡(玩家头颅) - 顶部中央（无 action）
 *   9  - 返回死亡地点(骨头, 无记录时灰显)
 *   11 - 出生点(床)
 *   13 - 领取 Kit (箱子)
 *   15 - 在线玩家(头颅)
 *   17 - 服务器地标(地图)
 *   36 - 飞行切换(羽毛, 开启时发光)
 *   38 - 连锁采集(木斧, 开启时发光)
 *   40 - 实体抓取(刷怪蛋, 开启时发光)
 *   42 - 本包配方(工作台)
 *   44 - 生存/旁观切换(末影之眼)
 *   53 - 关闭(屏障)
 *
 * 空槽全部用灰色玻璃板铺满，避免"透视格"感。
 */
public final class MenuUi {

    private MenuUi() {}

    public static void open(ServerPlayerEntity player) {
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

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

        // 开关/浏览行 (row 4)：5 个横向均分按钮
        inv.setStack(36, flyButton(player));
        actions.put(36, MenuUi::toggleFly);

        inv.setStack(38, veinMineButton(player));
        actions.put(38, MenuUi::toggleVeinMine);

        inv.setStack(40, entityCaptureButton(player));
        actions.put(40, MenuUi::toggleEntityCapture);

        inv.setStack(42, recipeButton(player));
        actions.put(42, RecipeMenu::open);

        inv.setStack(44, gameModeButton(player));
        actions.put(44, MenuUi::toggleGameMode);

        // 关闭按钮
        inv.setStack(53, MenuButtons.closeButton());
        actions.put(53, ServerPlayerEntity::closeHandledScreen);

        // 剩下的空位铺满玻璃板
        MenuButtons.fillBackground(inv);

        // 标题带玩家名
        Text title = Text.literal("§b§l§n" + player.getGameProfile().getName() + " 的菜单");
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            title
        ));
    }

    // ------------------------------------------------------------------ buttons

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
        boolean claimed = KitTracker.hasClaimed(player.getUuid());
        List<String> lore = List.of(
            claimed ? "§7首次领取 §a已完成" : "§7首次领取 §e未领取",
            "§7菜单入口可反复领取",
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
            "§8/landmark add <名字>",
            "",
            "§a▶ 点击浏览并传送"
        );
        return MenuButtons.button(Items.MAP, "§f§l服务器地标", lore);
    }

    private static ItemStack gameModeButton(ServerPlayerEntity player) {
        net.minecraft.world.GameMode cur = player.interactionManager.getGameMode();
        boolean isSpectator = cur == net.minecraft.world.GameMode.SPECTATOR;
        String targetName = isSpectator ? "生存" : "旁观";
        List<String> lore = List.of(
            "§7当前 §f" + com.tomkestookit.ChatModeToggle.label(cur),
            "§7点击切换到 §f" + targetName,
            "§8聊天快捷键 §7!c §8/ §7!s",
            "",
            "§a▶ 点击切换"
        );
        ItemStack stack = MenuButtons.button(Items.ENDER_EYE, "§f§l生存/旁观切换", lore);
        if (isSpectator) MenuButtons.withGlow(stack);
        return stack;
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
            "§8上限 128 块，消耗耐久，附魔生效",
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
        player.teleport(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, yaw, 0);
        player.sendMessage(Text.literal("已传送到出生点").formatted(Formatting.GREEN), true);
        player.closeHandledScreen();
    }

    private static void toggleFly(ServerPlayerEntity player) {
        player.getServer().getCommandManager().executeWithPrefix(
            player.getCommandSource(), "fly");
        player.closeHandledScreen();
    }

    private static void toggleGameMode(ServerPlayerEntity player) {
        net.minecraft.world.GameMode cur = player.interactionManager.getGameMode();
        net.minecraft.world.GameMode target = (cur == net.minecraft.world.GameMode.SPECTATOR)
            ? net.minecraft.world.GameMode.SURVIVAL
            : net.minecraft.world.GameMode.SPECTATOR;
        com.tomkestookit.ChatModeToggle.apply(player, target);
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
        KitCommand.giveKitItems(player);
        if (!KitTracker.hasClaimed(player.getUuid())) {
            KitTracker.markClaimed(player.getUuid());
        }
        player.sendMessage(Text.literal("已领取 Kit！").formatted(Formatting.GREEN), true);
        player.closeHandledScreen();
    }

    // ------------------------------------------------------------------ helpers

    static String dimShort(String key) {
        if ("minecraft:overworld".equals(key)) return "主世界";
        if ("minecraft:the_nether".equals(key)) return "下界";
        if ("minecraft:the_end".equals(key)) return "末地";
        return key;
    }
}
