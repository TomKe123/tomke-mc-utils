package com.tomkestookit.menu;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 只读菜单容器。根据 inventory 大小自适应 9x3 / 9x6 布局，
 * 玩家不能取走/放入/交换物品；只有 slot -> action 表里注册过的槽位
 * 在被点击时会触发对应动作。其他槽位（含玩家自己的背包区）点击均 no-op。
 */
public final class MenuScreenHandler extends GenericContainerScreenHandler {

    /** 菜单容器容量，等于 9 * rows。所有 >= 该值的槽位是玩家背包，一律禁用。*/
    private final int menuSize;
    private final Map<Integer, Consumer<ServerPlayerEntity>> actions;
    private final Map<Integer, Consumer<ServerPlayerEntity>> rightClickActions;
    private final BiConsumer<ServerPlayerEntity, ItemStack> playerInventoryClick;
    private Consumer<ServerPlayerEntity> onCloseCallback;

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory,
                             Inventory menuInventory,
                             Map<Integer, Consumer<ServerPlayerEntity>> actions) {
        this(syncId, playerInventory, menuInventory, actions, null);
    }

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory,
                             Inventory menuInventory,
                             Map<Integer, Consumer<ServerPlayerEntity>> actions,
                             BiConsumer<ServerPlayerEntity, ItemStack> playerInventoryClick) {
        this(syncId, playerInventory, menuInventory, actions, Map.of(), playerInventoryClick);
    }

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory,
                             Inventory menuInventory,
                             Map<Integer, Consumer<ServerPlayerEntity>> actions,
                             Map<Integer, Consumer<ServerPlayerEntity>> rightClickActions,
                             BiConsumer<ServerPlayerEntity, ItemStack> playerInventoryClick) {
        super(pickType(menuInventory.size()), syncId, playerInventory, menuInventory,
              menuInventory.size() / 9);
        this.menuSize = menuInventory.size();
        this.actions = actions;
        this.rightClickActions = rightClickActions;
        this.playerInventoryClick = playerInventoryClick;
    }

    /** 关闭菜单时触发。工厂无法传参，用 setter 从外部注册。*/
    public void setOnClose(Consumer<ServerPlayerEntity> callback) {
        this.onCloseCallback = callback;
    }

    @Override
    public void onClosed(net.minecraft.entity.player.PlayerEntity player) {
        super.onClosed(player);
        if (onCloseCallback != null && player instanceof ServerPlayerEntity sp) {
            onCloseCallback.accept(sp);
        }
    }

    /** 只支持 9x3 / 9x6 两种；其余 fallback 到最接近的 9x6。*/
    private static ScreenHandlerType<GenericContainerScreenHandler> pickType(int size) {
        return switch (size) {
            case 27 -> ScreenHandlerType.GENERIC_9X3;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // 完全接管点击：任何点击都不传递给 super，防止玩家把菜单物品拖走或换位置。
        if (slotIndex < 0) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        if (slotIndex >= menuSize) {
            if (playerInventoryClick == null || slotIndex >= this.slots.size()) return;
            playerInventoryClick.accept(serverPlayer, this.getSlot(slotIndex).getStack());
            return;
        }

        Consumer<ServerPlayerEntity> action = (actionType == SlotActionType.PICKUP && button == 1)
            ? rightClickActions.getOrDefault(slotIndex, actions.get(slotIndex))
            : actions.get(slotIndex);
        if (action == null) return;

        // 命中一个真正有 action 的槽位才播放音效，装饰玻璃板不响
        serverPlayer.playSound(
            SoundEvents.UI_BUTTON_CLICK.value(),
            SoundCategory.MASTER, 0.5f, 1.0f);
        action.accept(serverPlayer);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // 禁用 shift-click：不会把菜单物品塞进背包，也不会把背包物品塞进菜单
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /** 建一个大小刚好的只读展示用 inventory。*/
    public static Inventory buildInventory(int rows) {
        return new SimpleInventory(rows * 9);
    }
}
