package com.tomkestookit.regionfiller;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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

    /**
     * 该位置是否可被 {@code type} 覆盖填充：
     *   - 纯空气：允许（保留原语义）
     *   - 同类流体的"非源方块"（水桶匹配流动水、岩浆桶匹配流动岩浆）：允许，用源覆盖 → 相当于把流水凝成水源
     *
     * 特意只放行同类流体的流动态：
     *   - waterlogged 台阶/楼梯这类含水但主方块非流体的方块不动，避免误伤结构
     *   - 水桶不会把流动岩浆变成水源（反之亦然），语义清晰
     *   - 源方块本身跳过（isStill()=true），既不浪费 setBlockState 也不会 tick 邻居
     */
    public static boolean isFillable(BlockState state, FillerType type) {
        if (state.isAir()) return true;
        var fs = state.getFluidState();
        if (fs.isEmpty() || fs.isStill()) return false;
        return type == FillerType.WATER
            ? fs.isIn(net.minecraft.registry.tag.FluidTags.WATER)
            : fs.isIn(net.minecraft.registry.tag.FluidTags.LAVA);
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
