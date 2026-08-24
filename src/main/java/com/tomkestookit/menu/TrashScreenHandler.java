package com.tomkestookit.menu;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * 垃圾桶专用容器：接管所有点击，走翻页 / 提取流程。
 *
 * 之前那版沿用 {@link GenericContainerScreenHandler} 直接绑 SimpleInventory，翻页按钮
 * 是"幽灵物品"，vanilla 点击处理会把它当成正常槽位——所以什么都不做，或者更糟：
 * 把幽灵物品塞给玩家。这里彻底覆盖 {@link #onSlotClick} 和 {@link #quickMove}，
 * 页内槽 → 走 {@link TrashCanMenu#onEntryClicked}；45/53 → open 上/下一页；
 * 其它菜单槽 → no-op；玩家背包槽 → 一律禁移。
 */
public final class TrashScreenHandler extends GenericContainerScreenHandler {

    private static final int MENU_SIZE = 54;
    private static final int SLOTS_PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_TITLE = 49;
    private static final int SLOT_NEXT = 53;

    private final int page;
    private final int totalPages;

    public TrashScreenHandler(int syncId, PlayerInventory playerInventory, Inventory menuInventory,
                              int page, int totalPages) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, menuInventory, 6);
        this.page = page;
        this.totalPages = totalPages;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;

        // 玩家背包槽（>= MENU_SIZE）：一律禁移
        if (slotIndex >= MENU_SIZE) return;

        // 翻页控件
        if (slotIndex == SLOT_PREV) {
            if (page > 0) {
                playClick(sp);
                sp.getServer().execute(() -> TrashCanMenu.open(sp, page - 1));
            }
            return;
        }
        if (slotIndex == SLOT_NEXT) {
            if (page < totalPages - 1) {
                playClick(sp);
                sp.getServer().execute(() -> TrashCanMenu.open(sp, page + 1));
            }
            return;
        }
        if (slotIndex == SLOT_TITLE) return;
        if (slotIndex > SLOT_PREV && slotIndex < SLOT_NEXT) return; // 装饰槽

        // 页内物品槽（0..SLOTS_PER_PAGE-1）：走提取流程
        if (slotIndex >= SLOTS_PER_PAGE) return;
        ItemStack shown = this.getSlot(slotIndex).getStack();
        if (shown.isEmpty()) return;
        playClick(sp);
        // 传 stack 副本：即使玩家断线中途 ENTRIES 变化，NBT-stamped key 仍可反查条目
        ItemStack copy = shown.copy();
        sp.getServer().execute(() -> TrashCanMenu.onEntryClicked(sp, copy, page));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // 禁止 shift-click：不把菜单物品搬进背包，也不把背包物品搬进菜单
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private static void playClick(ServerPlayerEntity sp) {
        sp.playSound(SoundEvents.UI_BUTTON_CLICK.value(),
            SoundCategory.MASTER, 0.5f, 1.0f);
    }
}
