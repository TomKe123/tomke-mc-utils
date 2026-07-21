package com.tomkestookit.mixin;

import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 把铁砧"过于昂贵"上限从 40 提升到 NEW_CAP。
 *
 * vanilla AnvilScreenHandler.updateResult 里 40 出现三处：
 *   - 内部数值分支（默认罚金/累加上限）
 *   - 改名单独 &gt;= 40 时 clamp 到 39 的路径
 *   - 主"过于昂贵"上限判定：levelCost &gt;= 40 且非创造 → 结果槽清空
 * 三处都用同一常量拉高，语义连贯（阈值全部一起搬）。
 *
 * 未同步动的 39：改名 clamp 目标值。留原样问题不大——只影响
 * "改名恰好卡在新上限之上"的极窄场景，不影响大多数玩家的组合工作。
 */
@Mixin(AnvilScreenHandler.class)
public class AnvilCostCapMixin {

    private static final int NEW_CAP = 100;

    @ModifyConstant(
        method = "updateResult",
        constant = @Constant(intValue = 40)
    )
    private int tomkestookit$raiseCostCap(int original) {
        return NEW_CAP;
    }
}
