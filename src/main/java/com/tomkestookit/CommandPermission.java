package com.tomkestookit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;

/**
 * 把一批 vanilla 命令的权限门槛降到 0，让所有玩家都能使用。
 *
 * vanilla 会在这些根节点上挂 `source.hasPermissionLevel(N)` 作为 requires。
 * CommandRegistrationCallback 在所有 vanilla 命令注册完之后触发，我们在这里
 * 走到目标节点，用反射把 requirement 换成恒为 true。
 *
 * 开放列表：
 *   - /tp、/teleport   传送
 *   - /seed            查看世界种子
 *   - /locate          定位结构 / 生物群系 / 兴趣点
 *
 * 注意：EntitySelectorReader 在 parse 阶段自带一层权限检查，
 * 用 @a / @e / @r 等选择器仍然会被拒（另有 EntitySelectorPermissionMixin
 * 处理该问题）。直接写玩家名或坐标不受影响。
 */
public final class CommandPermission {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Predicate<ServerCommandSource> ALWAYS_ALLOW = src -> true;

    /** 开放到 permission level 0 的命令根节点列表。*/
    private static final List<String> OPEN_COMMANDS = List.of(
        "tp",
        "teleport",
        "seed",
        "locate"
    );

    private CommandPermission() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String name : OPEN_COMMANDS) {
                openPermission(dispatcher, name);
            }
        });
    }

    private static void openPermission(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
        CommandNode<ServerCommandSource> node = dispatcher.getRoot().getChild(name);
        if (node == null) {
            LOGGER.warn("[TomkesTookit] /{} 节点未找到，跳过权限调整。", name);
            return;
        }
        try {
            Field requirement = CommandNode.class.getDeclaredField("requirement");
            requirement.setAccessible(true);
            requirement.set(node, ALWAYS_ALLOW);
            LOGGER.info("[TomkesTookit] /{} 已对所有玩家开放。", name);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[TomkesTookit] 无法调整 /{} 的权限：", name, e);
        }
    }
}
