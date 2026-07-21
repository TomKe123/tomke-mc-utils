package com.tomkestookit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.function.Predicate;

/**
 * 把 /kill 的自杀那条路径开放给所有玩家。
 *
 * 原版 /kill 树：
 *   /kill                → executes 杀自己
 *   /kill <targets>      → executes 杀 targets
 * 根节点 requires 是 permission level 2，所以普通玩家两条都不能用。
 *
 * 我们的做法：
 *   - 根节点 requires 改成恒 true，普通玩家可以敲 /kill 自杀
 *   - 子节点 targets 新加 requires = level 2，保留"杀别人"的 OP 门槛
 *
 * 这样普通玩家只能 /kill 或 /kill @s，写 @a / player_name / @e 之类会
 * 触发 targets 节点上的权限拦截。
 */
public final class KillPermission {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Predicate<ServerCommandSource> ALWAYS_ALLOW = src -> true;
    private static final Predicate<ServerCommandSource> OP_ONLY = src -> src.hasPermissionLevel(2);

    private KillPermission() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("kill");
            if (root == null) {
                LOGGER.warn("[TomkesTookit] /kill 节点未找到，跳过权限调整。");
                return;
            }
            setRequirement(root, ALWAYS_ALLOW);

            CommandNode<ServerCommandSource> targets = root.getChild("targets");
            if (targets != null) {
                setRequirement(targets, OP_ONLY);
                LOGGER.info("[TomkesTookit] /kill 自杀路径已开放；/kill <targets> 仍需 OP。");
            } else {
                LOGGER.warn("[TomkesTookit] /kill targets 子节点未找到，全部权限被放开！");
            }
        });
    }

    private static void setRequirement(CommandNode<ServerCommandSource> node,
                                       Predicate<ServerCommandSource> req) {
        try {
            Field f = CommandNode.class.getDeclaredField("requirement");
            f.setAccessible(true);
            f.set(node, req);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[TomkesTookit] 修改 /kill 节点 {} 的 requires 失败", node.getName(), e);
        }
    }
}
