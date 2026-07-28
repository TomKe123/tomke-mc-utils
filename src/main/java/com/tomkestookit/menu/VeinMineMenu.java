package com.tomkestookit.menu;

import com.tomkestookit.VeinMineBlockConfig;
import com.tomkestookit.VeinMineTracker;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 连锁采集管理菜单：个人开关 + 全服特定方块白名单 + 每方块阈值。
 * 打开本菜单时，点击玩家物品栏里的方块会把该方块加入白名单。
 */
public final class VeinMineMenu {

    private static final int PAGE_SIZE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final Text TITLE = Text.literal("§b§l§n连锁采集管理");

    private VeinMineMenu() {}

    public static void open(ServerPlayerEntity player) {
        open(player, 0);
    }

    public static void open(ServerPlayerEntity player, int page) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(VeinMineBlockConfig.all().entrySet());
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        final int currentPage = page;

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        inv.setStack(0, toggleButton(player));
        actions.put(0, VeinMineMenu::toggleVeinMine);

        inv.setStack(2, MenuButtons.button(Items.HOPPER, "§f§l点击背包方块加入",
            List.of("§7打开本页后，直接点击", "§7下方物品栏里的方块", "§7即可加入白名单", "§8默认单次 32 块")));

        inv.setStack(4, MenuButtons.button(Items.WRITABLE_BOOK, "§f§l白名单状态",
            List.of("§7条目 §f" + entries.size(),
                "§7单次上限 §f" + VeinMineBlockConfig.MAX_LIMIT,
                "§8只有白名单方块会参与通用连锁")));

        inv.setStack(6, MenuButtons.button(Items.REDSTONE_BLOCK, "§c§l一键重置",
            List.of("§7恢复默认白名单：", "§f岩浆块 §7≤ §f32", "§f安山岩 §7≤ §f32", "", "§c▶ 点击立即重置")));
        actions.put(6, p -> {
            VeinMineBlockConfig.resetDefaults();
            p.sendMessage(Text.literal("已重置连锁白名单。").formatted(Formatting.YELLOW), true);
            open(p, 0);
        });

