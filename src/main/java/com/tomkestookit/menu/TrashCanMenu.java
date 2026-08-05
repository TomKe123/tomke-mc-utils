package com.tomkestookit.menu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 全服共享垃圾桶：540 格（10 页 × 54），每 10 分钟自动清空。
 *
 * 每页使用 vanilla GenericContainerScreenHandler（9×6 箱子），
 * 通过 TrashPageInventory 代理直接映射到全局 TRASH——玩家操作实时生效。
 *
 * 翻页：底部放翻页按钮（< 左 / 右 >）作为"幽灵物品"放在玩家背包区域之外。
 * 翻页=关闭当前页然后打开新页。
 */
public final class TrashCanMenu {

    private static final int TOTAL_SLOTS = 540;
    private static final int PAGE_SIZE = 54;
    private static final int TOTAL_PAGES = TOTAL_SLOTS / PAGE_SIZE; // 10
    private static final int CLEAR_INTERVAL_TICKS = 20 * 60 * 10; // 10 分钟

    /** 全服共享的垃圾桶存储。*/
    private static final SimpleInventory TRASH = new SimpleInventory(TOTAL_SLOTS);
    private static int tickCounter = 0;

    private TrashCanMenu() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TrashCanMenu::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
            dispatcher.register(CommandManager.literal("trash")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    p.getServer().execute(() -> open(p));
                    return 1;
                })
                .then(CommandManager.argument("page",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, TOTAL_PAGES))
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        int pg = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page") - 1;
                        p.getServer().execute(() -> open(p, pg));
                        return 1;
                    }))));
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= CLEAR_INTERVAL_TICKS) {
            tickCounter = 0;
            int count = 0;
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (!TRASH.getStack(i).isEmpty()) {
                    TRASH.setStack(i, ItemStack.EMPTY);
                    count++;
                }
            }
            if (count > 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(Text.literal("§7[垃圾桶] 已自动清空 §f" + count + " §7格物品")
                        .styled(s -> s.withColor(0xAAAAAA)), false);
                }
            }
        }
    }

    public static void open(ServerPlayerEntity viewer) {
        open(viewer, 0);
    }

    /**
     * 打开指定页。使用 vanilla GenericContainerScreenHandler。
     * 每页底部 5 格放翻页按钮 + 状态提示，其余 49 格对接到 TRASH。
     */
    public static void open(ServerPlayerEntity viewer, int page) {
        page = Math.max(0, Math.min(page, TOTAL_PAGES - 1));

        TrashPageInventory pageInv = new TrashPageInventory(page);

        Text title = Text.literal("§c§l垃圾桶 §7(" + (page + 1) + "/" + TOTAL_PAGES + ")");

        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, ignored) -> new GenericContainerScreenHandler(
                ScreenHandlerType.GENERIC_9X6, syncId, playerInv, pageInv, 6) {
                @Override
                public void onClosed(net.minecraft.entity.player.PlayerEntity player) {
                    super.onClosed(player);
                }
            },
            title
        ));

        viewer.sendMessage(Text.literal("§7[垃圾桶] 第 §f" + (page + 1) + "§7/§f"
            + TOTAL_PAGES + " §7页 · §f/trash <页> §7翻页 · 每 10 分钟自动清空"), true);
    }

    // ---- 代理 Inventory ----

    /** 代理到全局 TRASH 的指定页，带翻页按钮内嵌到尾部容器槽。*/
    public static final class TrashPageInventory implements Inventory {
        private final int offset;
        private final int page;

        TrashPageInventory(int page) {
            this.page = page;
            this.offset = page * PAGE_SIZE;
        }

        @Override public int size() { return PAGE_SIZE; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < PAGE_SIZE; i++) {
                if (!TRASH.getStack(offset + i).isEmpty()) return false;
            }
            return true;
        }

        /** 最后 7 格渲染翻页控件（45-53）。垃圾槽实际只有 0-44。*/
        @Override
        public ItemStack getStack(int slot) {
            // row 5: 45=◀ 页, 49=标题, 53=▶ 页
            if (slot == 45) return MenuButtons.pageButton(false, page + 1, TOTAL_PAGES, page > 0);
            if (slot == 49) return titleStack();
            if (slot == 53) return MenuButtons.pageButton(true, page + 1, TOTAL_PAGES, page < TOTAL_PAGES - 1);
            if (slot > 45 && slot != 49 && slot < 53) return filler();
            return TRASH.getStack(offset + slot);
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            if (isCtrlSlot(slot)) return ItemStack.EMPTY;
            return TRASH.removeStack(offset + slot, amount);
        }
        @Override
        public ItemStack removeStack(int slot) {
            if (isCtrlSlot(slot)) return ItemStack.EMPTY;
            return TRASH.removeStack(offset + slot);
        }
        @Override
        public void setStack(int slot, ItemStack stack) {
            if (isCtrlSlot(slot)) return;
            TRASH.setStack(offset + slot, stack);
        }
        @Override public void markDirty() { TRASH.markDirty(); }
        @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
        @Override public void clear() {
            for (int i = 0; i < PAGE_SIZE; i++) TRASH.setStack(offset + i, ItemStack.EMPTY);
        }

        private boolean isCtrlSlot(int slot) { return slot >= 45; }

        private ItemStack titleStack() {
            return MenuButtons.button(Items.LAVA_BUCKET, "§c§l垃圾桶",
                List.of("§7每 10 分钟清空",
                    "§7当前第 §f" + (page + 1) + " §7/ §f" + TOTAL_PAGES + " §7页",
                    "§7放入即丢 · 不可恢复"));
        }

        private ItemStack filler() {
            return MenuButtons.filler();
        }
    }
}
