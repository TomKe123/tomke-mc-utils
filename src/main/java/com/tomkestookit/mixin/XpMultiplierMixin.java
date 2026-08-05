package com.tomkestookit.mixin;

import com.tomkestookit.TomkesTookitConfig;
import com.tomkestookit.trinket.TrinketRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 经验倍率饰品：进入 addExperience 时按最高级饰品乘倍率。
 *
 * 只有正 experience 会被放大，避免修改经验消费路径（如附魔/铁砧扣经验会走
 * addExperienceLevels 与 applyEnchantmentCosts，不进 addExperience）。
 *
 * 叠加规则：取最高级饰品的倍率，不叠乘。
 */
@Mixin(PlayerEntity.class)
public abstract class XpMultiplierMixin {

    @ModifyVariable(
        method = "addExperience(I)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private int tomkestookit$scaleXp(int experience) {
        if (experience <= 0) return experience;
        if (!TomkesTookitConfig.enabled("item.trinket")) return experience;
        if (!((Object) this instanceof ServerPlayerEntity sp)) return experience;

        int mult = 1;
        if (TrinketRegistry.hasTrinketInBag(sp, "xp_crown")) mult = 100;
        else if (TrinketRegistry.hasTrinketInBag(sp, "xp_tome")) mult = 20;
        else if (TrinketRegistry.hasTrinketInBag(sp, "xp_scroll")) mult = 5;

        if (mult == 1) return experience;

        long scaled = (long) experience * mult;
        return scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
    }
}
