package com.tomkestookit.reforge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/**
 * 横扫范围扩大重铸——工具类。SweepRangeMixin 每次玩家 attack 时调用一次。
 *
 * 返回值：0 = 无 / 原版 · 1 = 3×3 (sweep_widener) · 2 = 5×5 (sweep_grand)
 * 高 tier 覆盖低 tier（同一把武器上同时有两块的话，取更大的）。
 */
public final class SweepRangeHandler {

    private SweepRangeHandler() {}

    /** 快速读主手 tier；无武器/非本包重铸 → 0。*/
    public static int getSweepTier(PlayerEntity player) {
        if (player == null) return 0;
        ItemStack main = player.getMainHandStack();
        if (main.isEmpty()) return 0;
        NbtCompound nbt = main.getNbt();
        if (nbt == null || !nbt.contains(ReforgeRegistry.APPLIED_TAG)) return 0;

        NbtList applied = nbt.getList(ReforgeRegistry.APPLIED_TAG, 8);
        boolean grand = false, widener = false;
        for (int i = 0; i < applied.size(); i++) {
            String id = applied.getString(i);
            if ("sweep_grand".equals(id)) grand = true;
            else if ("sweep_widener".equals(id)) widener = true;
        }
        if (grand) return 2;
        if (widener) return 1;
        return 0;
    }
}
