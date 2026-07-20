package com.starterkit.menu;

import com.starterkit.KitCommand;
import com.starterkit.KitTracker;
// PlayerListMenu / LandmarkMenu 同包，无需 import
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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
 * 布局（9x3，槽位下标 0..26）：
 *
 *   .  .  .  .  .  .  .  .  .        (row 0：装饰或留空)
 *   .  10 .  12 .  14 .  16 .        (row 1：功能按钮)
 *   .  .  .  .  .  .  .  .  .        (row 2：装饰或留空)
 *
 * 10 = 出生点(床)  12 = 飞行(羽毛)  14 = 领取Kit(木盒)  16 = 玩家列表(玩家头颅)
 * 后续加地标时用 20 或 22。
 */
public final class MenuUi {

    private static final Text TITLE = Text.literal("§b主菜单");

    private MenuUi() {}

    public static void open(ServerPlayerEntity player) {
        Inventory inv = MenuScreenHandler.buildInventory(3);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 出生点
        inv.setStack(10, button(Items.RED_BED, "§f返回出生点",
            List.of("§7点击传送回你的重生点。", "§7未设置重生点则前往世界出生点。")));
        actions.put(10, MenuUi::tpSpawn);

        // 飞行
        inv.setStack(12, button(Items.FEATHER, "§f切换飞行",
            List.of("§7开启/关闭 /fly。", "§7飞行会消耗经验。")));
        actions.put(12, MenuUi::toggleFly);

        // 领取 Kit
        inv.setStack(14, button(Items.CHEST, "§f领取新手 Kit",
            List.of("§7领取一次性新手装备。", "§7已领取过则会提示。")));
        actions.put(14, MenuUi::giveKit);

        // 玩家列表
        inv.setStack(16, button(Items.PLAYER_HEAD, "§f玩家列表",
            List.of("§7查看在线玩家。", "§7点击玩家头颅传送到其位置。")));
        actions.put(16, PlayerListMenu::open);

        // 地标
        inv.setStack(22, button(Items.MAP, "§f服务器地标",
            List.of("§7查看所有地标。", "§7添加请用 §f/landmark add <名字>")));
        actions.put(22, LandmarkMenu::open);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    // ------------------------------------------------------------------ actions

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
        player.closeHandledScreen();
    }

    private static void toggleFly(ServerPlayerEntity player) {
        // 复用 /fly 的实现：以玩家自己的身份执行，权限 0，行为跟手动敲 /fly 一致
        player.getServer().getCommandManager().executeWithPrefix(
            player.getCommandSource(), "fly");
        player.closeHandledScreen();
    }

    private static void giveKit(ServerPlayerEntity player) {
        if (KitTracker.hasClaimed(player.getUuid())) {
            player.sendMessage(Text.literal("你已经领取过 Kit。").formatted(Formatting.RED), true);
            return;
        }
        KitCommand.giveKitItems(player);
        KitTracker.markClaimed(player.getUuid());
        player.sendMessage(Text.literal("已领取 Kit！").formatted(Formatting.GREEN), true);
        player.closeHandledScreen();
    }

    // ------------------------------------------------------------------ helpers

    private static ItemStack button(net.minecraft.item.Item item, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name).styled(s -> s.withItalic(false)));

        NbtCompound tag = stack.getOrCreateNbt();
        NbtCompound display = tag.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : loreLines) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        tag.put("display", display);
        return stack;
    }
}
