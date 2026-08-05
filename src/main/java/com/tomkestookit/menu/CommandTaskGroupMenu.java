package com.tomkestookit.menu;

import com.tomkestookit.SignCommandHandler;
import com.tomkestookit.SignEditPrompt;
import com.tomkestookit.commandtask.CommandTaskStore;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 单个任务组的详情/编辑 UI。9x6：
 *
 *   row 0: [标题 4] . . . [返回 8]
 *   row 1..3 (槽 9..35): 命令条目，一格一条，左键删除
 *   row 4..5: 操作按钮 + 关闭
 */
public final class CommandTaskGroupMenu {

    private static final int PAGE_SIZE = 27; // 槽 9..35
    private static final int FIRST_SLOT = 9;
    private static final int BACK_SLOT = 8;
    private static final int ADD_CHAT_SLOT = 37;
    private static final int ADD_SIGN_SLOT = 38;
    private static final int PACK_SIGN_SLOT = 39;
    private static final int RENAME_SLOT = 41;
    private static final int DELETE_SLOT = 43;
    private static final int CLOSE_SLOT = 53;

    private CommandTaskGroupMenu() {}

    public static void open(ServerPlayerEntity viewer, String groupId) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[任务组] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }
        CommandTaskStore.TaskGroup group = CommandTaskStore.group(groupId);
        if (group == null) {
            viewer.sendMessage(Text.literal("§c[任务组] 已被删除").formatted(Formatting.RED), true);
            CommandTaskMenu.open(viewer);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        inv.setStack(4, MenuButtons.button(Items.REPEATER, "§d§l" + group.name,
            List.of("§7id §f" + group.id,
                "§7命令数 §f" + group.commands.size(),
                "",
                "§8左键条目：移除该命令")));

        inv.setStack(BACK_SLOT, MenuButtons.button(Items.ARROW, "§e◀ 返回列表",
            List.of("§7回到任务组列表")));
        actions.put(BACK_SLOT, p -> CommandTaskMenu.open(p, CommandTaskMenu.Tab.GROUPS));

        int count = Math.min(group.commands.size(), PAGE_SIZE);
        for (int i = 0; i < count; i++) {
            int slot = FIRST_SLOT + i;
            String cmd = group.commands.get(i);
            inv.setStack(slot, commandIcon(i + 1, cmd));
            final int idx = i;
            actions.put(slot, p -> {
                if (CommandTaskStore.removeCommand(groupId, idx)) {
                    p.sendMessage(Text.literal("§7[任务组] 已移除：§f/" + cmd)
                        .formatted(Formatting.GRAY), true);
                }
                open(p, groupId);
            });
        }

        inv.setStack(ADD_CHAT_SLOT, MenuButtons.button(Items.WRITABLE_BOOK, "§a§l+ 输入命令",
            List.of("§7告示牌首行输入命令",
                "§8受告示牌白名单限制",
                "§8允许前缀：cd tp landmark ...",
                "",
                "§e▶ 点击进入输入")));
        actions.put(ADD_CHAT_SLOT, p -> promptAddCommand(p, groupId));

        inv.setStack(ADD_SIGN_SLOT, MenuButtons.button(Items.OAK_SIGN, "§a§l+ 吸收主手告示牌",
            List.of("§7读取主手已绑定的命令告示牌",
                "§8需要已在告示牌上绑定命令",
                "§8不消耗告示牌本身",
                "",
                "§e▶ 点击吸收")));
        actions.put(ADD_SIGN_SLOT, p -> absorbSign(p, groupId));

        inv.setStack(PACK_SIGN_SLOT, MenuButtons.button(Items.LODESTONE, "§b§l生成任务组告示牌",
            List.of("§7生成一块 §f橡木告示牌",
                "§7放下后右键顺序执行组内命令",
                "",
                "§e▶ 点击到你背包")));
        actions.put(PACK_SIGN_SLOT, p -> giveTaskGroupSign(p, groupId));

        inv.setStack(RENAME_SLOT, MenuButtons.button(Items.NAME_TAG, "§e§l重命名",
            List.of("§7告示牌输入新名字",
                "§8长度 1..24")));
        actions.put(RENAME_SLOT, p -> promptRename(p, groupId));

        inv.setStack(DELETE_SLOT, MenuButtons.button(Items.BARRIER, "§c§l删除任务组",
            List.of("§7同时会从所有开机自启中移除",
                "",
                "§c▶ 点击立即删除")));
        actions.put(DELETE_SLOT, p -> {
            CommandTaskStore.deleteGroup(groupId);
            p.sendMessage(Text.literal("§7[任务组] 已删除：§f" + group.name)
                .formatted(Formatting.YELLOW), true);
            CommandTaskMenu.open(p, CommandTaskMenu.Tab.GROUPS);
        });

        inv.setStack(CLOSE_SLOT, MenuButtons.closeButton());
        actions.put(CLOSE_SLOT, ServerPlayerEntity::closeHandledScreen);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions,
                (p, clicked) -> onPlayerInvClick(p, clicked, groupId)),
            Text.literal("§d§l§n任务组 - " + group.name)
        ));
    }

    /** 管理员点击自己物品栏里的告示牌 → 吸收命令到任务组。*/
    private static void onPlayerInvClick(ServerPlayerEntity player, ItemStack clicked, String groupId) {
        if (clicked.isEmpty()) return;
        if (!SignCommandHandler.isSignItem(clicked)) {
            player.sendMessage(Text.literal("§7[任务组] 只能点击已绑定的命令告示牌。")
                .formatted(Formatting.GRAY), true);
            return;
        }
        NbtCompound nbt = clicked.getNbt();
        if (nbt == null || !nbt.contains(SignCommandHandler.STACK_PENDING_KEY)) {
            player.sendMessage(Text.literal("§c[任务组] 该告示牌未绑定命令。")
                .formatted(Formatting.RED), true);
            return;
        }
        String cmd = nbt.getString(SignCommandHandler.STACK_PENDING_KEY);
        String normalized = SignCommandHandler.normalize(cmd);
        if (normalized == null) {
            player.sendMessage(Text.literal("§c[任务组] 绑定的命令不在白名单。")
                .formatted(Formatting.RED), true);
            return;
        }
        if (CommandTaskStore.addCommand(groupId, normalized)) {
            player.sendMessage(Text.literal("§a[任务组] 已从告示牌吸收：§f/" + normalized)
                .formatted(Formatting.GREEN), true);
        }
        open(player, groupId);
    }

    private static ItemStack commandIcon(int idx, String cmd) {
        return MenuButtons.button(Items.PAPER, "§f#" + idx + "  §7/§f" + cmd,
            List.of("§7点击 §c← 移除该命令",
                "§8执行时按此顺序"));
    }

    private static void promptAddCommand(ServerPlayerEntity player, String groupId) {
        player.closeHandledScreen();
        String[] prefill = new String[]{"", "^ 首行输入命令", "允许前缀见帮助", ""};
        SignEditPrompt.open(player, prefill, lines -> player.getServer().execute(() -> {
            String raw = lines != null && lines.length > 0 && lines[0] != null ? lines[0].strip() : "";
            if (raw.isEmpty()) {
                player.sendMessage(Text.literal("§c[任务组] 未输入命令，已取消。"), false);
                open(player, groupId);
                return;
            }
            String normalized = SignCommandHandler.normalize(raw);
            if (normalized == null) {
                player.sendMessage(Text.literal("§c[任务组] 命令不在白名单：/" + raw), false);
                open(player, groupId);
                return;
            }
            if (CommandTaskStore.addCommand(groupId, normalized)) {
                player.sendMessage(Text.literal("§a[任务组] 已添加：§f/" + normalized), false);
            }
            open(player, groupId);
        }));
    }

    private static void promptRename(ServerPlayerEntity player, String groupId) {
        CommandTaskStore.TaskGroup group = CommandTaskStore.group(groupId);
        if (group == null) return;
        player.closeHandledScreen();
        String[] prefill = new String[]{group.name, "^ 新名字", "长度 1..24", ""};
        SignEditPrompt.open(player, prefill, lines -> player.getServer().execute(() -> {
            String name = lines != null && lines.length > 0 && lines[0] != null ? lines[0].strip() : "";
            if (name.isEmpty()) {
                open(player, groupId);
                return;
            }
            CommandTaskStore.renameGroup(groupId, name);
            player.sendMessage(Text.literal("§a[任务组] 已重命名为 §f" + name), false);
            open(player, groupId);
        }));
    }

    private static void absorbSign(ServerPlayerEntity player, String groupId) {
        ItemStack main = player.getMainHandStack();
        if (!SignCommandHandler.isSignItem(main)) {
            player.sendMessage(Text.literal("§c[任务组] 主手不是告示牌。").formatted(Formatting.RED), true);
            return;
        }
        NbtCompound nbt = main.getNbt();
        if (nbt == null || !nbt.contains(SignCommandHandler.STACK_PENDING_KEY)) {
            player.sendMessage(Text.literal("§c[任务组] 主手告示牌未绑定命令。").formatted(Formatting.RED), true);
            return;
        }
        String cmd = nbt.getString(SignCommandHandler.STACK_PENDING_KEY);
        String normalized = SignCommandHandler.normalize(cmd);
        if (normalized == null) {
            player.sendMessage(Text.literal("§c[任务组] 绑定的命令不在白名单：/" + cmd)
                .formatted(Formatting.RED), true);
            return;
        }
        if (CommandTaskStore.addCommand(groupId, normalized)) {
            player.sendMessage(Text.literal("§a[任务组] 已从告示牌吸收：§f/" + normalized)
                .formatted(Formatting.GREEN), true);
        }
        open(player, groupId);
    }

    private static void giveTaskGroupSign(ServerPlayerEntity player, String groupId) {
        CommandTaskStore.TaskGroup group = CommandTaskStore.group(groupId);
        if (group == null) return;
        ItemStack stack = new ItemStack(Items.OAK_SIGN);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(SignCommandHandler.TASK_GROUP_NBT_KEY, group.id);
        nbt.putString(SignCommandHandler.TASK_GROUP_NAME_NBT_KEY, group.name);
        stack.setCustomName(Text.literal("任务组告示牌 - " + group.name)
            .styled(s -> s.withItalic(false).withColor(Formatting.AQUA)));
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        for (String line : List.of(
            "§b任务组 §f" + group.name,
            "§7命令数 §f" + group.commands.size(),
            "§8放置后右键 → 顺序执行所有命令",
            "§8放置时自动蜡封"
        )) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
        nbt.put("display", display);

        if (!player.giveItemStack(stack)) {
            player.dropItem(stack, false);
        }
        player.sendMessage(Text.literal("§a[任务组] 已生成告示牌：§f" + group.name)
            .formatted(Formatting.GREEN), true);
    }
}
