package com.tomkestookit.trainingdummy;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 训练假人 DPS 测试菜单的 ScreenHandler。
 *
 * 只有 9 个槽位（3×3 Dispenser 布局），不显示玩家背包。
 * 所有槽位只读，点击触发对应动作。
 */
public final class TrainingDummyMenuHandler extends ScreenHandler {

    private final Inventory inventory;
    private final Map<Integer, Consumer<ServerPlayerEntity>> actions;

    public TrainingDummyMenuHandler(int syncId, PlayerInventory playerInventory,
                                    Inventory inventory,
                                    Map<Integer, Consumer<ServerPlayerEntity>> actions) {
        super(ScreenHandlerType.GENERIC_3X3, syncId);
        this.inventory = inventory;
        this.actions = actions;

        // 只添加菜单的 9 个槽位（不添加玩家背包槽）
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inventory, i, 8 + (i % 3) * 18, 18 + (i / 3) * 18));
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // 完全接管点击，不允许任何拖拽/交换
        if (slotIndex < 0 || slotIndex >= 9) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;

        Consumer<ServerPlayerEntity> action = actions.get(slotIndex);
        if (action == null) return;

        sp.playSound(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.MASTER, 0.5f, 1.0f);
        action.accept(sp);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // 禁用 shift-click
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
