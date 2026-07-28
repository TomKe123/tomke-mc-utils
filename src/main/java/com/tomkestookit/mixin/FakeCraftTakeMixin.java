package com.tomkestookit.mixin;

import com.tomkestookit.FakeCraftMarker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让"mixin 伪造的合成产物"在被拿走时也能扣掉输入。
 *
 * vanilla 的 {@link CraftingResultSlot#onTakeItem(PlayerEntity, ItemStack)} 需要匹配到
 * 一个 Recipe 才会扣输入。项目里若干 3×3 特殊配方是通过 mixin 直接塞结果槽实现的，
 * 没有真正的 Recipe 对象，所以 onTakeItem 走原版路径时不扣材料——玩家 shift-click
 * 可以无限白嫖。
 *
 * 我们给自己塞出去的产物打 {@link FakeCraftMarker#NBT_KEY} 标记；这里在 onTakeItem
 * HEAD 检查：若标记存在，手动扣每格非空输入 1 个 + 清标记，然后放行 vanilla（vanilla
 * 会再走一次"没匹配 recipe → 什么也不做"，但因为我们已经扣过材料，updateResult
 * 会重新计算：材料不够 → 结果空 → 玩家 shift 循环终止）。
 */
@Mixin(CraftingResultSlot.class)
public abstract class FakeCraftTakeMixin {

    @Shadow @Final private RecipeInputInventory input;

    @Inject(method = "onTakeItem", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$consumeInputsForFakeCraft(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!FakeCraftMarker.isMarked(stack)) return;
        FakeCraftMarker.clear(stack);
        // 关键：用 removeStack 扣输入，走 CraftingInventory 的 setStack/removeStack →
        // 触发 handler.onContentChanged → updateResult 重跑 → CraftingResultMixin TAIL
        // 会检查新输入，够料就塞新 fake result，shift-craft 循环得以继续。
        //
        // 之前用 getStack(i).decrement(1) 只改了 ItemStack.count，CraftingInventory
        // 不感知内容变了（其 markDirty() 是空的），结果槽保持我们上次塞的空壳 stack →
        // vanilla shift 循环外层 areItemsEqual 失败退出 → 一次只能合成 1 个。
        for (int i = 0; i < input.size(); i++) {
            if (!input.getStack(i).isEmpty()) {
                input.removeStack(i, 1);
            }
        }
        // 必须 cancel：vanilla onTakeItem 里 getRemainingStacks 回退分支会把
        // 输入原样 copy 塞回去，抵消我们扣的输入。
        // 副作用：跳过 vanilla 的 onCrafted 统计（stat/advancement），
        // 这类"魔改配方"不会计入合成次数成就，可以接受。
        ci.cancel();
    }
}
