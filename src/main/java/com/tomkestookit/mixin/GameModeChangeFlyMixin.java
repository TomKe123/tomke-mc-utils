package com.tomkestookit.mixin;

import com.tomkestookit.FlyCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复玩家切换游戏模式后 /fly 飞行状态不继承的 BUG。
 *
 * 原版 changeGameMode 会把 survival 的 abilities.allowFlying 重置为 false。
 * 如果玩家此前通过 /fly 获得了飞行权限，切模式再切回来就丢了。
 *
 * 修复：在 changeGameMode 返回后（TAIL），若玩家仍在 XP_DEBT 中（/fly 处于开启状态），
 * 且新模式是 survival / adventure（不是创造/旁观，那俩本身就能飞），重新赋予 allowFlying。
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class GameModeChangeFlyMixin {

    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "changeGameMode", at = @At("TAIL"))
    private void tomkestookit$restoreFlyAfterGameModeChange(GameMode gameMode,
                                                            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValue()) return; // 模式没变（false），不处理
            if (player.isCreative() || player.isSpectator()) return; // 这些模式自带飞行
            if (!FlyCommand.isEnabled(player.getUuid())) return; // 没开 /fly
            // 恢复飞行权限
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        } catch (Throwable ignored) {}
    }
}
