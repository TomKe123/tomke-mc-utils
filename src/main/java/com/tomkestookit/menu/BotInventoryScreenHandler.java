package com.tomkestookit.menu;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 假人容器：容器区分两种槽位：
 *   - 背包槽（映射到假人 PlayerInventory）：完全可读可写，走 vanilla 拖拽 / shift-click / Q 键 drop
 *   - 装饰按钮槽：{@link LockedSlot} 包一层，canInsert / canTakeItems 都 false，
 *     玩家无法把物品拖入或拿走玻璃板 / 按钮图标
 *
 * 装饰槽的点击 action 从 actions / rightActions 派发。
 */
public final class BotInventoryScreenHandler extends GenericContainerScreenHandler {

    private final int menuSize;
    private final Map<Integer, Consumer<ServerPlayerEntity>> actions;
    private final Map<Integer, Consumer<ServerPlayerEntity>> rightActions;

    private final java.util.UUID targetUuid;

    public BotInventoryScreenHandler(int syncId, PlayerInventory playerInventory,
                                     Inventory menuInventory,
                                     Map<Integer, Consumer<ServerPlayerEntity>> actions,
                                     Map<Integer, Consumer<ServerPlayerEntity>> rightActions,
                                     String targetName,
                                     java.util.UUID targetUuid) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, menuInventory, 6);
        this.menuSize = menuInventory.size();
        this.actions = actions;
        this.rightActions = rightActions;
        this.targetUuid = targetUuid;

        // 遍历 vanilla 生成的 slots，把装饰槽（不映射到假人背包）替换成 LockedSlot
        for (int i = 0; i < menuSize && i < this.slots.size(); i++) {
            if (PlayerDetailMenu.mapToInventorySlot(i) == null) {
                Slot original = this.slots.get(i);
                this.slots.set(i, new LockedSlot(original.inventory, original.getIndex(),
                    original.x, original.y));
            }
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (targetUuid != null && player instanceof ServerPlayerEntity sp) {
            BotMenuTracker.unwatch(sp.getUuid());
        }
    }

    /** 装饰 / 按钮槽：仅用于显示与点击，玩家无法拖入或拿走物品。*/
    private static final class LockedSlot extends Slot {
        LockedSlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }
        @Override public boolean canInsert(ItemStack stack) { return false; }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        // slotIndex == -999 是 vanilla “空外部区域”点击，用来扔 cursor stack。透传。
        if (slotIndex < 0) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        if (slotIndex < menuSize) {
            // 装饰按钮槽：走 actions / rightActions；不允许 vanilla 处理任何拖拽
            if (PlayerDetailMenu.mapToInventorySlot(slotIndex) == null) {
                Consumer<ServerPlayerEntity> action = null;
                if (actionType == SlotActionType.PICKUP && button == 1) {
                    action = rightActions == null ? null : rightActions.get(slotIndex);
                }
                if (action == null) action = actions.get(slotIndex);
                if (action != null) {
                    sp.playSound(SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundCategory.MASTER, 0.5f, 1.0f);
                    action.accept(sp);
                }
                return;
            }
            // 背包槽：走 vanilla（含 THROW / QUICK_MOVE / PICKUP / SWAP）
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // 装饰槽 shift-click：LockedSlot.canTakeItems=false 会让 vanilla quickMove 直接返回空
        return super.quickMove(player, slot);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
