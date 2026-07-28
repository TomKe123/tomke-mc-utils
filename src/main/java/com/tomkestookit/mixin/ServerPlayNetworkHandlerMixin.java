package com.tomkestookit.mixin;

import com.tomkestookit.SignCommandHandler;
import com.tomkestookit.SignEditPrompt;
import com.tomkestookit.menu.MenuUi;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
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

        // 主手是告示牌 → 启动告示牌命令绑定流程（受 item.sign_command 控制）
        if (SignCommandHandler.isSignItem(player.getMainHandStack())) {
            if (!com.tomkestookit.TomkesTookitConfig.enabled("item.sign_command")) return;
            SignCommandHandler.tryStartItemBinding(player);
            ci.cancel();
            return;
        }
        // 否则打开主菜单（受 cmd.menu 控制）
        if (!com.tomkestookit.TomkesTookitConfig.enabled("cmd.menu")) return;
        MenuUi.open(player);
        ci.cancel();
    }

    /**
     * 拦截 onUpdateSign：如果是 SignEditPrompt 的临时告示牌，就用输入触发回调
     * 并取消 vanilla 处理，避免真正写入告示牌 NBT。
     */
    @Inject(method = "onUpdateSign", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$signPromptSubmit(UpdateSignC2SPacket packet, CallbackInfo ci) {
        // SignEditPrompt 是 admin/landmark/bot 菜单等多个特性共用的文本输入设施，
        // 只要有等待中的 prompt 就消费；这里不做单一 feature 判断，早退在使用它的
        // 各特性入口自己判断。
        if (SignEditPrompt.submit(player.getUuid(), packet.getPos(), packet.getText())) {
            ci.cancel();
        }
    }

    /**
     * 拦截聊天消息：如果玩家正在 SignCommandHandler 的绑定等待状态，
     * 消费本次聊天（不广播到公屏）并把消息作为命令内容交给 handler 处理。
     * 服务器主线程执行避免 world 修改在网络线程。
     */
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void tomkestookit$signCommandChatCapture(ChatMessageC2SPacket packet, CallbackInfo ci) {
        String msg = packet.chatMessage();
        // !c / !s 聊天快捷命令（切旁观 / 切生存）
        if (com.tomkestookit.TomkesTookitConfig.enabled("cmd.gamemode_shortcut")
            && com.tomkestookit.GamemodeShortcut.tryHandle(player, msg)) {
            ci.cancel();
            return;
        }
        if (!com.tomkestookit.TomkesTookitConfig.enabled("item.sign_command")) return;
        // 只有在等待绑定时才拦截；handler 内部判断
        if (!com.tomkestookit.SignCommandHandler.hasPending(player.getUuid())) return;
        // 调度到主线程执行世界写入
        player.getServer().execute(() -> SignCommandHandler.tryConsumeChat(player, msg));
        ci.cancel();
    }
}
