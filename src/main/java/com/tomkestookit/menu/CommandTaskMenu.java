package com.tomkestookit.menu;

import com.tomkestookit.SignEditPrompt;
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
 * 命令任务组 / 开机自启指令集的一级总览 UI。9x6：
 *
 *   row 0: [任务组 tab 0] [自启 tab 1] . . [标题 4] . . . [返回 8]
 *   row 1..4 (槽 9..44): 当前 tab 的条目，一格一项
 *   row 5: [新建 45] . . . . . . . [关闭 53]
 */
public final class CommandTaskMenu {

    private static final int PAGE_SIZE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int NEW_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final int BACK_SLOT = 8;

    public enum Tab { GROUPS, STARTUPS }

    private CommandTaskMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, Tab.GROUPS);
    }

    public static void open(ServerPlayerEntity viewer, Tab tab) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[任务组] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();
        Map<Integer, Consumer<ServerPlayerEntity>> right = new HashMap<>();

        int groupCount = CommandTaskStore.groups().size();
        int startupCount = CommandTaskStore.startupSets().size();

        inv.setStack(0, tabButton("§d§l任务组", Items.REPEATER, groupCount, tab == Tab.GROUPS));
        actions.put(0, p -> open(p, Tab.GROUPS));

        inv.setStack(1, tabButton("§e§l开机自启", Items.CLOCK, startupCount, tab == Tab.STARTUPS));
        actions.put(1, p -> open(p, Tab.STARTUPS));

        inv.setStack(4, MenuButtons.button(Items.COMMAND_BLOCK, "§b§l§n命令任务组 / 自启",
            List.of(
                "§7左键条目：进入详情",
                "§7右键任务组：直接执行一次（以你身份）",
                "§7右键自启：切换启用",
                "",
                "§8任务组内命令仍受告示牌白名单限制")));

        inv.setStack(BACK_SLOT, MenuButtons.button(Items.ARROW, "§e◀ 返回",
            List.of("§7回到管理员面板")));
        actions.put(BACK_SLOT, AdminPanelMenu::open);

        if (tab == Tab.GROUPS) {
            List<CommandTaskStore.TaskGroup> list = new ArrayList<>(CommandTaskStore.groups());
            int max = Math.min(list.size(), PAGE_SIZE);
            for (int i = 0; i < max; i++) {
                CommandTaskStore.TaskGroup g = list.get(i);
                int slot = FIRST_SLOT + i;
                inv.setStack(slot, groupIcon(g));
                final String id = g.id;
                actions.put(slot, p -> CommandTaskGroupMenu.open(p, id));
                right.put(slot, p -> {
                    com.tomkestookit.commandtask.CommandTaskRunner.runGroup(p,
                        CommandTaskStore.group(id), "任务组菜单 " + g.name);
                    p.sendMessage(Text.literal("§7[任务组] 已执行 §f" + g.name)
                        .formatted(Formatting.GRAY), true);
                });
            }
            if (list.isEmpty()) {
                inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无任务组",
                    List.of("§8点击左下 §f+ §8新建一个")));
            }

            inv.setStack(NEW_SLOT, MenuButtons.button(Items.EMERALD, "§a§l+ 新建任务组",
                List.of("§7告示牌输入组名",
                    "§8长度自适应，不超过 24 字")));
            actions.put(NEW_SLOT, CommandTaskMenu::promptCreateGroup);
        } else {
            List<CommandTaskStore.StartupSet> list = new ArrayList<>(CommandTaskStore.startupSets());
            int max = Math.min(list.size(), PAGE_SIZE);
            for (int i = 0; i < max; i++) {
                CommandTaskStore.StartupSet set = list.get(i);
                int slot = FIRST_SLOT + i;
                inv.setStack(slot, startupIcon(set));
                final String id = set.id;
                actions.put(slot, p -> CommandStartupSetMenu.open(p, id));
                right.put(slot, p -> {
                    boolean now = !CommandTaskStore.startupSet(id).enabled;
                    CommandTaskStore.setStartupEnabled(id, now);
                    p.sendMessage(Text.literal("§7[自启] §f" + set.name + " §7"
                        + (now ? "§a已启用" : "§c已禁用"))
                        .formatted(Formatting.GRAY), true);
                    open(p, Tab.STARTUPS);
                });
            }
            if (list.isEmpty()) {
                inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无开机自启指令集",
                    List.of("§8点击左下 §f+ §8新建一个")));
            }

            inv.setStack(NEW_SLOT, MenuButtons.button(Items.EMERALD, "§a§l+ 新建开机自启集合",
                List.of("§7告示牌输入名字",
                    "§8长度自适应，不超过 24 字")));
            actions.put(NEW_SLOT, CommandTaskMenu::promptCreateStartup);
        }

        inv.setStack(CLOSE_SLOT, MenuButtons.closeButton());
        actions.put(CLOSE_SLOT, ServerPlayerEntity::closeHandledScreen);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions, right, null),
            Text.literal("§b§l§n命令任务组 / 自启")
        ));
    }

    private static void promptCreateGroup(ServerPlayerEntity player) {
        player.closeHandledScreen();
        String[] prefill = new String[]{"", "^ 任务组名", "长度 1..24", ""};
        SignEditPrompt.open(player, prefill, lines -> player.getServer().execute(() -> {
            String name = lines != null && lines.length > 0 && lines[0] != null ? lines[0].strip() : "";
            if (name.isEmpty()) {
                player.sendMessage(Text.literal("§c[任务组] 未输入名字，已取消。"), false);
                open(player, Tab.GROUPS);
                return;
            }
            CommandTaskStore.TaskGroup g = CommandTaskStore.createGroup(name);
            player.sendMessage(Text.literal("§a[任务组] 已新建 §f" + g.name), false);
            CommandTaskGroupMenu.open(player, g.id);
        }));
    }

    private static void promptCreateStartup(ServerPlayerEntity player) {
        player.closeHandledScreen();
        String[] prefill = new String[]{"", "^ 自启集名", "长度 1..24", ""};
        SignEditPrompt.open(player, prefill, lines -> player.getServer().execute(() -> {
            String name = lines != null && lines.length > 0 && lines[0] != null ? lines[0].strip() : "";
            if (name.isEmpty()) {
                player.sendMessage(Text.literal("§c[自启] 未输入名字，已取消。"), false);
                open(player, Tab.STARTUPS);
                return;
            }
            CommandTaskStore.StartupSet s = CommandTaskStore.createStartupSet(name);
            player.sendMessage(Text.literal("§a[自启] 已新建 §f" + s.name), false);
            CommandStartupSetMenu.open(player, s.id);
        }));
    }

    private static ItemStack tabButton(String label, net.minecraft.item.Item icon, int count, boolean selected) {
        ItemStack stack = MenuButtons.button(icon, label + (selected ? "  §a●" : ""),
            List.of("§7条目 §f" + count, selected ? "§a● 当前 tab" : "§8○ 点击切换"));
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack groupIcon(CommandTaskStore.TaskGroup g) {
        List<String> lore = new ArrayList<>();
        lore.add("§7id §f" + g.id);
        lore.add("§7命令数 §f" + g.commands.size());
        int show = Math.min(3, g.commands.size());
        for (int i = 0; i < show; i++) {
            lore.add("§8· /" + g.commands.get(i));
        }
        if (g.commands.size() > show) lore.add("§8· ...");
        lore.add("");
        lore.add("§a左键 §7→ 详情");
        lore.add("§b右键 §7→ 立即执行");
        return MenuButtons.button(Items.REPEATER, "§d§l" + g.name, lore);
    }

    private static ItemStack startupIcon(CommandTaskStore.StartupSet s) {
        List<String> lore = new ArrayList<>();
        lore.add("§7id §f" + s.id);
        lore.add("§7状态 " + (s.enabled ? "§a已启用" : "§c已禁用"));
        lore.add("§7任务组 §f" + s.groupIds.size());
        int show = Math.min(3, s.groupIds.size());
        for (int i = 0; i < show; i++) {
            String gid = s.groupIds.get(i);
            CommandTaskStore.TaskGroup g = CommandTaskStore.group(gid);
            lore.add("§8· " + (g == null ? gid + " §c(缺失)" : g.name));
        }
        if (s.groupIds.size() > show) lore.add("§8· ...");
        lore.add("");
        lore.add("§a左键 §7→ 详情");
        lore.add("§b右键 §7→ 切换启用");
        ItemStack stack = MenuButtons.button(s.enabled ? Items.LIME_DYE : Items.GRAY_DYE,
            (s.enabled ? "§a§l" : "§7§l") + s.name, lore);
        if (s.enabled) MenuButtons.withGlow(stack);
        return stack;
    }
}
