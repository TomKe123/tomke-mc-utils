package com.tomkestookit.regionfiller;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

/**
 * 附魔水/岩浆桶：3×3 全水桶 → 附魔水桶；3×3 全岩浆桶 → 附魔岩浆桶。
 * 一次性物品：一次 confirm 消耗一个；只填空气位置。
 *
 * 与 RegionClearer 一样复用 vanilla 桶 + NBT 标记，不注册新物品。
 */
public final class RegionFiller {

    public static final String NBT_TAG = "TomkesTookitRegionFiller";

    private RegionFiller() {}

    public enum FillerType {
        WATER(Items.WATER_BUCKET, Blocks.WATER, "水"),
        LAVA(Items.LAVA_BUCKET, Blocks.LAVA, "岩浆");

        public final Item baseItem;
        public final Block fluidBlock;
        public final String zh;

        FillerType(Item baseItem, Block fluidBlock, String zh) {
            this.baseItem = baseItem;
            this.fluidBlock = fluidBlock;
            this.zh = zh;
        }
    }

    /** 是否是我们的附魔桶（NBT 标记 + item 匹配）。*/
    public static boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_TAG)) return false;
        Item i = stack.getItem();
        return i == Items.WATER_BUCKET || i == Items.LAVA_BUCKET;
    }

    /** 取 type，非我们的工具返回 null。*/
    public static FillerType getType(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_TAG)) return null;
        Item i = stack.getItem();
        if (i != Items.WATER_BUCKET && i != Items.LAVA_BUCKET) return null;
        int ord = nbt.getInt(NBT_TAG);
        FillerType[] all = FillerType.values();
        if (ord < 0 || ord >= all.length) return null;
        return all[ord];
    }
}
