package com.tomkestookit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

/**
 * /veinmine — 全员可用，切换个人连锁开关，并管理全服特定方块连锁白名单。
 */
public final class VeinMineCommand {

    private VeinMineCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("veinmine")
                    .requires(src -> true)
                    .executes(VeinMineCommand::toggle)
                    .then(CommandManager.literal("blocks")
                        .then(CommandManager.literal("list")
                            .executes(VeinMineCommand::listBlocks))
                        .then(CommandManager.literal("add")
                            .then(CommandManager.argument("block", BlockStateArgumentType.blockState(registryAccess))
                                .then(CommandManager.argument("limit", IntegerArgumentType.integer(1, VeinMineBlockConfig.MAX_LIMIT))
                                    .executes(VeinMineCommand::addBlock))))
                        .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("block", BlockStateArgumentType.blockState(registryAccess))
                                .executes(VeinMineCommand::removeBlock))))
            ));
    }

    private static int toggle(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }
        boolean now = VeinMineTracker.toggle(player.getUuid());
        player.sendMessage(now
            ? Text.literal("连锁采集已 §a开启§f（shift+左键：树/叶/矿最多128；白名单方块单次≤32）")
                .formatted(Formatting.GREEN)
            : Text.literal("连锁采集已 §c关闭").formatted(Formatting.YELLOW),
            false);
        return 1;
    }

    private static int listBlocks(CommandContext<ServerCommandSource> ctx) {
        Map<String, Integer> blocks = VeinMineBlockConfig.all();
        if (blocks.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("当前没有配置任何特定连锁方块。")
                .formatted(Formatting.YELLOW), false);
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("特定连锁方块（全服）：")
            .formatted(Formatting.GOLD), false);
        for (Map.Entry<String, Integer> e : blocks.entrySet()) {
            ctx.getSource().sendFeedback(() -> Text.literal("  §e" + e.getKey() + " §7单次≤ §f" + e.getValue()), false);
        }
        return blocks.size();
    }

    private static int addBlock(CommandContext<ServerCommandSource> ctx) {
        Block block = BlockStateArgumentType.getBlockState(ctx, "block").getBlockState().getBlock();
        int limit = IntegerArgumentType.getInteger(ctx, "limit");
        VeinMineBlockConfig.set(block, limit);
        String id = VeinMineBlockConfig.idOf(block);
        ctx.getSource().sendFeedback(() -> Text.literal("已设置特定连锁方块 §e" + id + " §f单次≤ §a" + VeinMineBlockConfig.clamp(limit))
            .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int removeBlock(CommandContext<ServerCommandSource> ctx) {
        Block block = BlockStateArgumentType.getBlockState(ctx, "block").getBlockState().getBlock();
        String id = VeinMineBlockConfig.idOf(block);
        boolean removed = VeinMineBlockConfig.remove(block);
        ctx.getSource().sendFeedback(() -> removed
            ? Text.literal("已移除特定连锁方块 §e" + id).formatted(Formatting.YELLOW)
            : Text.literal("该方块未配置：§e" + id).formatted(Formatting.RED), false);
        return removed ? 1 : 0;
    }
}
