package com.tomkestookit;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 使用 Fabric ServerMessageEvents.ALLOW_CHAT_MESSAGE 拦截聊天消息，
 * 替代之前在 mixin onChatMessage HEAD cancel 的做法。
 *
 * 原因：1.20.1 的聊天签名验证系统在 mixin cancel onChatMessage 时会检测到
 * 签名链断裂并踢出玩家。ALLOW_CHAT_MESSAGE 在签名验证之后、广播之前触发，
 * 返回 false 可安全吞掉消息而不断签名链。
 */
public final class ChatInterceptHandler {

    private ChatInterceptHandler() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.getContent().getString();
            // !c / !s 聊天快捷命令
            if (TomkesTookitConfig.enabled("cmd.gamemode_shortcut")
                && GamemodeShortcut.tryHandle(sender, msg)) {
                return false; // 吞掉，不广播
            }
            // 告示牌命令绑定的聊天拦截
            if (!TomkesTookitConfig.enabled("item.sign_command")) return true;
            if (!SignCommandHandler.hasPending(sender.getUuid())) return true;
            sender.getServer().execute(() -> SignCommandHandler.tryConsumeChat(sender, msg));
            return false; // 吞掉，不广播
        });
    }
}
