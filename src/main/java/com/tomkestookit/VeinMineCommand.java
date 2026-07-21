package com.tomkestookit;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /veinmine — 全员可用，切换该玩家的"树木连锁采集"开关。
 * 状态持久化到 VeinMineTracker。
 */
public final class VeinMineCommand {

    private VeinMineCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("veinmine")
                    .requires(src -> true)
                    .executes(ctx -> {
                        ServerPlayerEntity player;
                        try {
                            player = ctx.getSource().getPlayerOrThrow();
                        } catch (Exception e) {
                            return 0;
                        }
                        boolean now = VeinMineTracker.toggle(player.getUuid());
                        player.sendMessage(now
                            ? Text.literal("连锁采集已 §a开启§f（shift+左键 斧砍树 / 镐挖矿）")
                                .formatted(Formatting.GREEN)
                            : Text.literal("连锁采集已 §c关闭").formatted(Formatting.YELLOW),
                            false);
                        return 1;
                    })
            );
        });
    }
}
