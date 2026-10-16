package com.tomkestookit.menu;

import com.tomkestookit.KitCommand;
import com.tomkestookit.TomkesTookitConfig;
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
 * 新手礼包物品清单编辑：
 *
 *   row 0 槽 4：说明按钮
 *   row 1..3 (槽 9..35)：当前 kit 物品，最多 27 件；左键 → 从 kit 移除
 *   row 4 槽 40：一键重置为默认
 *   row 5 槽 45 返回 admin panel，槽 49 关闭
 *
 * 玩家点自己背包物品 → 复制一份添加到 kit（保留 NBT / 附魔 / 命名），不消耗原物品。
 * 参考 VeinMineMenu / LandmarkMenu 的 playerInventoryClick 模式。
 */
public final class AdminKitMenu {

    private static final int MAX_ITEMS = 27;
    private static final int FIRST_SLOT = 9;

    private AdminKitMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("§c[管理员] 需要 OP 权限").formatted(Formatting.RED), true);
            return;
        }

        List<ItemStack> items = TomkesTookitConfig.kitItems();
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 顶栏说明
        inv.setStack(4, MenuButtons.button(Items.WRITABLE_BOOK, "§e§l新手礼包编辑",
            List.of(
                "§7当前 §f" + items.size() + "§7/§f" + MAX_ITEMS + " §7件",
                TomkesTookitConfig.isKitCustomized() ? "§8状态：§a已自定义" : "§8状态：§7使用默认",
                "",
                "§7点击 §f背包物品 §7→ 添加",
                "§7点击 §f上方槽位 §7→ 移除",
                "§8保留附魔 / 命名 / NBT")));

        // 当前物品（最多 27 件）
        for (int i = 0; i < Math.min(items.size(), MAX_ITEMS); i++) {
            int slot = FIRST_SLOT + i;
            ItemStack raw = items.get(i);
            // clone + 追加"点击移除"lore 以免污染原 stack；玩家真领取时走 config.kitItems()
            inv.setStack(slot, decorateForList(raw));
            final int idx = i;
            actions.put(slot, p -> removeAt(p, idx));
        }

        // 一键重置为默认
        inv.setStack(40, MenuButtons.button(Items.REDSTONE_BLOCK, "§c§l重置为默认礼包",
            List.of("§7恢复代码内建的默认 kit",
                "§7皮甲全套 + 石镐斧",
                "§f16 面包 §7+ 菜单时钟",
                "",
                "§c▶ 点击立即重置")));
        actions.put(40, p -> {
            TomkesTookitConfig.resetKitItems();
            p.sendMessage(Text.literal("§7[管理员] 已重置为默认新手礼包")
                .formatted(Formatting.YELLOW), true);
            open(p);
        });

        // 底部导航
        inv.setStack(45, MenuButtons.backButton());
        actions.put(45, AdminPanelMenu::open);
        inv.setStack(49, MenuButtons.closeButton());
        actions.put(49, ServerPlayerEntity::closeHandledScreen);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions,
                AdminKitMenu::onPlayerInventoryClick),
            Text.literal("§e§l§n新手礼包编辑")
        ));
    }

    /** 玩家点自己背包物品 → 复制一份加入 kit。*/
    private static void onPlayerInventoryClick(ServerPlayerEntity viewer, ItemStack clicked) {
        if (clicked.isEmpty()) return;
        List<ItemStack> items = TomkesTookitConfig.kitItems();
        if (items.size() >= MAX_ITEMS) {
            viewer.sendMessage(Text.literal("§c[管理员] 礼包已满（" + MAX_ITEMS + " 件）")
                .formatted(Formatting.RED), true);
            return;
        }
        items.add(clicked.copy());
        TomkesTookitConfig.setKitItems(items);
        viewer.sendMessage(Text.literal("§a[管理员] 已加入礼包：§f" + clicked.getName().getString())
            .formatted(Formatting.GREEN), true);
        open(viewer);
    }

    private static void removeAt(ServerPlayerEntity viewer, int index) {
        List<ItemStack> items = TomkesTookitConfig.kitItems();
        if (index < 0 || index >= items.size()) return;
        ItemStack removed = items.remove(index);
        TomkesTookitConfig.setKitItems(items);
        viewer.sendMessage(Text.literal("§7[管理员] 已移除：§f" + removed.getName().getString())
            .formatted(Formatting.YELLOW), true);
        open(viewer);
    }

    /**
     * 复制一份 stack，追加 "§c§l← 点击移除" lore；不改动原 config 里的 stack。
     * 展示时 count 保留原值，方便一眼看清物品数量。
     */
    private static ItemStack decorateForList(ItemStack raw) {
        ItemStack shown = raw.copy();
        NbtCompound nbt = shown.getOrCreateNbt();
        NbtCompound display = nbt.contains("display") ? nbt.getCompound("display") : new NbtCompound();
        NbtList lore = display.contains("Lore") ? display.getList("Lore", 8) : new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§c§l← 点击移除").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);
        return shown;
    }

    /** 提供给外部（比如 tear-down 想复位默认时）— 不直接用，占位保留 KitCommand 接入路径。*/
    @SuppressWarnings("unused")
    public static List<ItemStack> currentKit() {
        List<ItemStack> items = TomkesTookitConfig.kitItems();
        if (items.isEmpty()) return KitCommand.buildDefaultKit();
        return items;
    }
}
