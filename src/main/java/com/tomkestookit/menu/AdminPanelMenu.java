package com.tomkestookit.menu;

import com.tomkestookit.AdminFeature;
import com.tomkestookit.TomkesTookitConfig;
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
 * 管理员面板入口（OP-only）。
 *
 * 9x6 布局：
 *   row 0 槽 4  ：标题
 *   row 2..3 ：按 {@link AdminFeature.Group} 展开的分组按钮（一组一个）
 *   row 4 槽 22 ：新手礼包定制
 *   row 5 槽 45 返回主菜单，槽 49 关闭
 *
 * 打开时二次校验权限，防止菜单深链绕过 gate。
 */
public final class AdminPanelMenu {

    private static final Text TITLE = Text.literal("§c§l§n管理员控制面板");

    private AdminPanelMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[管理员] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 顶栏标题（无 action，纯装饰）
        inv.setStack(4, MenuButtons.button(Items.COMMAND_BLOCK, "§c§l管理员控制面板",
            List.of("§7逐项开关 mod 功能",
                "§7自定义新手礼包",
                "",
                "§8对全服生效；已开的功能默认全开")));

        // 分组按钮（row 2..3 分散）
        Map<AdminFeature.Group, Integer> counts = AdminFeature.groupCounts();
        int[] slots = {19, 21, 23, 25, 28, 30};
        AdminFeature.Group[] groups = AdminFeature.Group.values();
        for (int i = 0; i < groups.length && i < slots.length; i++) {
            AdminFeature.Group g = groups[i];
            int slot = slots[i];
            inv.setStack(slot, groupButton(g, counts.getOrDefault(g, 0)));
            actions.put(slot, p -> AdminFeatureListMenu.open(p, g));
        }

        // 新手礼包定制
        inv.setStack(40, kitButton());
        actions.put(40, AdminKitMenu::open);

        // 命令任务组 / 开机自启
        inv.setStack(42, MenuButtons.button(Items.REPEATER, "§d§l命令任务组 / 自启",
            List.of("§7把命令告示牌组装成任务组",
                "§7再把任务组打包成开机自启集合",
                "",
                "§e▶ 点击进入")));
        actions.put(42, CommandTaskMenu::open);

        // 底部导航
        inv.setStack(45, MenuButtons.backButton());
        actions.put(45, MenuUi::open);
        inv.setStack(49, MenuButtons.closeButton());
        actions.put(49, ServerPlayerEntity::closeHandledScreen);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    private static ItemStack groupButton(AdminFeature.Group g, int total) {
        int enabled = 0;
        for (AdminFeature f : AdminFeature.byGroup(g)) {
            if (TomkesTookitConfig.enabled(f.key)) enabled++;
        }
        List<String> lore = new ArrayList<>();
        lore.add("§7" + g.desc);
        lore.add("§7已启用 §a" + enabled + "§8/§f" + total);
        lore.add("");
        lore.add("§e▶ 点击进入");
        return MenuButtons.button(g.icon, g.label, lore);
    }

    private static ItemStack kitButton() {
        int count = TomkesTookitConfig.kitItems().size();
        boolean custom = TomkesTookitConfig.isKitCustomized();
        List<String> lore = new ArrayList<>();
        lore.add("§7当前 §f" + count + " §7件物品");
        lore.add(custom ? "§8状态：§a已自定义" : "§8状态：§7使用默认");
        lore.add("");
        lore.add("§e▶ 点击编辑物品清单");
        return MenuButtons.button(Items.CHEST, "§e§l新手礼包", lore);
    }
}
