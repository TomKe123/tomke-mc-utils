package com.starterkit.menu;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 玩家列表子菜单。9x3 布局：
 *
 *   row 0: 顶部 header 装饰（亮色玻璃板 + 中央标题头颅）
 *   row 1: 前 9 个玩家（槽 9..17）
 *   row 2: [◀ prev] . . . [返回] . . . [next ▶]  底部导航
 *           18                22               26
 *
 * 每页最多 9 个玩家（槽 9-17）——比之前的 21 少，但配合按距离排序，
 * 一屏能看到"离我最近的 9 个"就够用。翻页更容易的话，找远处玩家也不难。
 *
 * 排序规则：同维度的按跟观察者的距离升序，跨维度的排在最后（无距离数值）。
 */
public final class PlayerListMenu {

    private static final int PAGE_SIZE = 9;
    private static final int FIRST_SLOT = 9;
    private static final int PREV_SLOT = 18;
    private static final int BACK_SLOT = 22;
    private static final int NEXT_SLOT = 26;
    private static final Text TITLE = Text.literal("§b§l§n在线玩家");

    private PlayerListMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, 0);
    }

    public static void open(ServerPlayerEntity viewer, int page) {
        List<ServerPlayerEntity> others = new ArrayList<>();
        for (ServerPlayerEntity p : viewer.getServer().getPlayerManager().getPlayerList()) {
            if (!p.getUuid().equals(viewer.getUuid())) others.add(p);
        }
        // 排序：同维度按距离升序，跨维度放最后
        others.sort(Comparator
            .<ServerPlayerEntity, Integer>comparing(p -> sameDimension(viewer, p) ? 0 : 1)
            .thenComparingDouble(p -> sameDimension(viewer, p) ? p.squaredDistanceTo(viewer) : 0));

        int totalPages = Math.max(1, (others.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = MenuScreenHandler.buildInventory(3);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // header 中央显示总人数
        inv.setStack(4, MenuButtons.button(Items.COMPASS,
            "§b§l在线玩家",
            List.of("§7共 §f" + others.size() + " §7人",
                "§7第 §f" + (page + 1) + " §7/ §f" + totalPages + " §7页")));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, others.size());
        for (int i = start; i < end; i++) {
            ServerPlayerEntity target = others.get(i);
            int slot = FIRST_SLOT + (i - start);
            inv.setStack(slot, playerHead(viewer, target));
            String targetName = target.getGameProfile().getName();
            actions.put(slot, viewerRef -> teleportTo(viewerRef, targetName));
        }

        // 分页 & 返回
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

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    // ------------------------------------------------------------------ actions

    private static void teleportTo(ServerPlayerEntity viewer, String targetName) {
        ServerPlayerEntity target = viewer.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            viewer.sendMessage(Text.literal("目标玩家已下线。").formatted(Formatting.RED), true);
            viewer.closeHandledScreen();
            return;
        }
        ServerWorld world = (ServerWorld) target.getWorld();
        viewer.teleport(world, target.getX(), target.getY(), target.getZ(),
            target.getYaw(), target.getPitch());
        viewer.sendMessage(Text.literal("已传送到 " + targetName).formatted(Formatting.GREEN), true);
        viewer.closeHandledScreen();
    }

    // ------------------------------------------------------------------ items

    private static ItemStack playerHead(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        String name = target.getGameProfile().getName();
        String dim = MenuUi.dimShort(target.getWorld().getRegistryKey().getValue().toString());
        List<String> lore = new ArrayList<>();
        if (sameDimension(viewer, target)) {
            int distance = (int) Math.round(Math.sqrt(target.squaredDistanceTo(viewer)));
            lore.add("§7距离 §f" + distance + " §7格");
            lore.add("§7维度 §f" + dim);
        } else {
            lore.add("§7维度 §f" + dim + " §8(跨维度)");
        }
        lore.add("§7生命 §c" + (int) Math.ceil(target.getHealth())
            + "§7/§c" + (int) Math.ceil(target.getMaxHealth())
            + "  §7等级 §e" + target.experienceLevel);
        lore.add("");
        lore.add("§a▶ 点击传送");
        return MenuButtons.skull(target.getGameProfile(),
            "§e§l" + name, lore);
    }

    private static boolean sameDimension(ServerPlayerEntity a, ServerPlayerEntity b) {
        return a.getWorld().getRegistryKey().equals(b.getWorld().getRegistryKey());
    }
}
