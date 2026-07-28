package com.tomkestookit;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 注册 /echest 和 /crafting 两个全员可用命令，分别打开
 * {@link PortableWorkstations#openEnderChest} / {@link PortableWorkstations#openCraftingTable}。
 *
 * 开关关闭时由 openXxx 内部返回 false + 发红色提示，命令 executes 直接返 0。
 */
public final class PortableCommand {

    private PortableCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("echest")
                    .requires(src -> true)
                    .executes(PortableCommand::openEnderChest)
            );
            dispatcher.register(
                CommandManager.literal("crafting")
                    .requires(src -> true)
                    .executes(PortableCommand::openCrafting)
            );
        });
    }

    private static int openEnderChest(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }
        return PortableWorkstations.openEnderChest(player) ? 1 : 0;
    }

    private static int openCrafting(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }
        return PortableWorkstations.openCraftingTable(player) ? 1 : 0;
    }
}
