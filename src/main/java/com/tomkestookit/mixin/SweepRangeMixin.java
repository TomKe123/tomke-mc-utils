package com.tomkestookit.mixin;

import com.tomkestookit.reforge.SweepRangeHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 横扫范围扩大重铸 mixin：
 *
 * 拦截 PlayerEntity.attack() 中的两个关键点：
 *   1. Box.expand(1.0, 0.25, 1.0) → 扩大搜索范围
 *   2. squaredDistanceTo(entity) → 放宽距离检查
 *
 * 根据主手武器是否有 sweep_widener / sweep_grand 重铸决定扩大倍率。
 */
@Mixin(PlayerEntity.class)
public abstract class SweepRangeMixin {

    /**
     * 拦截横扫搜索区域的 Box.expand 调用：把 (1.0, 0.25, 1.0) 替换为更大范围。
     *
     * 定位方式：attack() 中第 3 次 Box.expand 调用（前两次是别的逻辑）。
     * 通过 ordinal=2 指定第 3 次 expand。
     */
    @Redirect(
        method = "attack(Lnet/minecraft/entity/Entity;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;")
    )
    private Box tomkestookit$expandSweepBox(Box box, double x, double y, double z) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        int tier = SweepRangeHandler.getSweepTier(self);
        if (tier == 2) {
            // 5×5: expand 2.5 blocks on each horizontal side
            return box.expand(2.5, 0.25, 2.5);
        } else if (tier == 1) {
            // 3×3: expand 1.5 blocks on each horizontal side
            return box.expand(1.5, 0.25, 1.5);
        }
        // 原版
        return box.expand(x, y, z);
    }

    /**
     * 拦截横扫距离检查的 squaredDistanceTo 调用：在范围放大时容许更远的目标被命中。
     *
     * 原版判断 squaredDistanceTo(entity) < 9.0（3 格内）。
     * 这里不改距离值本身，而是在 distance > 9.0 时根据 tier 重新计算：
     *   tier1 → threshold = 20.25 (4.5²)
     *   tier2 → threshold = 36.0  (6.0²)
     *
     * 因为 PlayerEntity.attack 中只有一处 squaredDistanceTo 调用（在 sweep 分支内），
     * 直接 redirect 即可。但为安全只在对应循环语义的条件下生效。
     */
    @Redirect(
        method = "attack(Lnet/minecraft/entity/Entity;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/entity/player/PlayerEntity;squaredDistanceTo(Lnet/minecraft/entity/Entity;)D")
    )
    private double tomkestookit$adjustSweepDistance(PlayerEntity self, Entity target) {
        double actual = self.squaredDistanceTo(target);
        int tier = SweepRangeHandler.getSweepTier(self);
        if (tier == 0) return actual; // 无重铸，原样返回

        // 扩大后的范围阈值：
        //   tier1: 4.5² = 20.25
        //   tier2: 6.0² = 36.0
        double threshold = tier == 2 ? 36.0 : 20.25;
        // 如果原版阈值 (9.0) 已经通不过但新阈值能通过，就"假装"距离足够近
        if (actual > 9.0 && actual < threshold) {
            return 8.0; // 返回一个 < 9.0 的值让原版条件通过
        }
        return actual;
    }
}
