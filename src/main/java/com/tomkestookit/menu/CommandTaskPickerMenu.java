package com.tomkestookit.menu;

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
 * 从任务组池里挑一个加入开机自启集合。9x6，单页最多 36 个任务组。
 */
public final class CommandTaskPickerMenu {

    private static final int PAGE_SIZE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int BACK_SLOT = 8;
    private static final int CLOSE_SLOT = 53;

    private CommandTaskPickerMenu() {}

    public static void open(ServerPlayerEntity viewer, String setId) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[自启] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }
        CommandTaskStore.StartupSet set = CommandTaskStore.startupSet(setId);
        if (set == null) {
            CommandTaskMenu.open(viewer, CommandTaskMenu.Tab.STARTUPS);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        inv.setStack(4, MenuButtons.button(Items.WRITABLE_BOOK, "§b§l选择要加入的任务组",
            List.of("§7目标集合 §f" + set.name,
                "§7左键任务组 → 加入并返回集合详情")));

        inv.setStack(BACK_SLOT, MenuButtons.button(Items.ARROW, "§e◀ 返回",
            List.of("§7返回集合详情，不添加")));
        actions.put(BACK_SLOT, p -> CommandStartupSetMenu.open(p, setId));

        List<CommandTaskStore.TaskGroup> groups = new ArrayList<>(CommandTaskStore.groups());
        int count = Math.min(groups.size(), PAGE_SIZE);
        for (int i = 0; i < count; i++) {
            int slot = FIRST_SLOT + i;
            CommandTaskStore.TaskGroup g = groups.get(i);
            boolean already = set.groupIds.contains(g.id);
            inv.setStack(slot, groupIcon(g, already));
            final String gid = g.id;
            actions.put(slot, p -> {
                if (CommandTaskStore.addGroupToStartupSet(setId, gid)) {
                    p.sendMessage(Text.literal("§a[自启] 已加入任务组：§f" + g.name)
                        .formatted(Formatting.GREEN), true);
                }
                CommandStartupSetMenu.open(p, setId);
            });
        }
        if (groups.isEmpty()) {
            inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无任务组",
                List.of("§8先去任务组标签页新建")));
        }

        inv.setStack(CLOSE_SLOT, MenuButtons.closeButton());
        actions.put(CLOSE_SLOT, ServerPlayerEntity::closeHandledScreen);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            Text.literal("§b§l§n加入任务组 - " + set.name)
        ));
    }

    private static ItemStack groupIcon(CommandTaskStore.TaskGroup g, boolean already) {
        List<String> lore = new ArrayList<>();
        lore.add("§7命令数 §f" + g.commands.size());
        if (already) {
            lore.add("§e已在集合里，再次点击会追加一份");
        }
        lore.add("");
        lore.add("§a▶ 点击加入");
        ItemStack stack = MenuButtons.button(Items.REPEATER,
            (already ? "§a§l" : "§d§l") + g.name, lore);
        if (already) MenuButtons.withGlow(stack);
        return stack;
    }
}
