package com.tomkestookit.menu;

import com.tomkestookit.AdminFeature;
import com.tomkestookit.TomkesTookitConfig;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
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
 * 某个 {@link AdminFeature.Group} 下所有特性的开关列表。
 *
 * 9x6 布局：
 *   row 0 槽 4：分组标题；槽 8 返回上级
 *   row 1..5 (槽 9..53)：每个特性一格；左键切换开关
 *
 * 开启态图标发光 + "§a● 开"；关闭态灰化 + "§7○ 关"。
 * 切换后重开自己，反馈立即可见。
 */
public final class AdminFeatureListMenu {

    private AdminFeatureListMenu() {}

    public static void open(ServerPlayerEntity viewer, AdminFeature.Group group) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[管理员] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 顶栏标题 + 返回
        inv.setStack(4, MenuButtons.button(group.icon, group.label,
            List.of("§7" + group.desc, "", "§8左键切换 · 右键无操作")));
        inv.setStack(8, MenuButtons.backButton());
        actions.put(8, AdminPanelMenu::open);

        // 逐条渲染
        List<AdminFeature> features = AdminFeature.byGroup(group);
        int max = Math.min(features.size(), 45); // 9..53
        for (int i = 0; i < max; i++) {
            AdminFeature f = features.get(i);
            int slot = 9 + i;
            inv.setStack(slot, featureButton(f));
            actions.put(slot, p -> {
                boolean now = !TomkesTookitConfig.enabled(f.key);
                TomkesTookitConfig.setEnabled(f.key, now);
                p.sendMessage(Text.literal(
                    (now ? "§a[管理员] 已开启：§f" : "§7[管理员] 已关闭：§f") + f.zhName)
                    .formatted(now ? Formatting.GREEN : Formatting.YELLOW), true);
                open(p, group);
            });
        }

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            Text.literal("§c§l§n" + group.label)
        ));
    }

    private static ItemStack featureButton(AdminFeature f) {
        boolean on = TomkesTookitConfig.enabled(f.key);
        List<String> lore = new ArrayList<>();
        lore.add(on ? "§a● 已开启" : "§7○ 已关闭");
        lore.add("§8key: " + f.key);
        lore.add("");
        // 拆行显示 desc（若太长）
        String desc = f.desc == null ? "" : f.desc;
        int width = 32;
        while (desc.length() > width) {
            lore.add("§7" + desc.substring(0, width));
            desc = desc.substring(width);
        }
        if (!desc.isEmpty()) lore.add("§7" + desc);
        lore.add("");
        lore.add(on ? "§e▶ 点击关闭" : "§e▶ 点击开启");
        ItemStack stack = MenuButtons.button(f.icon,
            (on ? "§a§l" : "§7§l") + f.zhName, lore);
        if (on) MenuButtons.withGlow(stack);
        return stack;
    }
}
