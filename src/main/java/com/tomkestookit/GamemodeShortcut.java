package com.tomkestookit;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

/**
 * 聊天前缀快捷命令：
 *
 *   !c → 切换到旁观者
 *   !s → 切回生存
 *
 * 只切模式，不记位置。玩家在哪 !s 就在哪切回生存。
 *
 * 该逻辑由 ServerPlayNetworkHandlerMixin.onChatMessage HEAD 调用；
 * 返回 true 表示消费了本条聊天（不广播）。
 */
public final class GamemodeShortcut {

    private GamemodeShortcut() {}

    /** 尝试消费一条聊天消息；返回 true 表示已处理，调用方应 cancel packet。*/
    public static boolean tryHandle(ServerPlayerEntity player, String message) {
        if (message == null) return false;
        String cmd = message.strip();
        if (cmd.equals("!c")) {
            player.getServer().execute(() -> toSpectator(player));
            return true;
        }
        if (cmd.equals("!s")) {
            player.getServer().execute(() -> toSurvival(player));
            return true;
        }
        return false;
    }

    private static void toSpectator(ServerPlayerEntity player) {
        GameMode current = player.interactionManager.getGameMode();
        if (current == GameMode.SPECTATOR) {
            player.sendMessage(Text.literal("§7[!c] 已经是旁观者").formatted(Formatting.GRAY), true);
            return;
        }
        player.changeGameMode(GameMode.SPECTATOR);
        player.sendMessage(Text.literal("§b[!c] 已切换到旁观 §8· §7输入 §f!s §7返回")
            .formatted(Formatting.AQUA), true);
    }

    private static void toSurvival(ServerPlayerEntity player) {
        GameMode current = player.interactionManager.getGameMode();
        if (current == GameMode.SURVIVAL) {
            player.sendMessage(Text.literal("§7[!s] 已经是生存").formatted(Formatting.GRAY), true);
            return;
        }
        player.changeGameMode(GameMode.SURVIVAL);
        player.sendMessage(Text.literal("§a[!s] 已切换到生存")
            .formatted(Formatting.GREEN), true);
    }
}
