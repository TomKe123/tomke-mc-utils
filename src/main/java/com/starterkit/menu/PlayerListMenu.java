package com.starterkit.menu;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 玩家列表子菜单：显示在线玩家（自己除外）的玩家头颅，点击 tp 过去。
 *
 * 布局（9x3）：
 *   前 21 格 = 玩家槽位（0..20）
 *   槽 26 = 返回主菜单
 *   若一页放不下，槽 24 = 上一页 / 槽 25 = 下一页
 *
 * 玩家头颅通过 SkullOwner NBT 显示对应玩家皮肤（客户端会异步向 Mojang 请求皮肤）。
 */
public final class PlayerListMenu {

    private static final int PAGE_SIZE = 21;
    private static final int PREV_SLOT = 18;
    private static final int NEXT_SLOT = 26;
    private static final int BACK_SLOT = 22;
    private static final Text TITLE = Text.literal("§b在线玩家");

    private PlayerListMenu() {}

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, 0);
    }

    public static void open(ServerPlayerEntity viewer, int page) {
        List<ServerPlayerEntity> others = new ArrayList<>();
        for (ServerPlayerEntity p : viewer.getServer().getPlayerManager().getPlayerList()) {
            if (!p.getUuid().equals(viewer.getUuid())) others.add(p);
        }

        int totalPages = Math.max(1, (others.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = MenuScreenHandler.buildInventory(3);
        Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, others.size());
        for (int i = start; i < end; i++) {
            ServerPlayerEntity target = others.get(i);
            int slot = i - start;
            inv.setStack(slot, playerHead(target));
            String targetName = target.getGameProfile().getName();
            actions.put(slot, viewerRef -> teleportTo(viewerRef, targetName));
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

    // ------------------------------------------------------------------ actions

    /**
     * 传送到指定玩家。查目标时用玩家管理器按名字取，
     * 传送过程中断线或对方切世界都能安全兜底。
     */
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

    private static ItemStack playerHead(ServerPlayerEntity target) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        NbtCompound nbt = stack.getOrCreateNbt();
        // 使用 GameProfile 让客户端异步拉取皮肤
        nbt.put("SkullOwner", NbtHelper.writeGameProfile(new NbtCompound(), target.getGameProfile()));

        String name = target.getGameProfile().getName();
        stack.setCustomName(Text.literal(name)
            .formatted(Formatting.YELLOW)
            .styled(s -> s.withItalic(false)));

        NbtCompound display = nbt.getCompound("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("点击传送").formatted(Formatting.GRAY)
                .styled(s -> s.withItalic(false)))));
        // 附上世界信息
        String dim = target.getWorld().getRegistryKey().getValue().toString();
        lore.add(NbtString.of(Text.Serializer.toJson(
            Text.literal("§8" + dim)
                .styled(s -> s.withItalic(false)))));
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
