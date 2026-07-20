package com.starterkit.menu;

import com.starterkit.landmark.Landmark;
import com.starterkit.landmark.LandmarkCommand;
import com.starterkit.landmark.LandmarkStore;
import com.starterkit.landmark.LandmarkTeleport;
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
 * 地标子菜单：分页展示所有地标，点击 tp 过去。
 *
 * 布局跟玩家列表一致（前 21 格 + 翻页 + 返回）。
 */
public final class LandmarkMenu {

    private static final int PAGE_SIZE = 21;
    private static final int PREV_SLOT = 18;
    private static final int NEXT_SLOT = 26;
    private static final int BACK_SLOT = 22;
    private static final Text TITLE = Text.literal("§b服务器地标");

    private LandmarkMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, 0);
    }

    public static void open(ServerPlayerEntity viewer, int page) {
        List<Landmark> list = new ArrayList<>(LandmarkStore.all());
        int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = MenuScreenHandler.buildInventory(3);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, list.size());
        for (int i = start; i < end; i++) {
            Landmark lm = list.get(i);
            int slot = i - start;
            inv.setStack(slot, landmarkIcon(lm));
            actions.put(slot, p -> {
                LandmarkTeleport.execute(p, lm);
                p.closeHandledScreen();
            });
        }

        if (list.isEmpty()) {
            inv.setStack(13, hint());
        }

        if (page > 0) {
            inv.setStack(PREV_SLOT, arrow("§f← 上一页 (" + page + "/" + totalPages + ")"));
            int prev = page - 1;
            actions.put(PREV_SLOT, p -> open(p, prev));
        }
        if (page < totalPages - 1) {
            inv.setStack(NEXT_SLOT, arrow("§f下一页 → (" + (page + 2) + "/" + totalPages + ")"));
            int next = page + 1;
            actions.put(NEXT_SLOT, p -> open(p, next));
        }

        inv.setStack(BACK_SLOT, backButton());
        actions.put(BACK_SLOT, MenuUi::open);

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new MenuScreenHandler(syncId, playerInv, inv, actions),
            TITLE
        ));
    }

    // ------------------------------------------------------------------ items

    private static ItemStack landmarkIcon(Landmark lm) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.setCustomName(Text.literal(lm.name)
            .formatted(Formatting.YELLOW)
            .styled(s -> s.withItalic(false)));

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§7" + LandmarkCommand.dimShort(lm.dimension))
                .styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal(String.format("§7%.0f, %.0f, %.0f", lm.x, lm.y, lm.z))
                .styled(s -> s.withItalic(false)))));
        if (lm.creator != null && !lm.creator.isEmpty()) {
            lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§8添加者：" + lm.creator)
                    .styled(s -> s.withItalic(false)))));
        }
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§a▶ 点击传送")
                .styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);

        // 隐藏 filled_map 自带的地图数据 tooltip（HideFlags bit 32 = Map Decorations）
        nbt.putInt("HideFlags", 63);
        return stack;
    }

    private static ItemStack hint() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.setCustomName(Text.literal("§7暂无地标").styled(s -> s.withItalic(false)));
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8/landmark add <名字>").styled(s -> s.withItalic(false)))));
        display.put("Lore", lore);
        nbt.put("display", display);
        return stack;
    }

    private static ItemStack arrow(String name) {
        ItemStack stack = new ItemStack(Items.ARROW);
        stack.setCustomName(Text.literal(name).styled(s -> s.withItalic(false)));
        return stack;
    }

    private static ItemStack backButton() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.setCustomName(Text.literal("§c返回主菜单").styled(s -> s.withItalic(false)));
        return stack;
    }
}
