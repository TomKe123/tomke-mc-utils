package com.tomkestookit.trainingdummy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 训练假人调试命令（仅 op 可用）。
 *
 * /trainingdummy test <duration> - 开始 DPS 测试
 * /trainingdummy stop - 停止当前 DPS 测试
 */
public final class TrainingDummyCommand {

    private TrainingDummyCommand() {}

    public static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> register(dispatcher)
        );
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("trainingdummy")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("test")
                    .then(CommandManager.argument("duration", IntegerArgumentType.integer(1, 300))
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            ServerPlayerEntity player = source.getPlayer();
                            int duration = IntegerArgumentType.getInteger(ctx, "duration");

                            TrainingDummyHandler.startDpsTest(player, duration);
                            return 1;
                        })))
                .then(CommandManager.literal("stop")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ServerPlayerEntity player = source.getPlayer();

                        TrainingDummyHandler.endDpsTest(player);
                        return 1;
                    }))
        );
    }
}
