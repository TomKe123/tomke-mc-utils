package com.tomkestookit;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

/**
 * 聊天快捷指令：
 *   - !c → 切换到旁观模式（Spectator）
 *   - !s → 切换到生存模式（Survival）
 *
 * 通过 ALLOW_CHAT_MESSAGE 拦截：命中就切换游戏模式并返回 false，
 * 消息不会被广播出去（发送者也不看到自己的 !c/!s 回显）。
 *
 * 全员可用；游戏模式变更走 server.execute 确保在主线程执行。
 * 这两条快捷键会覆盖玩家原本聊天里发的 "!c" / "!s" 文本——
 * 需要发这两个字符串的场景请通过其他方式表达。
 */
public final class ChatModeToggle {

    private ChatModeToggle() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString().trim();
            GameMode target;
            if (text.equalsIgnoreCase("!c")) {
                target = GameMode.SPECTATOR;
            } else if (text.equalsIgnoreCase("!s")) {
                target = GameMode.SURVIVAL;
            } else {
                return true; // 非快捷键 → 放行原始消息
            }

            // 切换 gamemode 必须在主线程。ALLOW_CHAT_MESSAGE 一般已在主线程，
            // 但为了万无一失走 server.execute 队列到下一个 tick 边界。
            sender.getServer().execute(() -> apply(sender, target));
            return false; // 拦截，不广播原始的 !c/!s
        });
    }

    /**
     * 切换玩家的 gamemode 并私下提示；供 ChatModeToggle 与菜单按钮共用。
     */
    public static void apply(ServerPlayerEntity player, GameMode mode) {
        if (player.interactionManager.getGameMode() == mode) {
            player.sendMessage(Text.literal("已经是" + label(mode) + "模式").formatted(Formatting.YELLOW), true);
            return;
        }
        player.changeGameMode(mode);
        player.sendMessage(Text.literal("已切换到 " + label(mode) + " 模式").formatted(Formatting.GREEN), true);
    }

    /** 中文模式名，供聊天/菜单显示。*/
    public static String label(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "生存";
            case CREATIVE -> "创造";
            case ADVENTURE -> "冒险";
            case SPECTATOR -> "旁观";
        };
    }
}
