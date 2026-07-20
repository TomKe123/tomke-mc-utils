package com.starterkit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * 把 Carpet 模组注册的所有指令（/carpet, /tick, /log, /player, /spawn, /info 等）
 * 的权限降为所有玩家可用。
 *
 * Carpet 在服务端启动期间注册指令，用 CommandRegistrationCallback 可能时机太早
 * 还没注册完，因此改用 SERVER_STARTED 事件，这时所有 mod 的指令已全部就位。
 *
 * 遍历根命令下所有节点，如果节点名属于已知 Carpet 指令列表，则递归解锁整棵子树。
 */
public final class CarpetPermission {

    private static final Logger LOGGER = LoggerFactory.getLogger(StarterKitMod.MOD_ID);
    private static final Predicate<ServerCommandSource> ALWAYS_ALLOW = src -> true;

    /**
     * Carpet 模组已知的所有根命令。
     * 包含 fabric-carpet 核心和 carpet-extra 常用指令。
     */
    private static final String[] CARPET_COMMANDS = {
        "carpet", "tick", "log", "player", "spawn", "info",
        "counter", "perimeterinfo", "distance", "draw", "script",
        "profile", "c", "s"
    };

    private CarpetPermission() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CommandDispatcher<ServerCommandSource> dispatcher =
                server.getCommandManager().getDispatcher();
            int count = 0;
            for (String cmd : CARPET_COMMANDS) {
                if (openTree(dispatcher, cmd)) {
                    count++;
                }
            }
            LOGGER.info("[StarterKit] 已对所有玩家开放 {} 个 Carpet 指令。", count);
        });
    }

    private static boolean openTree(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
        CommandNode<ServerCommandSource> node = dispatcher.getRoot().getChild(name);
        if (node == null) return false;
        openNodeRecursive(node);
        LOGGER.debug("[StarterKit] /{} 树已开放。", name);
        return true;
    }

    private static void openNodeRecursive(CommandNode<ServerCommandSource> node) {
        try {
            Field requirement = CommandNode.class.getDeclaredField("requirement");
            requirement.setAccessible(true);
            requirement.set(node, ALWAYS_ALLOW);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[StarterKit] 无法调整节点 {} 的权限：", node.getName(), e);
            return;
        }
        Collection<CommandNode<ServerCommandSource>> children = node.getChildren();
        for (CommandNode<ServerCommandSource> child : children) {
            openNodeRecursive(child);
        }
    }
}