        if (entries.isEmpty()) {
            inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无白名单方块",
                List.of("§8点击下方物品栏里的方块添加")));
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, entries.size());
            for (int i = start; i < end; i++) {
                Map.Entry<String, Integer> e = entries.get(i);
                int slot = FIRST_SLOT + (i - start);
                inv.setStack(slot, blockIcon(e.getKey(), e.getValue()));
                String id = e.getKey();
                actions.put(slot, p -> openBlockDetail(p, id, currentPage));
            }
        }

        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;
        inv.setStack(PREV_SLOT, MenuButtons.pageButton(false, page, totalPages, hasPrev));
        if (hasPrev) {
            final int prev = page - 1;
            actions.put(PREV_SLOT, p -> open(p, prev));
        }
        inv.setStack(NEXT_SLOT, MenuButtons.pageButton(true, page, totalPages, hasNext));
        if (hasNext) {
            final int next = page + 1;
            actions.put(NEXT_SLOT, p -> open(p, next));
        }

        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, MenuUi::open);

        MenuButtons.fillBackground(inv);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions,
                (p, clicked) -> addClickedBlock(p, clicked, currentPage)),
            TITLE
        ));
    }

    private static void openBlockDetail(ServerPlayerEntity player, String id, int returnPage) {
        Block block = blockById(id);
        if (block == null) {
            player.sendMessage(Text.literal("方块不存在：" + id).formatted(Formatting.RED), true);
            open(player, returnPage);
            return;
        }

        int limit = VeinMineBlockConfig.getLimit(block);
        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        inv.setStack(4, detailIcon(block, id, limit));

        setLimitButton(inv, actions, 19, block, id, 8, returnPage);
        setLimitButton(inv, actions, 21, block, id, 16, returnPage);
        setLimitButton(inv, actions, 23, block, id, 24, returnPage);
        setLimitButton(inv, actions, 25, block, id, 32, returnPage);

        inv.setStack(29, MenuButtons.button(Items.ARROW, "§e-4",
            List.of("§7当前 §f" + limit, "§7调整后 §f" + VeinMineBlockConfig.clamp(limit - 4))));
        actions.put(29, p -> setLimitAndReopen(p, block, id, limit - 4, returnPage));

        inv.setStack(33, MenuButtons.button(Items.SPECTRAL_ARROW, "§a+4",
            List.of("§7当前 §f" + limit, "§7调整后 §f" + VeinMineBlockConfig.clamp(limit + 4))));
        actions.put(33, p -> setLimitAndReopen(p, block, id, limit + 4, returnPage));

        inv.setStack(40, MenuButtons.button(Items.BARRIER, "§c§l移除白名单",
            List.of("§7方块 §f" + id, "", "§c▶ 点击移除")));
        actions.put(40, p -> {
            VeinMineBlockConfig.remove(block);
            p.sendMessage(Text.literal("已移除连锁白名单：" + id).formatted(Formatting.YELLOW), true);
            open(p, returnPage);
        });

        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, p -> open(p, returnPage));

        MenuButtons.fillBackground(inv);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            Text.literal("§b§l§n连锁方块详情")
        ));
    }

    private static void setLimitButton(Inventory inv, Map<Integer, Consumer<ServerPlayerEntity>> actions,
                                       int slot, Block block, String id, int limit, int returnPage) {
        inv.setStack(slot, MenuButtons.button(Items.LIME_DYE, "§a设为 " + limit,
            List.of("§7方块 §f" + id, "§7单次 §f" + limit + " §7块")));
        actions.put(slot, p -> setLimitAndReopen(p, block, id, limit, returnPage));
    }

    private static void setLimitAndReopen(ServerPlayerEntity player, Block block, String id,
                                          int limit, int returnPage) {
        int clamped = VeinMineBlockConfig.clamp(limit);
        VeinMineBlockConfig.set(block, clamped);
        player.sendMessage(Text.literal("已设置 " + id + " 单次≤ " + clamped).formatted(Formatting.GREEN), true);
        openBlockDetail(player, id, returnPage);
    }

    private static void addClickedBlock(ServerPlayerEntity player, ItemStack clicked, int page) {
        if (clicked.isEmpty() || !(clicked.getItem() instanceof BlockItem bi)) {
            player.sendMessage(Text.literal("请点击物品栏里的方块。").formatted(Formatting.YELLOW), true);
            return;
        }
        Block block = bi.getBlock();
        VeinMineBlockConfig.set(block, VeinMineBlockConfig.MAX_LIMIT);
        String id = VeinMineBlockConfig.idOf(block);
        player.sendMessage(Text.literal("已加入连锁白名单：" + id + " 单次≤ " + VeinMineBlockConfig.MAX_LIMIT)
            .formatted(Formatting.GREEN), true);
        open(player, page);
    }

    private static ItemStack toggleButton(ServerPlayerEntity player) {
        boolean enabled = VeinMineTracker.isEnabled(player.getUuid());
        ItemStack stack = MenuButtons.button(enabled ? Items.LIME_DYE : Items.GRAY_DYE,
            "§f§l个人连锁开关",
            List.of("§7当前 " + (enabled ? "§a已开启" : "§c已关闭"), "", "§a▶ 点击切换"));
        if (enabled) MenuButtons.withGlow(stack);
        return stack;
    }

    private static void toggleVeinMine(ServerPlayerEntity player) {
        boolean now = VeinMineTracker.toggle(player.getUuid());
        player.sendMessage(now
            ? Text.literal("连锁采集已 §a开启").formatted(Formatting.GREEN)
            : Text.literal("连锁采集已 §c关闭").formatted(Formatting.YELLOW), true);
        open(player, 0);
    }

    private static ItemStack blockIcon(String id, int limit) {
        Block block = blockById(id);
        Item item = block == null || block.asItem() == Items.AIR ? Items.STONE : block.asItem();
        return MenuButtons.button(item, "§e§l" + shortName(id),
            List.of("§7ID §f" + id, "§7单次 §f" + limit + " §7块", "", "§a▶ 点击设置"));
    }

    private static ItemStack detailIcon(Block block, String id, int limit) {
        Item item = block.asItem() == Items.AIR ? Items.STONE : block.asItem();
        return MenuButtons.button(item, "§e§l" + shortName(id),
            List.of("§7ID §f" + id, "§7当前单次 §f" + limit + " §7块", "", "§8选择下方按钮调整"));
    }

    private static Block blockById(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) return null;
        return Registries.BLOCK.get(identifier);
    }

    private static String shortName(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
