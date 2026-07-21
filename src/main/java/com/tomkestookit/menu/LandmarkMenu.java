package com.tomkestookit.menu;

import com.tomkestookit.landmark.Landmark;
import com.tomkestookit.landmark.LandmarkStore;
import com.tomkestookit.landmark.LandmarkTeleport;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 地标子菜单。9x6 布局：
 *
 *   row 0: [◈ all] [◈ 主] [◈ 下] [◈ 末]  维度筛选 tab (槽 0-3)
 *   row 1-4: 地标格 9-44 共 36 个
 *   row 5: [◀ 45]  .  .  .  [返回 49]  .  .  .  [▶ 53]
 *
 * 选中 tab 加发光光效。
 */
public final class LandmarkMenu {

    private static final int PAGE_SIZE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final Text TITLE = Text.literal("§b§l§n服务器地标");

    private static final String FILTER_ALL = "";
    private static final String FILTER_OVERWORLD = "minecraft:overworld";
    private static final String FILTER_NETHER = "minecraft:the_nether";
    private static final String FILTER_END = "minecraft:the_end";

    private LandmarkMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, FILTER_ALL, 0);
    }

    public static void open(ServerPlayerEntity viewer, String filter, int page) {
        List<Landmark> filtered = new ArrayList<>();
        for (Landmark lm : LandmarkStore.all()) {
            if (filter.isEmpty() || filter.equals(lm.dimension)) filtered.add(lm);
        }
        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = MenuScreenHandler.buildInventory(6);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        // 维度 tab
        inv.setStack(0, tabButton(Items.NETHER_STAR, "§f全部",
            LandmarkStore.all().size(), filter.equals(FILTER_ALL)));
        actions.put(0, p -> open(p, FILTER_ALL, 0));

        inv.setStack(1, tabButton(Items.GRASS_BLOCK, "§a主世界",
            countIn(FILTER_OVERWORLD), filter.equals(FILTER_OVERWORLD)));
        actions.put(1, p -> open(p, FILTER_OVERWORLD, 0));

        inv.setStack(2, tabButton(Items.NETHERRACK, "§c下界",
            countIn(FILTER_NETHER), filter.equals(FILTER_NETHER)));
        actions.put(2, p -> open(p, FILTER_NETHER, 0));

        inv.setStack(3, tabButton(Items.END_STONE, "§d末地",
            countIn(FILTER_END), filter.equals(FILTER_END)));
        actions.put(3, p -> open(p, FILTER_END, 0));

        // 页面内容
        if (filtered.isEmpty()) {
            inv.setStack(22, MenuButtons.button(Items.PAPER, "§7暂无地标",
                filter.isEmpty()
                    ? List.of("§8使用 §f/landmark add <名字> §8添加")
                    : List.of("§8该维度还没有地标",
                        "§8/landmark add <名字>")));
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, filtered.size());
            for (int i = start; i < end; i++) {
                Landmark lm = filtered.get(i);
                int slot = FIRST_SLOT + (i - start);
                inv.setStack(slot, landmarkIcon(lm));
                actions.put(slot, p -> {
                    LandmarkTeleport.execute(p, lm);
                    p.closeHandledScreen();
                });
            }
        }

        // 分页 & 返回
        String filterRef = filter;
        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;
        inv.setStack(PREV_SLOT, MenuButtons.pageButton(false, page, totalPages, hasPrev));
        if (hasPrev) {
            final int prev = page - 1;
            actions.put(PREV_SLOT, p -> open(p, filterRef, prev));
        }
        inv.setStack(NEXT_SLOT, MenuButtons.pageButton(true, page, totalPages, hasNext));
        if (hasNext) {
            final int next = page + 1;
            actions.put(NEXT_SLOT, p -> open(p, filterRef, next));
        }

        inv.setStack(BACK_SLOT, MenuButtons.backButton());
        actions.put(BACK_SLOT, MenuUi::open);

        MenuButtons.fillBackground(inv);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    // ------------------------------------------------------------------ items

    private static ItemStack tabButton(net.minecraft.item.Item item,
                                       String name, int count, boolean selected) {
        List<String> lore = List.of(
            "§7地标 §f" + count + " §7个",
            selected ? "§a● 当前筛选" : "§8○ 点击筛选"
        );
        ItemStack stack = MenuButtons.button(item, name, lore);
        if (selected) MenuButtons.withGlow(stack);
        return stack;
    }

    private static ItemStack landmarkIcon(Landmark lm) {
        List<String> lore = new ArrayList<>();
        lore.add("§7维度 §f" + MenuUi.dimShort(lm.dimension));
        lore.add(String.format("§7坐标 §f%.0f, %.0f, %.0f", lm.x, lm.y, lm.z));
        if (lm.creator != null && !lm.creator.isEmpty()) {
            lore.add("§8添加者 " + lm.creator);
        }
        lore.add("");
        lore.add("§a▶ 点击传送");
        ItemStack stack = MenuButtons.button(Items.FILLED_MAP, "§e§l" + lm.name, lore);
        // filled_map 的地图数据 tooltip 得隐藏，否则显示"未探索的地图"
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("HideFlags", 63);
        return stack;
    }

    private static int countIn(String dim) {
        int n = 0;
        for (Landmark lm : LandmarkStore.all()) if (dim.equals(lm.dimension)) n++;
        return n;
    }
}
