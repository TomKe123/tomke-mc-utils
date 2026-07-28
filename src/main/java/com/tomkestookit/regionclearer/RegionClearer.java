package com.tomkestookit.regionclearer;

import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

/**
 * 区域清除斧的核心工具类：tier 枚举 + 是否是我们的斧。
 * 无 tier 门槛：所有方块都会被清，只是掉落策略不同（见 RegionClearerHandler）。
 *
 * 复用 vanilla 六种斧头材质做外观，通过 NBT 标记 {@link #NBT_TAG} 区分。
 * NBT 里存的是 {@link ClearerTier#ordinal()}，读回来用 ordinal()->fromOrdinal 转换。
 */
public final class RegionClearer {

    public static final String NBT_TAG = "TomkesTookitRegionClearer";

    private RegionClearer() {}

    /**
     * 挖掘能力级别（对齐 vanilla 的 needs_*_tool tag：STONE=1, IRON=2, DIAMOND=3）。
     * 六个 tier 一次性映射：外观 / 材质 / 使用次数 / 每次耐久扣减。
     *
     * useCount 是我们想让玩家能用的次数，perUseDamage 由 vanilla maxDamage / useCount
     * 反推得到；扣减到 maxDamage 时 vanilla 自动破碎。这样耐久条会显示得比较自然。
     */
    public enum ClearerTier {
        WOOD(0, Items.WOODEN_AXE, "木", 1),
        STONE(1, Items.STONE_AXE, "石", 1),
        IRON(2, Items.IRON_AXE, "铁", 3),
        GOLD(2, Items.GOLDEN_AXE, "金", 3),
        DIAMOND(3, Items.DIAMOND_AXE, "钻石", 5),
        NETHERITE(3, Items.NETHERITE_AXE, "下界合金", 10);

        public final int level;
        public final Item baseItem;
        public final String zh;
        public final int useCount;

        ClearerTier(int level, Item baseItem, String zh, int useCount) {
            this.level = level;
            this.baseItem = baseItem;
            this.zh = zh;
            this.useCount = useCount;
        }

        /**
         * 每次 confirm 消耗的耐久。用 maxDamage/useCount 的向上取整，
         * 保证 useCount 次内工具耐久归零。
         */
        public int perUseDamage() {
            int max = new ItemStack(baseItem).getMaxDamage();
            return Math.max(1, (int) Math.ceil((double) max / useCount));
        }
    }

    /** 是否是任意 tier 的 RegionClearer。*/
    public static boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof AxeItem)) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_TAG);
    }

    /** 取 tier，非我们的工具返回 null。*/
    public static ClearerTier getTier(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_TAG)) return null;
        int ord = nbt.getInt(NBT_TAG);
        ClearerTier[] all = ClearerTier.values();
        if (ord < 0 || ord >= all.length) return null;
        return all[ord];
    }

}
