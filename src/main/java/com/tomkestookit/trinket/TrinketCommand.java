package com.tomkestookit.trinket;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /trinketreset — 重置所有玩家的饰品栏解锁进度（回到初始 3 格）。
 * 仅 OP (权限 2) 可用。不清空已有饰品，超出容量的饰品保留存档但不生效。
 *
 * /trinketbag — 打开自己的饰品袋。
 */
public final class TrinketCommand {

    private TrinketCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                CommandManager.literal("trinketreset")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(ctx -> {
                        int count = TrinketStore.resetAllCapacity();
                        ctx.getSource().sendFeedback(() ->
                            Text.literal("§a[饰品] 已重置 " + count + " 位玩家的解锁进度到初始 3 格")
                                .formatted(Formatting.GREEN), true);
                        return count;
                    })
            )
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                CommandManager.literal("trinketbag")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
                            ctx.getSource().sendError(Text.literal("该命令只能由玩家执行"));
                            return 0;
                        }
                        com.tomkestookit.menu.TrinketBagMenu.open(player);
                        return 1;
                    })
            )
        );
    }
}
