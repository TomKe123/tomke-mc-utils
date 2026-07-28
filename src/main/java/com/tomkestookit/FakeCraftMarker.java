package com.tomkestookit;

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.WeakHashMap;

/**
 * 追踪"由 mixin 塞进结果槽的伪造合成产物"。
 *
 * 背景：项目里多个 mixin（面包精华 / 贪婪之瓶 / 自动合成器 / 物流终端 / 区域清除 / 区域填充）
 * 用同一套方式在 CraftingScreenHandler.updateResult TAIL 塞结果——但 vanilla 找不到 Recipe
 * 对象，因此 CraftingResultSlot.onTakeItem 不会执行标准的"扣输入 + 补 remainder"流程。
 * 玩家 shift-click 拿产物时会反复触发 updateResult → 又拿一份，最终材料没消耗、
 * 产物却把背包塞满。
 *
 * 实现细节（历史演化，两次踩坑后决定改成 identity 追踪）：
 *
 * 1) 一开始用 NBT tag：产物 NBT 里塞 TomkesTookitFakeCraft=true。
 *    ×  vanilla shift-craft 时 insertItem 会 stack.copy() 塞进背包，copy 也带这个 NBT，
 *       污染玩家背包物品，堆叠、比较都会出问题。
 *
 * 2) NBT tag + onTakeItem 里清标记。
 *    ×  vanilla insertItem 先 copy 再把原 stack setCount(0)，onTakeItem 拿到的是
 *       count=0 的原 stack；用 stack.isEmpty() 短路就永远不触发。已经 copy 出去的
 *       那份 NBT 无法回过头清除。
 *
 * 3) 最终方案：identity 追踪。塞结果时把 stack 实例本身塞进弱引用集合；
 *    onTakeItem 里 identity 比较（同实例即为我们塞的那份）。塞进背包的是 copy，
 *    是不同实例，不会被误识别，也没有 NBT 污染。
 *
 * 弱引用 + 定时清理保证不会内存泄漏——CraftingResultInventory.setStack 后旧 stack
 * 失去引用，WeakHashMap 会自动 GC。
 */
public final class FakeCraftMarker {

    /** identity set，key 是 stack 实例本身。用 WeakHashMap.keySet 避免内存泄漏。*/
    private static final java.util.Set<ItemStack> MARKED = Collections.newSetFromMap(new WeakHashMap<>());

    private FakeCraftMarker() {}

    public static ItemStack mark(ItemStack stack) {
        if (stack == null) return null;
        synchronized (MARKED) { MARKED.add(stack); }
        return stack;
    }

    /** identity 判定：仅当参数 stack 就是被 mark 过的那个实例时返回 true。*/
    public static boolean isMarked(ItemStack stack) {
        if (stack == null) return false;
        synchronized (MARKED) { return MARKED.contains(stack); }
    }

    /** 从追踪集合里移除，用于 onTakeItem 消费完后立刻释放。*/
    public static void clear(ItemStack stack) {
        if (stack == null) return;
        synchronized (MARKED) { MARKED.remove(stack); }
    }
}
