package com.tomkestookit.menu;

import com.tomkestookit.SignEditPrompt;
import com.tomkestookit.commandtask.CommandTaskRunner;
import com.tomkestookit.commandtask.CommandTaskStore;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * 单个开机自启指令集的详情/编辑 UI。9x6：
 *
 *   row 0: [标题 4] . . . [返回 8]
 *   row 1..3 (槽 9..35): 已绑定任务组，一格一项，左键移除；右键上移
 *   row 4..5: 操作按钮 + 关闭
 */
public final class CommandStartupSetMenu {

    private static final int PAGE_SIZE = 27;
    private static final int FIRST_SLOT = 9;
    private static final int BACK_SLOT = 8;
    private static final int TOGGLE_SLOT = 37;
    private static final int ADD_SLOT = 38;
    private static final int RUN_SLOT = 39;
    private static final int RENAME_SLOT = 41;
    private static final int DELETE_SLOT = 43;
    private static final int CLOSE_SLOT = 53;

    private CommandStartupSetMenu() {}

    public static void open(ServerPlayerEntity viewer, String setId) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[自启] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }
        CommandTaskStore.StartupSet set = CommandTaskStore.startupSet(setId);
        if (set == null) {
            viewer.sendMessage(Text.literal("§c[自启] 已被删除").formatted(Formatting.RED), true);
            CommandTaskMenu.open(viewer, CommandTaskMenu.Tab.STARTUPS);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        Map<Integer, Consumer<ServerPlayerEntity>> right = new HashMap<>();

        inv.setStack(4, MenuButtons.button(Items.CLOCK, "§e§l" + set.name,
            List.of("§7id §f" + set.id,
                "§7状态 " + (set.enabled ? "§a已启用" : "§c已禁用"),
                "§7任务组 §f" + set.groupIds.size(),
                "",
                "§8左键条目：从集合移除",
                "§8右键条目：向上移动一位")));

        inv.setStack(BACK_SLOT, MenuButtons.button(Items.ARROW, "§e◀ 返回列表",
            List.of("§7回到自启列表")));
        actions.put(BACK_SLOT, p -> CommandTaskMenu.open(p, CommandTaskMenu.Tab.STARTUPS));

        int count = Math.min(set.groupIds.size(), PAGE_SIZE);
        for (int i = 0; i < count; i++) {
            int slot = FIRST_SLOT + i;
            String gid = set.groupIds.get(i);
            CommandTaskStore.TaskGroup group = CommandTaskStore.group(gid);
            inv.setStack(slot, memberIcon(i + 1, gid, group));
            final String gidRef = gid;
            actions.put(slot, p -> {
                CommandTaskStore.removeGroupFromStartupSet(setId, gidRef);
                open(p, setId);
            });
            right.put(slot, p -> {
                CommandTaskStore.moveGroupInStartupSet(setId, gidRef, -1);
                open(p, setId);
            });
        }

        inv.setStack(TOGGLE_SLOT, MenuButtons.button(set.enabled ? Items.LIME_DYE : Items.GRAY_DYE,
            set.enabled ? "§a§l已启用（点击禁用）" : "§7§l已禁用（点击启用）",
            List.of("§7开机时执行该集合",
                "§8无效或缺失任务组会被跳过")));
        actions.put(TOGGLE_SLOT, p -> {
            boolean now = !set.enabled;
            CommandTaskStore.setStartupEnabled(setId, now);
            p.sendMessage(Text.literal("§7[自启] §f" + set.name + " §7"
                + (now ? "§a已启用" : "§c已禁用")), true);
            open(p, setId);
        });

        inv.setStack(ADD_SLOT, MenuButtons.button(Items.WRITABLE_BOOK, "§a§l+ 添加任务组",
            List.of("§7从已有任务组中挑选",
                "§8执行顺序 = 添加顺序")));
        actions.put(ADD_SLOT, p -> CommandTaskPickerMenu.open(p, setId));

        inv.setStack(RUN_SLOT, MenuButtons.button(Items.PLAYER_HEAD, "§b§l立即执行一次",
            List.of("§7以服务器身份跑一次该集合",
                "§8失败会记录日志，继续下一条")));
        actions.put(RUN_SLOT, p -> {
            CommandTaskRunner.runStartupSet(p.getServer(), set);
            p.sendMessage(Text.literal("§7[自启] 已运行 §f" + set.name), true);
        });

        inv.setStack(RENAME_SLOT, MenuButtons.button(Items.NAME_TAG, "§e§l重命名",
            List.of("§7告示牌输入新名字")));
        actions.put(RENAME_SLOT, p -> promptRename(p, setId));

        inv.setStack(DELETE_SLOT, MenuButtons.button(Items.BARRIER, "§c§l删除该集合",
            List.of("§7只删除集合本身，不删任务组",
                "",
                "§c▶ 点击立即删除")));
        actions.put(DELETE_SLOT, p -> {
            CommandTaskStore.deleteStartupSet(setId);
            p.sendMessage(Text.literal("§7[自启] 已删除：§f" + set.name)
                .formatted(Formatting.YELLOW), true);
            CommandTaskMenu.open(p, CommandTaskMenu.Tab.STARTUPS);
        });

        inv.setStack(CLOSE_SLOT, MenuButtons.closeButton());
        actions.put(CLOSE_SLOT, ServerPlayerEntity::closeHandledScreen);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions, right, null),
            Text.literal("§e§l§n自启 - " + set.name)
        ));
    }

    private static ItemStack memberIcon(int idx, String groupId, CommandTaskStore.TaskGroup group) {
        List<String> lore = new ArrayList<>();
        if (group == null) {
            lore.add("§c任务组已丢失：§f" + groupId);
            lore.add("§8执行时会跳过该条");
        } else {
            lore.add("§7id §f" + group.id);
            lore.add("§7命令数 §f" + group.commands.size());
        }
        lore.add("");
        lore.add("§a左键 §7→ 移除");
        lore.add("§b右键 §7→ 上移一位");
        return MenuButtons.button(group == null ? Items.BARRIER : Items.REPEATER,
            "§f#" + idx + "  §d§l" + (group == null ? groupId : group.name), lore);
    }

    private static void promptRename(ServerPlayerEntity player, String setId) {
        CommandTaskStore.StartupSet set = CommandTaskStore.startupSet(setId);
        if (set == null) return;
        player.closeHandledScreen();
        String[] prefill = new String[]{set.name, "^ 新名字", "长度 1..24", ""};
        SignEditPrompt.open(player, prefill, lines -> player.getServer().execute(() -> {
            String name = lines != null && lines.length > 0 && lines[0] != null ? lines[0].strip() : "";
            if (name.isEmpty()) {
                open(player, setId);
                return;
            }
            CommandTaskStore.renameStartupSet(setId, name);
            player.sendMessage(Text.literal("§a[自启] 已重命名为 §f" + name), false);
            open(player, setId);
        }));
    }
}
