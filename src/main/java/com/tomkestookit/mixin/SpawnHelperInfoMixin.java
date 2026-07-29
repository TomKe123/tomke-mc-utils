package com.tomkestookit.mixin;

import com.tomkestookit.PerPlayerSpawnCap;
import com.tomkestookit.TomkesTookitConfig;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.SpawnHelper.Info;

/**
 * 覆盖 vanilla SpawnHelper$Info.isBelowCap，改成"每玩家独立配额"。
 *
 * priority = 2000（远高于常规 mixin 的 1000）+ @At("HEAD") cancellable + setReturnValue：
 *   - 我们最先运行、直接把返回值定死并 cancel，其他 mod 对同一方法的 @Inject 全都
 *     不会到 tail、也不会走到原方法体，密度上限逻辑完全由我们接管
 *   - 关闭开关时不 setReturnValue，控制权原样交回 vanilla / 其他 mod
 *
 * 判定委托给 {@link PerPlayerSpawnCap#isBelowCap}，把 (SpawnHelper.Info)(Object) this
 * 传进去让它反查 world（Info 类本身没有 world 引用，
 * PerPlayerSpawnCap 里通过 ServerChunkManager.getSpawnInfo() 找到归属 world）。
 */
@Mixin(value = Info.class, priority = 2000)
public abstract class SpawnHelperInfoMixin {

    @Inject(method = "isBelowCap", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$perPlayerCap(SpawnGroup group, ChunkPos chunkPos,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!TomkesTookitConfig.enabled("world.per_player_spawn")) return;
        SpawnHelper.Info self = (SpawnHelper.Info) (Object) this;
        cir.setReturnValue(PerPlayerSpawnCap.isBelowCap(self, group, chunkPos));
    }
}
