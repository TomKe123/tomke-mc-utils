package com.tomkestookit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 自动合成器共享库存。
 *
 * 槽位：
 *   0..8 = 材料缓存池（横排，任意在配方里出现过的物品都能进）
 *   9    = 输出格
 *
 * 面向：
 *   下方（DOWN） → 只能从输出槽 9 抽出（→ 底部漏斗）
 *   其它面        → 只能塞入 0..8 材料槽（→ 顶部/侧面漏斗）
 */
public final class AutoCrafterInventory implements SidedInventory {
    private final AutoCrafterTracker.Config config;
    private final ServerWorld world;
    private final BlockPos pos;
    private final ItemStack[] stacks = new ItemStack[10];

    /** 记录每槽上次 flush 到 config 时的引用，只 flush 变化过的槽。*/
    private final ItemStack[] lastFlushed = new ItemStack[10];

    public AutoCrafterInventory(AutoCrafterTracker.Config config, ServerWorld world, BlockPos pos) {
        this.config = config;
        this.world = world;
        this.pos = pos;
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = AutoCrafterTracker.inventoryStack(config, i);
            lastFlushed[i] = stacks[i].copy();
        }
    }

    @Override public int size() { return stacks.length; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return stacks[slot]; }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack result = stacks[slot].split(amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack result = stacks[slot];
        stacks[slot] = ItemStack.EMPTY;
        markDirty();
        return result;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        stacks[slot] = stack;
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        markDirty();
    }
    @Override public void markDirty() {
        // 只写变化的槽；save() 只是标 dirty，不落盘（handler 定时 flush）
        boolean anyChanged = false;
        for (int i = 0; i < stacks.length; i++) {
            if (!ItemStack.areEqual(stacks[i], lastFlushed[i])) {
                AutoCrafterTracker.setInventoryStack(config, i, stacks[i]);
                lastFlushed[i] = stacks[i].copy();
                anyChanged = true;
            }
        }
        if (anyChanged) AutoCrafterTracker.save();
    }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return player.getWorld() == world && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
    @Override public void clear() {
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        markDirty();
    }

    /**
     * 材料缓存槽（0..8）：每个槽绑定一种去重后的配方物品。
     *
     * 举例：配方需要 8 绿宝石块 + 1 空瓶 → 去重物品 = [绿宝石块, 空瓶]
     *   - 槽 0：只接受绿宝石块
     *   - 槽 1：只接受空瓶
     *   - 槽 2..8：不接受任何物品
     *
     * 输出槽（9）：不接受。
     */
    @Override public boolean isValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 9) return false;
        String id = AutoCrafterTracker.idFromStack(stack);
        if (id == null) return false;
        String expected = slotBindingId(config, slot);
        return expected != null && expected.equals(id);
    }

    /** 返回缓存槽应绑定的物品 id；无绑定返回 null。*/
    public static String slotBindingId(AutoCrafterTracker.Config config, int slot) {
        if (slot < 0 || slot >= 9) return null;
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (String r : config.recipe) {
            if (r != null && !r.isBlank()) seen.add(r);
        }
        int i = 0;
        for (String id : seen) {
            if (i == slot) return id;
            i++;
        }
        return null;
    }

    // ---- SidedInventory ------------------------------------------------

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) return new int[]{9};
        // 只暴露有绑定的槽给漏斗，无绑定槽保持不接受
        int bindings = 0;
        for (int i = 0; i < 9; i++) if (slotBindingId(config, i) != null) bindings++;
        int[] out = new int[bindings];
        int k = 0;
        for (int i = 0; i < 9; i++) if (slotBindingId(config, i) != null) out[k++] = i;
        return out;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction dir) {
        if (dir == Direction.DOWN) return false;
        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return dir == Direction.DOWN && slot == 9;
    }

    public AutoCrafterTracker.Config config() { return config; }
}
