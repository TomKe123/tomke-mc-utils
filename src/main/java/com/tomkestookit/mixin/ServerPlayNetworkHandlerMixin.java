package com.tomkestookit.mixin;

import com.tomkestookit.menu.MenuUi;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift+F 打开主菜单。
 *
 * F 键在客户端发的是 PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND；
 * 我们拦截 onPlayerAction 的入口，如果 action == SWAP + player.isSneaking()
 * 就 openMenu + ci.cancel()，阻止 vanilla 交换主副手的默认动作。
 *
 * 非 sneak 时的 F 键正常走 vanilla（互换主副手），跟原版行为一致。
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow @Final public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$shiftFOpensMenu(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() != PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        if (!player.isSneaking()) return;
        // ci.cancel() 前先在服务器主线程调度菜单打开：本方法运行在网络线程，
        // 网络处理链会 forceMainThread —— 走到这里时已经在主线程了，直接调即可。
        MenuUi.open(player);
        ci.cancel();
    }
}
