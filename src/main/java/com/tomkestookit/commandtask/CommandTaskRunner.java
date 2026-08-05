package com.tomkestookit.commandtask;

import com.tomkestookit.CarpetBotUtil;
import com.tomkestookit.SignCommandHandler;
import com.tomkestookit.TomkesTookitMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * 服务器启动时执行开机自启任务组。
 */
public final class CommandTaskRunner {

    private CommandTaskRunner() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> runStartupSets(server)));
    }

    public static void runStartupSets(MinecraftServer server) {
        if (!com.tomkestookit.TomkesTookitConfig.enabled("cmd.command_task")) return;
        CommandTaskStore.startupSets().stream()
            .filter(set -> set.enabled)
            .forEach(set -> runStartupSet(server, set));
    }

    public static void runStartupSet(MinecraftServer server, CommandTaskStore.StartupSet set) {
        if (set == null || !set.enabled) return;
        ServerCommandSource source = server.getCommandSource().withLevel(2).withSilent();
        for (String groupId : set.groupIds) {
            CommandTaskStore.TaskGroup group = CommandTaskStore.group(groupId);
            if (group == null) continue;
            runGroup(source, group, "启动集 " + set.name);
        }
    }

    public static void runGroup(ServerPlayerEntity player, CommandTaskStore.TaskGroup group) {
        if (player == null || group == null) return;
        runGroup(player.getCommandSource(), group, "任务组 " + group.name);
    }

    public static void runGroup(ServerPlayerEntity player, CommandTaskStore.TaskGroup group, String context) {
        if (player == null || group == null) return;
        runGroup(player.getCommandSource(), group, context);
    }

    public static void runGroup(ServerCommandSource source, CommandTaskStore.TaskGroup group, String context) {
        if (source == null || group == null) return;
        for (String command : group.commands) {
            String normalized = SignCommandHandler.normalize(command);
            if (normalized == null) {
                TomkesTookitMod.LOGGER.warn("[TomkesTookit] {} 跳过不允许的命令：{}", context, command);
                continue;
            }
            try {
                source.getServer().getCommandManager().executeWithPrefix(source, "/" + normalized);
            } catch (Exception e) {
                TomkesTookitMod.LOGGER.warn("[TomkesTookit] {} 执行失败：/{}", context, normalized, e);
            }
        }
    }
}
